# Web 前端更新 AI 操作手册

本文档给 AI / 协作者使用，用于在 Cloud-Ops-Hub 中安全修改并发布 `web` 前端模块。

当前前端发布已经接入 Jenkins 单模块流水线：

```text
本地修改 web 代码
-> commit + push 到 GitHub main
-> Jenkins 手动 Build Now
-> Jenkins 构建 web 镜像并推送 ACR
-> Jenkins 回写 infra/k8s/base/web/deployment.yaml
-> Jenkins commit + push 回 GitHub
-> Jenkins 发布 web Deployment
-> 验证 deriou.com 页面
```

## 1. AI 工作边界

### 1.1 可以做

- 修改 `web/**` 前端代码。
- 修改前端相关文档。
- 必要时修改 `Jenkinsfile.web`。
- 必要时修改 `docs/cicd/**` 中的 Web CI/CD 文档。
- 本地运行 `cd web && npm run build` 做构建验证。
- 提交并推送业务代码到 GitHub。

### 1.2 默认不要做

- 不要手动修改 `infra/k8s/base/web/deployment.yaml` 中的镜像 tag。
- 不要手动执行 `kubectl set image` 发布 web。
- 不要直接在 ECS 上改前端文件。
- 不要把 GitHub token、ACR 密码写入代码或文档。
- 不要把 Jenkins 暴露到公网。

例外：

- 如果是修复 Jenkins Pipeline 或版本规则，才允许修改 `Jenkinsfile.web`。
- 如果 Jenkins 自动回写失败，才根据人工确认处理 `deployment.yaml`。

## 2. 日常前端更新流程

### 2.1 本地修改前检查

```bash
git status
```

作用：

- 查看当前工作区是否干净。
- 避免覆盖用户未提交修改。

```bash
git pull origin main
```

作用：

- 拉取 GitHub 最新代码。
- Jenkins 发布后会自动提交 image tag，因此本地修改前应先同步。

### 2.2 修改前端代码

优先只改：

```text
web/src/**
web/public/**
web/package.json
web/package-lock.json
web/.env.example
```

如果不是必要，不要改：

```text
infra/k8s/base/web/deployment.yaml
Jenkinsfile.web
```

### 2.3 本地构建验证

```bash
cd web
npm run build
```

作用：

- 验证 TypeScript 与 Vite 构建。
- Jenkins 后续构建镜像时也会执行 `npm run build`。

回到仓库根目录：

```bash
cd ..
```

### 2.4 提交前检查

```bash
git status --short
```

作用：

- 确认本次只包含预期文件。

```bash
git diff --stat
```

作用：

- 快速查看修改规模。

### 2.5 提交并推送

```bash
git add web
git commit -m "feat(web): update frontend"
git push origin main
```

作用：

- 将前端代码推送到 GitHub。
- Jenkins 会从 GitHub `main` 拉取代码。

提交信息可以按实际内容调整，例如：

```bash
git commit -m "fix(web): reduce blog tag list"
git commit -m "feat(web): improve ops cluster entry"
git commit -m "style(web): polish dashboard cards"
```

## 3. Jenkins 发布操作

进入 Jenkins：

```text
cloud-ops-web-pipeline
-> Build Now
```

作用：

- 手动触发 Web 单模块发布。
- 当前阶段不使用 GitHub Webhook 自动触发。

查看构建日志：

```text
cloud-ops-web-pipeline
-> 本次构建号
-> Console Output
```

重点确认：

- `Checkout` 成功。
- `Prepare Tag` 生成新镜像 tag。
- `Docker Login` 成功。
- `Build Image` 成功。
- `Push Image` 成功。
- `Update Manifest` 修改 `deployment.yaml`。
- `Commit Manifest` 成功 push 回 GitHub。
- `Deploy Web` 成功 apply。
- `Verify` 成功 rollout。

## 4. 发布后本地同步

Jenkins 发布成功后，会自动生成一次提交：

```text
chore(cicd): bump web image <image-tag> [skip ci]
```

所以本地仓库需要同步：

```bash
git pull origin main
```

作用：

- 拉取 Jenkins 自动提交的 `deployment.yaml` tag。
- 避免本地仓库落后，后续修改时产生冲突。
- 避免将来在 ECS 手动 `kubectl apply` 时回滚到旧镜像。

注意：

- 这一步不是为了完成发布。
- 发布已经由 Jenkins 完成。
- 这一步是为了让本地 Git 状态追上 Jenkins 的自动提交。

## 5. 发布后验证

### 5.1 检查 Git tag 回写

```bash
git log --oneline -5
```

作用：

- 确认最近有 Jenkins 自动提交。

示例：

```text
chore(cicd): bump web image 0.0.7-web-18-a1b2c3d [skip ci]
```

```bash
grep -n "image:" infra/k8s/base/web/deployment.yaml
```

作用：

- 确认 Git 中的 web 镜像 tag 已更新。

### 5.2 检查线上页面

```bash
curl -I http://deriou.com/
```

作用：

- 验证首页可访问。

```bash
curl -I http://deriou.com/ops/cluster
```

作用：

- 验证运维入口页可访问。

### 5.3 可选 ECS 检查

以下命令在 ECS 上执行：

```bash
kubectl -n cloud-ops get deploy web -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

作用：

- 查看 K3s 中实际运行的 web 镜像。
- 应与 Git 中 `infra/k8s/base/web/deployment.yaml` 一致。

```bash
kubectl -n cloud-ops rollout status deploy/web --timeout=180s
```

作用：

- 确认 web Deployment 已完成滚动更新。

```bash
kubectl -n cloud-ops get pod -l app=web -o wide
```

作用：

- 查看 web Pod 是否运行正常。

## 6. 镜像 Tag 规范

当前 `web` 模块由 `Jenkinsfile.web` 发布。

镜像 tag 格式：

```text
0.0.7-web-${BUILD_NUMBER}-${gitShortSha}
```

示例：

```text
0.0.7-web-18-a1b2c3d
```

完整镜像示例：

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/web:0.0.7-web-18-a1b2c3d
```

含义：

| 片段 | 含义 |
| --- | --- |
| `0.0.7` | 当前 web 发布线版本前缀。 |
| `web` | 模块名。 |
| `${BUILD_NUMBER}` | Jenkins 构建号。 |
| `${gitShortSha}` | 当前源码 commit 的 7 位短 SHA。 |

### 6.1 是否每次发布都要改版本号

不需要。

日常每次点击 Jenkins `Build Now` 时，Jenkins 会自动追加：

```text
BUILD_NUMBER
gitShortSha
```

所以即使 `0.0.7-web` 前缀不变，每次构建也会得到不同 tag。

例如：

```text
0.0.7-web-18-a1b2c3d
0.0.7-web-19-b2c3d4e
0.0.7-web-20-c3d4e5f
```

因此：

- 普通前端代码发布：不需要修改 `Jenkinsfile.web`。
- Jenkins 会自动生成唯一 tag。
- Jenkins 会自动回写 `infra/k8s/base/web/deployment.yaml`。

### 6.2 什么时候修改 `0.0.7-web`

只有当项目进入新的阶段版本线时，才修改 `Jenkinsfile.web` 中的：

```groovy
IMAGE_VERSION_PREFIX = '0.0.7-web'
```

建议场景：

- 完成一个阶段性功能，例如 observability、CI/CD、博客发布能力。
- 简历项目进入新的演示版本。
- 希望从 tag 上区分不同阶段。

示例：

```groovy
IMAGE_VERSION_PREFIX = '0.0.8-web'
```

之后 Jenkins 会生成：

```text
0.0.8-web-1-a1b2c3d
0.0.8-web-2-b2c3d4e
```

注意：

- 不要每次小改都手动递增 `0.0.x`。
- 不要手动修改 `infra/k8s/base/web/deployment.yaml` 的镜像 tag。
- Jenkins 发布成功后会自动提交 Deployment tag 变化。

### 6.3 为什么当前写在 Jenkinsfile 中

当前项目处于学习与简历展示阶段，第一目标是跑通：

```text
Jenkins -> ACR -> K3s -> Grafana 验证
```

把版本前缀写在 `Jenkinsfile.web` 中有几个优点：

- 简单直接。
- Jenkins UI 不需要额外参数。
- 版本规则随流水线一起进入 Git 管理。
- 对当前单模块 Web 发布最容易维护。

后续如果模块增多，可以演进为：

- 每个模块一个 `VERSION` 文件。
- Jenkins 参数化输入版本前缀。
- 统一版本配置文件，例如 `ci/version.env`。
- 根据 Git tag 自动生成镜像版本。

### 6.4 后续后端模块建议规则

当 `gateway-portal` 与 `blog-service` 接入 Jenkins 后，建议采用类似规则：

```text
0.0.6-gateway-${BUILD_NUMBER}-${gitShortSha}
0.0.6-blog-${BUILD_NUMBER}-${gitShortSha}
```

对应部署文件：

```text
infra/k8s/base/gateway/deployment.yaml
infra/k8s/base/blog/deployment.yaml
```

原则保持一致：

- Jenkins 自动生成唯一 tag。
- Jenkins 自动回写对应 Deployment。
- Jenkins 自动 commit + push。
- ECS 不再手动改镜像 tag。

## 7. AI 处理前端更新的判断规则

### 7.1 只改前端 UI

如果用户要求：

```text
修改页面样式
调整文案
新增前端入口
修复前端展示问题
```

AI 应：

1. 修改 `web/**`。
2. 运行 `cd web && npm run build`。
3. 提交并 push。
4. 告诉用户去 Jenkins 点击 `Build Now`。

不应：

- 修改 `deployment.yaml`。
- 修改 `Jenkinsfile.web`。
- 登录 ECS 发布。

### 7.2 修改 CI/CD 行为

如果用户要求：

```text
修改镜像 tag 规则
修改 Jenkins 发布阶段
修改 ACR 仓库
修改 kubectl 发布方式
```

AI 才应考虑修改：

```text
Jenkinsfile.web
docs/cicd/**
infra/k8s/cicd/jenkins/**
```

修改后需要明确提醒：

- 这是流水线行为变更。
- 需要先提交 GitHub。
- 需要 Jenkins 重新拉取新 `Jenkinsfile.web`。

### 7.3 Jenkins 已发布成功后

如果用户截图显示 Jenkins 构建绿色成功，AI 应要求用户或自行确认：

```bash
git pull origin main
git log --oneline -5
grep -n "image:" infra/k8s/base/web/deployment.yaml
curl -I http://deriou.com/ops/cluster
```

判断完成标准：

- Jenkins 构建绿色。
- GitHub 出现 Jenkins bump commit。
- `deployment.yaml` tag 已更新。
- 线上页面可访问。

## 8. 当前 Web CI/CD 完成状态

截至本文档更新时，Web 单模块 CI/CD 第一阶段已完成：

- Jenkins 已部署在 K3s `cicd` namespace。
- Jenkins UI 可通过本地隧道访问。
- Jenkins 已具备 Docker build / push 能力。
- Jenkins 已具备 kubectl 发布能力。
- Jenkins Credentials 已按固定 ID 设计。
- `Jenkinsfile.web` 已落入仓库。
- `cloud-ops-web-pipeline` 已可手动触发。
- Web 镜像 tag 已由 Jenkins 自动生成。
- Jenkins 已能回写 `infra/k8s/base/web/deployment.yaml` 并推送 GitHub。
- Jenkins 已能发布 `web` Deployment。

当前仍未完成：

- GitHub Webhook 自动触发。
- `gateway-portal` 后端流水线。
- `blog-service` 后端流水线。
- 多模块路径感知构建。
- Jenkins 动态 Agent / Kaniko / BuildKit rootless 演进。

## 9. 面试表达

可以这样描述：

> Web 模块已经接入 Jenkins 单模块 CI/CD。开发者将前端代码推送到 GitHub 后，手动触发 Jenkins Pipeline，流水线会构建 linux/amd64 镜像、推送到阿里云 ACR、生成包含构建号和 Git 短 SHA 的唯一 tag、回写 Kubernetes Deployment 并提交回 GitHub，最后通过 kubectl 发布到 K3s。这样保证 Git 仓库中的期望状态和集群实际运行版本一致，避免手动 apply 导致版本回滚。

## 10. 当前项目完成度评估

### 10.1 CI/CD 当前状态

已完成：

- Jenkins 已部署到 K3s `cicd` namespace。
- Jenkins Home 已通过 PVC 持久化。
- Jenkins 可通过本地隧道访问 UI。
- Jenkins 已能使用 Docker 构建并推送镜像。
- Jenkins 已能使用 kubectl 发布 `cloud-ops` 中的 `web` Deployment。
- `Jenkinsfile.web` 已完成并验证 Web 单模块发布成功。
- Web 发布已经实现 GitHub 回写 Deployment tag，避免 Git 与集群状态漂移。

未完成：

- `gateway-portal` 流水线。
- `blog-service` 流水线。
- GitHub Webhook 自动触发。
- 多模块路径感知构建。
- Jenkins 动态 Agent / Kaniko / BuildKit rootless。
- Jenkins 插件与权限的进一步安全加固。

判断：

```text
Web 单模块 CI/CD 第一阶段已完成。
整体 CI/CD 处于“单模块闭环完成，后端与自动触发待扩展”状态。
```

### 10.2 可观测性当前状态

已完成：

- `gateway-portal`、`blog-service` 已接入 Actuator + Micrometer Prometheus。
- Prometheus 已部署并抓取业务服务指标。
- Loki + Promtail 已部署，能按 `namespace=cloud-ops` 查询业务日志。
- traceId 已能贯通业务响应、后端日志和 Loki 查询。
- Grafana 已部署，并接入 Prometheus 与 Loki。
- Grafana 已有 Cloud Ops Overview 最小看板。
- 前端 `/ops/cluster` 已提供 Grafana 看板入口与联查说明。

需注意：

- Loki 如果仍使用非持久化配置，Pod 删除后历史日志会丢。
- Prometheus Targets 不公网暴露，仍通过本地隧道 / port-forward 查看。
- 前端 `/ops/cluster` 当前主要是入口层，不是自研实时监控大屏。

判断：

```text
PLG 可观测性展示闭环已完成第一版。
后续重点是告警规则、日志持久化、前端真实指标摘要。
```

### 10.3 应用与部署当前状态

已完成：

- `web`、`gateway-portal`、`blog-service` 已容器化并部署到 K3s。
- ACR 私有镜像仓库已作为统一镜像来源。
- K8s base 清单已成为业务部署主要入口。
- `deployment.yaml` tag 同步纪律已建立：发布后的镜像 tag 必须回写 Git。

未完成：

- 后端服务自动化发布。
- 数据库变更自动化。
- 多环境发布策略。
- 自动回滚策略。

判断：

```text
项目已经具备“应用部署 + 可观测性 + Web CI/CD”的简历展示主链路。
下一阶段最有价值的是把后端 gateway/blog 流水线接入 Jenkins。
```
