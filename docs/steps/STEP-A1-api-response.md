# STEP A1 - 统一返回体 ApiResponse

## 目标

建立全项目统一响应结构，所有 Controller 返回 `ApiResponse<T>`。

## 前置条件

- 已有 `common/common-core` 模块
- 可运行 JUnit 5 测试

## 目录与类建议

- `common/common-core/src/main/java/.../api/ApiResponse.java`
- `common/common-core/src/main/java/.../api/ResultCode.java`
- `common/common-core/src/test/java/.../api/ApiResponseTest.java`

## Red（先写失败测试）

1. 新建 `ApiResponseTest`，先写 3 个测试：
   - `success_should_fill_ok_code_message_and_data`
   - `fail_should_fill_error_code_message_and_null_data`
   - `trace_id_should_exist_when_building_response`
2. 断言要求：
   - `success()` 默认 `code=OK`、`message=success`
   - `fail()` 传入错误码和错误信息后可正确返回
   - `traceId` 非空
3. 运行测试，确认失败（类不存在或断言不通过）。

## Green（最小实现）

1. 新建 `ResultCode` 枚举：
   - `OK`
   - `BIZ_ERROR`
   - `UNAUTHORIZED`
   - `FORBIDDEN`
   - `SYSTEM_ERROR`
2. 实现 `ApiResponse<T>` 字段：
   - `code`
   - `message`
   - `data`
   - `traceId`
3. 提供工厂方法：
   - `success(T data)`
   - `fail(ResultCode code, String message)`
4. `traceId` 先使用最小实现（如 `UUID`），后续与日志链路统一。
5. 再次运行测试并确保全部通过。

## Refactor（重构）

1. 将重复构建逻辑收敛为私有构造方法。
2. 补充泛型和 `null` 安全处理。
3. 清理硬编码字符串，统一来自 `ResultCode`。
4. 再跑测试，确保回归通过。

## 与业务代码对接

1. 在任一试点 Controller 中，把原返回对象改为 `ApiResponse<T>`。
2. 添加一个 Controller 层测试，断言 JSON 结构符合标准字段。

## 通过标准（DoD）

- `ApiResponseTest` 全通过
- 试点 Controller 返回结构统一
- 无裸对象直接返回

## 执行命令清单

- `mvn -pl common/common-core test`
- `mvn -pl apps/gateway-portal test -Dtest=*Controller*`
