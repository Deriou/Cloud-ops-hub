# Jenkins on K3s Manifests

本目录保存 Jenkins 在 K3s 中运行所需的原生 Kubernetes 清单。

## 文件作用

- `namespace.yaml`：创建 `cicd` namespace，把 CI/CD 组件和业务 `cloud-ops` namespace 隔离。
- `service-account.yaml`：创建 Jenkins controller 使用的 `jenkins` ServiceAccount。
- `rbac.yaml`：授权 Jenkins 在 `cicd` namespace 中创建和管理临时 Agent Pod。
- `pv-pvc.yaml`：用 `hostPath` 持久化 `/var/jenkins_home`，保留插件、任务配置、凭据和构建历史。
- `deployment.yaml`：部署 Jenkins controller，使用 ACR 镜像 `jenkins:lts-jdk21-amd64`，并通过 initContainer 修复 hostPath 权限。
- `service.yaml`：提供集群内访问入口，`8080` 用于 UI，`50000` 用于 inbound agent。
- `kustomization.yaml`：把上述资源组合成一个可执行的 Kustomize 部署入口。

## 部署

```bash
kubectl apply -k infra/k8s/cicd/jenkins
```

## 验证

```bash
kubectl -n cicd get pod,svc,pvc
kubectl -n cicd rollout status deploy/jenkins --timeout=300s
kubectl -n cicd logs deploy/jenkins --tail=100
```

如果 Pod 出现 `missing rw permissions on JENKINS_HOME`：

```bash
kubectl -n cicd logs deploy/jenkins --previous --tail=100
kubectl apply -k infra/k8s/cicd/jenkins
kubectl -n cicd delete pod -l app=jenkins
```

原因：

- `hostPath` 目录 `/opt/cloud-ops/jenkins-home` 可能由 root 创建。
- Jenkins 容器内默认用户是 UID `1000`。
- `fix-jenkins-home-permissions` initContainer 会在主容器启动前把目录权限调整给 UID/GID `1000`。

## 本地访问 Jenkins UI

```bash
kubectl -n cicd port-forward svc/jenkins 8080:8080
```

然后访问：

```text
http://127.0.0.1:8080
```

## 初始管理员密码

```bash
kubectl -n cicd exec deploy/jenkins -- cat /var/jenkins_home/secrets/initialAdminPassword
```

## 前置要求

- `cicd` namespace 中需要有 `acr-secret`，用于拉取 ACR 私有镜像。
- ECS 节点需要存在或允许自动创建 `/opt/cloud-ops/jenkins-home`。
- Jenkins 镜像需要先同步到 ACR：

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/jenkins:lts-jdk21-amd64
```
