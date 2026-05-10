# Cloud-Ops-Hub 当前完成度评估

更新时间：2026-05-10

本文从简历展示和工程闭环角度评估当前项目状态。

## 1. 总体判断

当前项目已经从“Web 单模块 CI/CD 闭环”升级为“三模块手动发布闭环”。

已形成主链路：

```text
代码提交
-> Jenkins 手动触发
-> 模块构建
-> Docker 镜像推送 ACR
-> Kubernetes Deployment tag 回写 Git
-> K3s 发布
-> Grafana / Prometheus / Loki 验证
```

当前最有价值的简历关键词：

```text
K3s
Jenkins
Docker
ACR
Kubernetes
GitOps 化回写
Prometheus
Loki
Grafana
node-exporter
多模块发布雏形
```

## 2. CI/CD 完成度

已完成：

- Jenkins 已部署在 K3s `cicd` namespace。
- Jenkins Home 已通过 PVC 持久化。
- Jenkins 可通过本地 `kubectl port-forward` 访问。
- Jenkins 已具备 Docker build / push 能力。
- Jenkins 已具备 kubectl 发布 `cloud-ops` namespace Deployment 的能力。
- 已配置 GitHub token 与 ACR 凭据。
- `cloud-ops-web-pipeline` 已跑通。
- `cloud-ops-gateway-pipeline` 已跑通。
- `cloud-ops-blog-pipeline` 已跑通。
- 三条流水线均能生成唯一镜像 tag。
- 三条流水线均能回写对应 Deployment 并 push 回 GitHub。
- 三条流水线均以手动触发方式发布到 K3s。

当前边界：

- 仍是三个独立 Jenkinsfile，不是统一智能 Pipeline。
- 仍是手动触发，不接 GitHub Webhook。
- gateway/blog 第一版以 `-DskipTests` 跑通发布闭环。
- gateway/blog 不同时运行，避免两个 Job 并发回写 main。
- Jenkins 仍挂载 Docker socket，适合学习阶段，不是生产最佳实践。

判断：

```text
CI/CD 已达到“暑期日常实习项目可展示”的核心闭环。
当前可以描述为：完成 web、gateway-portal、blog-service 三个模块的独立发布流水线，为后续多模块路径感知发布打下基础。
```

## 3. 应用部署完成度

已完成：

- `web` 已容器化并部署到 K3s。
- `gateway-portal` 已容器化并部署到 K3s。
- `blog-service` 已容器化并部署到 K3s。
- MySQL 已作为 blog 依赖服务部署。
- ACR 私有镜像仓库已作为业务镜像来源。
- K8s base 清单已覆盖业务 Deployment / Service / ConfigMap / Ingress。
- `deriou.com` 已通过 Ingress 分发：
  - `/` -> web
  - `/api/v1/gateway` -> gateway-portal
  - `/api/v1/blog` -> blog-service
- Deployment image tag 同步纪律已建立：发布后的镜像 tag 必须回写 Git。

当前边界：

- 数据库迁移仍走人工 SOP。
- 没有多环境发布策略。
- 没有自动回滚策略。
- 没有灰度、蓝绿或金丝雀发布。

判断：

```text
应用部署链路已完整，适合展示“从代码到 K3s 集群运行”的端到端能力。
```

## 4. 可观测性完成度

已完成：

- `gateway-portal` 和 `blog-service` 已接入 Actuator。
- 后端服务已接入 Micrometer Prometheus。
- Prometheus 已部署并抓取业务指标。
- node-exporter 已部署到 K3s，并由 Prometheus 抓取节点 CPU / 内存真实指标。
- Loki + Promtail 已部署，并能查询 `cloud-ops` namespace 日志。
- traceId 已贯通响应、后端日志和 Loki 查询。
- Grafana 已接入 Prometheus 与 Loki。
- Cloud Ops Overview 看板已完成第一版，覆盖服务健康、请求量、5xx、p95 延迟、JVM Heap、错误日志趋势、节点 CPU 与节点内存。
- Grafana Unified Alerting 已完成服务不可用告警验证。
- 飞书机器人已完成 `firing` 与 `resolved` 通知闭环验证。
- 前端 `/ops/cluster` 已作为 Grafana / Prometheus / Trace 联查入口。

当前边界：

- Loki 日志持久化能力仍需继续确认和加强。
- 告警规则已具备最小闭环，但尚未形成多维度规则体系。
- 前端 `/ops/cluster` 主要是入口层，不是完整自研监控大屏。
- 还没有把 CI/CD 构建结果纳入 Grafana 展示。
- 前端集群摘要中的 CPU / 内存仍来自 mock 数据，尚未接入 Prometheus 查询结果。

判断：

```text
PLG 可观测性闭环已经从“业务指标 + 日志展示”升级为“业务指标 + 节点资源 + 日志 + 告警通知”。
当前已经能支撑排障演示：请求 -> traceId -> Loki 日志 -> Prometheus 指标 -> Grafana 看板 -> 飞书告警。
```

## 5. 文档与交接完成度

已完成：

- Web 前端更新 AI 手册已精简为日常操作要点。
- gateway/blog 后端流水线部署文档已补充。
- Jenkins on K3s 方案文档已记录整体演进。
- Web 单模块发布 SOP 已记录操作流程。
- 镜像 tag 规范已融入现有文档，不再单独维护旧文档。
- PLG 告警完成演示 Runbook 已记录告警触发与恢复验证路径。
- node-exporter 节点 CPU / 内存部署 Runbook 已记录镜像同步、Helm 升级、Prometheus 验证和 Grafana 排障路径。

当前边界：

- 后续如果引入统一 Pipeline，需要新增单独设计文档。
- 如果启用 Webhook，需要补充触发规则和防循环策略。
- 如果启用测试阶段，需要补充失败处理与测试范围说明。
- 如果前端集群摘要改为真实数据，需要新增 Prometheus / K8s API 聚合接口设计文档。

判断：

```text
当前文档足以支撑交接、复现和面试讲解。
```

## 6. 简历展示成熟度

当前可以稳定表达：

```text
基于 K3s 搭建 Cloud-Ops-Hub 运维开发实践项目，完成 web、gateway-portal、blog-service 三个模块的 Jenkins CI/CD 发布闭环。流水线支持模块构建、Docker 镜像推送阿里云 ACR、Kubernetes Deployment 镜像 tag 回写 GitHub、K3s rollout 校验；可观测性侧基于 Prometheus、node-exporter、Loki、Grafana 和飞书机器人完成业务指标、节点 CPU / 内存、日志查询与告警通知闭环。
```

更细一点可以说：

- “不是一次性做复杂平台，而是先落地三个独立单模块 Pipeline。”
- “每条流水线都保持 Git 中的期望镜像 tag 与集群实际运行镜像一致。”
- “三条流水线为后续 changed paths 多模块路径感知发布提供了雏形。”
- “当前手动触发是学习阶段的稳定取舍，后续可演进 Webhook、动态 Agent、Kaniko / BuildKit。”
- “Grafana 看板已经同时覆盖应用层指标、JVM 指标、日志趋势和节点 CPU / 内存资源指标。”
- “告警链路已经验证 firing 与 resolved，能演示服务异常发现、通知和恢复闭环。”

## 7. 下一阶段建议

优先级从高到低：

1. 设计前端 `/ops/cluster` 集群摘要真实化方案，明确 Prometheus 查询、后端聚合接口和前端展示边界。
2. 实现最小只读 Ops 聚合接口：先返回节点 CPU、节点内存、服务 up 状态和检查时间。
3. 将前端 `/ops/cluster` 顶部摘要从 mock 切换为真实接口，并保留 mock 作为本地开发 fallback。
4. 给 gateway/blog 补最小 smoke test。
5. 给后端 Pipeline 增加测试阶段，先从 gateway 开始。
6. 设计统一多模块路径感知 Pipeline。
7. 引入 GitHub Webhook，但要先处理 `[skip ci]`、路径过滤和回写防循环。

当前不建议马上做：

- 一步到位改成复杂统一 Pipeline。
- 自动执行数据库迁移。
- Jenkins 公网暴露。
- 没有回滚策略时做自动发布到多环境。
- 直接做完整自研监控大屏，当前更适合先做“关键摘要 + Grafana 深链”的轻量闭环。
