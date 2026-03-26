# Gateway-Portal API

Base path: `/api/v1/gateway`

## 1. 应用注册表

- `GET /apps`
  - 描述：获取前端导航可见的子应用列表
  - 权限：Guest/Master
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
- `DELETE /guest-tokens/{tokenId}`
  - 描述：吊销 Guest Token
  - 权限：Master

## 3. 典型响应

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "mode": "guest"
  },
  "traceId": "0f91f9a877d34d47"
}
```

## 4. 备注

- 演示模式下，所有写操作返回 `403`
- 静态资源由 Gateway 统一托管
