# STEP A3 - AuthInterceptor 双令牌鉴权

## 目标

实现 `X-Ops-Key` 鉴权：Master 全权限，Guest 仅 GET 且支持 TTL。

## 前置条件

- A1/A2 已完成
- 已引入 Caffeine

## 目录与类建议

- `common/common-core/src/main/java/.../auth/AuthProperties.java`
- `common/common-core/src/main/java/.../auth/GuestTokenStore.java`
- `common/common-core/src/main/java/.../auth/AuthInterceptor.java`
- `common/common-core/src/main/java/.../config/WebMvcConfig.java`
- `common/common-core/src/test/java/.../auth/AuthInterceptorTest.java`

## Red（先写失败测试）

按以下顺序写测试并逐个失败：

1. `no_key_should_return_401`
2. `master_key_should_allow_post_put_delete`
3. `guest_key_should_allow_get_only`
4. `guest_key_write_request_should_return_403`
5. `expired_guest_key_should_return_401`

测试要点：

- 使用 MockMvc 构造 `GET/POST/PUT/DELETE` 请求
- Header 使用 `X-Ops-Key`
- 断言 HTTP 状态码与返回体 `code`

## Green（最小实现）

1. `AuthProperties`：
   - 读取 `ops.auth.master-key`
   - 读取 `ops.auth.guest-token-ttl-seconds`
2. `GuestTokenStore`：
   - 基于 Caffeine，`expireAfterWrite(ttl)`
   - 支持 `put/contains/revoke`
3. `AuthInterceptor#preHandle`：
   - 无 key -> 401
   - key 匹配 master -> 放行
   - key 命中 guest 且方法为 GET -> 放行
   - 其他场景 -> 403 或 401
4. 注册拦截器到 WebMvc。
5. 所有测试通过。

## Refactor（重构）

1. 抽取权限判定方法：
   - `isMasterKey()`
   - `isGuestKey()`
   - `isReadOnlyRequest()`
2. 抽取错误响应构建，避免重复 JSON 序列化逻辑。
3. 增加审计日志（仅关键事件：拒绝访问、token 过期）。

## 与业务代码对接

1. Gateway 的 token 签发接口写入 `GuestTokenStore`。
2. 演示模式由拦截器统一执行，不在 Controller 重复判断。

## 通过标准（DoD）

- 双令牌规则完全符合预期
- Guest 写操作全部被阻断
- Token 过期行为可重复验证

## 执行命令清单

- `mvn -pl common/common-core test -Dtest=*AuthInterceptor*`
- `mvn -pl apps/gateway-portal test`
