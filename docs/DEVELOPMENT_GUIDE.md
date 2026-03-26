# Development Guide

## 1. 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+
- Docker Desktop (可选，本地容器验证)

## 2. 跨平台规范

- 路径统一使用 `/`
- 文本文件统一 LF
- 禁止提交任何 `.env` 与密钥文件

建议本地 `.env`（仅示例）：

```bash
OPS_MASTER_KEY=replace-with-local-key
DB_BLOG_PASSWORD=replace-with-local-password
JENKINS_TOKEN=replace-with-local-token
```

## 3. 开发启动顺序

1. 启动基础依赖（MySQL 等）
2. 启动 `common` 依赖的基础模块
3. 启动 `apps/gateway-portal`
4. 启动 `apps/blog-service`
5. 启动 `apps/ops-core`
6. 启动 `web/portal-ui`

## 4. 后端开发约定

- 新增接口前先确认 `docs/API_STANDARDS.md`
- 异常统一抛 `BizException`
- 控制器禁止返回裸对象，统一 `ApiResponse`
- 禁止在 Controller 写复杂业务逻辑

## 5. 前端开发约定

- 状态管理集中于 Pinia
- 页面采用 Bento Grid 卡片组织
- 动效轻量化，避免阻塞主线程
- 统一读取网关下发应用注册表

## 6. 测试策略

- 重点测试鉴权链路与诊断核心逻辑
- 简单 CRUD 不强制单测
- 提交前至少完成本地 smoke test

## 7. 提交流程

- 提交信息格式：`<type>(<scope>): <subject>`
- 提交前检查：
  - 无明文密钥
  - 无跨模块直连依赖
  - 文档同步更新
