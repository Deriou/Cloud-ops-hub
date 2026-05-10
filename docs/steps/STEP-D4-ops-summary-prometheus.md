# STEP D4 - 前端集群摘要 Prometheus 真实化

## 目标

将前端 `/ops/cluster` 顶部集群摘要从 mock 数据切换为真实 Prometheus 数据，形成轻量闭环：

```text
node-exporter / actuator metrics
-> Prometheus
-> gateway-portal 只读聚合接口
-> web /ops/cluster 摘要卡片
```

本阶段只做关键摘要，不做完整自研监控大屏；Grafana 仍然承担深度看板和排障入口。

## 范围

第一版接口：

```text
GET /api/v1/ops/clusters/summary
```

返回前端现有 `ClusterSummary` 结构：

```json
{
  "clusterName": "k3s-single-node",
  "region": "cn-wulanchabu",
  "checkedAt": "2026-05-10T12:00:00Z",
  "stats": [
    {
      "label": "CPU 利用率",
      "value": "12%",
      "trend": [10, 11, 12],
      "tone": "normal"
    }
  ]
}
```

## Prometheus 查询

节点 CPU：

```promql
1 - avg(rate(node_cpu_seconds_total{job="node-exporter", mode="idle"}[5m]))
```

节点内存：

```promql
1 - (sum(node_memory_MemAvailable_bytes{job="node-exporter"}) / sum(node_memory_MemTotal_bytes{job="node-exporter"}))
```

服务可用：

```promql
sum(up{namespace="cloud-ops", service=~"gateway-portal|blog-service"})
```

趋势数据使用 Prometheus `query_range` 获取最近 30 分钟数据，步长 5 分钟。

## 实现位置

第一阶段把只读聚合接口放在 `gateway-portal`：

```text
apps/gateway-portal/src/main/java/dev/deriou/gateway/ops
```

原因：

- 仓库当前尚未实现 `apps/ops-core` 模块。
- 前端已预留 `fetchClusterSummary()`。
- Ingress 可新增 `/api/v1/ops -> gateway-portal`，后续拆出 `ops-core` 时前端路径不需要变化。

## 配置

gateway 默认 Prometheus 地址：

```properties
ops.prometheus.base-url=http://prometheus-server.monitoring.svc.cluster.local
ops.cluster.name=k3s-single-node
ops.cluster.region=cn-wulanchabu
ops.cluster.expected-service-count=2
```

K8s ConfigMap 需要补充：

```yaml
OPS_PROMETHEUS_BASE_URL: "http://prometheus-server.monitoring.svc.cluster.local"
OPS_CLUSTER_NAME: "k3s-single-node"
OPS_CLUSTER_REGION: "cn-wulanchabu"
OPS_CLUSTER_EXPECTED_SERVICE_COUNT: "2"
```

## 访问控制

`GET /api/v1/ops/clusters/summary` 是访客可见的只读摘要接口。

原因：

- 前端 `/ops/cluster` 是公开展示页。
- 不应该把 `X-Ops-Key` 打包到浏览器前端。
- 接口只返回聚合后的 CPU / 内存百分比、服务可用数量和检查时间，不暴露 Prometheus 查询能力。

后续如果增加写操作、原始 PromQL 查询或更敏感的集群对象明细，必须重新进入鉴权控制。

## 前端改动

前端保持现有 API 封装：

```text
web/src/api/ops.ts
```

生产环境设置：

```text
VITE_OPS_API_BASE_URL=
VITE_OPS_SUMMARY_USE_MOCK=false
```

`VITE_OPS_API_BASE_URL` 留空时默认走当前站点同源 `/api/v1/ops`。

`VITE_OPS_USE_MOCK` 继续保留给 workloads / pipelines / diagnostics 等尚未实现的 Ops 子页使用，避免这些页面在生产环境误打未实现接口。

## DoD

- `gateway-portal` 单元测试覆盖 Prometheus 查询成功和失败降级。
- `GET /api/v1/ops/clusters/summary` 返回 `ApiResponse<ClusterSummary>`。
- 前端 mock fallback 保留。
- Ingress 已路由 `/api/v1/ops` 到 `gateway-portal`。
- ECS 上访问 `http://deriou.com/api/v1/ops/clusters/summary` 能返回真实 CPU / 内存摘要。
