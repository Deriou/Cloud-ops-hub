# Cloud-Ops-Hub

Cloud-Ops-Hub 是一个面向单节点 `8GB` ECS 的轻量化 Cloud-Native 运维开发平台，聚合以下能力：

- 导航入口与统一网关（Gateway-Portal）
- 自研博客系统（Blog-Service）
- 运维核心能力（Ops-Core）
- 自动化交付与全栈可观测性（Jenkins + PLG）

## 技术基线

- Backend: Java 21, Spring Boot 3, MyBatis-Plus
- Frontend: Vue 3, Vite, TypeScript, Tailwind CSS
- Runtime: Docker, K3s, Alibaba Cloud ECS
- Observability: Prometheus, Loki, Grafana

## 核心约束

- 单机 8GB 内存，优先轻量实现，避免不必要中间件
- 禁用 Spring Security/JWT，采用 `HandlerInterceptor` 自定义鉴权
- 模块间禁止循环依赖，严禁跨库查询
- 统一 API 返回、异常处理、日志格式

## 文档导航

- 架构总览: `docs/ARCHITECTURE.md`
- 模块边界: `docs/MODULES.md`
- API 规范: `docs/API_STANDARDS.md`
- 开发指南: `docs/DEVELOPMENT_GUIDE.md`
- 运维与交付: `docs/OPS_RUNBOOK.md`
- 接口清单:
  - `docs/apis/gateway-portal.md`
  - `docs/apis/blog-service.md`
  - `docs/apis/ops-core.md`

## 当前阶段建议

先实现 `common` 规范模块，再启动三大业务服务与前端联调，最后接入 Jenkins 与 PLG。
