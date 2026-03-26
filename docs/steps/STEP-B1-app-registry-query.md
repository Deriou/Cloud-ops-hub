# STEP B1 - 应用注册表查询

## 目标

实现 Gateway 应用注册表查询接口，前端可动态获取子应用元数据。

## 前置条件

- 阶段 A 已完成（统一返回 + 异常 + 鉴权）

## 目录与类建议

- `apps/gateway-portal/src/main/java/.../registry/AppMeta.java`
- `apps/gateway-portal/src/main/java/.../registry/AppRegistryService.java`
- `apps/gateway-portal/src/main/java/.../registry/AppRegistryController.java`
- `apps/gateway-portal/src/test/java/.../registry/AppRegistryControllerTest.java`

## Red（先写失败测试）

1. `get_apps_should_return_empty_list_when_no_data`
2. `get_apps_should_return_expected_fields`
3. `response_should_follow_api_response_contract`

断言字段建议：

- `appKey`
- `title`
- `route`
- `status`
- `description`

## Green（最小实现）

1. 定义 `AppMeta` DTO。
2. `AppRegistryService` 先用内存列表实现。
3. 暴露 `GET /api/v1/gateway/apps`。
4. 返回 `ApiResponse<List<AppMeta>>`。
5. 跑测试直到通过。

## Refactor（重构）

1. 抽离数据源接口（后续可接 DB/配置中心）。
2. 保证返回按 `sortOrder` 或 `title` 稳定排序。

## 通过标准（DoD）

- 前端可直接消费注册表
- 空数据/有数据都稳定返回
- 结构符合统一 API 规范

## 执行命令清单

- `mvn -pl apps/gateway-portal test -Dtest=*AppRegistry*`
