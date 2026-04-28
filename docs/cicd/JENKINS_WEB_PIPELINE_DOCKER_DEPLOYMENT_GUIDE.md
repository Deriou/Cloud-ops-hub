# Jenkins Web Pipeline Docker 部署指南

本文档记录 Cloud-Ops-Hub 第一阶段 CI/CD 方案：先用 Jenkins 跑通 `web` 模块自动构建、推送 ACR、回写 Deployment tag、部署到 K3s 的最小闭环。

当前阶段不追求企业级复杂度，目标是让简历项目具备一条真实可演示的发布链路：

```text
手动触发 Jenkins
-> 拉取 GitHub main
-> 构建 web 镜像
-> 推送到 ACR
-> 修改 infra/k8s/base/web/deployment.yaml 镜像 tag
-> commit + push 回 GitHub
-> kubectl apply -f infra/k8s/base/web/deployment.yaml
-> rollout status
-> 通过网站与 Grafana 验证
```

## 1. 本阶段边界

### 1.1 本阶段做什么

- 只做第一条流水线：`web`。
- 先手动触发 Jenkins 构建，不接 GitHub Webhook。
- 镜像构建使用 ECS 当前已有 Docker 能力。
- 每次发布生成唯一镜像 tag。
- Jenkins 自动把新 tag 回写到 `infra/k8s/base/web/deployment.yaml`。
- Jenkins 自动提交并推送 tag 变更到 GitHub `main`。
- Jenkins 自动执行 K8s apply 与 rollout 验证。

### 1.2 本阶段暂不做什么

- 暂不做 `gateway-portal` 与 `blog-service` 流水线。
- 暂不做 GitHub Webhook 自动触发。
- 暂不做 Kaniko / BuildKit rootless。
- 暂不做多环境发布。
- 暂不做审批流、蓝绿发布、金丝雀发布。

### 1.3 为什么 Jenkins 需要 GitHub 写权限

Jenkins 构建新镜像后，需要同步修改仓库里的部署版本：

```yaml
image: crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/web:<new-tag>
```

如果只执行 `kubectl set image`，线上会更新，但 Git 仓库仍然保留旧 tag。下一次执行：

```bash
kubectl apply -f infra/k8s/base/web/deployment.yaml
```

就可能把线上版本回滚。

因此第一版选择：

```text
Jenkins 构建镜像 -> 修改 deployment.yaml -> commit/push -> apply
```

这样 Git 仓库与 K3s 集群状态保持一致。

## 2. 目标版本规范

### 2.1 当前已知镜像仓库

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub
```

### 2.2 Web 镜像 tag 规则

建议第一版使用：

```text
0.0.7-web-${BUILD_NUMBER}-${gitShortSha}
```

示例：

```text
0.0.7-web-12-a1b2c3d
```

说明：

- `0.0.7`：表示 web 下一轮发布主版本段。
- `web`：标识模块。
- `${BUILD_NUMBER}`：Jenkins 构建号，便于回看构建记录。
- `${gitShortSha}`：Git commit 短 SHA，便于定位源码版本。

## 3. 前置检查

以下命令默认在 ECS 上执行。

### 3.1 确认 Jenkins 正常运行

```bash
kubectl -n cicd get pod,svc,pvc
```

作用：

- 查看 Jenkins Pod、Service、PVC 状态。
- 确认 Pod 为 `1/1 Running`，PVC 为 `Bound`。

```bash
kubectl -n cicd rollout status deploy/jenkins --timeout=300s
```

作用：

- 确认 Jenkins Deployment 已稳定发布。

### 3.2 确认 ECS Docker 可用

```bash
docker version
```

作用：

- 确认 ECS 宿主机 Docker daemon 可用。
- Jenkins 第一阶段会复用这个 Docker 能力构建镜像。

```bash
docker images | head
```

作用：

- 快速确认本机 Docker 可以正常访问镜像缓存。

### 3.3 确认 ACR 登录可用

```bash
docker login crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com
```

作用：

- 验证 ECS 可以登录阿里云 ACR。
- 后续 Jenkins 中也要配置同一组 ACR 凭据。

```bash
docker pull crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/nginx:stable-alpine
```

作用：

- 验证 ECS 可以从 ACR 拉取基础镜像。
- `web/Dockerfile` 第二阶段依赖该 nginx 镜像。

### 3.4 确认 K8s 部署入口可用

```bash
kubectl apply -k infra/k8s/base --dry-run=server
```

作用：

- 在不真正更新资源的情况下，让 apiserver 校验 Kustomize 渲染后的全量清单。
- 提前发现 YAML、字段、权限问题。
- 这条命令适合管理员在 ECS 上预检查；Jenkins 第一版发布时只应用 `web/deployment.yaml`，避免给 Jenkins 过大的集群权限。

```bash
kubectl -n cloud-ops get deploy web
```

作用：

- 确认 `web` Deployment 存在。

### 3.5 确认当前 Web 部署 tag

```bash
kubectl -n cloud-ops get deploy web -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

作用：

- 查看线上 `web` 当前实际运行镜像。
- 用于和 `infra/k8s/base/web/deployment.yaml` 对齐。

```bash
grep -n "image:" infra/k8s/base/web/deployment.yaml
```

作用：

- 查看 Git 仓库中声明的 `web` 镜像 tag。
- 如果这里和线上不一致，说明 Git 与集群存在漂移。

## 4. Jenkins 需要准备的能力

第一阶段 Pipeline 需要 Jenkins 具备以下命令：

| 命令 | 作用 |
| --- | --- |
| `git` | 拉取代码、提交 Deployment tag 变更、推送 GitHub。 |
| `docker` | 构建并推送 `web` 镜像。 |
| `kubectl` | 执行 `kubectl apply` 与 `rollout status`。 |
| `sed` | 修改 `deployment.yaml` 中的镜像 tag。 |

### 4.1 检查 Jenkins 容器内工具

```bash
kubectl -n cicd exec deploy/jenkins -- git --version
```

作用：

- 确认 Jenkins 容器内有 Git。

```bash
kubectl -n cicd exec deploy/jenkins -- docker version
```

作用：

- 确认 Jenkins 容器内能访问 Docker CLI 与 Docker daemon。
- 如果失败，通常说明还没有挂载 Docker socket，或容器内缺少 Docker CLI。

```bash
kubectl -n cicd exec deploy/jenkins -- kubectl version --client
```

作用：

- 确认 Jenkins 容器内有 `kubectl`。

当前仓库已提供 CI 专用 Jenkins 镜像构建文件：

```text
infra/jenkins/Dockerfile
```

目标镜像：

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/jenkins:lts-jdk21-docker-kubectl-amd64
```

该镜像在原 Jenkins 镜像基础上补充：

- `docker` CLI
- `git`
- `openssh-client`

`kubectl` 不在镜像构建阶段下载，原因是 ECS 到 `dl.k8s.io` 可能不稳定。当前采用运行时挂载方式：

```text
ECS /usr/local/bin/kubectl -> Jenkins Pod /usr/local/bin/kubectl
```

### 4.2 构建 Jenkins CI 镜像

```bash
docker build --platform linux/amd64 \
  -t crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/jenkins:lts-jdk21-docker-kubectl-amd64 \
  infra/jenkins
```

作用：

- 构建 Jenkins CI 专用镜像。
- 明确指定 `linux/amd64`，避免 ECS 拉取后出现架构不匹配。

```bash
docker push crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/jenkins:lts-jdk21-docker-kubectl-amd64
```

作用：

- 将 Jenkins CI 镜像推送到 ACR。
- K3s 后续从 ACR 拉取该镜像。

## 5. Jenkins 挂载 Docker 能力

### 5.1 检查宿主机 Docker socket

```bash
ls -l /var/run/docker.sock
```

作用：

- 查看 Docker socket 是否存在。
- 查看 socket 属主、属组和权限。

```bash
stat -c '%U %G %a %g' /var/run/docker.sock
```

作用：

- 查看 Docker socket 的用户、用户组、权限和 GID。
- 如果 Jenkins 以非 root 用户访问 socket，通常需要把该 GID 加到 Pod `supplementalGroups`。

### 5.2 Jenkins Deployment 已增加挂载

目标是让 Jenkins Pod 可以访问宿主机 Docker socket：

```yaml
volumeMounts:
  - name: docker-sock
    mountPath: /var/run/docker.sock
volumes:
  - name: docker-sock
    hostPath:
      path: /var/run/docker.sock
      type: Socket
```

作用：

- Jenkins 容器执行 `docker build` 时，实际使用 ECS 宿主机 Docker daemon。
- 当前 ECS 的 Docker socket GID 为 `999`，`infra/k8s/cicd/jenkins/deployment.yaml` 已配置 `supplementalGroups: [999]`。
- 当前 ECS 已存在 `/usr/local/bin/kubectl`，`deployment.yaml` 已通过 `hostPath` 只读挂载到 Jenkins Pod。

注意：

- 这是学习项目快速闭环方案。
- Jenkins 一旦可以访问 Docker socket，就具备较高宿主机控制能力。
- 生产环境建议演进到 Kaniko / BuildKit rootless，避免直接暴露 Docker socket。

## 6. Jenkins 部署 K8s 的权限

Jenkins 当前已在 `cicd` namespace 运行。后续发布 `web` 时，需要它能更新 `cloud-ops` namespace 的 Deployment。

### 6.1 检查当前权限

```bash
kubectl -n cloud-ops auth can-i get deployments --as=system:serviceaccount:cicd:jenkins
```

作用：

- 检查 Jenkins ServiceAccount 是否能读取 `cloud-ops` 的 Deployment。

```bash
kubectl -n cloud-ops auth can-i patch deployments --as=system:serviceaccount:cicd:jenkins
```

作用：

- 检查 Jenkins ServiceAccount 是否能更新 `cloud-ops` 的 Deployment。

### 6.2 cloud-ops 发布权限已补充

建议给 Jenkins 最小发布权限：

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: jenkins-deployer
  namespace: cloud-ops
rules:
  - apiGroups: ["", "apps", "networking.k8s.io"]
    resources:
      - configmaps
      - services
      - deployments
      - ingresses
      - pods
      - pods/log
    verbs:
      - get
      - list
      - watch
      - create
      - update
      - patch
      - delete
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: jenkins-deployer
  namespace: cloud-ops
subjects:
  - kind: ServiceAccount
    name: jenkins
    namespace: cicd
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: jenkins-deployer
```

作用：

- 允许 `cicd/jenkins` 更新 `cloud-ops` 中的业务资源。
- 支撑第一阶段 `kubectl apply -f infra/k8s/base/web/deployment.yaml` 和 `rollout status`。

## 7. Jenkins 凭据规划

### 7.1 GitHub 凭据

建议创建 GitHub Fine-grained Personal Access Token：

```text
Repository: Deriou/Cloud-ops-hub
Permissions:
  Contents: Read and write
```

Jenkins 中新增：

```text
Kind: Username with password
ID: github-cloud-ops-hub-token
Username: Deriou
Password: <GitHub token>
Description: GitHub token for Cloud-Ops-Hub tag update
```

作用：

- Jenkins 拉取仓库。
- Jenkins 修改 `infra/k8s/base/web/deployment.yaml` 后提交并推送回 `main`。

### 7.2 ACR 凭据

Jenkins 中新增：

```text
Kind: Username with password
ID: acr-cloud-ops-hub
Username: <ACR 用户名>
Password: <ACR 密码或访问凭据>
Description: Aliyun ACR credential for Cloud-Ops-Hub
```

作用：

- Jenkins 执行 `docker login`。
- Jenkins 推送新构建的 `web` 镜像到 ACR。

### 7.3 K8s 凭据

第一阶段优先使用 Jenkins Pod 的 ServiceAccount：

```text
system:serviceaccount:cicd:jenkins
```

作用：

- 避免把 `/etc/rancher/k3s/k3s.yaml` 管理员 kubeconfig 放进 Jenkins。
- 权限通过 RBAC 明确限制在需要的 namespace 和资源范围内。

## 8. Web Pipeline 设计

### 8.1 Pipeline 阶段

| 阶段 | 作用 |
| --- | --- |
| Checkout | 从 GitHub 拉取 `main`。 |
| Prepare Tag | 生成唯一镜像 tag。 |
| Docker Login | 登录 ACR。 |
| Build Image | 执行 `docker build` 构建 web 镜像。 |
| Push Image | 推送新镜像到 ACR。 |
| Update Manifest | 修改 `infra/k8s/base/web/deployment.yaml`。 |
| Commit Manifest | 提交并推送 tag 变更到 GitHub。 |
| Deploy | 执行 `kubectl apply -f infra/k8s/base/web/deployment.yaml`。 |
| Verify | 执行 `rollout status` 并检查线上镜像。 |

### 8.2 关键命令

```bash
git rev-parse --short HEAD
```

作用：

- 生成当前源码版本短 SHA。
- 用于拼接镜像 tag。

```bash
docker build --platform linux/amd64 -t "$IMAGE" ./web
```

作用：

- 构建 `web` 镜像。
- 明确指定 `linux/amd64`，避免 ECS 架构不匹配。

```bash
docker push "$IMAGE"
```

作用：

- 将新镜像推送到 ACR。

```bash
sed -i "s#image: .*cloud-ops-hub/web:.*#image: ${IMAGE}#" infra/k8s/base/web/deployment.yaml
```

作用：

- 把 `web` Deployment 中的镜像替换为本次新 tag。

```bash
git add infra/k8s/base/web/deployment.yaml
git commit -m "chore(cicd): bump web image ${IMAGE_TAG} [skip ci]"
git push origin main
```

作用：

- 将新部署版本写回 GitHub。
- `[skip ci]` 用于避免未来接 Webhook 后发生自触发循环。

```bash
kubectl apply -f infra/k8s/base/web/deployment.yaml
```

作用：

- 将仓库内 `web` Deployment 声明式清单应用到 K3s。
- 第一阶段只发布 `web`，因此避免在 Jenkins 中应用全量 base 清单。

```bash
kubectl -n cloud-ops rollout status deploy/web --timeout=180s
```

作用：

- 等待 `web` Deployment 滚动更新完成。

```bash
kubectl -n cloud-ops get deploy web -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

作用：

- 确认线上 Deployment 已使用新镜像。

## 9. Jenkins 任务创建建议

第一版建议创建 Freestyle 或 Pipeline Job：

```text
Job name: cloud-ops-web-pipeline
Type: Pipeline
Branch: main
Script Path: Jenkinsfile.web
```

推荐使用仓库内 `Jenkinsfile.web`，原因：

- Pipeline 逻辑版本化。
- 修改流水线也可以走 Git 审计。
- 后续扩展后端流水线更容易。

## 10. 发布后验证

### 10.1 检查 Jenkins 构建结果

在 Jenkins UI 中确认：

- Console Output 无红色错误。
- Build History 中本次构建为绿色。
- 构建日志中能看到新镜像 tag。

### 10.2 检查 GitHub 回写

```bash
git pull origin main
git log --oneline -5
```

作用：

- 拉取 Jenkins 推送的最新提交。
- 确认存在 `chore(cicd): bump web image ... [skip ci]`。

```bash
grep -n "image:" infra/k8s/base/web/deployment.yaml
```

作用：

- 确认本地仓库中的 `web` 镜像 tag 已更新。

### 10.3 检查 K8s 状态

```bash
kubectl -n cloud-ops get pod -l app=web -o wide
```

作用：

- 查看新的 `web` Pod 是否已经启动。

```bash
kubectl -n cloud-ops describe deploy web
```

作用：

- 查看 Deployment 事件、镜像、Replica 状态。

```bash
kubectl -n cloud-ops logs deploy/web --tail=100
```

作用：

- 查看 `web` 容器最近日志。

### 10.4 检查网站

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

### 10.5 检查 Grafana

访问：

```text
http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
```

重点观察：

- `web` 发布期间 Pod 是否重建。
- `gateway-portal`、`blog-service` 是否仍为 UP。
- 发布后 5xx 是否异常。
- 错误日志趋势是否异常。

## 11. 常见问题

### 11.1 Jenkins 里执行 docker 报错

现象：

```text
docker: command not found
```

说明：

- Jenkins 容器内没有 Docker CLI。

处理：

- 后续补 CI 专用 Jenkins 镜像，内置 Docker CLI。
- 或挂载宿主机 Docker CLI，但要注意依赖库兼容性。

现象：

```text
permission denied while trying to connect to the Docker daemon socket
```

说明：

- Jenkins 容器用户没有权限访问 `/var/run/docker.sock`。

处理：

- 查看 socket GID：`stat -c '%g' /var/run/docker.sock`。
- 在 Jenkins Pod `securityContext.supplementalGroups` 中加入该 GID。

### 11.2 docker push 失败

现象：

```text
unauthorized: authentication required
```

说明：

- ACR 凭据错误或 Jenkins 没有执行 `docker login`。

处理：

- 检查 Jenkins Credentials 中的 `acr-cloud-ops-hub`。
- 在 Pipeline 中确认已执行 `docker login`。

### 11.3 git push 失败

现象：

```text
Permission denied
```

或：

```text
remote: Permission to Deriou/Cloud-ops-hub.git denied
```

说明：

- GitHub token 没有写权限。

处理：

- 确认 token 作用仓库是 `Deriou/Cloud-ops-hub`。
- 确认权限包含 `Contents: Read and write`。

### 11.4 kubectl apply 失败

现象：

```text
forbidden: User "system:serviceaccount:cicd:jenkins" cannot patch resource ...
```

说明：

- Jenkins ServiceAccount 缺少 `cloud-ops` namespace 发布权限。

处理：

- 补充 `jenkins-deployer` Role 与 RoleBinding。
- 再执行 `kubectl auth can-i` 验证。

### 11.5 发布后页面没有变化

可能原因：

- 新镜像没有推送成功。
- `deployment.yaml` 没有更新到新 tag。
- `kubectl apply` 没有执行成功。
- 浏览器缓存。
- Ingress / Service 指向的不是新 Pod。

排查命令：

```bash
kubectl -n cloud-ops get deploy web -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
kubectl -n cloud-ops get pod -l app=web -o wide
kubectl -n cloud-ops rollout history deploy/web
curl -I http://deriou.com/
```

作用：

- 依次确认镜像、Pod、Deployment 历史和公网访问状态。

## 12. 面试表达

可以这样描述第一阶段：

> 我在 K3s 中部署 Jenkins Controller，并通过 PVC 持久化 Jenkins Home。第一版 CI/CD 先选择 Jenkins 复用 ECS 宿主机 Docker 能力，跑通 Web 模块从 GitHub 拉代码、构建 linux/amd64 镜像、推送阿里云 ACR、自动回写 Kubernetes Deployment tag、提交 GitHub、执行 Kustomize 发布和 rollout 验证的完整闭环。

可以主动补充边界：

> 这个方案适合学习阶段快速验证链路，但 Jenkins 挂 Docker socket 权限较高。后续会演进到 Kubernetes Agent + Kaniko / BuildKit rootless，减少对宿主机 Docker daemon 的依赖。
