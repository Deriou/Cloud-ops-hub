# Ops-Core API

Base path: `/api/v1/ops`

## 1. 集群状态

- `GET /clusters/summary`
  - 描述：获取 K3s 集群摘要（节点、工作负载、异常数）
  - 权限：Guest/Master
- `GET /workloads`
  - 描述：分页查询 Deployment/Pod 状态
  - 参数：`pageNo`、`pageSize`
  - 权限：Guest/Master

## 2. Jenkins 流水线

- `GET /pipelines/runs`
  - 描述：查询最近流水线运行记录列表
  - 权限：Guest/Master
- `POST /pipelines/{jobName}/trigger`
  - 描述：触发 Jenkins 任务（异步）
  - 权限：Master
- `GET /pipelines/runs/{runId}`
  - 描述：查询任务执行状态
  - 权限：Guest/Master

## 3. 发布诊断

- `GET /diagnostics/reports`
  - 描述：查询最近诊断报告列表
  - 权限：Guest/Master
- `POST /diagnostics/release`
  - 描述：基于部署异常事件生成诊断报告
  - 权限：Master
- `GET /diagnostics/release/{reportId}`
  - 描述：查看诊断报告
  - 权限：Guest/Master

## 4. 诊断报告结构建议

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "reportId": "rpt_20260326_001",
    "severity": "high",
    "summary": "deployment rollout timeout",
    "metrics": [],
    "logs": [],
    "suggestions": []
  },
  "traceId": "e2d1fb20d84444b4"
}
```

## 5. 设计约束

- 外部调用（K8s/Jenkins/Prometheus/Loki）统一异步化并设置超时
- 失败重试必须有限次，避免雪崩
