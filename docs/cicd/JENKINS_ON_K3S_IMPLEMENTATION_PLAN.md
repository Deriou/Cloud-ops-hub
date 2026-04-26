# Jenkins-on-K3s CI/CD 实施计划

本文档基于 `docs/learning/L3-jenkins-on-k3s-handbook.md` 与当前仓库实际结构，规划 Cloud-Ops-Hub 第一版 CI/CD 落地路径。

目标不是一次性做成复杂平台，而是先把现有手工发布流程稳定自动化：

```text
git push -> Jenkins Pipeline
-> 路径感知测试/构建
-> 构建 linux/amd64 镜像
-> 推送 ACR
-> 回写 deployment image tag
-> kubectl apply
-> rollout 验证
-> 通过 Grafana/Loki/Prometheus 观察发布结果
```

## 1. 当前仓库基线

### 1.1 应用模块

- 后端父工程：`pom.xml`
- 公共模块：`common/common-core`
- 网关服务：`apps/gateway-portal`
- 博客服务：`apps/blog-service`
- 前端门户：`web`

### 1.2 构建入口

- 后端整体测试：`mvn test`
- 单后端模块构建：
  - `mvn package -pl apps/gateway-portal -am`
  - `mvn package -pl apps/blog-service -am`
- 前端构建：
  - `cd web && npm ci && npm run build`

### 1.3 镜像入口

- `apps/gateway-portal/Dockerfile`
- `apps/blog-service/Dockerfile`
- `web/Dockerfile`

三个业务镜像均已具备 multi-stage 构建与健康检查声明，后续 CI/CD 主要补齐自动化编排、镜像 tag 管理、推送与部署验证。

### 1.4 部署入口

- K8s 基础清单：`infra/k8s/base/`
- Kustomize 入口：`infra/k8s/base/kustomization.yaml`
- 目标命名空间：`cloud-ops`
- 镜像仓库：`crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub`

当前发布纪律保持不变：

- 不使用 `latest` 作为生产部署 tag。
- 镜像 tag 变化必须同步回写对应 `deployment.yaml`。
- ECS/K3s 节点最终只执行拉取代码、应用清单、验证发布状态。

## 2. 第一版实施边界

### 2.1 本期要做

1. Jenkins 部署到 K3s，并使用 PVC 持久化 Jenkins Home。
2. Jenkins 通过 Kubernetes Plugin 创建动态 Agent Pod。
3. 新增仓库根目录 `Jenkinsfile`。
4. 支持 `web`、`gateway-portal`、`blog-service` 三类业务模块构建与发布。
5. 支持路径感知构建，只构建受影响模块。
6. 构建并推送 `linux/amd64` 镜像到 ACR。
7. 自动更新对应 `infra/k8s/base/**/deployment.yaml` 的 image tag。
8. 执行 `kubectl apply -k infra/k8s/base/`。
9. 对受影响服务执行 `kubectl rollout status`。
10. 发布后通过 Grafana、Loki、Prometheus 观察 Pod 重建、错误日志与指标变化。

### 2.2 本期暂不做

- 不实现 Ops-Core 对 Jenkins 的异步触发与状态跟踪。
- 不把流水线运行记录写入业务数据库。
- 不自动执行数据库结构变更。
- 不做多环境矩阵发布。
- 不做复杂审批流。
- 不做蓝绿发布或金丝雀发布。

这些能力放到 Jenkins 主链路稳定之后再迭代。

## 3. 技术栈与职责

### 3.1 Jenkins Controller

Jenkins Controller 常驻运行在 K3s 中，负责：

- 保存任务配置、凭据、构建历史。
- 调度 Pipeline。
- 与 K8s API 通信，创建临时 Agent Pod。

Controller 不承担重构建任务，避免 8G 单机场景下内存长期膨胀。

建议资源：

```yaml
requests:
  cpu: 100m
  memory: 300Mi
limits:
  cpu: 1000m
  memory: 1024Mi
```

### 3.2 Kubernetes Dynamic Agent

Agent Pod 是每次构建临时创建的工作节点，负责：

- 拉取代码。
- 执行 Maven / npm 构建。
- 构建并推送镜像。
- 执行 kubectl 部署命令。

任务结束后 Agent Pod 自动销毁，减少空闲资源占用。

第一版建议使用一个合并型 Agent 模板，包含：

- `jnlp`：Jenkins inbound agent。
- `maven`：JDK 21 + Maven。
- `node`：Node 20 + npm。
- `build`：镜像构建工具。
- `kubectl`：部署与 rollout 验证。

后续如构建耗时或镜像体积明显增加，再拆分 Maven Agent 与 Node Agent。

### 3.3 Maven

负责 Java 后端测试与打包。

当前后端是 Maven multi-module：

- `common/common-core` 被两个后端服务依赖。
- `apps/gateway-portal` 与 `apps/blog-service` 都应通过 `-am` 自动带上依赖模块。

### 3.4 Node / Vite

负责前端静态资源构建。

第一版执行：

```bash
cd web
npm ci
npm run build
```

构建产物进入 `web/Dockerfile` 的 nginx 镜像。

### 3.5 镜像构建工具

可选路线：

1. Docker-in-Docker
   - 优点：命令与当前手工流程一致。
   - 缺点：通常需要 privileged，安全面和资源开销更大。
2. Kaniko
   - 优点：适合 K8s 内无 Docker daemon 构建。
   - 缺点：需要调整构建命令与缓存配置。
3. BuildKit rootless
   - 优点：能力强，适合长期演进。
   - 缺点：首次配置复杂度高于 Docker CLI。

建议第一版优先评估 Kaniko。若 Jenkins Agent 配置成本过高，可先用 Docker-in-Docker 跑通学习闭环，再迁移到 Kaniko 或 BuildKit rootless。

### 3.6 ACR

ACR 保存业务镜像与基础镜像。

Jenkins 中只保存凭据，不把 ACR 用户名、密码、token 写入仓库。

### 3.7 kubectl / Kustomize

用于应用仓库内声明式清单：

```bash
kubectl apply -k infra/k8s/base/
```

发布验证使用：

```bash
kubectl -n cloud-ops rollout status deploy/web
kubectl -n cloud-ops rollout status deploy/gateway-portal
kubectl -n cloud-ops rollout status deploy/blog-service
```

### 3.8 Git

Git 继续作为线上期望状态来源。

Jenkins 自动构建新镜像后，需要把新 image tag 回写到对应 Deployment 文件并提交，例如：

- `infra/k8s/base/web/deployment.yaml`
- `infra/k8s/base/gateway/deployment.yaml`
- `infra/k8s/base/blog/deployment.yaml`

Jenkins 回写 commit 建议使用统一格式：

```text
chore(cicd): bump image tags [skip ci]
```

`[skip ci]` 用于避免 Jenkins 因自己提交的 tag 变更再次触发完整发布。

## 4. 路径感知策略

Pipeline 通过 `git diff --name-only` 判断变更范围。

### 4.1 路径规则

| 变更路径 | 动作 |
| --- | --- |
| `web/**` | 测试/构建/发布 web |
| `apps/gateway-portal/**` | 测试/构建/发布 gateway-portal |
| `apps/blog-service/**` | 测试/构建/发布 blog-service |
| `common/**` | 测试后端，并发布 gateway-portal 与 blog-service |
| `pom.xml` | 测试后端，并发布 gateway-portal 与 blog-service |
| `infra/k8s/**` | 校验并 apply 清单，不构建镜像 |
| `docs/**` | 默认不发布 |
| `README.md` | 默认不发布 |

### 4.2 特殊情况

如果无法可靠获取 diff，例如首次构建、手动重放、分支历史不足，则默认进入保守模式：

- 后端全量测试。
- 前端构建。
- 三个业务镜像全部构建发布。

这样会牺牲速度，但可以避免漏发。

## 5. 镜像 tag 策略

第一版推荐 tag：

```text
<build-number>-<git-short-sha>
```

示例：

```text
web:128-a1b2c3d
gateway-portal:128-a1b2c3d
blog-service:128-a1b2c3d
```

如果一次流水线只发布 web，则只更新 web deployment。未受影响模块不改 tag。

优势：

- 每次构建唯一。
- 可以从镜像反查 commit。
- 回滚时可以明确选择旧 tag。
- 避免递增版本号人工协调。

## 6. Pipeline 阶段设计

### 6.1 Checkout

拉取代码，记录：

- commit SHA
- branch
- build number
- 变更文件列表

### 6.2 Detect Changes

根据路径规则生成三个布尔值：

- `BUILD_WEB`
- `BUILD_GATEWAY`
- `BUILD_BLOG`

同时生成：

- `APPLY_INFRA`
- `BACKEND_TEST_REQUIRED`
- `FULL_BUILD_FALLBACK`

### 6.3 Test

后端：

```bash
mvn test
```

前端：

```bash
cd web
npm ci
npm run build
```

后续可以把前端 build 与镜像 build 的依赖安装缓存优化掉，第一版先保持简单。

### 6.4 Image Build

web：

```bash
docker build --platform linux/amd64 \
  -t $REGISTRY/web:$IMAGE_TAG \
  web/
```

gateway：

```bash
docker build --platform linux/amd64 \
  -f apps/gateway-portal/Dockerfile \
  -t $REGISTRY/gateway-portal:$IMAGE_TAG \
  .
```

blog：

```bash
docker build --platform linux/amd64 \
  -f apps/blog-service/Dockerfile \
  -t $REGISTRY/blog-service:$IMAGE_TAG \
  .
```

如果采用 Kaniko，需要将上述 Docker 命令替换为 Kaniko executor 命令。

### 6.5 Image Push

推送受影响模块的镜像到 ACR。

推送前 Jenkins 使用凭据登录 ACR，凭据只存在 Jenkins Credentials 中。

### 6.6 Update Manifests

更新受影响模块的 Deployment image：

- web -> `infra/k8s/base/web/deployment.yaml`
- gateway -> `infra/k8s/base/gateway/deployment.yaml`
- blog -> `infra/k8s/base/blog/deployment.yaml`

建议使用 `yq` 或受控脚本修改 YAML，避免用脆弱的文本替换。

### 6.7 Commit Manifest Changes

如果 Deployment 文件发生变更：

```bash
git add infra/k8s/base
git commit -m "chore(cicd): bump image tags [skip ci]"
git push
```

如果没有变更，不提交。

### 6.8 Deploy

执行：

```bash
kubectl apply -k infra/k8s/base/
```

### 6.9 Rollout Verify

只验证受影响服务：

```bash
kubectl -n cloud-ops rollout status deploy/web --timeout=180s
kubectl -n cloud-ops rollout status deploy/gateway-portal --timeout=240s
kubectl -n cloud-ops rollout status deploy/blog-service --timeout=300s
```

blog-service 依赖 MySQL 与 PVC，超时时间可略长。

### 6.10 Post Actions

无论成功失败，都输出：

- 发布模块。
- image tag。
- commit SHA。
- rollout 结果。
- 失败时的关键排查命令。

后续 Ops-Core 落地后，再把这些信息写入 `/api/v1/ops/pipelines/runs`。

## 7. Jenkins 与 K8s 资源规划

### 7.1 Namespace

建议 Jenkins 放在独立命名空间：

```text
cicd
```

业务应用继续放在：

```text
cloud-ops
```

### 7.2 Jenkins 资源

需要创建：

- Namespace：`cicd`
- PVC：`jenkins-home`
- Deployment 或 StatefulSet：`jenkins`
- Service：`jenkins`
- Ingress 或 NodePort
- ServiceAccount：`jenkins`
- Role / RoleBinding：允许 Jenkins 创建 Agent Pod
- 部署权限：允许 Jenkins apply `cloud-ops` 命名空间资源

### 7.3 权限原则

第一版可以先授予 Jenkins 对 `cloud-ops` namespace 的部署权限，避免卡在 RBAC 细节。

后续再收敛到最小权限：

- 读取 Pod、Deployment、Service、Ingress。
- 更新 Deployment。
- 创建/读取临时 Pod。
- 读取 rollout 状态。

## 8. 凭据规划

Jenkins Credentials 至少需要：

| 凭据 | 用途 |
| --- | --- |
| Git 凭据 | checkout、回写 deployment tag |
| ACR 凭据 | 登录并推送镜像 |
| kubeconfig 或 ServiceAccount token | 执行 kubectl apply / rollout |

禁止进入 Git 的内容：

- ACR 密码或 token。
- Git token。
- kubeconfig 私钥。
- Jenkins admin 初始密码。

## 9. 可观测性联动

发布后通过现有 PLG 栈观察：

### 9.1 Prometheus

关注：

- Pod 是否重建。
- JVM 指标是否恢复。
- HTTP 请求错误率是否异常。
- 内存占用是否接近 limit。

### 9.2 Loki / Promtail

关注：

- 新 Pod 启动日志。
- Spring Boot 启动失败。
- Nginx 访问异常。
- ImagePullBackOff、CrashLoopBackOff 相关事件。

### 9.3 Grafana

将 Jenkins 发布时刻与以下图表对齐：

- 服务可用性。
- Pod 重启。
- JVM 内存。
- HTTP 错误率。
- Loki 日志异常。

第一版不要求 Jenkins 主动写 Grafana 注释，人工对齐时间窗即可。

## 10. 风险与应对

### 10.1 Jenkins 自动提交造成循环触发

应对：

- Jenkins 回写 commit 使用 `[skip ci]`。
- Pipeline 开头识别 commit message，命中后直接跳过。

### 10.2 K3s 内构建镜像权限不足

应对：

- 优先评估 Kaniko。
- 如果临时使用 Docker-in-Docker，需要明确 privileged 风险。
- 保持构建并发为 1。

### 10.3 8G 单机资源不足

应对：

- Jenkins executors 设置为 0 或 1。
- 禁止并发构建。
- Agent Pod 设置资源限制。
- 构建历史与 artifact 设置保留策略。
- Maven 与 npm 缓存谨慎启用，避免 PVC 膨胀。

### 10.4 common-core 变更漏发

应对：

- `common/**` 和根 `pom.xml` 变更强制发布两个后端服务。

### 10.5 Deployment tag 已回写但 rollout 失败

应对：

1. 保留失败 commit，便于定位。
2. 使用 `kubectl rollout undo` 临时恢复。
3. 或提交新的回滚 commit，把 deployment tag 改回上一稳定版本。
4. 从 Loki 和 `kubectl logs --previous` 定位失败原因。

### 10.6 数据库结构变更无法自动处理

应对：

- 第一版不自动执行 SQL。
- 涉及 DB migration 的发布必须人工执行 SOP。
- 后续考虑引入 Flyway 或 Liquibase。

## 11. 分阶段实施计划

### Phase 0：准备检查

目标：确认 CI/CD 前置条件齐全。

任务：

- 确认 K3s 可用。
- 确认 `cloud-ops` namespace 正常。
- 确认 `acr-secret` 可拉取镜像。
- 确认 ACR 可推送业务镜像。
- 确认本地可执行 `mvn test`。
- 确认本地可执行 `cd web && npm ci && npm run build`。

验收：

- 手工发布流程仍可按 `docs/DEPLOYMENT_PLAYBOOK.md` 跑通。

### Phase 1：部署 Jenkins

目标：Jenkins UI 可访问，配置可持久化。

任务：

- 新增 Jenkins K8s 清单或 Helm values。
- 创建 `cicd` namespace。
- 创建 Jenkins PVC。
- 配置资源 request/limit。
- 暴露 Jenkins UI。
- 安装最小插件集合。

验收：

- Jenkins 重启后配置不丢失。
- Jenkins UI 可访问。
- Controller 空闲资源占用可接受。

### Phase 2：配置动态 Agent

目标：Pipeline 任务在临时 Pod 中执行。

任务：

- 配置 Kubernetes Cloud。
- 配置 Agent Pod Template。
- 配置 Maven、Node、构建工具、kubectl 容器。
- 验证任务结束后 Agent Pod 自动销毁。

验收：

- Jenkins 执行测试任务时创建 Agent Pod。
- 任务结束后 Agent Pod 被回收。

### Phase 3：最小 Pipeline

目标：先跑通 web 单模块发布。

任务：

- 新增根目录 `Jenkinsfile`。
- 固定构建 web。
- 构建镜像并推送 ACR。
- 更新 `infra/k8s/base/web/deployment.yaml`。
- commit/push tag 变更。
- apply 并 rollout web。

验收：

- web 镜像可推送。
- web Deployment tag 已回写 Git。
- `kubectl -n cloud-ops rollout status deploy/web` 成功。
- 线上页面出现本次改动。

### Phase 4：后端模块接入

目标：gateway 与 blog 纳入同一流水线。

任务：

- 增加 Maven test 阶段。
- 增加 gateway 镜像构建、推送、deployment tag 回写。
- 增加 blog 镜像构建、推送、deployment tag 回写。
- 增加对应 rollout 验证。

验收：

- gateway 可单独发布。
- blog 可单独发布。
- common 变更会触发两个后端服务发布。

### Phase 5：路径感知构建

目标：只构建受影响模块。

任务：

- 实现 `git diff --name-only` 路径识别。
- 实现 fallback 全量构建。
- 实现 docs-only 跳过。
- 实现 `[skip ci]` 跳过。

验收：

- 修改 `web/**` 只发布 web。
- 修改 `apps/gateway-portal/**` 只发布 gateway。
- 修改 `apps/blog-service/**` 只发布 blog。
- 修改 `common/**` 发布两个后端。
- 修改 `docs/**` 不触发发布。

### Phase 6：可观测与故障演练

目标：证明发布和监控链路能对应起来。

任务：

- 记录一次成功发布的 Jenkins build number、commit SHA、image tag。
- 在 Grafana 中观察 Pod 重建时间窗。
- 在 Loki 中查看新 Pod 启动日志。
- 人工制造一次可控失败，例如错误镜像 tag 或 readiness 失败。
- 验证失败日志和 rollout timeout 是否清晰。

验收：

- 能通过 Jenkins 日志定位失败阶段。
- 能通过 Grafana/Loki 关联发布影响。
- 能按回滚流程恢复服务。

## 12. 第一版 DoD

- Jenkins 在 K3s 中稳定运行。
- Jenkins Home 已持久化。
- 动态 Agent Pod 可创建并自动销毁。
- `web`、`gateway-portal`、`blog-service` 均可通过 Pipeline 发布。
- 路径感知构建生效。
- 镜像推送到 ACR。
- Deployment tag 自动回写 Git。
- `kubectl apply -k infra/k8s/base/` 自动执行。
- 受影响服务 rollout 验证通过。
- 失败时 Jenkins 日志能定位到具体阶段。
- 发布结果能在 Grafana/Loki/Prometheus 中找到对应时间窗。

## 13. 后续演进

第一版稳定后，再考虑：

- Ops-Core 接入 Jenkins API，实现门户触发与状态查询。
- Pipeline 运行结果回写业务接口。
- Grafana annotation 标记发布时间。
- Flyway/Liquibase 管理数据库迁移。
- Kaniko/BuildKit 缓存优化。
- 多环境发布，例如 dev/staging/prod。
- 手动审批关卡。
- 发布诊断自动生成。
- 更细粒度 RBAC。
