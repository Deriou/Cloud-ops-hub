# Web 单模块 Jenkins 发布操作文档

本文档用于 Cloud-Ops-Hub 第一条 Jenkins 流水线：只发布 `web` 前端模块。

目标是跑通最小 CI/CD 闭环：

```text
Jenkins 手动触发
-> 拉取 GitHub main
-> 构建 web 镜像
-> 推送 ACR
-> 回写 infra/k8s/base/web/deployment.yaml
-> commit + push GitHub
-> 发布 web Deployment
-> rollout 验证
-> 页面与 Grafana 验证
```

## 1. 当前阶段边界

### 1.1 本阶段做什么

- 只发布 `web` 模块。
- 手动点击 Jenkins Build，不接 GitHub Webhook。
- 每次发布生成唯一镜像 tag。
- Jenkins 自动提交 `infra/k8s/base/web/deployment.yaml` 的 tag 变化。
- Jenkins 只应用 `web` Deployment，不全量 `kubectl apply -k infra/k8s/base`。

### 1.2 本阶段暂不做什么

- 不发布 `gateway-portal`。
- 不发布 `blog-service`。
- 不自动触发 GitHub Webhook。
- 不做蓝绿发布 / 金丝雀发布。
- 不做数据库变更。
- 不把 Jenkins 公开到公网。

## 2. 前置条件

### 2.1 Jenkins 已部署完成

Jenkins 已运行在：

```text
namespace: cicd
deployment: jenkins
service: jenkins
```

Jenkins 当前应具备：

- Jenkins UI 可通过本地隧道访问。
- Jenkins Home 已 PVC 持久化。
- Jenkins Pod 可访问 Docker socket。
- Jenkins Pod 可调用 `kubectl`。
- Jenkins ServiceAccount 具备发布 `cloud-ops` Deployment 的权限。

### 2.2 ACR 镜像仓库

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub
```

### 2.3 Web 当前部署文件

```text
infra/k8s/base/web/deployment.yaml
```

### 2.4 Jenkins 凭据 ID 约定

后续 `Jenkinsfile.web` 使用固定凭据 ID：

| 凭据 ID | 类型 | 作用 |
| --- | --- | --- |
| `github-cloud-ops-hub-token` | Username with password | 拉取 GitHub 仓库、提交并推送 Deployment tag。 |
| `acr-cloud-ops-hub` | Username with password | 登录 ACR，推送 web 镜像。 |

## 3. ECS 发布前检查

以下命令默认在 ECS 项目根目录执行：

```bash
cd ~/projects/Cloud-ops-hub
```

作用：

- 进入 ECS 上的项目目录。
- 后续 `kubectl` 与 `git` 命令都基于该目录执行。

### 3.1 拉取最新仓库

```bash
git pull origin main
```

作用：

- 拉取 GitHub 最新代码与 Jenkins 配置文件。
- 避免 ECS 本地清单落后。

### 3.2 检查 Jenkins Pod

```bash
kubectl -n cicd get pod -l app=jenkins -o wide
```

作用：

- 查看 Jenkins Pod 是否运行中。
- 期望状态为 `1/1 Running`。

```bash
kubectl -n cicd rollout status deploy/jenkins --timeout=300s
```

作用：

- 确认 Jenkins Deployment 发布完成。
- 如果超时，说明 Jenkins 当前环境未稳定，不应继续创建流水线。

### 3.3 检查 Jenkins Docker 能力

```bash
kubectl -n cicd exec deploy/jenkins -- docker version
```

作用：

- 确认 Jenkins 容器内有 `docker` 命令。
- 确认 Jenkins 可通过 `/var/run/docker.sock` 访问 ECS 宿主机 Docker daemon。

期望看到：

```text
Client:
Server:
```

如果只看到 `Client` 或报权限错误，说明 Docker socket 挂载或权限还没通。

### 3.4 检查 Jenkins kubectl 能力

```bash
kubectl -n cicd exec deploy/jenkins -- kubectl version --client
```

作用：

- 确认 Jenkins Pod 内可调用 `kubectl`。
- 当前 `kubectl` 来自 ECS `/usr/local/bin/kubectl` 的 hostPath 挂载。

### 3.5 检查 Jenkins 发布权限

```bash
kubectl -n cloud-ops auth can-i patch deployments --as=system:serviceaccount:cicd:jenkins
```

作用：

- 确认 Jenkins ServiceAccount 可以更新 `cloud-ops` namespace 中的 Deployment。

期望输出：

```text
yes
```

```bash
kubectl -n cloud-ops auth can-i get pods --as=system:serviceaccount:cicd:jenkins
```

作用：

- 确认 Jenkins 可以查看发布后的 Pod 状态。

期望输出：

```text
yes
```

### 3.6 检查当前线上 web 镜像

```bash
kubectl -n cloud-ops get deploy web -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

作用：

- 查看 K3s 中 `web` 当前实际运行镜像。
- 用于发布前记录基线。

```bash
grep -n "image:" infra/k8s/base/web/deployment.yaml
```

作用：

- 查看 Git 仓库声明的 `web` 镜像。
- 应与线上镜像保持一致。

如果两者不一致，先不要执行 Jenkins 发布，需要先对齐 Git 和集群状态。

## 4. Jenkins Credentials 配置

进入 Jenkins UI：

```text
Manage Jenkins
-> Credentials
-> System
-> Global credentials
-> Add Credentials
```

### 4.1 GitHub Token 凭据

GitHub 建议使用 Fine-grained Personal Access Token。

建议权限：

```text
Repository: Deriou/Cloud-ops-hub
Permissions:
  Contents: Read and write
```

Jenkins 中填写：

```text
Kind: Username with password
Scope: Global
Username: Deriou
Password: <GitHub token>
ID: github-cloud-ops-hub-token
Description: GitHub token for Cloud-Ops-Hub Jenkins pipeline
```

作用：

- Jenkins 从 GitHub 拉取代码。
- Jenkins 修改 `infra/k8s/base/web/deployment.yaml` 后 commit 并 push 回 `main`。

### 4.2 ACR 凭据

Jenkins 中填写：

```text
Kind: Username with password
Scope: Global
Username: <ACR 用户名>
Password: <ACR 密码>
ID: acr-cloud-ops-hub
Description: Aliyun ACR credential for Cloud-Ops-Hub
```

作用：

- Jenkins 执行 `docker login`。
- Jenkins 推送 `web` 镜像到 ACR。

## 5. Jenkins Job 创建

Jenkins 首页点击：

```text
New Item
```

填写：

```text
Item name: cloud-ops-web-pipeline
Type: Pipeline
```

### 5.1 Pipeline 配置

在 Job 配置中选择：

```text
Definition: Pipeline script from SCM
SCM: Git
Repository URL: https://github.com/Deriou/Cloud-ops-hub.git
Credentials: github-cloud-ops-hub-token
Branch Specifier: */main
Script Path: Jenkinsfile.web
```

作用：

- Jenkins 每次构建时从 GitHub `main` 拉取仓库。
- Jenkins 使用仓库根目录的 `Jenkinsfile.web` 作为流水线脚本。

### 5.2 暂不配置 Build Triggers

第一阶段不勾选：

```text
GitHub hook trigger for GITScm polling
Poll SCM
Build periodically
```

作用：

- 避免还没稳定前被自动触发。
- 当前只通过手动点击 `Build Now` 发布。

## 6. Jenkinsfile.web

仓库根目录已提供 `Jenkinsfile.web`：

```text
Jenkinsfile.web
```

它会完成以下阶段：

| 阶段 | 作用 |
| --- | --- |
| Checkout | 拉取 GitHub `main`。 |
| Prepare Tag | 生成唯一 web 镜像 tag。 |
| Docker Login | 使用 `acr-cloud-ops-hub` 登录 ACR。 |
| Build Image | 执行 `docker build --platform linux/amd64` 构建 web 镜像。 |
| Push Image | 推送镜像到 ACR。 |
| Update Manifest | 修改 `infra/k8s/base/web/deployment.yaml` 的 image。 |
| Commit Manifest | 使用 `github-cloud-ops-hub-token` commit + push 到 GitHub。 |
| Deploy Web | 执行 `kubectl apply -f infra/k8s/base/web/deployment.yaml`。 |
| Verify | 执行 `kubectl -n cloud-ops rollout status deploy/web`。 |

### 6.1 镜像 tag 规则

建议格式：

```text
0.0.7-web-${BUILD_NUMBER}-${gitShortSha}
```

示例：

```text
0.0.7-web-15-a1b2c3d
```

作用：

- `BUILD_NUMBER` 对应 Jenkins 构建号。
- `gitShortSha` 对应源码版本。
- 每次构建生成唯一 tag，避免复用旧 tag 导致 K8s 不拉新镜像。

### 6.2 镜像构建命令

```bash
docker build --platform linux/amd64 -t "$IMAGE" ./web
```

作用：

- 构建前端 nginx 镜像。
- `web/Dockerfile` 内部会执行 `npm ci` 与 `npm run build`。
- `--platform linux/amd64` 保证 ECS 可运行。

### 6.3 镜像推送命令

```bash
docker push "$IMAGE"
```

作用：

- 将新 web 镜像推送到 ACR。

### 6.4 Deployment tag 回写命令

```bash
sed -i "s#image: .*cloud-ops-hub/web:.*#image: ${IMAGE}#" infra/k8s/base/web/deployment.yaml
```

作用：

- 将 `web` Deployment 镜像替换为本次新 tag。
- 保证 Git 仓库中的期望状态和线上部署状态一致。

### 6.5 Git 回写命令

```bash
git add infra/k8s/base/web/deployment.yaml
git commit -m "chore(cicd): bump web image ${IMAGE_TAG} [skip ci]"
git push origin main
```

作用：

- 提交本次镜像 tag 变化。
- `[skip ci]` 用于后续接入自动触发时避免 Jenkins 自己触发自己。

### 6.6 发布命令

```bash
kubectl apply -f infra/k8s/base/web/deployment.yaml
```

作用：

- 只应用 `web` Deployment。
- 第一阶段不全量 apply base，避免 Jenkins 需要过大的 PV、Namespace、Ingress 等资源权限。

### 6.7 Rollout 验证命令

```bash
kubectl -n cloud-ops rollout status deploy/web --timeout=180s
```

作用：

- 等待 `web` Deployment 滚动更新完成。
- 如果超时，Jenkins 构建应判定失败。

## 7. 手动触发发布

进入 Jenkins：

```text
cloud-ops-web-pipeline
-> Build Now
```

作用：

- 手动触发一次 `web` 单模块发布。

点击构建号进入：

```text
Console Output
```

重点观察：

- 是否成功 checkout。
- 是否成功 docker login。
- 是否成功 docker build。
- 是否成功 docker push。
- 是否成功 commit + push。
- 是否成功 rollout。

## 8. 发布后验证

### 8.1 在 ECS 拉取 Jenkins 回写提交

```bash
cd ~/projects/Cloud-ops-hub
git pull origin main
```

作用：

- 拉取 Jenkins 自动提交的 `deployment.yaml` tag 变化。

```bash
git log --oneline -5
```

作用：

- 查看最近提交。
- 应看到类似：

```text
chore(cicd): bump web image 0.0.7-web-15-a1b2c3d [skip ci]
```

### 8.2 检查 Git 中的 web 镜像

```bash
grep -n "image:" infra/k8s/base/web/deployment.yaml
```

作用：

- 确认 Git 仓库中的 web 镜像已经更新为新 tag。

### 8.3 检查 K8s 中的 web 镜像

```bash
kubectl -n cloud-ops get deploy web -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

作用：

- 确认 K3s 中实际运行的 web 镜像与 Git 中一致。

### 8.4 检查 web Pod

```bash
kubectl -n cloud-ops get pod -l app=web -o wide
```

作用：

- 查看新 web Pod 是否已启动。
- 期望状态为 `Running`。

```bash
kubectl -n cloud-ops describe deploy web
```

作用：

- 查看 Deployment 事件和 Replica 状态。
- 排查镜像拉取失败、探针失败、滚动更新失败。

### 8.5 检查公网页面

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

### 8.6 检查 Grafana

访问：

```text
http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
```

观察：

- `gateway-portal` 与 `blog-service` 是否仍为 UP。
- 发布期间 5xx 是否异常。
- 错误日志趋势是否异常。
- web 发布不会直接改变后端指标，但可作为发布验证入口。

## 9. 回滚操作

如果发布后页面异常，优先按 Git 回滚。

### 9.1 找到 Jenkins bump commit

```bash
git log --oneline -10
```

作用：

- 找到最近一次 Jenkins 自动提交的 bump commit。

示例：

```text
abc1234 chore(cicd): bump web image 0.0.7-web-15-a1b2c3d [skip ci]
```

### 9.2 回滚该提交

```bash
git revert abc1234
```

作用：

- 创建一个反向提交，将 `deployment.yaml` 恢复到上一个镜像 tag。

### 9.3 推送回 GitHub

```bash
git push origin main
```

作用：

- 将回滚后的期望状态推回 GitHub。

### 9.4 应用回滚

```bash
kubectl apply -f infra/k8s/base/web/deployment.yaml
```

作用：

- 将回滚后的 `web` Deployment 应用到 K3s。

```bash
kubectl -n cloud-ops rollout status deploy/web --timeout=180s
```

作用：

- 等待 web 回滚完成。

## 10. 常见问题

### 10.1 docker command not found

现象：

```text
docker: executable file not found in $PATH
```

原因：

- Jenkins 镜像不是 CI 扩展镜像。
- Jenkins Pod 未使用 `jenkins:lts-jdk21-docker-kubectl-amd64`。

排查：

```bash
kubectl -n cicd get deploy jenkins -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

作用：

- 查看 Jenkins 当前使用的镜像 tag。

### 10.2 Docker socket 权限不足

现象：

```text
permission denied while trying to connect to the Docker daemon socket
```

原因：

- Jenkins Pod 未加入 Docker socket 所属组。

排查：

```bash
stat -c '%U %G %a %g' /var/run/docker.sock
```

作用：

- 查看 ECS Docker socket GID。
- 当前环境应为 `999`。

```bash
kubectl -n cicd get deploy jenkins -o yaml | grep -A3 supplementalGroups
```

作用：

- 确认 Jenkins Pod 已配置 `supplementalGroups: [999]`。

### 10.3 kubectl command not found

现象：

```text
kubectl: executable file not found in $PATH
```

原因：

- Jenkins Pod 没有挂载 ECS 的 `/usr/local/bin/kubectl`。

排查：

```bash
kubectl -n cicd exec deploy/jenkins -- ls -l /usr/local/bin/kubectl
```

作用：

- 确认 Jenkins Pod 内是否存在 kubectl。

### 10.4 kubectl forbidden

现象：

```text
forbidden: User "system:serviceaccount:cicd:jenkins" cannot patch resource "deployments"
```

原因：

- Jenkins ServiceAccount 缺少 `cloud-ops` 发布权限。

排查：

```bash
kubectl -n cloud-ops auth can-i patch deployments --as=system:serviceaccount:cicd:jenkins
```

作用：

- 验证 Jenkins 是否有权限更新 Deployment。

### 10.5 git push 失败

现象：

```text
remote: Permission denied
```

原因：

- GitHub token 权限不足。
- Jenkins Job 未正确选择 `github-cloud-ops-hub-token`。

处理：

- 确认 GitHub token 对 `Deriou/Cloud-ops-hub` 有 `Contents: Read and write`。
- 确认 Jenkins Credentials ID 是 `github-cloud-ops-hub-token`。

### 10.6 ACR push 失败

现象：

```text
unauthorized: authentication required
```

原因：

- ACR 凭据错误。
- Pipeline 没有正确执行 `docker login`。

处理：

- 确认 Jenkins Credentials ID 是 `acr-cloud-ops-hub`。
- 确认 ACR 用户名和密码可在 ECS 上执行 `docker login`。

## 11. 完成标准

本阶段完成后应满足：

- Jenkins Job `cloud-ops-web-pipeline` 可手动触发。
- Jenkins 能构建 `web` 镜像。
- 新镜像成功推送到 ACR。
- `infra/k8s/base/web/deployment.yaml` 被 Jenkins 自动回写并推送到 GitHub。
- K3s 中 `web` Deployment 完成滚动更新。
- `http://deriou.com/` 可访问。
- `http://deriou.com/ops/cluster` 可访问。
- Git 仓库 tag 与线上镜像一致。

## 12. 下一阶段

Web 单模块发布跑通后，再扩展：

```text
gateway-portal Pipeline
blog-service Pipeline
路径感知构建
GitHub Webhook 自动触发
发布后 Grafana 验证增强
```
