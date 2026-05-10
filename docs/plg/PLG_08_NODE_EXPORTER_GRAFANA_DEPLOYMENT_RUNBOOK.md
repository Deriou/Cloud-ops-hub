# PLG-08 节点 CPU / 内存真实指标部署 Runbook

## 1. 本阶段目标

本阶段补齐 Grafana 看板中的节点级 CPU / 内存真实数据，让当前 PLG 观测闭环从业务指标扩展到基础资源指标：

```text
K3s Node
-> node-exporter DaemonSet
-> Prometheus scrape
-> Grafana Cloud Ops Overview
```

完成后，Grafana `Cloud Ops Overview` 会新增：

- `节点 CPU 使用率`
- `节点内存使用率`

当前只做节点级资源展示，不做 Pod / Container 级资源，不引入 `kube-state-metrics`，也不新增后端 `ops-core`。

## 2. 涉及文件

Prometheus Helm values：

```text
infra/helm/prometheus/values-dev.yaml
```

Grafana Dashboard：

```text
infra/helm/grafana/dashboards/cloud-ops-overview.json
```

## 3. 新增镜像

本轮只新增一个镜像：

```text
quay.io/prometheus/node-exporter:v1.9.1
```

项目内使用的 ACR 镜像地址：

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/node-exporter:v1.9.1-amd64
```

不需要新增：

- `kube-state-metrics`
- cAdvisor 镜像
- 后端服务镜像
- 前端镜像

## 4. ECS 前置检查

登录 ECS：

```bash
ssh deriou@8.145.50.162
```

确认 K3s 可用：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl get nodes -o wide
```

确认 `monitoring` namespace 存在：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl get ns monitoring
```

确认 ACR Secret 存在：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring get secret acr-secret
```

如果 `acr-secret` 不存在，先按现有部署文档补齐镜像拉取密钥。

## 5. 准备 node-exporter 镜像

在能访问公网镜像源和 ACR 的机器上执行：

```bash
docker pull --platform linux/amd64 quay.io/prometheus/node-exporter:v1.9.1
```

打 ACR tag：

```bash
docker tag quay.io/prometheus/node-exporter:v1.9.1 \
  crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/node-exporter:v1.9.1-amd64
```

推送到 ACR：

```bash
docker push crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/node-exporter:v1.9.1-amd64
```

说明：

- `node-exporter` 以 DaemonSet 运行，每个 K3s 节点一个 Pod。
- 当前是单节点 K3s，所以正常情况下只会看到一个 node-exporter Pod。

## 6. 部署 Prometheus 变更

在仓库根目录执行：

```bash
helm upgrade --install prometheus prometheus-community/prometheus \
  -n monitoring \
  -f infra/helm/prometheus/values-dev.yaml
```

命令作用：

- 开启 `prometheus-node-exporter`。
- 使用 ACR 中的 `node-exporter:v1.9.1-amd64` 镜像。
- 新增 Prometheus 抓取任务 `node-exporter`。

确认 node-exporter DaemonSet：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring get ds,pod -l app.kubernetes.io/name=prometheus-node-exporter -o wide
```

预期：

```text
DESIRED   CURRENT   READY
1         1         1
```

如果 Pod 处于 `ImagePullBackOff`，优先检查：

- ACR 镜像是否已推送。
- `monitoring` namespace 是否有 `acr-secret`。
- `prometheus-node-exporter.imagePullSecrets` 是否包含 `acr-secret`。

## 7. 验证 Prometheus 指标

临时打开 Prometheus：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring port-forward svc/prometheus-server 9090:80 --address 127.0.0.1
```

如果从本地电脑访问 ECS，可另开一个本地终端建立 SSH 隧道：

```bash
ssh -L 9090:127.0.0.1:9090 deriou@8.145.50.162
```

浏览器访问：

```text
http://localhost:9090/targets
```

确认 `node-exporter` target 为 `UP`。

在 Prometheus UI 查询：

```promql
up{job="node-exporter"}
```

预期返回 `1`。

继续查询 CPU 指标：

```promql
node_cpu_seconds_total{job="node-exporter"}
```

继续查询内存指标：

```promql
node_memory_MemAvailable_bytes{job="node-exporter"}
node_memory_MemTotal_bytes{job="node-exporter"}
```

如果这些指标有数据，说明 Prometheus 已经拿到节点资源指标。

## 8. 部署 Grafana Dashboard 变更

更新 Grafana：

```bash
helm upgrade --install grafana grafana/grafana \
  -n monitoring \
  -f infra/helm/grafana/values-dev.yaml \
  --set-file dashboards.default.cloud-ops-overview.json=infra/helm/grafana/dashboards/cloud-ops-overview.json
```

确认 Grafana Pod 重建完成：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring rollout status deploy/grafana
```

打开看板：

```text
http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
```

检查新增面板：

- `节点 CPU 使用率`
- `节点内存使用率`

两个面板使用的 PromQL：

```promql
1 - avg by (instance) (rate(node_cpu_seconds_total{job="node-exporter", mode="idle"}[5m]))
```

```promql
1 - (node_memory_MemAvailable_bytes{job="node-exporter"} / node_memory_MemTotal_bytes{job="node-exporter"})
```

Grafana 单位为 `percentunit`，所以展示为百分比。

## 9. 常见问题

### 9.1 node-exporter Pod 没有启动

查看事件：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring describe pod -l app.kubernetes.io/name=prometheus-node-exporter
```

重点看：

- 镜像拉取是否失败。
- 是否缺少 `acr-secret`。
- 节点是否有 taint。当前 values 已设置 `tolerations: operator: Exists`，通常可以调度到 K3s 单节点。

### 9.2 Prometheus Targets 看不到 node-exporter

确认 Service 名：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring get svc | grep node-exporter
```

当前 scrape 配置按以下 Service 名过滤：

```text
prometheus-prometheus-node-exporter
```

如果 Helm release 名不是 `prometheus`，Service 名可能不同，需要同步调整 `infra/helm/prometheus/values-dev.yaml` 中的 relabel regex。

### 9.3 Grafana 面板无数据

先在 Prometheus 中确认：

```promql
up{job="node-exporter"}
```

再确认：

```promql
node_cpu_seconds_total{job="node-exporter"}
node_memory_MemTotal_bytes{job="node-exporter"}
```

如果 Prometheus 有数据但 Grafana 没有，检查 Grafana 数据源 `Prometheus` 是否仍指向：

```text
http://prometheus-server.monitoring.svc.cluster.local
```

### 9.4 Grafana 首页提示 Failed to load home dashboard

如果 Grafana 首页出现：

```text
Failed to load home dashboard
Dashboards > Not found
```

优先确认 Dashboard JSON 是否被 Helm 注入并挂载到 Pod：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring exec deploy/grafana -- \
  ls -l /var/lib/grafana/dashboards/default
```

预期能看到：

```text
cloud-ops-overview.json
```

如果目录为空或文件不存在，通常是部署 Grafana 时漏掉了 `--set-file`。重新执行：

```bash
helm upgrade --install grafana grafana/grafana \
  -n monitoring \
  -f infra/helm/grafana/values-dev.yaml \
  --set-file dashboards.default.cloud-ops-overview.json=infra/helm/grafana/dashboards/cloud-ops-overview.json
```

再查看 rollout：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring rollout status deploy/grafana
```

如果文件存在但仍然无法加载，查看 Grafana 日志：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring logs deploy/grafana --tail=200 | grep -Ei "dashboard|provision|error"
```

重点关注：

- dashboard JSON 解析失败。
- datasource `prometheus` 或 `loki` 不存在。
- provisioning path 权限或文件读取失败。

## 10. 后端配置结论

本轮不需要修改 Java 后端配置。

原因：

- 节点 CPU / 内存来自 `node-exporter`，不是 Spring Boot Actuator。
- Prometheus 直接抓取 node-exporter，不经过 `gateway-portal` 或 `blog-service`。
- Grafana 直接查询 Prometheus，不需要新增业务 API。

后续如果要把前端 `/ops/cluster` 的 mock 摘要替换为真实数据，再考虑新增或实现 `ops-core`：

- 查询 Prometheus HTTP API 获取 CPU / 内存。
- 查询 K8s API 获取工作负载状态。
- 对外暴露 `/api/v1/ops/clusters/summary`。

## 11. 完成标准

满足以下条件即可认为本阶段完成：

- `kubectl -n monitoring get ds` 中 node-exporter `READY=1/1`。
- Prometheus `up{job="node-exporter"}` 返回 `1`。
- Prometheus 能查询到 `node_cpu_seconds_total`。
- Prometheus 能查询到 `node_memory_MemAvailable_bytes`。
- Grafana `Cloud Ops Overview` 能看到节点 CPU / 内存趋势。
