# L2：PLG 监控实操手册（Prometheus + Loki + Grafana）

## 学习目标

你将手动完成：

- Prometheus 抓取 gateway/blog 指标
- Loki 聚合应用日志
- Grafana 同屏联查日志与指标

## 前置条件

- L1 已完成，服务已在 K3s 跑起来
- 应用可访问 `/actuator/prometheus`（或已启用相应端点）

## Step 1：部署 Prometheus（最小配置）

建议先用最小 Helm 或原生 yaml，重点是抓取目标清晰。

抓取对象先控制在：

- gateway-portal
- blog-service
- kubelet / node-exporter（可选）

验收标准：

- Prometheus `Targets` 页面全部 `UP`

## Step 2：部署 Loki + 日志采集器

可选组合：

- Loki + Promtail
- Loki + Fluent Bit

学习期建议：

- 优先 Promtail（配置直观）
- 日志保留先设 3~7 天

验收标准：

- Grafana Explore 中可按 `namespace=cloud-ops` 查询到日志

## Step 3：接入 Grafana

添加两个数据源：

- Prometheus
- Loki

建立三个最小看板：

1. 服务健康与 QPS
2. JVM 内存与 GC
3. 错误日志趋势

验收标准：

- 同一时间窗可看指标与日志

## Step 4：建立最小告警

先做 3 条高价值告警：

- Pod 重启频繁
- 5xx 比例超过阈值
- JVM 堆内存持续高位

验收标准：

- 人工制造异常时告警能触发

## Step 5：为 Ops-Core 的“发布诊断”准备数据

约定日志字段（建议）：

- `service`
- `traceId`
- `path`
- `latencyMs`
- `resultCode`

约定关键指标：

- 请求总量、错误率、延迟分位数
- JVM 堆使用率、线程数

验收标准：

- 能按一次异常发布，检索到对应日志与指标

## 8G 单机优化建议

- Prometheus 抓取间隔先 30s
- 禁止高基数标签（如 userId）
- Loki 严格保留策略，避免磁盘膨胀
- Grafana Dashboard 控制数量与刷新频率

