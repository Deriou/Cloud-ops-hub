# Cloud-Ops-Hub PDR

## 1. 文档目的

本 PDR（Product Development Requirements）用于定义 Cloud-Ops-Hub 的产品与工程交付边界，作为研发、测试与运维协作的统一依据。

## 2. 项目目标

在阿里云单节点 `8GB` 内存 ECS 上，基于 `K3s` 构建一个集以下能力于一体的运维开发平台：

- 导航门户（统一入口）
- 自研博客（内容与 SEO）
- 自动化交付（Jenkins）
- 全栈可观测性（Prometheus + Loki + Grafana）

## 3. 范围定义

### 3.1 In Scope

- Mono-Repo 多模块架构建设
- `Gateway-Portal`、`Blog-Service`、`Ops-Core` 三个后端服务
- `Vue3 + TS + Tailwind` 前端看板
- Jenkins-on-K8s 声明式流水线
- PLG 可观测性方案

### 3.2 Out of Scope（当前阶段）

- 多节点高可用集群
- 重型 IAM 平台接入
- 大规模消息中间件集成

## 4. 架构与技术基线

- **架构模式**：Maven Mono-Repo，`apps/` 内服务逻辑隔离
- **后端**：Java 21 + Spring Boot 3 + MyBatis-Plus
- **前端**：Vue 3 + Vite + TypeScript + Tailwind CSS
- **运维**：K3s + Jenkins + Prometheus + Loki + Grafana
- **性能策略**：全面启用虚拟线程、优先使用 Caffeine、本地轻量缓存
- **约束**：禁止 Spring Security/JWT；鉴权采用自定义 `HandlerInterceptor`
- **统一规范**：所有应用继承 `common` 模块的 API/异常/日志规范

## 5. 核心功能需求

## 5.1 Gateway-Portal（唯一公网入口）

### 功能

- 托管前端静态资源（Portal UI）
- 动态应用注册表（子应用元数据与健康状态）
- 聚合下游服务 Actuator 健康信息

### 鉴权与模式切换

- `Master Key`（管理模式）：环境变量读取，允许全量读写
- `Guest Key`（演示模式）：临时 TTL 令牌，仅允许 GET
- 演示模式下屏蔽所有写操作（POST/PUT/DELETE）

### 验收要点

- 所有写接口在 Guest 模式下返回 403
- 应用注册表与健康状态可被前端实时消费

## 5.2 Blog-Service（高性能内容模块）

### 功能

- 文章、标签、分类管理（Tag/Category 多对多）
- Markdown 渲染引擎与 HTML 输出
- MySQL 全文索引站内搜索
- 图片存储支持 OSS 与本地持久化双模式

### 性能策略

- Caffeine 二级缓存渲染结果（缓存键包含文章版本）
- 分页查询与索引优先，避免全表扫描

### 验收要点

- 热门文章详情稳定命中缓存
- 搜索可用且响应稳定

## 5.3 Ops-Core（运维大脑）

### 功能

- 通过 Kubernetes Java Client 获取集群状态
- 通过 REST 触发 Jenkins 流水线
- 发布诊断：聚合 Prometheus 指标 + Loki 日志生成报告

### 核心输出

- 部署异常诊断报告（严重级别、根因候选、处置建议）

### 验收要点

- 异常场景可生成可读报告而非仅报错
- 触发流水线具备异步、超时、重试控制

## 6. 前端与 UX 要求

- Bento Grid 非对称布局，卡片化信息组织
- 毛玻璃与轻量动画，兼顾低配与弱网流畅性
- 状态灯语义统一（绿=正常，红=异常）
- 卡片内嵌迷你趋势图（Sparklines）展示近 1 小时负载曲线

## 7. CI/CD 与可观测性要求

### 7.1 镜像构建

- 强制 Multi-stage 构建
- 运行时镜像使用 `eclipse-temurin:21-jre-alpine`
- 目标平台显式支持 `linux/amd64`
- Dockerfile 必须声明健康检查

### 7.2 Jenkins 声明式流水线

- Jenkins-on-K8s，动态 Pod Agent
- 路径感知构建，仅构建变更模块
- 空闲内存占用目标 < 200MB
- 当前下一阶段重点是先打通 web/gateway/blog 的构建、推送 ACR、更新 Deployment 与 rollout 验证闭环

### 7.3 PLG 监控栈

- Prometheus 仅采集核心业务 + JVM 指标
- Loki 默认日志保留 7 天
- Grafana 提供指标与日志同时间轴联查
- 当前已完成 Prometheus、Loki + Promtail、Grafana Dashboard 展示闭环
- Grafana 公网匿名只读，Prometheus 不公网暴露
- 节点 CPU/内存真实指标尚未接入，后续需补节点/集群指标采集

## 8. 安全与环境兼容性

### 8.1 环境规范

- 兼容 Windows 与 macOS
- 统一 Unix 路径与 LF 换行
- 敏感信息通过 K8s Secret 或本地 `.env` 注入
- 严禁任何明文密钥进入 Git

### 8.2 访问控制

- 演示模式：只读展示，禁止写操作
- 管理模式：全量权限，预留 TOTP 二期扩展

## 9. 非功能性需求（NFR）

- **资源约束**：单节点 8GB 内存稳定运行，无持续 OOM
- **一致性**：API 返回、异常格式、日志字段全服务统一
- **可维护性**：模块隔离，无循环依赖、无跨库查询
- **可观测性**：关键链路可通过 traceId、指标、日志快速定位

## 10. 里程碑与交付

- **M1**：完成 common 规范底座 + Gateway 基础能力
- **M2**：完成 Blog 核心链路（渲染缓存 + 搜索）
- **M3**：完成 Ops-Core 采集、流水线触发、诊断报告
- **M4**：全链路联调、CI/CD 稳定化、PLG 看板验收

当前阶段说明：

- Blog、Gateway、Web 与 PLG 展示闭环已优先推进。
- Ops-Core 仍为后续模块，CI/CD 会先以 Jenkins + K8s 发布闭环为目标。

## 11. 验收标准

- 三个服务可在 K3s 单节点稳定运行
- Guest/Master 双模式权限行为符合设计
- TDD 步骤文档对应能力均可通过测试验证
- 关键故障可在 Grafana + 诊断报告中形成闭环分析

## 12. 主要风险与对策

- **风险**：单机资源瓶颈导致服务抖动  
  **对策**：严格 requests/limits、缓存策略与采集降载
- **风险**：跨模块边界被破坏  
  **对策**：代码评审强制检查依赖方向与数据边界
- **风险**：观测系统反向占用资源  
  **对策**：指标白名单与日志保留策略最小化
