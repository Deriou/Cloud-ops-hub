# PLG-04 Loki + Promtail 最小部署操作计划

## 1. 本阶段目标

本阶段目标是在当前 K3s 集群中部署最小日志链路：

```text
Spring Boot stdout 日志 -> Promtail -> Loki -> LogQL 查询
```

当前项目已经完成了日志侧的应用准备：

- `gateway-portal` 已输出包含 `service`、`traceId`、`method`、`path` 的控制台日志
- `blog-service` 已输出包含 `service`、`traceId`、`method`、`path` 的控制台日志
- 业务服务运行在 `cloud-ops` namespace
- `/actuator/**` 访问日志已经降噪
- 响应头、响应体、后端日志已经可以通过 `traceId` 关联

本阶段先不追求生产级日志平台，只追求最小可验证闭环：

- Loki Pod 正常运行
- Promtail DaemonSet 正常运行
- `loki-gateway` Service 可被集群内访问
- Loki 能收到 `cloud-ops` namespace 的容器日志
- 日志保留时间控制在 `3~7` 天，本阶段配置为 `168h`
- 能通过 `traceId` 找到一次业务请求的日志
- 能区分 `gateway-portal` 与 `blog-service` 日志

> 注意：截至 2026-04，Grafana 官方文档已标注 Promtail 进入 EOL。这个项目仍然按学习计划使用 Promtail，是因为它配置直观，适合理解 Loki 日志采集链路。后续生产化阶段可以再迁移到 Grafana Alloy 或 OpenTelemetry Collector。

## 2. 部署方式选择

本阶段推荐：

```text
Loki：Helm chart grafana-community/loki
Promtail：Helm chart grafana/promtail
```

原因：

- Loki 自己手写 YAML 容易遗漏 StatefulSet、Service、ConfigMap、RBAC 等资源。
- Promtail 需要以 DaemonSet 形式读取节点上的容器日志，Helm chart 已经封装了常用挂载和 Kubernetes 服务发现配置。
- 当前 Prometheus 已经通过 Helm 部署，继续使用 Helm 可以保持监控组件部署方式一致。

本阶段不推荐：

```text
loki-stack
kube-prometheus-stack
Loki distributed mode
Simple scalable mode
```

原因：

- `loki-stack` 容易把 Loki、Promtail、Grafana 混在一起，不利于学习每一层的职责。
- `kube-prometheus-stack` 会引入 Operator、ServiceMonitor、PrometheusRule 等概念，当前阶段太重。
- distributed/simple scalable 模式会引入更多组件和对象存储，当前单节点 ECS 没必要。

## 3. 关键设计约定

### 3.1 namespace

监控组件继续放在：

```text
monitoring
```

业务日志来源重点关注：

```text
cloud-ops
```

### 3.2 镜像架构

当前 ECS 是：

```text
linux/amd64
```

因此 Loki、Promtail 以及 Loki chart 可能用到的 gateway 镜像，都必须同步 `linux/amd64` 版本到 ACR，并使用 `-amd64` tag。

当前最小部署只保留 3 个镜像：

```text
grafana/loki:3.7.1
nginxinc/nginx-unprivileged:1.29-alpine
grafana/promtail:3.5.1
```

当前阶段明确关闭这些非核心组件，避免额外公网镜像：

```text
lokiCanary
gateway metrics exporter / access-log-exporter
ruler
rules sidecar / k8s-sidecar
chunks/results cache
test job
```

如果 `helm template` 仍渲染出新的公网镜像，先不要部署。处理原则是：能明确关掉的先关掉；确实需要的再同步到 ACR。

如果镜像架构错误，Pod 通常会报：

```text
exec format error
```

### 3.3 ACR Secret

`monitoring` namespace 必须有：

```text
acr-secret
```

否则私有 ACR 镜像会拉取失败。

### 3.4 Loki label 原则

低基数字段可以做 label：

- `namespace`
- `pod`
- `container`
- `app`
- `service`

高基数字段不要做 label：

- `traceId`
- 用户 ID
- 完整 URL 参数
- 请求体
- 订单号
- 时间戳

本项目第一版建议这样查 `traceId`：

```logql
{namespace="cloud-ops"} |= "traceId=demo-trace-001"
```

而不是这样设计 label：

```logql
{traceId="demo-trace-001"}
```

## 4. 前置检查

### 4.1 确认 kubectl 可用

```bash
kubectl get nodes
```

命令用途：

- 确认当前终端能连接 K3s 集群。
- 确认 ECS 节点处于 `Ready` 状态。

预期结果：

```text
NAME      STATUS   ROLES
xxx       Ready    ...
```

如果节点不是 `Ready`，先不要部署 Loki，优先排查 K3s。

### 4.2 确认业务服务正在运行

```bash
kubectl -n cloud-ops get pod
kubectl -n cloud-ops get svc
```

命令用途：

- `get pod`：确认 `gateway-portal` 和 `blog-service` Pod 正常运行。
- `get svc`：确认两个服务的 Service 存在。

预期结果：

- `gateway-portal` Pod 为 `Running`
- `blog-service` Pod 为 `Running`
- 两个服务都有对应 Service

### 4.3 确认业务日志已经输出到 stdout

```bash
kubectl -n cloud-ops logs deploy/gateway-portal --tail=50
```

命令用途：

- 查看 `gateway-portal` 最近 50 行容器日志。
- 验证日志是否输出到了 Kubernetes 能采集的标准输出。

继续检查 `blog-service`：

```bash
kubectl -n cloud-ops logs deploy/blog-service --tail=50
```

预期能看到类似字段：

```text
service=gateway-portal traceId=... method=... path=...
service=blog-service traceId=... method=... path=...
```

如果 `kubectl logs` 看不到业务日志，Promtail 部署成功也采不到应用日志，需要先排查应用日志配置。

### 4.4 确认 monitoring namespace 存在

```bash
kubectl get ns monitoring
```

命令用途：

- 确认 Prometheus 阶段创建的 `monitoring` namespace 仍然存在。

如果不存在，创建它：

```bash
kubectl create namespace monitoring
```

命令用途：

- 创建监控组件专用 namespace。

如果提示 `AlreadyExists`，说明 namespace 已经存在，可以继续。

### 4.5 确认 monitoring namespace 有 ACR Secret

```bash
kubectl -n monitoring get secret acr-secret
```

命令用途：

- 确认 `monitoring` namespace 里存在私有镜像仓库拉取密钥。

如果不存在，可以从 `cloud-ops` namespace 复制：

```bash
kubectl -n cloud-ops get secret acr-secret -o jsonpath='{.data.\.dockerconfigjson}' \
  | base64 -d > /tmp/acr-dockerconfigjson

kubectl -n monitoring create secret generic acr-secret \
  --from-file=.dockerconfigjson=/tmp/acr-dockerconfigjson \
  --type=kubernetes.io/dockerconfigjson \
  --dry-run=client -o yaml \
  | kubectl apply -f -

rm -f /tmp/acr-dockerconfigjson
```

命令用途：

- 第一条命令：从 `cloud-ops` 读取已有 ACR 登录配置，并临时保存到 `/tmp`。
- 第二条命令：在 `monitoring` namespace 创建或更新同名 `acr-secret`。
- 第三条命令：删除本机临时密钥文件。

再次检查：

```bash
kubectl -n monitoring get secret acr-secret
```

## 5. 准备 Helm 仓库

### 5.1 添加 Loki chart 仓库

```bash
helm repo add grafana-community https://grafana-community.github.io/helm-charts
```

命令用途：

- 添加当前 Loki Helm chart 仓库。
- `grafana-community` 是本地 Helm 仓库别名。

### 5.2 添加 Promtail chart 仓库

```bash
helm repo add grafana https://grafana.github.io/helm-charts
```

命令用途：

- 添加 Grafana Helm chart 仓库。
- Promtail chart 仍通过这个仓库安装。

### 5.3 更新 Helm 索引

```bash
helm repo update
```

命令用途：

- 拉取本地 Helm 仓库索引。
- 让 `helm search repo` 能看到最新 chart 信息。

### 5.4 查看 Loki chart

```bash
helm search repo grafana-community/loki --versions | head
```

命令用途：

- 查看 Loki chart 是否可用。
- 查看当前 chart 版本和 app 版本。

注意：

- Loki chart 在 2026 年发生过仓库迁移和 chart 版本变化。
- 执行部署前一定要用 `helm template` 预检查 values 文件。

### 5.5 查看 Promtail chart

```bash
helm search repo grafana/promtail --versions | head
```

命令用途：

- 查看 Promtail chart 是否可用。
- 查看当前 Promtail app 版本。

## 6. 准备本项目 values 文件

本仓库为本阶段新增两个文件：

```text
infra/helm/loki/values-dev.yaml
infra/helm/promtail/values-dev.yaml
```

这两个文件的职责不同：

- `loki/values-dev.yaml`：控制 Loki 如何存储、查询、暴露服务。
- `promtail/values-dev.yaml`：控制 Promtail 从哪里采日志、推到哪个 Loki 地址。

### 6.1 创建目录

```bash
mkdir -p infra/helm/loki infra/helm/promtail
```

命令用途：

- 创建 Loki 和 Promtail 的 Helm values 文件目录。
- `-p` 表示目录已存在时不报错。

### 6.2 Loki values 第一版建议

文件路径：

```text
infra/helm/loki/values-dev.yaml
```

当前第一版内容：

```yaml
imagePullSecrets:
  - name: acr-secret

deploymentMode: SingleBinary

loki:
  auth_enabled: false
  image:
    registry: crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com
    repository: cloud-ops-hub/loki
    tag: 3.7.1-amd64
    pullPolicy: IfNotPresent
  commonConfig:
    path_prefix: /var/loki
    replication_factor: 1
  storage:
    type: filesystem
  schemaConfig:
    configs:
      - from: "2024-04-01"
        store: tsdb
        object_store: filesystem
        schema: v13
        index:
          prefix: loki_index_
          period: 24h
  limits_config:
    retention_period: 168h
    reject_old_samples: true
    reject_old_samples_max_age: 168h
    volume_enabled: true

singleBinary:
  replicas: 1
  persistence:
    enabled: false
  resources:
    requests:
      cpu: 100m
      memory: 256Mi
    limits:
      cpu: 500m
      memory: 768Mi

gateway:
  enabled: true
  verboseLogging: false
  image:
    registry: crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com
    repository: cloud-ops-hub/nginx-unprivileged
    tag: 1.29-alpine-amd64
    pullPolicy: IfNotPresent
  metrics:
    enabled: false

ruler:
  enabled: false

sidecar:
  rules:
    enabled: false

minio:
  enabled: false

chunksCache:
  enabled: false

resultsCache:
  enabled: false

lokiCanary:
  enabled: false

test:
  enabled: false

backend:
  replicas: 0
read:
  replicas: 0
write:
  replicas: 0
ingester:
  replicas: 0
querier:
  replicas: 0
queryFrontend:
  replicas: 0
queryScheduler:
  replicas: 0
distributor:
  replicas: 0
compactor:
  replicas: 0
indexGateway:
  replicas: 0
bloomPlanner:
  replicas: 0
bloomBuilder:
  replicas: 0
bloomGateway:
  replicas: 0
```

配置说明：

- `imagePullSecrets`：让 Loki Pod 使用 `acr-secret` 拉取 ACR 私有镜像。
- `deploymentMode: SingleBinary`：学习阶段使用单体 Loki。
- `auth_enabled: false`：关闭 Loki 多租户鉴权，Promtail 不需要传 tenant。
- `loki.image`：Loki 主镜像，必须替换为已经推到 ACR 的 amd64 tag。
- `commonConfig.replication_factor: 1`：单副本 Loki 必须设置为 1。
- `storage.type: filesystem`：使用本地文件系统存储日志块。
- `schemaConfig`：使用 TSDB schema v13。
- `retention_period: 168h`：日志保留 7 天。
- `singleBinary.persistence.enabled=false`：学习阶段不持久化，Pod 删除后历史日志会丢。
- `gateway.enabled=true`：提供统一 HTTP 入口，Promtail 可以推送到 `loki-gateway`。
- `chunksCache/resultsCache/lokiCanary/test`：第一版关闭，减少镜像和资源占用。
- 其他组件 `replicas: 0`：避免 chart 默认部署 simple scalable 或 distributed 组件。

注意：

- 当前配置使用 Loki `3.7.1`，与当前 `grafana-community/loki` chart app version 对齐。
- 当前 gateway 使用 `nginxinc/nginx-unprivileged:1.29-alpine` 对应的 ACR `1.29-alpine-amd64` tag。
- `gateway.verboseLogging=false`：关闭 gateway access log exporter，避免额外拉取 `ghcr.io/jkroepke/access-log-exporter`。
- `gateway.metrics.enabled=false`：关闭 gateway metrics exporter。`access-log-exporter` 实际由这个开关控制，当前阶段不需要观察 Loki gateway 自身指标。
- `ruler.enabled=false` 与 `sidecar.rules.enabled=false`：本阶段不做 Loki ruler，避免额外拉取 `kiwigrid/k8s-sidecar`。
- 如果部署前 `helm search repo` 或 `helm show chart` 显示版本已经变化，需要同步调整镜像 tag。
- 如果 `helm template` 报 `deploymentMode` 只支持 `Monolithic`，说明你使用的 chart 版本已经改名，需要根据 `helm show values grafana-community/loki` 的输出调整，不要硬套旧字段。

### 6.3 Promtail values 第一版建议

文件路径：

```text
infra/helm/promtail/values-dev.yaml
```

当前第一版内容：

```yaml
imagePullSecrets:
  - name: acr-secret

image:
  registry: crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com
  repository: cloud-ops-hub/promtail
  tag: 3.5.1-amd64
  pullPolicy: IfNotPresent

config:
  clients:
    - url: http://loki-gateway.monitoring.svc.cluster.local/loki/api/v1/push
  snippets:
    pipelineStages:
      - cri: {}

resources:
  requests:
    cpu: 50m
    memory: 64Mi
  limits:
    cpu: 200m
    memory: 256Mi
```

配置说明：

- `imagePullSecrets`：让 Promtail DaemonSet 使用 ACR 密钥拉取镜像。
- `image`：Promtail 镜像，必须替换为 ACR 中的 amd64 tag。
- `clients.url`：Promtail 推送日志到 Loki 的地址。
- `loki-gateway.monitoring.svc.cluster.local`：Loki gateway 在集群内的 DNS 名称。
- `/loki/api/v1/push`：Loki 接收日志的标准写入接口。
- `pipelineStages.cri`：解析 Kubernetes/containerd 的 CRI 日志格式。
- `resources`：限制 Promtail 在单节点 ECS 上的资源占用。

注意：

- 当前配置按 `grafana/promtail` chart `6.17.1` 的 app version `3.5.1` 准备。
- 如果你后续关闭 `gateway.enabled`，Promtail 的 `clients.url` 也要改成实际 Loki Service 地址。
- 不要第一版就在 Promtail 里写复杂过滤规则，先确认日志能进 Loki。

## 7. 确认 chart 实际需要的镜像

### 7.1 查看 Loki 默认 values

```bash
helm show values grafana-community/loki > /tmp/loki-values-default.yaml
```

命令用途：

- 把 Loki chart 的默认 values 保存到临时文件。
- 方便确认当前 chart 的字段名、默认镜像、默认组件。

查看关键字段：

```bash
rg -n "deploymentMode|singleBinary|gateway:|image:" /tmp/loki-values-default.yaml
```

命令用途：

- 快速定位部署模式、单体组件、gateway 镜像字段。
- 检查本文 values 示例是否和当前 chart 字段一致。

### 7.2 预渲染 Loki YAML

```bash
helm template loki grafana-community/loki \
  -n monitoring \
  -f infra/helm/loki/values-dev.yaml \
  > /tmp/loki-rendered.yaml
```

命令用途：

- 只渲染 Kubernetes YAML，不真正部署。
- 提前发现 values 字段错误、chart 校验失败、组件副本冲突等问题。

如果这一步失败，不要继续 `helm upgrade --install`。先根据错误修正 values 文件。

### 7.3 从渲染结果里找镜像

```bash
rg "image:" /tmp/loki-rendered.yaml
```

命令用途：

- 找出 Loki 部署实际会使用哪些镜像。
- 用于确认哪些镜像需要提前同步到 ACR。

第一版常见镜像：

```text
docker.io/grafana/loki:3.7.1
docker.io/nginxinc/nginx-unprivileged:1.29-alpine
```

实际仓库 values 已经把它们改写为 ACR：

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/loki:3.7.1-amd64
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/nginx-unprivileged:1.29-alpine-amd64
```

如果你没有关闭 `lokiCanary`，还会出现：

```text
docker.io/grafana/loki-canary:3.7.1
```

当前 `values-dev.yaml` 已关闭 `lokiCanary`、`ruler`、rules sidecar 和 gateway metrics exporter。预渲染结果里不应该再出现：

```text
ghcr.io/jkroepke/access-log-exporter
docker.io/kiwigrid/k8s-sidecar
```

如果仍然出现，先不要部署，优先检查 `gateway.metrics.enabled`、`gateway.verboseLogging`、`ruler.enabled` 和 `sidecar.rules.enabled`。

本阶段 Loki 预渲染的理想镜像结果是只出现 ACR 镜像：

```text
cloud-ops-hub/nginx-unprivileged:1.29-alpine-amd64
cloud-ops-hub/loki:3.7.1-amd64
```

### 7.4 查看 Promtail 默认 values

```bash
helm show values grafana/promtail > /tmp/promtail-values-default.yaml
```

命令用途：

- 保存 Promtail chart 默认 values。
- 确认镜像字段和配置字段。

查看关键字段：

```bash
rg -n "image:|clients:|pipelineStages|DaemonSet|daemonset" /tmp/promtail-values-default.yaml
```

命令用途：

- 找到 Promtail 镜像、Loki 客户端地址、日志解析 pipeline、DaemonSet 配置。

### 7.5 预渲染 Promtail YAML

```bash
helm template promtail grafana/promtail \
  -n monitoring \
  -f infra/helm/promtail/values-dev.yaml \
  > /tmp/promtail-rendered.yaml
```

命令用途：

- 只渲染 Promtail YAML，不真正部署。
- 提前验证 `values-dev.yaml` 是否能被 chart 正确解析。

查找镜像：

```bash
rg "image:" /tmp/promtail-rendered.yaml
```

第一版常见镜像：

```text
docker.io/grafana/promtail:3.5.1
```

实际仓库 values 已经把它改写为 ACR：

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/promtail:3.5.1-amd64
```

## 8. 同步镜像到 ACR

本阶段镜像处理采用最务实原则：

```text
以 helm template 实际渲染出来的 image 为准
所有保留组件的镜像必须指向 ACR
不需要的附加组件优先关闭
```

当前 values 目标是最终只需要同步 3 个镜像：

```text
grafana/loki:3.7.1
nginxinc/nginx-unprivileged:1.29-alpine
grafana/promtail:3.5.1
```

### 8.1 设置变量

```bash
export ACR_HOST=crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com
export REGISTRY=$ACR_HOST/cloud-ops-hub
```

命令用途：

- `ACR_HOST`：阿里云 ACR 仓库域名。
- `REGISTRY`：当前项目统一使用的 ACR 命名空间。

登录 ACR：

```bash
docker login $ACR_HOST
```

命令用途：

- 让本机 Docker 可以向 ACR 推送镜像。

### 8.2 同步 Loki 主镜像

```bash
export LOKI_VERSION=3.7.1

docker pull --platform linux/amd64 grafana/loki:$LOKI_VERSION
docker tag grafana/loki:$LOKI_VERSION $REGISTRY/loki:$LOKI_VERSION-amd64
docker push $REGISTRY/loki:$LOKI_VERSION-amd64
```

命令用途：

- `docker pull --platform linux/amd64`：明确拉取 amd64 镜像。
- `docker tag`：改成本项目 ACR 镜像名。
- `docker push`：推送到 ACR。

### 8.3 同步 Loki gateway 镜像

先从 `/tmp/loki-rendered.yaml` 里确认 gateway 使用的上游镜像和 tag，再同步。

示例：

```bash
export NGINX_UNPRIVILEGED_VERSION=1.29-alpine

docker pull --platform linux/amd64 nginxinc/nginx-unprivileged:$NGINX_UNPRIVILEGED_VERSION
docker tag nginxinc/nginx-unprivileged:$NGINX_UNPRIVILEGED_VERSION $REGISTRY/nginx-unprivileged:$NGINX_UNPRIVILEGED_VERSION-amd64
docker push $REGISTRY/nginx-unprivileged:$NGINX_UNPRIVILEGED_VERSION-amd64
```

命令用途：

- 同步 Loki gateway 使用的 nginx 镜像。
- 避免 ECS 从外网镜像仓库拉取失败。

### 8.4 同步 Promtail 镜像

```bash
export PROMTAIL_VERSION=3.5.1

docker pull --platform linux/amd64 grafana/promtail:$PROMTAIL_VERSION
docker tag grafana/promtail:$PROMTAIL_VERSION $REGISTRY/promtail:$PROMTAIL_VERSION-amd64
docker push $REGISTRY/promtail:$PROMTAIL_VERSION-amd64
```

命令用途：

- 同步 Promtail DaemonSet 使用的镜像。
- 使用 `-amd64` tag 固化架构信息。

### 8.5 回填 values 文件

镜像推送完成后，确认 values 文件中的 tag 与刚推送的 ACR tag 一致：

```text
loki: 3.7.1-amd64
nginx-unprivileged: 1.29-alpine-amd64
promtail: 3.5.1-amd64
```

检查：

```bash
rg -n "repository:|tag:" infra/helm/loki/values-dev.yaml infra/helm/promtail/values-dev.yaml
```

命令用途：

- 确认镜像仓库和 tag 指向 ACR。

## 9. 部署 Loki

### 9.1 再次预检查

```bash
helm template loki grafana-community/loki \
  -n monitoring \
  -f infra/helm/loki/values-dev.yaml \
  > /tmp/loki-rendered.yaml
```

命令用途：

- 在真正部署前再做一次 values 渲染检查。
- 确认替换版本号后仍然合法。

### 9.2 安装或升级 Loki

```bash
helm upgrade --install loki grafana-community/loki \
  -n monitoring \
  -f infra/helm/loki/values-dev.yaml
```

命令用途：

- `helm upgrade --install`：未安装则安装，已安装则升级。
- `loki`：Helm release 名称。
- `grafana-community/loki`：Loki chart。
- `-n monitoring`：安装到 `monitoring` namespace。
- `-f`：使用本项目 values 文件。

### 9.3 查看 Loki release

```bash
helm -n monitoring list
```

命令用途：

- 查看 `monitoring` namespace 下 Helm release 状态。

预期结果包含：

```text
loki   monitoring   deployed
```

### 9.4 查看 Loki Pod

```bash
kubectl -n monitoring get pod -l app.kubernetes.io/instance=loki
```

命令用途：

- 查看 Loki release 创建的 Pod。

预期至少看到：

```text
loki-0                  Running
loki-gateway-xxxxx      Running
```

实际名称可能随 chart 版本不同而变化，以 `kubectl -n monitoring get pod` 为准。

### 9.5 查看 Loki Service

```bash
kubectl -n monitoring get svc -l app.kubernetes.io/instance=loki
```

命令用途：

- 查看 Loki 对外暴露给集群内部访问的 Service。

重点确认是否存在：

```text
loki-gateway
```

如果 Service 名不是 `loki-gateway`，后续 Promtail 的 `clients.url` 要按实际 Service 名调整。

## 10. 验证 Loki HTTP 接口

### 10.1 本地端口转发

```bash
kubectl -n monitoring port-forward svc/loki-gateway 3100:80
```

命令用途：

- 把本地 `3100` 端口转发到集群内 `loki-gateway` Service 的 `80` 端口。
- 当前终端不要关闭，关闭后转发停止。

另开一个终端执行：

```bash
curl -i http://localhost:3100/ready
```

命令用途：

- 检查 Loki 是否已经 ready。

预期结果：

```text
HTTP/1.1 200 OK
ready
```

### 10.2 查询标签接口

```bash
curl -s "http://localhost:3100/loki/api/v1/labels"
```

命令用途：

- 查询 Loki 当前已知的 label 名称。
- 刚部署完成且 Promtail 还没安装时，结果可能为空，这是正常的。

## 11. 部署 Promtail

### 11.1 确认 Promtail 推送地址

先确认 Loki gateway Service 存在：

```bash
kubectl -n monitoring get svc loki-gateway
```

命令用途：

- 确认 Promtail values 里的地址可以被集群内 Pod 解析。

Promtail values 中应使用：

```text
http://loki-gateway.monitoring.svc.cluster.local/loki/api/v1/push
```

### 11.2 预检查 Promtail YAML

```bash
helm template promtail grafana/promtail \
  -n monitoring \
  -f infra/helm/promtail/values-dev.yaml \
  > /tmp/promtail-rendered.yaml
```

命令用途：

- 渲染 Promtail YAML，但不真正部署。
- 提前发现 values 错误。

### 11.3 安装或升级 Promtail

```bash
helm upgrade --install promtail grafana/promtail \
  -n monitoring \
  -f infra/helm/promtail/values-dev.yaml
```

命令用途：

- 安装或升级 Promtail。
- Promtail 默认以 DaemonSet 方式运行，每个节点一个 Pod。
- 单节点 ECS 上通常只会有一个 Promtail Pod。

### 11.4 查看 Promtail DaemonSet

```bash
kubectl -n monitoring get ds promtail
```

命令用途：

- 查看 Promtail DaemonSet 是否调度成功。

预期结果：

```text
NAME       DESIRED   CURRENT   READY
promtail   1         1         1
```

### 11.5 查看 Promtail Pod

```bash
kubectl -n monitoring get pod -l app.kubernetes.io/instance=promtail
```

命令用途：

- 查看 Promtail Pod 是否 Running。

### 11.6 查看 Promtail 日志

```bash
kubectl -n monitoring logs ds/promtail --tail=100
```

命令用途：

- 查看 Promtail 是否成功启动。
- 查看是否有推送 Loki 失败、权限不足、路径不存在等错误。

正常情况下，不应该持续看到类似错误：

```text
connection refused
no such host
server returned HTTP status 401
server returned HTTP status 429
```

## 12. 产生一条可追踪业务日志

### 12.1 访问 gateway-portal

先转发 Gateway：

```bash
kubectl -n cloud-ops port-forward svc/gateway-portal 18080:8080
```

命令用途：

- 把本机 `18080` 转发到集群内 `gateway-portal` Service 的 `8080`。

另开终端制造一次带固定 `traceId` 的请求：

```bash
curl -i \
  -H "X-Trace-Id: demo-loki-001" \
  http://localhost:18080/actuator/health
```

命令用途：

- 发送一个固定 `X-Trace-Id` 的请求。
- 便于稍后在 Loki 中搜索对应日志。

注意：

- `/actuator/**` 已经做访问日志降噪，如果查不到请求完成日志，可以换一个业务接口。
- 如果业务接口需要 `X-Ops-Key`，按当前环境补上请求头。

### 12.2 直接检查 Kubernetes 日志

```bash
kubectl -n cloud-ops logs deploy/gateway-portal --tail=100 | rg "demo-loki-001|traceId"
```

命令用途：

- 先确认这条日志确实存在于容器 stdout。
- 如果 Kubernetes 原生日志里都没有，Loki 也不会有。

## 13. 查询 Loki 验收

### 13.1 端口转发 Loki

如果前面的 `port-forward` 已经关闭，重新执行：

```bash
kubectl -n monitoring port-forward svc/loki-gateway 3100:80
```

命令用途：

- 让本机可以通过 `localhost:3100` 请求 Loki API。

### 13.2 查询有哪些 label

另开终端：

```bash
curl -s "http://localhost:3100/loki/api/v1/labels"
```

命令用途：

- 查看 Loki 已经收到哪些 label。

预期结果中通常会有：

```text
namespace
pod
container
```

### 13.3 查询 namespace label 的值

```bash
curl -s "http://localhost:3100/loki/api/v1/label/namespace/values"
```

命令用途：

- 查看 Loki 收到过哪些 namespace 的日志。

预期包含：

```text
cloud-ops
monitoring
```

如果没有 `cloud-ops`，说明 Promtail 可能没有采到业务 Pod 日志。

### 13.4 查询 cloud-ops 最近日志

```bash
curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={namespace="cloud-ops"}' \
  --data-urlencode 'limit=20'
```

命令用途：

- 使用 LogQL 查询 `cloud-ops` namespace 最近日志。
- `limit=20` 表示最多返回 20 条。

如果返回 JSON 里有日志内容，说明链路已经基本打通。

### 13.5 查询 gateway-portal 日志

先看当前有哪些 `pod` 或 `app` label：

```bash
curl -s "http://localhost:3100/loki/api/v1/labels"
```

如果存在 `app` label，可以查：

```bash
curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={namespace="cloud-ops", app="gateway-portal"}' \
  --data-urlencode 'limit=20'
```

如果没有 `app` label，可以先用内容过滤：

```bash
curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={namespace="cloud-ops"} |= "service=gateway-portal"' \
  --data-urlencode 'limit=20'
```

命令用途：

- 第一种方式使用 label 过滤。
- 第二种方式使用日志内容过滤。
- 第一版先能查到即可，后续再优化 label 提取。

### 13.6 查询 traceId

```bash
curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={namespace="cloud-ops"} |= "traceId=demo-loki-001"' \
  --data-urlencode 'limit=20'
```

命令用途：

- 通过日志内容搜索固定 `traceId`。
- 验证响应头、响应体、后端日志、Loki 查询是否能串起来。

预期结果：

- 返回 JSON 中包含 `demo-loki-001`
- 能看到对应服务、路径、日志级别或请求信息

## 14. 常用 LogQL 查询

查看 `cloud-ops` 所有日志：

```logql
{namespace="cloud-ops"}
```

按服务内容过滤：

```logql
{namespace="cloud-ops"} |= "service=gateway-portal"
```

查某个 traceId：

```logql
{namespace="cloud-ops"} |= "traceId=demo-loki-001"
```

查错误日志：

```logql
{namespace="cloud-ops"} |= "level=ERROR"
```

查某个路径：

```logql
{namespace="cloud-ops"} |= "path=/api/v1"
```

统计 5 分钟内错误日志数量：

```logql
sum(count_over_time({namespace="cloud-ops"} |= "level=ERROR" [5m]))
```

按服务统计错误日志数量：

```logql
sum by (container) (count_over_time({namespace="cloud-ops"} |= "level=ERROR" [5m]))
```

注意：

- LogQL 里的 `{...}` 是 label selector。
- `|= "xxx"` 是日志内容包含过滤。
- `[5m]` 表示 5 分钟时间窗口。
- 第一版不会强求每个查询都能按 `app` label 聚合，能用 `namespace + 内容过滤` 定位问题即可。

## 15. 常见问题排查

### 15.1 Helm 找不到 Loki chart

现象：

```text
Error: repo grafana-community not found
```

处理：

```bash
helm repo add grafana-community https://grafana-community.github.io/helm-charts
helm repo update
```

命令用途：

- 添加 Loki chart 仓库并更新索引。

### 15.2 Helm 找不到 Promtail chart

现象：

```text
Error: repo grafana not found
```

处理：

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
```

命令用途：

- 添加 Grafana chart 仓库并更新索引。

### 15.3 Loki values 校验失败

先执行：

```bash
helm template loki grafana-community/loki \
  -n monitoring \
  -f infra/helm/loki/values-dev.yaml
```

命令用途：

- 在不部署的情况下显示具体 values/chart 错误。

常见原因：

- `deploymentMode` 和 chart 当前版本不匹配。
- `singleBinary.replicas` 与 `read/write/backend` 等组件副本同时大于 0。
- chart 版本升级后字段名变化。
- `schemaConfig` 缺失。
- `replication_factor` 不是 1。

处理思路：

- 用 `helm show values grafana-community/loki` 查看当前 chart 字段。
- 保持第一版只有一个 Loki 主实例。
- 保持 `read/write/backend` 等非本阶段组件副本为 0。

### 15.4 Loki Pod ImagePullBackOff

查看事件：

```bash
kubectl -n monitoring describe pod <loki-pod-name>
```

命令用途：

- 查看镜像拉取失败原因。

重点检查：

- `monitoring` namespace 是否有 `acr-secret`
- values 文件里的 repository/tag 是否写错
- ACR 里是否已经推送对应 `-amd64` tag
- 是否误用了 ARM 镜像

### 15.5 Loki Pod CrashLoopBackOff

查看日志：

```bash
kubectl -n monitoring logs <loki-pod-name> --tail=200
```

命令用途：

- 查看 Loki 启动失败原因。

常见原因：

- 配置文件不合法。
- storage/schema 配置不匹配。
- 单副本但 `replication_factor` 不是 1。
- 内存限制太低。

### 15.6 Promtail no such host

查看日志：

```bash
kubectl -n monitoring logs ds/promtail --tail=100
```

如果看到：

```text
no such host
```

通常说明 `config.clients.url` 里的 Loki Service 名写错。

检查：

```bash
kubectl -n monitoring get svc
```

命令用途：

- 找到实际 Loki gateway Service 名。

然后修正：

```text
infra/helm/promtail/values-dev.yaml
```

重新部署：

```bash
helm upgrade --install promtail grafana/promtail \
  -n monitoring \
  -f infra/helm/promtail/values-dev.yaml
```

### 15.7 Promtail 401 Unauthorized

现象：

```text
server returned HTTP status 401
```

可能原因：

- Loki 开启了鉴权。
- Promtail 没有配置 tenant 或认证信息。

本阶段建议：

- Loki 使用 `auth_enabled: false`
- Promtail `clients` 不配置 `tenant_id`

修改 Loki values 后重新部署 Loki 和 Promtail。

### 15.8 Loki 里没有 cloud-ops 日志

先确认 Kubernetes 原生日志存在：

```bash
kubectl -n cloud-ops logs deploy/gateway-portal --tail=50
kubectl -n cloud-ops logs deploy/blog-service --tail=50
```

再确认 Promtail 正常：

```bash
kubectl -n monitoring get ds promtail
kubectl -n monitoring logs ds/promtail --tail=100
```

再查 Loki label：

```bash
curl -s "http://localhost:3100/loki/api/v1/label/namespace/values"
```

常见原因：

- Promtail 没有正确挂载 `/var/log/pods` 或 `/var/log/containers`。
- Promtail RBAC 或 ServiceAccount 有问题。
- Loki push 地址错误。
- 查询时间范围太短。
- 应用最近没有新日志。

### 15.9 查不到 traceId

先确认业务日志里确实有这个 traceId：

```bash
kubectl -n cloud-ops logs deploy/gateway-portal --tail=200 | rg "demo-loki-001"
```

再查 Loki：

```bash
curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={namespace="cloud-ops"} |= "demo-loki-001"' \
  --data-urlencode 'limit=50'
```

如果 Kubernetes 日志有、Loki 没有，重点排查 Promtail。

如果 Kubernetes 日志也没有，说明请求没有打到服务，或者该路径被访问日志降噪。

### 15.10 label 太多导致查询变慢

不要把下面字段提升为 label：

- `traceId`
- `path` 的完整值
- 用户 ID
- 请求参数
- 异常堆栈摘要

第一版建议只依赖 Promtail 默认 Kubernetes label，再通过内容搜索定位 `traceId`。

## 16. 卸载与回滚

### 16.1 卸载 Promtail

```bash
helm -n monitoring uninstall promtail
```

命令用途：

- 删除 Promtail DaemonSet、ConfigMap、ServiceAccount 等资源。
- 不会删除 Loki 里已经收到的日志。

### 16.2 卸载 Loki

```bash
helm -n monitoring uninstall loki
```

命令用途：

- 删除 Loki 相关资源。

注意：

- 本阶段 Loki 使用非持久化存储。
- 卸载 Loki 后历史日志会丢失。

### 16.3 不要轻易删除 monitoring namespace

如果 Prometheus 仍在使用，不要执行：

```bash
kubectl delete namespace monitoring
```

原因：

- 这会同时删除 Prometheus、Loki、Promtail 以及后续 Grafana。

## 17. 本阶段完成标准

满足以下条件，就可以认为 Loki + Promtail 最小部署完成：

- `helm -n monitoring list` 显示 `loki` 为 `deployed`
- `helm -n monitoring list` 显示 `promtail` 为 `deployed`
- `kubectl -n monitoring get pod` 中 Loki 相关 Pod 为 `Running`
- `kubectl -n monitoring get ds promtail` 显示 `READY=1`
- `curl http://localhost:3100/ready` 返回 `ready`
- Loki label values 中能看到 `cloud-ops`
- LogQL `{namespace="cloud-ops"}` 能查到日志
- 能查到 `gateway-portal` 日志
- 能查到 `blog-service` 日志
- 能通过固定 `traceId` 查到一次请求日志

## 18. 下一阶段衔接

当前项目已经完成后续衔接：

- Grafana 已部署到 `monitoring` namespace。
- Grafana 已接入 Prometheus 与 Loki 数据源。
- Dashboard 已覆盖服务健康、请求量、5xx、p95、JVM Heap 与错误日志趋势。
- 前端 `/ops/cluster` 已提供 Grafana Dashboard 入口、Prometheus Targets 查看说明和 traceId 联查示例。

后续建议：

- 保持 Grafana Explore 不对访客公开。
- Loki 当前非持久化，生产化前再补 PVC。
- 告警阶段先做 `up=0`、5xx 比例升高、JVM Heap 高位。

## 19. 参考资料

- Loki Helm chart 文档：[https://grafana.com/docs/loki/latest/setup/install/helm/install-monolithic/](https://grafana.com/docs/loki/latest/setup/install/helm/install-monolithic/)
- Promtail 安装文档：[https://grafana.com/docs/loki/latest/send-data/promtail/installation/](https://grafana.com/docs/loki/latest/send-data/promtail/installation/)
- Loki label cardinality 文档：[https://grafana.com/docs/loki/latest/get-started/labels/cardinality/](https://grafana.com/docs/loki/latest/get-started/labels/cardinality/)
