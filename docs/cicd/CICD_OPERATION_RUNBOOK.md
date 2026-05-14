# Cloud-Ops-Hub CI/CD 操作手册

更新时间：2026-05-10

本文是当前项目统一 CI/CD 操作入口，覆盖三条已跑通的 Jenkins 单模块流水线：

```text
cloud-ops-web-pipeline
cloud-ops-gateway-pipeline
cloud-ops-blog-pipeline
```

当前阶段目标是稳定的手动发布闭环，不接 Webhook，不做统一智能 Pipeline。

## 1. 当前状态

三条流水线均已完成：

```text
Checkout
-> 模块构建
-> Docker build
-> 推送 ACR
-> 回写 Kubernetes Deployment image tag
-> Jenkins commit + push GitHub
-> kubectl apply
-> rollout + actual image 校验
```

统一镜像仓库：

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub
```

发布命名空间：

```text
cloud-ops
```

Jenkins 命名空间：

```text
cicd
```

## 2. 流水线清单

| 模块 | Jenkins Job | Jenkinsfile | Deployment | Tag 前缀 |
| --- | --- | --- | --- | --- |
| web | `cloud-ops-web-pipeline` | `Jenkinsfile.web` | `infra/k8s/base/web/deployment.yaml` | `0.0.7-web` |
| gateway | `cloud-ops-gateway-pipeline` | `Jenkinsfile.gateway` | `infra/k8s/base/gateway/deployment.yaml` | `0.0.7-gateway` |
| blog | `cloud-ops-blog-pipeline` | `Jenkinsfile.blog` | `infra/k8s/base/blog/deployment.yaml` | `0.0.7-blog` |

Tag 格式：

```text
<version-prefix>-${BUILD_NUMBER}-${gitShortSha}
```

示例：

```text
0.0.7-web-1-3c90b7b
0.0.7-gateway-1-61d1c7c
0.0.7-blog-1-e1dd8e6
```

日常发布不要手动修改 tag。Jenkins 会自动生成唯一 tag 并回写 Git。

## 3. 发布前检查

在本地或 ECS 项目目录执行：

```bash
git status --short
```

作用：确认当前工作区是否有未提交修改。

```bash
git pull origin main
```

作用：同步 Jenkins 自动回写的 Deployment tag，避免后续提交或手工 apply 使用旧镜像。

检查 Jenkins 访问：

```bash
KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n cicd port-forward svc/jenkins 8080:8080
```

作用：通过本地 `http://127.0.0.1:8080` 访问 Jenkins。

## 4. 修改与本地验证

### 4.1 web

常改范围：

```text
web/src/**
web/public/**
web/package.json
web/package-lock.json
web/.env.example
```

本地验证：

```bash
cd web
npm run build
cd ..
```

作用：验证前端 TypeScript / Vite 构建。

提交示例：

```bash
git add web
git commit -m "feat(web): update frontend"
git push origin main
```

### 4.2 gateway

常改范围：

```text
apps/gateway-portal/**
common/common-core/**
Jenkinsfile.gateway
infra/k8s/base/gateway/**
```

本地验证：

```bash
./mvnw -pl apps/gateway-portal -am clean package -DskipTests -B
```

作用：构建 gateway 模块，并自动构建依赖模块 `common-core`。

提交示例：

```bash
git add apps/gateway-portal common/common-core
git commit -m "feat(gateway): update gateway portal"
git push origin main
```

### 4.3 blog

常改范围：

```text
apps/blog-service/**
common/common-core/**
Jenkinsfile.blog
infra/k8s/base/blog/**
```

本地验证：

```bash
./mvnw -pl apps/blog-service -am clean package -DskipTests -B
```

作用：构建 blog 模块，并自动构建依赖模块 `common-core`。

提交示例：

```bash
git add apps/blog-service common/common-core
git commit -m "feat(blog): update blog service"
git push origin main
```

注意：

- 修改 `common/common-core/**` 后，通常需要依次发布 gateway 和 blog。
- 不要同时运行 gateway 与 blog 两个 Jenkins Job，避免并发回写 `main`。

## 5. Jenkins 手动发布

当前 Triggers 全部不启用：

```text
不启用 GitHub hook trigger
不启用 Poll SCM
不启用 Build periodically
不启用远程触发
```

手动触发：

```text
Jenkins -> 对应 Job -> Build Now
```

推荐顺序：

1. 只改 web：运行 `cloud-ops-web-pipeline`。
2. 只改 gateway：运行 `cloud-ops-gateway-pipeline`。
3. 只改 blog：运行 `cloud-ops-blog-pipeline`。
4. 修改 common：先运行 `cloud-ops-gateway-pipeline`，成功后再运行 `cloud-ops-blog-pipeline`。

Console Output 重点看：

- `Checkout`
- `Prepare Tag`
- `Maven Package` 或前端 Docker 内构建
- `Docker Login`
- `Build Image`
- `Push Image`
- `Update Manifest`
- `Commit Manifest`
- `Deploy`
- `Verify`

## 6. 发布后同步

Jenkins 成功后会自动提交：

```text
chore(cicd): bump <module> image <image-tag> [skip ci]
```

本地和 ECS 项目目录执行：

```bash
git pull origin main
```

作用：

- 拉取 Jenkins 回写的 Deployment image tag。
- 避免后续提交覆盖线上镜像。
- 避免 ECS 手工 apply 时回滚到旧 tag。

## 7. 发布后验证

### 7.1 Git 回写

```bash
git log --oneline -5
```

作用：确认最近存在 Jenkins bump commit。

```bash
grep -n "image:" infra/k8s/base/web/deployment.yaml
grep -n "image:" infra/k8s/base/gateway/deployment.yaml
grep -n "image:" infra/k8s/base/blog/deployment.yaml
```

作用：确认 Git 中对应模块镜像 tag 已更新。

### 7.2 K3s 实际状态

```bash
kubectl -n cloud-ops rollout status deploy/web --timeout=180s
kubectl -n cloud-ops rollout status deploy/gateway-portal --timeout=240s
kubectl -n cloud-ops rollout status deploy/blog-service --timeout=300s
```

作用：确认 Deployment 发布完成。

```bash
kubectl -n cloud-ops get deploy web -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
kubectl -n cloud-ops get deploy gateway-portal -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
kubectl -n cloud-ops get deploy blog-service -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

作用：确认集群实际运行镜像与 Git 回写 tag 一致。

```bash
kubectl -n cloud-ops get pod -l app=web -o wide
kubectl -n cloud-ops get pod -l app=gateway-portal -o wide
kubectl -n cloud-ops get pod -l app=blog-service -o wide
```

作用：查看 Pod 是否 Running。

### 7.3 业务访问

```bash
curl -I http://deriou.com/
```

作用：验证 web 首页。

```bash
curl -I http://deriou.com/ops/cluster
```

作用：验证前端运维入口。

```bash
curl http://deriou.com/api/v1/blog/posts
```

作用：验证 blog 公开读接口。

gateway 业务接口需要 `X-Ops-Key`：

```bash
curl -H "X-Ops-Key: <your-ops-key>" http://deriou.com/api/v1/gateway/apps
```

作用：验证 gateway 对外路由与接口。

## 8. 观测验证

Grafana：

```text
http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
```

Prometheus targets：

```text
http://prometheus.deriou.com/targets
```

重点看：

- `web` 是否可访问。
- `gateway-portal` target 是否 UP。
- `blog-service` target 是否 UP。
- 请求量是否正常。
- 5xx 是否异常。
- Loki 中是否有新版本启动日志。

## 9. 禁止事项

默认不要做：

- 不要手动修改 Deployment image tag。
- 不要用 `kubectl set image` 替代 Jenkins 发布。
- 不要同时运行 gateway 和 blog Job。
- 不要把 Jenkins 暴露到公网。
- 不要把 GitHub token、ACR 密码、Ops Key 写入仓库。
- 不要在 Pipeline 中自动执行数据库迁移。
- 不要在未设计回滚策略前做自动多环境发布。

允许例外：

- Jenkins 回写失败时，经人工确认后修正 Deployment。
- 排障时可临时查看 Pod、Deployment、日志、事件。
- 数据库迁移按独立 SOP 人工执行。

## 10. 常见问题

### 10.1 首次构建 Maven 下载很久

正常。

原因：

- Jenkins 容器首次没有 Maven 依赖缓存。
- Docker build 内部也会执行 Maven 构建。
- gateway/blog 首次构建可能会下载两轮依赖。

处理：

- 只要日志持续出现 `Downloading` / `Downloaded`，继续等待。
- 后续构建会因缓存而变快。

### 10.2 Git push 被拒绝

原因：

- 远端 `main` 有新提交。
- 可能有另一个 Job 或人工提交已经 push。

处理：

```bash
git pull --rebase origin main
git push origin main
```

作用：把本地提交接到最新 `main` 后再推送。

### 10.3 rollout 超时

检查：

```bash
kubectl -n cloud-ops describe deploy <deploy-name>
kubectl -n cloud-ops logs deploy/<deploy-name> --tail=200
kubectl -n cloud-ops get pod -l app=<app-label> -o wide
```

作用：

- 查看镜像拉取、探针、配置、启动日志。

### 10.4 Jenkins 找不到 Jenkinsfile

检查 Job 配置：

```text
Definition: Pipeline script from SCM
SCM: Git
Branch: */main
Script Path: Jenkinsfile.web / Jenkinsfile.gateway / Jenkinsfile.blog
```

作用：确认 Jenkins 从 GitHub main 读取正确 Jenkinsfile。

## 11. 当前边界

当前已经完成：

- 三模块独立发布流水线。
- 镜像唯一 tag。
- Deployment tag Git 回写。
- K3s rollout 校验。
- Grafana / Prometheus / Loki 第一版观测闭环。

当前仍不做：

- GitHub Webhook 自动触发。
- 统一多模块智能 Pipeline。
- 自动数据库迁移。
- 自动回滚。
- Jenkins 动态 Agent。
- Kaniko / BuildKit rootless。

## 12. 简历表达

可以描述为：

```text
基于 Jenkins + Docker + K3s 为 Cloud-Ops-Hub 落地 web、gateway-portal、blog-service 三个模块的 CI/CD 发布闭环。流水线支持模块构建、ACR 镜像推送、Kubernetes Deployment image tag 回写 GitHub、K3s rollout 校验，并结合 Prometheus、Loki、Grafana 完成发布后的观测验证。三条独立流水线构成多模块路径感知发布的工程雏形。
```
