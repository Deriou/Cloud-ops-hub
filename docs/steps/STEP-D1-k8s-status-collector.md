# STEP D1 - K8s 状态采集

## 目标

在 Ops-Core 中实现 K3s 工作负载状态采集与标准化输出。

## 前置条件

- 已接入 Kubernetes Java Client
- 已具备统一返回与异常处理

## 目录与类建议

- `apps/ops-core/src/main/java/.../k8s/K8sClientFacade.java`
- `apps/ops-core/src/main/java/.../k8s/WorkloadStatusMapper.java`
- `apps/ops-core/src/main/java/.../k8s/ClusterStatusService.java`
- `apps/ops-core/src/test/java/.../k8s/ClusterStatusServiceTest.java`

## Red（先写失败测试）

1. `pod_running_should_map_to_healthy`
2. `crashloopbackoff_should_map_to_unhealthy`
3. `deployment_unavailable_should_be_counted_in_summary`
4. `k8s_api_failure_should_return_graceful_error`

## Green（最小实现）

1. 封装 K8s API 调用在 `K8sClientFacade`。
2. 实现状态映射器：
   - `Running -> HEALTHY`
   - `Pending -> DEGRADED`
   - `CrashLoopBackOff -> UNHEALTHY`
3. 暴露接口：
   - `GET /api/v1/ops/clusters/summary`
   - `GET /api/v1/ops/workloads`
4. 测试通过。

## Refactor（重构）

1. 将映射规则集中配置，便于扩展更多状态。
2. 增加采集耗时指标与失败计数指标。

## 通过标准（DoD）

- 前端可直接消费状态摘要与工作负载列表
- 异常状态识别准确
- K8s API 偶发失败不导致系统崩溃

## 执行命令清单

- `mvn -pl apps/ops-core test -Dtest=*ClusterStatusService*`
