# Ops Runbook

## 1. 运行目标

- 单节点 K3s 稳定运行
- 服务可观测、可诊断、可恢复
- 空闲时 CI/CD 资源占用可控

## 2. K3s 部署基线

- 所有服务必须设置 `requests/limits`
- 探针必须启用：
  - `livenessProbe`
  - `readinessProbe`
- 镜像必须包含健康检查与最小运行时依赖

## 3. Docker 基线

- 必须使用多阶段构建
- 运行时镜像：`eclipse-temurin:21-jre-alpine`
- 强制 `linux/amd64` 构建目标

## 4. Jenkins 规范

- Jenkins-on-K8s，使用临时 Agent Pod
- Pipeline 必须路径感知，仅构建变更模块
- Agent 任务完成后立即销毁

## 5. 观测体系（PLG）

- Prometheus:
  - 仅采集核心业务与 JVM 指标
  - 禁止高基数标签
  - 不直接公网暴露，Targets 通过 SSH 隧道或 `port-forward` 查看
- Loki:
  - 统一结构化日志
  - 当前通过 Promtail 采集 `cloud-ops` namespace 容器日志
  - 当前非持久化，保留窗口按 Loki 配置控制在 3~7 天，Pod 重建会丢历史日志
- Grafana:
  - 指标与日志同屏联查
  - `http://grafana.deriou.com` 公网匿名只读
  - 提供服务健康、请求量、5xx、p95、JVM 与错误日志趋势看板
  - 不向访客公开 Explore 入口，避免日志明细泄露

当前已跑通链路：

```text
业务请求 -> Spring Boot 指标/日志 -> Prometheus/Loki -> Grafana Dashboard
业务响应 traceId -> Loki LogQL 查询 -> 后端访问日志
```

当前尚未接入：

- 节点 CPU/内存真实指标（需后续补 `node-exporter` 或 kubelet/cAdvisor 抓取）
- `kube-state-metrics`
- Grafana 外部通知告警

## 6. 故障处理最小流程

1. 在 Gateway 确认健康状态聚合结果
2. 在 Grafana 查看异常时间窗指标波动
3. 使用 LogQL 检索对应 `traceId` 与错误日志
4. 如果是发布问题，再结合 Jenkins/CI 日志定位构建或部署阶段
5. 记录处理结论并沉淀到 runbook

## 7. 安全与密钥

- 密钥统一由 K8s Secret 注入
- 本地环境仅使用 `.env`，不可入库
- 演示模式默认只读，生产写操作必须 Master 权限
