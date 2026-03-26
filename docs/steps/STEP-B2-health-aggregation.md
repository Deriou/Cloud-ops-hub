# STEP B2 - 健康状态聚合

## 目标

Gateway 聚合各子服务健康状态，具备超时降级与短 TTL 缓存能力。

## 前置条件

- B1 已完成
- 已接入 Actuator 端点约定

## 目录与类建议

- `apps/gateway-portal/src/main/java/.../health/HealthClient.java`
- `apps/gateway-portal/src/main/java/.../health/HealthAggregationService.java`
- `apps/gateway-portal/src/main/java/.../health/HealthController.java`
- `apps/gateway-portal/src/test/java/.../health/HealthAggregationServiceTest.java`

## Red（先写失败测试）

1. `single_service_up_should_map_to_healthy`
2. `timeout_should_map_to_degraded`
3. `cache_hit_should_skip_remote_call`
4. `partial_failure_should_return_partial_status_not_500`

断言重点：

- 每个应用有独立状态
- 整体接口不因单点失败而崩溃
- 缓存命中减少下游调用次数

## Green（最小实现）

1. `HealthClient` 封装下游 `/actuator/health` 调用。
2. 设置调用超时（如 1~2 秒）。
3. `HealthAggregationService`：
   - 拉取注册表服务列表
   - 并发获取健康信息（可先串行，后续优化）
   - 将结果写入 Caffeine 短 TTL
4. 暴露 `GET /api/v1/gateway/apps/{appKey}/health` 与聚合接口（可选）。
5. 测试通过。

## Refactor（重构）

1. 引入虚拟线程执行并发探活，控制最大并发。
2. 抽取状态映射器，统一 `UP/DOWN/DEGRADED/UNKNOWN`。

## 通过标准（DoD）

- 下游偶发故障时网关接口仍稳定
- 缓存行为可被测试证明
- 状态字段语义清晰、可用于看板展示

## 执行命令清单

- `mvn -pl apps/gateway-portal test -Dtest=*HealthAggregation*`
