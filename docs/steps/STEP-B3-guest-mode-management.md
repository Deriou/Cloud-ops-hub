# STEP B3 - Guest 模式令牌管理

## 目标

实现 Guest Token 的签发、吊销、过期控制，并与 A3 鉴权联动。

## 前置条件

- A3 已完成
- B1/B2 已有网关基础能力

## 目录与类建议

- `apps/gateway-portal/src/main/java/.../auth/GuestTokenController.java`
- `apps/gateway-portal/src/main/java/.../auth/GuestTokenService.java`
- `apps/gateway-portal/src/test/java/.../auth/GuestTokenControllerTest.java`

## Red（先写失败测试）

1. `create_guest_token_should_return_token_and_expire_time`
2. `revoked_token_should_be_invalid_immediately`
3. `token_should_expire_after_ttl`
4. `guest_mode_write_api_should_be_rejected`

## Green（最小实现）

1. 提供签发接口：
   - `POST /api/v1/gateway/guest-tokens`
2. 提供吊销接口：
   - `DELETE /api/v1/gateway/guest-tokens/{tokenId}`
3. 将 token 写入 `GuestTokenStore`。
4. 返回 token 元数据（`tokenId`, `expireAt`）。
5. 跑测试并全部通过。

## Refactor（重构）

1. token 生成抽象成 `TokenGenerator`，便于后续替换算法。
2. 补充审计日志（谁创建、何时失效、谁吊销）。

## 通过标准（DoD）

- Guest Token 生命周期完整可测
- 只读模式写接口阻断稳定

## 执行命令清单

- `mvn -pl apps/gateway-portal test -Dtest=*GuestToken*`
