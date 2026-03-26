# STEP D3 - 发布诊断引擎

## 目标

在部署异常时自动聚合 Prometheus 指标与 Loki 日志，输出诊断报告与处置建议。

## 前置条件

- D1/D2 已完成
- Prometheus 与 Loki 查询客户端可用

## 目录与类建议

- `apps/ops-core/src/main/java/.../diagnosis/DiagnosisRule.java`
- `apps/ops-core/src/main/java/.../diagnosis/DiagnosisEngine.java`
- `apps/ops-core/src/main/java/.../diagnosis/DiagnosisReportService.java`
- `apps/ops-core/src/main/java/.../diagnosis/DiagnosisController.java`
- `apps/ops-core/src/test/java/.../diagnosis/DiagnosisEngineTest.java`

## Red（先写失败测试）

按“先简单后复杂”顺序写：

1. `metrics_anomaly_only_should_generate_medium_report`
2. `error_logs_only_should_generate_medium_report`
3. `metrics_and_logs_anomaly_should_generate_high_report`
4. `dependency_partial_failure_should_generate_degraded_report`
5. `report_output_should_contain_summary_rootCause_suggestions`

## Green（最小实现）

1. 定义统一报告结构：
   - `reportId`
   - `severity`
   - `summary`
   - `metrics`
   - `logs`
   - `suggestions`
2. 实现最小规则集：
   - CPU/内存/错误率阈值规则
   - 错误日志关键字规则（如 OOM, timeout, refused）
3. 聚合结果生成结论与建议。
4. 提供接口：
   - `POST /api/v1/ops/diagnostics/release`
   - `GET /api/v1/ops/diagnostics/release/{reportId}`
5. 全部测试通过。

## Refactor（重构）

1. 把规则拆分为规则链（每条规则独立类）。
2. 引入规则优先级与冲突处理策略。
3. 为规则执行增加耗时与命中率指标。

## 根据测试结果修正实现（强制动作）

每次测试失败后，必须在本步骤记录并修正：

1. 失败测试名
2. 失败原因（规则误判/字段缺失/降级逻辑不足）
3. 修正动作（代码级）
4. 回归结果（通过/仍失败）

建议在 `docs/steps/artifacts/STEP-D3-test-log.md` 维护该记录。

## 通过标准（DoD）

- 4 类核心场景测试全部通过
- 报告结构稳定，前端可直接渲染
- 外部依赖部分不可用时可输出降级报告而非 500

## 执行命令清单

- `mvn -pl apps/ops-core test -Dtest=*DiagnosisEngine*`
- `mvn -pl apps/ops-core test -Dtest=*DiagnosisReportService*`
