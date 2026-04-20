# PLG-03 Prometheus 最小部署操作计划

## 1. 本阶段目标

本阶段目标是先在 K3s 集群中部署一个最小 Prometheus，让它能够抓取当前项目里两个 Spring Boot 服务的指标：

- `gateway-portal`
- `blog-service`

本阶段不部署 Grafana、不部署 Loki、不做告警规则。先把“应用指标 -> Prometheus 抓取 -> Prometheus 查询验证”这条链路跑通。

当前项目已经完成了应用侧准备：

- `gateway-portal` 暴露 `/actuator/prometheus`
- `blog-service` 暴露 `/actuator/prometheus`
- 两个服务的 K8s Service 都是 `ClusterIP`
- 两个服务都运行在 `cloud-ops` namespace
- 两个服务的 Service 端口都是 `8080`

因此 Prometheus 第一版抓取地址应为：

```text
http://gateway-portal.cloud-ops.svc.cluster.local:8080/actuator/prometheus
http://blog-service.cloud-ops.svc.cluster.local:8080/actuator/prometheus
```

## 2. 部署方式选择

本阶段推荐使用 Helm 部署 Prometheus。

原因：

- Prometheus 的 Kubernetes 部署涉及 Deployment、Service、ConfigMap、RBAC、PVC 等多个资源，手写 YAML 容易遗漏。
- Helm chart 已经封装了大部分标准配置，更适合学习阶段快速跑通。
- 以后升级到 Grafana、Alertmanager 或 `kube-prometheus-stack` 时，也更容易理解 Helm 的使用方式。

本阶段推荐 chart：

```text
prometheus-community/prometheus
```

不推荐一开始使用：

```text
prometheus-community/kube-prometheus-stack
```

原因是 `kube-prometheus-stack` 会同时引入 Prometheus Operator、Grafana、Alertmanager、ServiceMonitor、PodMonitor 等概念，学习曲线更陡。等本阶段跑通后，再进入 Grafana 阶段或升级到 Operator 会更顺。

## 3. 前置检查

### 3.1 确认 kubectl 可用

```bash
kubectl get nodes
```

命令用途：

- 查看当前 kubectl 是否能连接 K3s 集群。
- 查看节点是否处于 `Ready` 状态。

预期结果：

```text
NAME      STATUS   ROLES
xxx       Ready    ...
```

如果节点不是 `Ready`，先不要部署 Prometheus，优先排查 K3s。

### 3.2 确认业务 namespace 存在

```bash
kubectl get ns cloud-ops
```

命令用途：

- 确认业务应用所在 namespace 已存在。

预期结果：

```text
NAME        STATUS   AGE
cloud-ops   Active   ...
```

### 3.3 确认业务服务已经运行

```bash
kubectl -n cloud-ops get pod
kubectl -n cloud-ops get svc
```

命令用途：

- `get pod`：确认 `gateway-portal` 和 `blog-service` 的 Pod 正常运行。
- `get svc`：确认 `gateway-portal` 和 `blog-service` 的 Service 存在。

预期结果：

- `gateway-portal` Pod 为 `Running`
- `blog-service` Pod 为 `Running`
- `gateway-portal` Service 暴露 `8080`
- `blog-service` Service 暴露 `8080`

### 3.4 在集群内验证应用指标 endpoint

先用临时 Pod 在集群内请求两个服务：

```bash
kubectl -n cloud-ops run curl-check --rm -it --image=curlimages/curl --restart=Never -- \
  curl -s http://gateway-portal:8080/actuator/prometheus
```

命令用途：

- 在 `cloud-ops` namespace 内创建一个临时 curl Pod。
- 通过 Service 名 `gateway-portal` 访问应用指标。
- `--rm` 表示命令结束后自动删除临时 Pod。
- `--restart=Never` 表示创建普通 Pod，不创建 Deployment。

再验证 `blog-service`：

```bash
kubectl -n cloud-ops run curl-check --rm -it --image=curlimages/curl --restart=Never -- \
  curl -s http://blog-service:8080/actuator/prometheus
```

预期结果：

返回内容里能看到类似：

```text
# HELP
jvm_memory_used_bytes
http_server_requests
```

如果这里失败，说明 Prometheus 即使部署成功也抓不到应用指标，需要先排查应用 Service、Pod 或 `/actuator/prometheus`。

## 4. 准备 Prometheus namespace

建议把监控组件放在单独 namespace：

```bash
kubectl create namespace monitoring
```

命令用途：

- 创建 `monitoring` namespace。
- 后续 Prometheus、Grafana、Loki 都可以放在这个 namespace。

如果 namespace 已经存在，会提示 `AlreadyExists`。这不是严重错误，可以继续。

检查：

```bash
kubectl get ns monitoring
```

预期结果：

```text
NAME         STATUS   AGE
monitoring   Active   ...
```

## 5. 准备 Helm 仓库

### 5.1 添加 chart 仓库

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
```

命令用途：

- 告诉 Helm 去哪里下载 Prometheus chart。
- `prometheus-community` 是本地仓库别名。

### 5.2 更新 chart 索引

```bash
helm repo update
```

命令用途：

- 拉取最新 chart 索引。
- 让本地 Helm 知道有哪些 chart 和版本可用。

### 5.3 查看可用 chart

```bash
helm search repo prometheus-community/prometheus
```

命令用途：

- 确认 Helm 能找到 `prometheus-community/prometheus`。
- 查看当前可用 chart 版本。

预期结果：

```text
NAME                            CHART VERSION   APP VERSION
prometheus-community/prometheus ...
```

## 6. 生成学习版 values 文件

建议把 Prometheus 的自定义配置保存到文档外的临时文件中，例如：

```bash
mkdir -p /tmp/cloud-ops-hub-plg
```

命令用途：

- 创建一个临时目录保存 Helm values 文件。
- 放在 `/tmp` 是为了避免一开始把实验配置直接混入项目代码。

创建 values 文件：

```bash
cat > /tmp/cloud-ops-hub-plg/prometheus-values.yaml <<'EOF'
server:
  persistentVolume:
    enabled: false
  retention: "3d"
  service:
    type: ClusterIP

alertmanager:
  enabled: false

prometheus-pushgateway:
  enabled: false

kube-state-metrics:
  enabled: false

prometheus-node-exporter:
  enabled: false

serverFiles:
  prometheus.yml:
    scrape_configs:
      - job_name: "prometheus"
        static_configs:
          - targets:
              - "localhost:9090"

      - job_name: "cloud-ops-gateway-portal"
        metrics_path: "/actuator/prometheus"
        scrape_interval: 30s
        static_configs:
          - targets:
              - "gateway-portal.cloud-ops.svc.cluster.local:8080"
            labels:
              namespace: "cloud-ops"
              service: "gateway-portal"

      - job_name: "cloud-ops-blog-service"
        metrics_path: "/actuator/prometheus"
        scrape_interval: 30s
        static_configs:
          - targets:
              - "blog-service.cloud-ops.svc.cluster.local:8080"
            labels:
              namespace: "cloud-ops"
              service: "blog-service"
EOF
```

配置说明：

- `server.persistentVolume.enabled=false`：学习阶段先不持久化 Prometheus 数据，删除 Pod 后指标历史会丢失，但部署最简单。
- `retention: "3d"`：指标保留 3 天，避免学习环境占用太多磁盘。
- `alertmanager.enabled=false`：本阶段不做告警。
- `pushgateway.enabled=false`：本阶段不采集短任务指标。
- `kube-state-metrics.enabled=false`：本阶段先不采集 K8s 对象状态。
- `node-exporter.enabled=false`：本阶段先不采集节点指标。
- `scrape_configs`：显式配置 Prometheus 要抓取哪些目标。

> 注意：如果后续你想把这个 values 文件纳入项目管理，可以再单独放到 `infra/helm/prometheus/values-dev.yaml`。本阶段先用 `/tmp`，更适合手动学习。

## 7. 安装 Prometheus

```bash
helm upgrade --install prometheus prometheus-community/prometheus \
  -n monitoring \
  -f /tmp/cloud-ops-hub-plg/prometheus-values.yaml
```

命令用途：

- `helm upgrade --install`：如果还没安装就安装，如果已安装就升级。
- `prometheus`：Helm release 名称。
- `prometheus-community/prometheus`：要安装的 chart。
- `-n monitoring`：安装到 `monitoring` namespace。
- `-f`：使用我们自定义的 values 文件。

预期结果：

```text
Release "prometheus" has been upgraded/install...
```

## 8. 查看部署结果

### 8.1 查看 Helm release

```bash
helm -n monitoring list
```

命令用途：

- 查看 `monitoring` namespace 下 Helm 管理的 release。

预期结果：

```text
NAME         NAMESPACE    STATUS
prometheus   monitoring   deployed
```

### 8.2 查看 Pod

```bash
kubectl -n monitoring get pod
```

命令用途：

- 查看 Prometheus 相关 Pod 是否启动成功。

预期结果：

```text
prometheus-server-xxxxx   Running
```

如果 Pod 长时间不是 `Running`，执行：

```bash
kubectl -n monitoring describe pod <pod-name>
kubectl -n monitoring logs <pod-name> --tail=100
```

命令用途：

- `describe pod`：查看调度失败、镜像拉取失败、资源不足等事件。
- `logs`：查看容器启动日志。

### 8.3 查看 Service

```bash
kubectl -n monitoring get svc
```

命令用途：

- 查看 Prometheus server 的 Service 名称和端口。

通常你会看到类似：

```text
prometheus-server   ClusterIP   ...   80/TCP
```

## 9. 访问 Prometheus UI

Prometheus 暂时不要直接暴露公网，学习阶段建议使用 `port-forward`。

```bash
kubectl -n monitoring port-forward svc/prometheus-server 9090:80
```

命令用途：

- 把本机 `9090` 端口转发到集群内 `prometheus-server` Service 的 `80` 端口。
- 只在当前终端会话中生效。
- 终端不要关闭，关闭后转发会停止。

然后浏览器访问：

```text
http://localhost:9090
```

如果你在服务器上操作，而不是本机操作，可以使用 SSH 端口转发：

```bash
ssh -L 9090:localhost:9090 <user>@<server-ip>
```

命令用途：

- 把服务器上的 `localhost:9090` 映射到你本机的 `localhost:9090`。
- 适合 K3s 跑在云服务器上的情况。

## 10. 验证 Prometheus 抓取状态

打开 Prometheus UI 后，进入：

```text
Status -> Targets
```

应该看到三个 job：

- `prometheus`
- `cloud-ops-gateway-portal`
- `cloud-ops-blog-service`

预期状态：

```text
UP
```

如果 `cloud-ops-gateway-portal` 或 `cloud-ops-blog-service` 是 `DOWN`，优先检查：

- Service 名是否正确
- namespace 是否正确
- 端口是否为 `8080`
- `/actuator/prometheus` 是否能访问
- 应用镜像是否已经包含阶段一代码

## 11. 查询指标验证

在 Prometheus UI 的查询框中执行：

```promql
up
```

命令用途：

- 查看所有 scrape target 是否可用。
- `1` 表示可抓取，`0` 表示不可抓取。

推荐继续查询：

```promql
up{job="cloud-ops-gateway-portal"}
```

```promql
up{job="cloud-ops-blog-service"}
```

预期结果：

```text
1
```

查询 JVM 指标：

```promql
jvm_memory_used_bytes{application="gateway-portal"}
```

```promql
jvm_memory_used_bytes{application="blog-service"}
```

查询 HTTP 请求指标：

```promql
http_server_requests_seconds_count
```

如果 HTTP 请求指标为空，可以先访问一次业务接口或 actuator：

```bash
kubectl -n cloud-ops port-forward svc/gateway-portal 18080:8080
```

另开一个终端：

```bash
curl -i http://localhost:18080/actuator/health
```

然后再回 Prometheus 查询：

```promql
http_server_requests_seconds_count{application="gateway-portal"}
```

## 12. 常见问题排查

### 12.1 Helm 找不到 chart

现象：

```text
Error: repo prometheus-community not found
```

处理：

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
```

### 12.2 Prometheus Pod 镜像拉取失败

查看：

```bash
kubectl -n monitoring describe pod <pod-name>
```

如果看到：

```text
ImagePullBackOff
```

可能原因：

- 服务器访问海外镜像仓库不稳定。
- 镜像源被网络限制。

学习阶段处理思路：

- 先多等一会儿或重试。
- 如果长期失败，再考虑配置镜像代理或替换 chart values 中的镜像仓库。

### 12.3 Target DOWN

先在集群内验证：

```bash
kubectl -n monitoring run curl-check --rm -it --image=curlimages/curl --restart=Never -- \
  curl -i http://gateway-portal.cloud-ops.svc.cluster.local:8080/actuator/prometheus
```

再验证：

```bash
kubectl -n monitoring run curl-check --rm -it --image=curlimages/curl --restart=Never -- \
  curl -i http://blog-service.cloud-ops.svc.cluster.local:8080/actuator/prometheus
```

如果这里访问不通，说明是集群网络、Service、应用启动或 endpoint 问题，不是 Prometheus 本身问题。

### 12.4 查询不到 `application` 标签

检查应用配置是否包含：

```properties
management.metrics.tags.application=${spring.application.name}
```

再确认当前运行的镜像是否已经包含阶段一改动。只改了代码但没有重新构建镜像、没有更新 Deployment，集群里还是旧版本。

## 13. 卸载与回滚

如果想删除 Prometheus：

```bash
helm -n monitoring uninstall prometheus
```

命令用途：

- 删除 Helm release 管理的 Prometheus 资源。

检查是否删除：

```bash
kubectl -n monitoring get pod,svc
```

如果本阶段使用的是非持久化配置，卸载后不会保留 Prometheus 历史指标。

如果确认不再需要 `monitoring` namespace：

```bash
kubectl delete namespace monitoring
```

命令用途：

- 删除整个监控 namespace。

注意：

- 这会删除 namespace 里的所有资源。
- 后续如果已经部署 Grafana、Loki，不要直接删 namespace。

## 14. 本阶段完成标准

满足以下条件，就可以认为 Prometheus 最小部署阶段完成：

- `helm -n monitoring list` 显示 `prometheus` 为 `deployed`
- `kubectl -n monitoring get pod` 显示 Prometheus server 为 `Running`
- 可以通过 `kubectl port-forward` 打开 Prometheus UI
- `Status -> Targets` 中 `cloud-ops-gateway-portal` 为 `UP`
- `Status -> Targets` 中 `cloud-ops-blog-service` 为 `UP`
- PromQL 查询 `up{job="cloud-ops-gateway-portal"}` 返回 `1`
- PromQL 查询 `up{job="cloud-ops-blog-service"}` 返回 `1`
- 能查询到 JVM 指标，例如 `jvm_memory_used_bytes`
- 能查询到 HTTP 指标，例如 `http_server_requests_seconds_count`

## 15. 下一阶段衔接

Prometheus 跑通后，下一步建议是部署 Grafana。

Grafana 阶段要做的事情：

- 添加 Prometheus 数据源
- 做一个最小服务健康面板
- 展示 `up`
- 展示 HTTP 请求量
- 展示 5xx 错误趋势
- 展示 JVM 内存使用

等 Grafana 面板跑通后，再进入 Loki/Promtail 日志采集阶段。

## 16. 参考资料

- Prometheus Community Helm Charts：https://github.com/prometheus-community/helm-charts
- Prometheus Helm Chart：https://artifacthub.io/packages/helm/prometheus-community/prometheus
- Prometheus 配置文档：https://prometheus.io/docs/prometheus/latest/configuration/configuration/
