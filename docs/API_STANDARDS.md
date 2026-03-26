# API Standards

## 1. 统一返回结构

所有接口统一返回 `ApiResponse<T>`：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "b8f4d1f8c6b44c9a"
}
```

字段约束：

- `code`: 业务状态码（如 `OK`, `BIZ_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`）
- `message`: 对应状态描述
- `data`: 业务数据，允许为 `null`
- `traceId`: 请求链路追踪 ID

## 2. 鉴权协议

- Header 名：`X-Ops-Key`
- 模式：
  - Master Key：环境变量读取，允许所有请求方法
  - Guest Key：临时令牌（TTL），仅允许 GET 请求

未携带或非法密钥响应：

- `401 Unauthorized`（未认证）
- `403 Forbidden`（演示模式写操作）

## 3. HTTP 语义

- 查询：`GET`
- 新建：`POST`
- 更新：`PUT` / `PATCH`
- 删除：`DELETE`

演示模式下仅开放 `GET`。

## 4. 错误响应规范

```json
{
  "code": "BIZ_ERROR",
  "message": "article not found",
  "data": null,
  "traceId": "b8f4d1f8c6b44c9a"
}
```

禁止将堆栈信息直接返回给客户端。

## 5. 版本与路径规范

- 前缀：`/api/v1`
- 健康检查：`/actuator/health`
- 监控指标：`/actuator/prometheus`

## 6. 分页与排序规范

- 入参建议：
  - `pageNo`: 从 1 开始
  - `pageSize`: 默认 20，最大 100
  - `sortBy`, `sortDir`
- 响应中返回总量与分页信息

## 7. 可观测性字段

日志与响应建议统一包含：

- `traceId`
- `path`
- `method`
- `latencyMs`
- `resultCode`
