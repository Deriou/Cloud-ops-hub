# Gateway-Portal API

Base path: `/api/v1/gateway`

## 1. 应用注册表

- `GET /apps`
  - 描述：获取前端导航可见的子应用列表
  - 权限：Guest/Master
  - 响应字段：
    - `appKey`：应用唯一标识
    - `title`：应用标题
    - `route`：前端访问路由
    - `status`：应用状态
    - `description`：应用描述
    - `sortOrder`：排序权重，值越小越靠前
- `GET /apps/{appKey}/health`
  - 描述：获取指定应用健康状态（聚合自 Actuator）
  - 权限：Guest/Master
- `PUT /apps/{appKey}`
  - 描述：更新应用元数据（路由、标题、图标、可见性）
  - 权限：Master

## 2. 访问模式管理

- `GET /access-mode`
  - 描述：查询当前模式（guest/admin）
  - 权限：Guest/Master
- `POST /guest-tokens`
  - 描述：生成带 TTL 的 Guest Token
  - 权限：Master
  - 响应字段：
    - `tokenId`：Guest Token 唯一标识，用于后续吊销
    - `token`：实际访问时写入 `X-Ops-Key` 的访客令牌
    - `expireAt`：令牌过期时间，UTC ISO-8601 格式
- `DELETE /guest-tokens/{tokenId}`
  - 描述：吊销 Guest Token
  - 权限：Master
  - 路径参数：
    - `tokenId`：待吊销的 Guest Token 标识
  - 响应字段：
    - `tokenId`：本次请求处理的令牌标识
    - `revoked`：是否按成功处理；接口采用幂等语义，重复吊销仍返回 `true`

## 3. 典型响应

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "appKey": "gateway-portal",
      "title": "Gateway Portal",
      "route": "/",
      "status": "UP",
      "description": "统一公网入口",
      "sortOrder": 1
    }
  ],
  "traceId": "0f91f9a877d34d47"
}
```

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "tokenId": "4f0d9d4d8d9a4f8e9e47bdb31c5b4f3a",
    "token": "7b6c4f629d2f4d6a8a3ab0d1d0e1f2a3",
    "expireAt": "2026-03-28T06:42:40Z"
  },
  "traceId": "7e3f0b0a9b2f4f8aa2b8d9f1c2d3e4f5"
}
```

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "tokenId": "4f0d9d4d8d9a4f8e9e47bdb31c5b4f3a",
    "revoked": true
  },
  "traceId": "b6a73c0d9f2140a3ab4d5e6f708192aa"
}
```

## 4. 备注

- 演示模式下，所有写操作返回 `403`
- 静态资源由 Gateway 统一托管
