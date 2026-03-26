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
- Loki:
  - 统一结构化日志
  - 默认 7 天保留
- Grafana:
  - 指标与日志同屏联查
  - 提供服务健康与发布诊断大盘

## 6. 故障处理最小流程

1. 在 Gateway 确认健康状态聚合结果
2. 在 Grafana 查看异常时间窗指标波动
3. 在 Loki 检索对应 `traceId` 与错误日志
4. 在 Ops-Core 拉取诊断报告与建议
5. 记录处理结论并沉淀到 runbook

## 7. 安全与密钥

- 密钥统一由 K8s Secret 注入
- 本地环境仅使用 `.env`，不可入库
- 演示模式默认只读，生产写操作必须 Master 权限
