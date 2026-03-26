# Architecture Overview

## 1. 目标与原则

Cloud-Ops-Hub 的首要目标是在单节点 `8GB` ECS 上稳定运行，因此所有设计遵循：

- 轻量优先：能不用的中间件不引入
- 边界清晰：应用隔离、数据库隔离、职责隔离
- 可观测优先：核心业务与 JVM 指标可追踪
- 平滑扩展：在不破坏边界的前提下逐步增加能力

## 2. Mono-Repo 结构

```text
Cloud-ops-hub/
  apps/
    gateway-portal/
    blog-service/
    ops-core/
  common/
    common-core/
  web/
    portal-ui/
  infra/
    docker/
    k8s/
    jenkins/
    observability/
  docs/
```

## 3. 服务职责

- Gateway-Portal:
  - 唯一公网入口
  - 托管前端静态资源
  - 统一鉴权与演示模式控制
  - 聚合子应用元数据与健康状态
- Blog-Service:
  - Markdown 渲染与缓存
  - 文章、标签、分类管理与查询
  - 轻量站内搜索
- Ops-Core:
  - K8s 状态采集
  - Jenkins 流水线触发
  - 发布诊断报告聚合（Prometheus + Loki）

## 4. 关键技术决策

- Java 21 虚拟线程默认开启：`spring.threads.virtual.enabled=true`
- 鉴权采用自定义 `HandlerInterceptor`，不使用 Spring Security/JWT
- 缓存优先 Caffeine，本地缓存 + TTL
- 统一异常处理与日志输出，保障运维排障一致性

## 5. 数据与依赖边界

- 每个应用独立数据库 schema：`db_gateway`, `db_blog`, `db_ops`
- 严禁跨 schema 查询
- `apps/*` 仅可依赖 `common/*`，应用之间通过 API 通信

## 6. 资源基线建议

- JVM: `-Xms256m -Xmx512m -XX:MaxRAMPercentage=75.0`
- 所有服务必须配置健康检查、资源 requests/limits
- 仅采集必要监控指标，避免观测系统反客为主
