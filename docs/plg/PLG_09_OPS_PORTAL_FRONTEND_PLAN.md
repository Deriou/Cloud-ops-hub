# PLG-09 Ops 门户前端增强计划

> 日期：2026-05-10  
> 状态：实现中  
> 前置成果：PLG-08 节点 CPU / 内存真实指标闭环已完成

## 1. 本轮已完成内容

本轮目标是让前端集群摘要不再展示占位数据，而是通过真实链路读取 Prometheus 指标，形成一个可演示、可解释的运维开发闭环。

已完成内容：

- Prometheus 已接入 `node-exporter`，可以查询节点级 CPU / 内存指标。
- Grafana `Cloud Ops Overview` 看板已展示节点 CPU / 内存真实图表。
- Gateway 新增 `/api/v1/ops/clusters/summary` 接口，从 Prometheus 聚合集群摘要。
- 前端 `/ops/cluster` 页面已读取真实接口，展示 CPU 利用率、内存使用率、服务可用状态。
- 修复 PromQL 中 `{...}` 被 Spring URI 模板误识别的问题，确保 gateway 能正确请求 Prometheus。

## 2. 关键实现链路

当前真实指标链路如下：

```text
node-exporter
  -> Prometheus scrape
  -> Gateway /api/v1/ops/clusters/summary
  -> Web /ops/cluster
  -> Grafana Cloud Ops Overview
```

Gateway 侧核心 PromQL：

```promql
1 - avg(rate(node_cpu_seconds_total{job="node-exporter", mode="idle"}[5m]))
```

```promql
1 - (sum(node_memory_MemAvailable_bytes{job="node-exporter"}) / sum(node_memory_MemTotal_bytes{job="node-exporter"}))
```

```promql
sum(up{namespace="cloud-ops", service=~"gateway-portal|blog-service"})
```

## 3. 部署与验收流程

本轮涉及两类变更：

- 基础配置变更：`ConfigMap`、`Ingress`
- 应用镜像变更：`gateway-portal`、`web`

基础配置需要手动应用，因为当前 Jenkins 流水线主要负责构建镜像、推送 ACR、更新 Deployment 镜像标签，并不会自动应用所有 K8s 基础配置。

配置应用：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl apply -f infra/k8s/base/gateway/configmap.yaml
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl apply -f infra/k8s/base/ingress.yaml
```

应用发布：

```text
1. Jenkins 运行 cloud-ops-gateway-pipeline
2. Jenkins 运行 cloud-ops-web-pipeline
```

发布后验收：

```bash
curl -s http://deriou.com/api/v1/ops/clusters/summary
```

页面验收：

```text
http://deriou.com/ops/cluster
```

成功标准：

- 前端指标卡片不再是 `N/A`。
- CPU / 内存显示百分比。
- 服务可用显示类似 `2/2`。
- Grafana 仍能展示节点 CPU / 内存图表。

## 4. 本轮排障要点

曾出现的问题：

```text
Not enough variable values available to expand 'namespace="cloud-ops"...'
```

根因：

Spring `UriBuilder` 会把 PromQL 中的 `{namespace="cloud-ops"}` 误认为 URI 模板变量，导致请求还没发到 Prometheus 就在 gateway 内部失败。

修复方式：

- 不直接把 PromQL 拼进 `queryParam`。
- 改成 URI 变量值传入，让框架按 query 参数安全编码。
- 增加 `PrometheusHttpClientTest` 覆盖带 `{...}` label selector 的 PromQL。

排查顺序：

```bash
curl -i http://deriou.com/api/v1/ops/clusters/summary
```

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
kubectl -n cloud-ops logs deploy/gateway-portal --tail=200 | grep -E "Failed to load ops metric|Prometheus|ops"
```

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
kubectl -n cloud-ops exec deploy/gateway-portal -- printenv | grep OPS_
```

## 5. 下一轮目标

本轮目标是把前端 Ops 页面继续补成“简历展示型运维门户”。

这里的核心不是做一个复杂控制台，而是让面试官能快速看懂：

- 项目部署在哪里。
- 服务是否健康。
- 最近是否有发布。
- 可观测性链路是否完整。
- 出问题时如何从页面入口进入排查路径。

## 6. 推荐展示形态

建议继续保持“门户页”而不是“后台管理系统”的表达方式。

第一屏保留轻量摘要：

- 集群名称、区域、检查时间。
- CPU 利用率。
- 内存使用率。
- 服务可用。
- Grafana 看板入口。

第二层补充运维闭环模块：

- 服务健康：gateway、blog、web 的运行状态。
- 最近发布：最近一次 gateway / web / blog Jenkins 发布记录。
- 告警状态：当前是否有 firing 告警，或展示“暂无活动告警”。
- 排障入口：Prometheus Targets、Grafana Dashboard、Loki traceId 查询示例。

## 7. 可能实现路径

### 路线 A：先做前端静态增强

适合快速形成简历展示效果。

做法：

- 保留当前真实集群摘要接口。
- 页面新增几个展示区块，但数据先来自已有 mock 或固定说明。
- 强调运维链路说明、入口和截图式信息密度。

优点：

- 快。
- 风险低。
- 不影响后端。

缺点：

- 有些模块仍不是实时数据。

### 路线 B：补一个轻量后端聚合接口

适合进一步增强真实性。

做法：

- Gateway 增加 `/api/v1/ops/overview`。
- 聚合服务健康、Prometheus target 简要状态、最近发布信息。
- 前端只调一个 Overview 接口。

优点：

- 前端逻辑简单。
- 数据更真实。
- 更像运维开发项目。

缺点：

- 需要明确 Jenkins 最近发布数据从哪里取。
- 需要处理 Prometheus / Jenkins 不可用时的降级。

### 路线 C：分模块逐步真实化

推荐路线。

做法：

1. 当前集群摘要保持真实。
2. 服务健康优先接真实数据。
3. 最近发布先用静态展示或从已有流水线文档说明。
4. 告警状态后续再接 Prometheus Alertmanager 或 Grafana 告警。

优点：

- 每一轮都有可验收成果。
- 不会一次性把范围拉太大。
- 更适合简历项目持续迭代。

## 8. 下一轮建议拆分

建议下一轮只做一个小闭环：

```text
前端 Ops 门户：服务健康 + 发布入口 + 可观测性入口
```

最小完成标准：

- `/ops/cluster` 页面结构更像运维门户。
- 展示真实集群摘要。
- 增加服务健康区块。
- 增加最近发布区块。
- 增加排障入口区块。
- 不引入复杂权限和后台操作。

如果要继续真实化，优先顺序：

1. 服务健康真实化。
2. 发布记录真实化。
3. 告警状态真实化。
4. 工作负载列表真实化。

## 9. 讨论问题

本轮已锁定的实现取舍：

1. 页面只做展示，不做操作按钮。
2. 最近发布记录不接 Jenkins API，先展示三条流水线入口与发布职责。
3. 服务健康使用 Prometheus `up` 真实查询。
4. 告警状态不接实时 API，继续作为 Grafana / Feishu 闭环入口展示。
5. 页面风格保持个人博客门户风，不做复杂后台控制台。

## 10. 本轮接口设计

新增 gateway 只读接口：

```text
GET /api/v1/ops/services/health
```

返回示例：

```json
[
  {
    "name": "gateway-portal",
    "displayName": "Gateway Portal",
    "status": "UP",
    "value": "1/1",
    "source": "prometheus",
    "checkedAt": "2026-05-10T08:00:00Z",
    "detail": "Prometheus target is up"
  }
]
```

实现规则：

- `gateway-portal` 查询 `up{namespace="cloud-ops", service="gateway-portal"}`。
- `blog-service` 查询 `up{namespace="cloud-ops", service="blog-service"}`。
- `up >= 1` 展示为 `UP`。
- `up < 1` 展示为 `DOWN`。
- 查询失败时该服务展示为 `UNKNOWN`，接口整体仍返回 200。
- `web` 静态站没有 Prometheus target，本轮不强行伪装成真实服务健康。

## 11. 本轮前端展示

`/ops/cluster` 升级为 Ops 门户页：

- 顶部继续展示真实集群摘要。
- 新增服务健康区块，展示 gateway 与 blog 的 Prometheus target 状态。
- 新增最近发布入口，展示：
  - `cloud-ops-web-pipeline`
  - `cloud-ops-gateway-pipeline`
  - `cloud-ops-blog-pipeline`
- 新增排障入口，整理：
  - Grafana Dashboard
  - Prometheus Targets SSH 隧道
  - Loki traceId 查询
  - gateway 聚合接口日志排查命令

前端新增环境变量：

```text
VITE_OPS_SERVICE_HEALTH_USE_MOCK
```

默认策略：

- 本地开发默认 mock。
- 生产构建默认请求真实接口。

## 12. 本轮验收流程

后端测试：

```bash
JAVA_HOME=/Users/Deriou/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./mvnw -pl common/common-core,apps/gateway-portal -am test
```

前端构建：

```bash
cd web
npm run build
```

部署顺序：

```text
1. git pull origin main
2. 运行 cloud-ops-gateway-pipeline
3. git pull origin main
4. 运行 cloud-ops-web-pipeline
```

接口验收：

```bash
curl -s http://deriou.com/api/v1/ops/clusters/summary
curl -s http://deriou.com/api/v1/ops/services/health
```

页面验收：

```text
http://deriou.com/ops/cluster
```

成功标准：

- 集群摘要仍显示真实 CPU / 内存 / 服务可用。
- 服务健康显示 `gateway-portal` 与 `blog-service`。
- 服务状态为 `UP`、`DOWN` 或 `UNKNOWN`，页面不白屏。
- 最近发布入口能说明三条 Jenkins pipeline 的职责。
- 排障入口能从页面指向 Grafana、Prometheus、Loki 与 gateway 日志。
