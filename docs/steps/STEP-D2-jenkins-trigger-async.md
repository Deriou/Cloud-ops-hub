# STEP D2 - Jenkins 异步触发与状态跟踪

## 目标

通过 Ops-Core 异步触发 Jenkins 流水线，并可查询任务状态、处理超时与重试。

## 前置条件

- D1 已完成
- Jenkins API 凭据可通过环境变量读取

## 目录与类建议

- `apps/ops-core/src/main/java/.../jenkins/JenkinsClient.java`
- `apps/ops-core/src/main/java/.../jenkins/PipelineRunTask.java`
- `apps/ops-core/src/main/java/.../jenkins/PipelineService.java`
- `apps/ops-core/src/main/java/.../jenkins/PipelineController.java`
- `apps/ops-core/src/test/java/.../jenkins/PipelineServiceTest.java`

## Red（先写失败测试）

1. `trigger_should_return_run_id_when_accepted`
2. `timeout_should_mark_run_as_failed`
3. `retry_should_stop_after_max_attempts`
4. `query_run_status_should_return_current_state`

## Green（最小实现）

1. 定义运行状态枚举：
   - `PENDING`
   - `RUNNING`
   - `SUCCESS`
   - `FAILED`
   - `TIMEOUT`
2. 实现触发接口：
   - `POST /api/v1/ops/pipelines/{jobName}/trigger`
3. 使用异步执行（虚拟线程或 `@Async`）处理 Jenkins 调用。
4. 增加超时控制与有限重试（如最多 2 次）。
5. 提供状态查询接口：
   - `GET /api/v1/ops/pipelines/runs/{runId}`
6. 测试通过。

## Refactor（重构）

1. 抽离重试策略组件，避免业务逻辑与重试耦合。
2. 增加关键日志：`runId`, `jobName`, `attempt`, `latencyMs`。

## 通过标准（DoD）

- 触发、查询、超时、重试行为可测可复现
- 异步任务不阻塞请求线程
- 状态机流转清晰无脏状态

## 执行命令清单

- `mvn -pl apps/ops-core test -Dtest=*PipelineService*`
