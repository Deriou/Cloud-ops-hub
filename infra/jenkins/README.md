# Jenkins CI Image

本目录保存 Cloud-Ops-Hub 第一阶段 CI/CD 使用的 Jenkins 扩展镜像。

基础镜像：

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/jenkins:lts-jdk21-amd64
```

扩展能力：

- `docker` CLI：通过宿主机 `/var/run/docker.sock` 构建并推送镜像。
- `kubectl`：使用 Jenkins Pod 的 ServiceAccount 发布到 K3s。
- `git` / `openssh-client`：拉取仓库并回写 Deployment tag。

## 构建镜像

```bash
docker build --platform linux/amd64 \
  -t crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/jenkins:lts-jdk21-docker-kubectl-amd64 \
  infra/jenkins
```

作用：

- 基于现有 Jenkins ACR 镜像构建 CI 专用镜像。
- 明确指定 `linux/amd64`，避免 ECS 架构不匹配。

## 推送镜像

```bash
docker push crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/jenkins:lts-jdk21-docker-kubectl-amd64
```

作用：

- 将 CI 专用 Jenkins 镜像推送到 ACR。
- K3s 后续从 ACR 拉取该镜像。

## 验证镜像

```bash
docker run --rm \
  crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/jenkins:lts-jdk21-docker-kubectl-amd64 \
  docker version --client
```

作用：

- 验证镜像内已包含 Docker CLI。

```bash
docker run --rm \
  crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/jenkins:lts-jdk21-docker-kubectl-amd64 \
  kubectl version --client
```

作用：

- 验证镜像内已包含 kubectl。
