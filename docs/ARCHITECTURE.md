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
    ops-core/                 # 规划中，当前仓库尚未落地
  common/
    common-core/
  web/
    # 当前前端位于仓库根目录 web/
  infra/
    k8s/
    helm/
  docs/
```

## 3. 服务职责

- Gateway-Portal:
  - 唯一公网入口
  - 统一鉴权与演示模式控制
  - 聚合子应用元数据与健康状态
- Blog-Service:
  - Markdown 渲染与缓存
  - 文章、标签、分类管理与查询
  - 轻量站内搜索
- Ops-Core:
  - 当前为规划中模块
  - K8s 状态采集
  - Jenkins 流水线触发
  - 发布诊断报告聚合（Prometheus + Loki）
- Web:
  - 承载 `deriou.com` 门户页面
  - `/ops/cluster` 提供可观测性入口说明
  - 不直接重做 Grafana、Prometheus、Loki 图表平台

## 4. 关键技术决策

- Java 21 虚拟线程默认开启：`spring.threads.virtual.enabled=true`
- 鉴权采用自定义 `HandlerInterceptor`，不使用 Spring Security/JWT
- 缓存优先 Caffeine，本地缓存 + TTL
- 统一异常处理与日志输出，保障运维排障一致性
- PLG 采用 Helm 部署，配置与 Dashboard 进入仓库
- Grafana 公网匿名只读，Prometheus 不公网暴露

## 5. 数据与依赖边界

- 当前已落地数据库重点是 `db_blog`
- Gateway 与后续 Ops-Core 独立数据边界保留规划
- 严禁跨 schema 查询
- `apps/*` 仅可依赖 `common/*`，应用之间通过 API 通信

## 6. 资源基线建议

- JVM: `-Xms256m -Xmx512m -XX:MaxRAMPercentage=75.0`
- 所有服务必须配置健康检查、资源 requests/limits
- 仅采集必要监控指标，避免观测系统反客为主
- 当前节点 CPU/内存真实指标尚未接入，后续需补 `node-exporter` 或 kubelet/cAdvisor
