# STEP A2 - 全局异常处理

## 目标

通过 `BizException + GlobalExceptionHandler` 统一错误输出，禁止异常细节外泄。

## 前置条件

- A1 已完成，`ApiResponse<T>` 可用

## 目录与类建议

- `common/common-core/src/main/java/.../exception/BizException.java`
- `common/common-core/src/main/java/.../exception/GlobalExceptionHandler.java`
- `common/common-core/src/test/java/.../exception/GlobalExceptionHandlerTest.java`

## Red（先写失败测试）

1. 编写 `GlobalExceptionHandlerTest`（建议使用 `@WebMvcTest`）：
   - `biz_exception_should_return_biz_error`
   - `unknown_exception_should_return_system_error`
   - `response_should_not_contain_stack_trace`
2. 断言点：
   - HTTP 状态与业务码一致
   - 返回体字段完整（`code/message/data/traceId`）
   - `message` 不含 Java 异常堆栈关键字
3. 运行测试并确认失败。

## Green（最小实现）

1. 实现 `BizException`：
   - 包含 `ResultCode`
   - 支持业务 message
2. 实现 `GlobalExceptionHandler`：
   - `@ExceptionHandler(BizException.class)`
   - `@ExceptionHandler(Exception.class)`
3. 统一返回 `ApiResponse.fail(...)`。
4. 仅记录异常日志，不把堆栈透传给客户端。
5. 再跑测试直到通过。

## Refactor（重构）

1. 提取公共错误构建方法，避免 handler 代码重复。
2. 统一默认错误文案，如 `system error`。
3. 加入 traceId 透传（如从 MDC 读取，若无则生成）。

## 与业务代码对接

1. 在 Service 中抛出 `BizException` 替代 `RuntimeException`。
2. 清理 Controller 中手写 try-catch 返回错误的代码。

## 通过标准（DoD）

- 异常场景均返回统一格式
- 不泄露内部堆栈
- 现有接口错误行为稳定

## 执行命令清单

- `mvn -pl common/common-core test -Dtest=*Exception*`
- `mvn -pl apps/gateway-portal test`
