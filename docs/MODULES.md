# Module Boundaries

## 1. 模块清单

- `common/common-core`
- `apps/gateway-portal`
- `apps/blog-service`
- `web`
- `infra/*`

规划中模块：

- `apps/ops-core`

## 2. 依赖方向

- 允许：
  - `apps/* -> common/common-core`
  - `web -> apps/* (HTTP API)`
- 禁止：
  - `apps/gateway-portal <-> apps/blog-service` 直接代码依赖
  - `apps/gateway-portal <-> apps/ops-core` 直接代码依赖
  - `apps/blog-service <-> apps/ops-core` 直接代码依赖

## 3. common-core 职责

- 统一返回体 `ApiResponse<T>`
- 统一异常体系 `BizException` + `GlobalExceptionHandler`
- 统一鉴权拦截器 `AuthInterceptor`
- 统一日志与 TraceId 约定
- 可复用基础 DTO 与工具类

## 4. 数据边界

- Gateway-Portal: 仅维护网关配置与应用注册数据
- Blog-Service: 仅维护博客域数据
- Ops-Core: 规划中，后续仅维护运维任务与诊断数据
- 严禁跨库 join 与跨域事务

## 5. 包命名建议

- `dev.deriou.common.*`
- `dev.deriou.gateway.*`
- `dev.deriou.blog.*`
- `dev.deriou.ops.*`（规划中）

## 6. 模块开发流程

1. 先在 `common-core` 定义通用规范与契约
2. 在目标应用模块实现业务逻辑
3. 在 `docs/apis/*.md` 更新接口文档
4. 补充最小必要测试（核心算法与鉴权链路）
