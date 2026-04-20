---
title: PLG 初学者实施规划
date: 2026-04-19
tags:
  - plg
  - observability
  - prometheus
  - loki
  - grafana
  - learning-plan
status: draft
aliases:
  - L2 监控栈实施规划
  - PLG Beginner Plan
---

> [!info]
> 本文面向“先跑通、边做边学”的学习目标，优先追求最小可用闭环，不追求一次做到生产级复杂度。

### 目标

本规划用于指导 Cloud-Ops-Hub 在当前阶段完成最小可观测性闭环：

- 让 `gateway-portal` 与 `blog-service` 暴露 Prometheus 指标
- 在 K3s 中部署最小 Prometheus
- 在 K3s 中部署 Loki + Promtail 收集日志
- 在 Grafana 中完成指标与日志联查
- 为后续 `Ops-Core` 的发布诊断能力准备基础数据

相关文档：

- [[docs/learning/L2-observability-plg-handbook]]
- [[docs/PDR]]
- [[docs/API_STANDARDS]]
- [[docs/DEPLOYMENT_PLAYBOOK]]

### 为什么这一步值得做

在当前项目目标中，可观测性不是附属功能，而是核心交付之一。`PDR` 已明确要求：

- 平台运行在单节点 `8GB` ECS 上
- 需要接入 `Prometheus + Loki + Grafana`
- 关键链路要能通过 `traceId`、指标、日志快速定位问题

这意味着本阶段的重点不是“装一个监控界面”，而是建立下面这条链路：

`应用产出指标 -> Prometheus 抓取 -> 应用产生日志 -> Loki 聚合 -> Grafana 联查`

### 当前仓库现状

结合仓库代码，当前已经具备一些基础：

- `gateway-portal` 与 `blog-service` 已接入 `Spring Boot Actuator`
- 两个服务都已经有 `readiness/liveness` 健康检查
- K8s 基础部署结构已经使用 `Kustomize`
- 命名空间、Service、Deployment 已具备最小运行条件

当前还缺少的关键部分：

- 还没有启用 `/actuator/prometheus`
- 还没有接入 `micrometer-registry-prometheus`
- 仓库里还没有现成的 `infra/observability` 资源清单
- 日志还没有形成统一的“可关联字段”规范落地
- 根模块中还没有真正实现 `ops-core`

> [!warning]
> 这意味着本阶段正确目标是“打通 gateway/blog 的观测链路”，而不是直接实现最终版发布诊断平台。

### 先掌握的最小基础知识

#### 1. Actuator 与 Micrometer

你可以把它理解为 Spring Boot 对外暴露运行状态的标准方式。

- `Actuator` 负责提供健康检查、信息、指标端点
- `Micrometer` 负责采集 JVM、HTTP 请求、线程池等指标
- `Prometheus Registry` 负责把指标转换成 Prometheus 能抓取的格式

如果没有这一层，Prometheus 就没有目标可以抓。

#### 2. Prometheus

Prometheus 的核心思路是“拉模式”：

- 它不会等应用主动上报
- 它会定时访问目标地址
- 典型抓取地址就是 `/actuator/prometheus`

需要先理解的概念：

- `target`：被抓取的目标
- `job`：一组抓取任务
- `label`：时间序列的维度标签
- `scrape interval`：抓取间隔

#### 3. Loki 与 Promtail

Loki 主要负责“按标签组织日志”，Promtail 负责“从节点上把容器日志采集出来并发送给 Loki”。

学习阶段先记住两点就够了：

- 容器日志应直接输出到 `stdout/stderr`
- 低基数字段做标签，高变化字段写进日志内容

#### 4. Grafana

Grafana 不是日志库也不是指标库，它是“统一查看入口”。

在这个项目中，你最重要的使用方式有两种：

- `Explore`：临时查问题
- `Dashboard`：长期看趋势

### 对初学者最友好的技术选择

本项目建议采用下面的组合：

- 业务服务部署：继续沿用仓库现有 `YAML + Kustomize`
- 监控栈部署：优先使用 `Helm`
- 日志采集器：优先 `Promtail`
- 日志格式：第一版用 `key=value` 风格

这样选择的原因：

- 业务部署已经是 Kustomize 风格，继续沿用最自然
- Prometheus/Loki/Grafana 都是成熟第三方组件，用 Helm 更快跑通
- `Promtail` 比 `Fluent Bit` 更直观，适合学习
- `key=value` 比 JSON 更容易上手，也比纯文本更容易后续检索

### 本阶段不建议一开始就做的事

- 不要一开始引入很重的 Operator 体系
- 不要第一版就追求全量 K8s 指标采集
- 不要把高基数标签放进监控系统
- 不要一开始就设计复杂告警策略
- 不要试图一步做到 `Ops-Core` 全自动诊断闭环

### 最小实施路线

#### 阶段 0：明确成功标准

这一阶段完成前，先统一本次学习项目的“完成定义”。

建议 DoD 如下：

- `gateway-portal` 与 `blog-service` 都能访问 `/actuator/prometheus`
- Prometheus `Targets` 页面中两个服务都显示 `UP`
- Grafana Explore 中可按 `namespace=cloud-ops` 查到应用日志
- Grafana 中至少有 3 个最小可用面板
- 能人为制造一个异常，并在 Grafana 中同时看到日志与指标变化

#### 阶段 1：补齐应用指标暴露

这一阶段只做应用侧最小改造，不部署监控栈。

目标：

- 给 `gateway-portal` 增加 Prometheus registry 依赖
- 给 `blog-service` 增加 Prometheus registry 依赖
- 在两个服务中开放 `prometheus` endpoint

你要理解的重点：

- `Actuator` 不等于默认有 Prometheus 格式输出
- 只有开启 registry 并暴露端点后，Prometheus 才能真正抓取

验收方式：

- 本地启动应用后访问 `/actuator/prometheus`
- 能看到文本格式的指标输出

建议补充的指标关注点：

- HTTP 请求总量
- HTTP 状态码分布
- JVM 内存
- JVM GC
- 线程数

建议落实项：

- 在 `gateway-portal` 与 `blog-service` 中增加 `micrometer-registry-prometheus`
- 将 `management.endpoints.web.exposure.include` 扩展为 `health,info,prometheus`
- 增加统一低基数标签，例如 `application=${spring.application.name}`
- 为 `http.server.requests` 开启 histogram，便于后续做延迟趋势与分位数图

建议本地验收命令：

```bash
# gateway-portal
curl http://localhost:8080/actuator/prometheus

# blog-service
curl http://localhost:8080/actuator/prometheus
```

建议重点检查返回内容：

- 是否包含 `# HELP`
- 是否包含 `jvm_`、`process_` 或 `system_` 开头指标
- 是否包含 `http_server_requests`
- 是否包含统一标签 `application`

建议集群验收命令：

```bash
kubectl -n cloud-ops port-forward deploy/gateway-portal 18080:8080
kubectl -n cloud-ops port-forward deploy/blog-service 18081:8080

curl http://localhost:18080/actuator/prometheus
curl http://localhost:18081/actuator/prometheus
```

阶段通过标准：

- 两个服务都能稳定返回 `/actuator/prometheus`
- `/actuator/health` 探针不受影响
- HTTP 请求指标可在触发请求后出现在输出中

#### 阶段 2：补齐最小日志规范

这一阶段的目标不是做完整链路追踪，而是先让日志“可读、可筛、可关联”。

建议统一日志内容至少包含：

- `service`
- `traceId`
- `path`
- `method`
- `latencyMs`
- `resultCode`

推荐第一版日志格式：

```text
level=INFO service=blog-service traceId=abc123 path=/api/v1/blog/posts method=GET latencyMs=24 resultCode=OK msg="request completed"
```

这里要特别注意：

- `traceId` 不建议作为 Loki label
- `path` 不建议作为 label
- `userId` 一类字段不要进入 label

> [!tip]
> 第一版只要做到“日志里有这些字段”，就已经足够支撑学习期排障。

本阶段实施时，优先把能力放在 `common-core`，让 `gateway-portal` 与 `blog-service` 复用同一套规范：

1. 新增请求级 `traceId` 上下文
   - 请求头优先读取 `X-Trace-Id`
   - 没有传入时自动生成 32 位无横线 UUID
   - 将 `traceId`、`method`、`path` 写入 MDC
   - 响应头统一返回 `X-Trace-Id`

2. 让 `ApiResponse.traceId` 与请求上下文对齐
   - 标准响应体里的 `traceId` 应与响应头一致
   - 鉴权失败、业务异常、系统异常也要使用同一个 `traceId`
   - 没有 Web 请求上下文时，继续 fallback 随机生成，保证单元测试和非 Web 场景可用

3. 输出统一访问日志

```text
event=http_request service=blog-service traceId=abc123 method=GET path=/api/v1/blog/posts status=200 latencyMs=24 resultCode=OK msg="request completed"
```

4. 对 `/actuator/**` 做日志降噪
   - `/actuator/health` 与 `/actuator/prometheus` 仍返回 `X-Trace-Id`
   - 不输出 `event=http_request` 访问日志
   - 避免后续 Prometheus 抓取制造大量无效日志

5. 补齐应用 console 日志 pattern
   - 普通异常日志也能看到 `service`
   - 普通异常日志也能看到 MDC 中的 `traceId`、`method`、`path`
   - 日志继续输出到容器 `stdout/stderr`，不写本地文件

阶段 2 自动化验收命令：

```bash
./apps/gateway-portal/mvnw -f pom.xml -pl common/common-core test
./apps/gateway-portal/mvnw -f pom.xml -pl apps/gateway-portal -am test -Dsurefire.failIfNoSpecifiedTests=false
./apps/gateway-portal/mvnw -f pom.xml -pl apps/blog-service -am test -Dsurefire.failIfNoSpecifiedTests=false
```

阶段 2 本地手动验收建议：

```bash
curl -i -H "X-Ops-Key: <your-key>" http://localhost:8080/api/v1/gateway/access-mode
curl -i -H "X-Trace-Id: demo-trace-001" -H "X-Ops-Key: <your-key>" http://localhost:8080/api/v1/gateway/access-mode
curl -i http://localhost:8080/actuator/health
```

你要重点观察：

- 业务接口响应头包含 `X-Trace-Id`
- 标准 JSON 响应体里的 `traceId` 与响应头一致
- 应用日志中出现 `event=http_request`
- 日志中包含 `service`、`traceId`、`method`、`path`、`latencyMs`、`resultCode`
- 访问 `/actuator/health` 不产生 `event=http_request` 访问日志

#### 阶段 3：部署最小 Prometheus

这一阶段开始进入集群部署。

建议策略：

- 用 Helm 部署最小 Prometheus
- 先只抓两个业务服务
- `scrape interval` 先设为 `30s`

第一版抓取对象：

- `gateway-portal`
- `blog-service`

第一版先不强求：

- `node-exporter`
- `kube-state-metrics`
- 全量控制平面指标

原因很简单：学习项目最重要的是先学会“看懂业务服务指标”，而不是一开始就被一大堆系统指标淹没。

验收方式：

- 打开 Prometheus `Targets`
- 两个目标均为 `UP`
- 能执行最简单的 PromQL 查询

建议先学的 3 条查询：

- 查看请求量趋势
- 查看 5xx 数量趋势
- 查看 JVM 堆内存使用趋势

#### 阶段 4：部署 Loki + Promtail

这一阶段解决“日志能查”的问题。

建议策略：

- 用 Helm 部署 Loki
- 同时部署 Promtail
- 先把日志保留时间控制在 `3~7 天`

你要重点观察的不是“日志有多少”，而是：

- 是否能按 `namespace=cloud-ops` 查询
- 是否能按 `app` 或 `pod` 区分服务
- 是否能在异常时段查到对应错误

验收方式：

- 打开 Grafana Explore
- 选中 Loki 数据源
- 能筛到 `cloud-ops` 命名空间日志
- 能定位 `gateway-portal` 和 `blog-service` 的容器日志

#### 阶段 5：接入 Grafana 并做最小看板

Grafana 第一版不要做太多，只做最值钱的 3 块。

建议最小看板：

1. 服务健康与请求趋势
2. JVM 内存与 GC
3. 错误日志趋势

每块看板的学习重点：

1. 服务健康与请求趋势
   重点理解服务是否存活、请求量是否异常波动
2. JVM 内存与 GC
   重点理解 Java 服务是否有明显内存压力
3. 错误日志趋势
   重点理解错误是否集中在某一时段爆发

验收方式：

- 同一时间窗内能切换查看指标和日志
- 遇到一次错误时能定位对应时段

#### 阶段 6：做人为异常演练

如果没有故障演练，监控系统很容易停留在“装好了但不会用”。

建议至少做一次简单演练：

- 临时让某个接口报错
- 或临时给服务一个错误配置
- 或模拟下游超时

观察内容：

- Grafana 面板是否出现波动
- Loki 是否出现对应错误日志
- 是否能通过时间窗口把日志与指标对应起来

这一阶段的目标是培养排障思路，而不是证明系统绝对稳定。

### 推荐的执行顺序

如果你是第一次独立做这类项目，建议按下面顺序推进：

1. 先完成应用侧 `/actuator/prometheus`
2. 再完成最小日志字段规范
3. 再部署 Prometheus
4. 再部署 Loki + Promtail
5. 最后接入 Grafana 看板
6. 收尾时补一次异常演练与验收记录

这个顺序的优点是：

- 每一步都可以独立验证
- 出错时更容易定位问题在哪一层
- 学习节奏更平滑，不容易被第三方组件复杂度劝退

### 完整监控模块的前后端实现顺序

从工程角度看，一个完整的监控模块并不是单独的“前端页面”，而是由 3 条线共同组成：

- 应用侧产出数据
- 监控基础设施采集和存储数据
- 前端运维模块负责摘要展示与入口承接

但这 3 条线的启动顺序并不相同。更合理的顺序是“先打通数据链路，再建设前端入口”。

#### 1. 先定义要观察什么

这一阶段先不写页面，也不急着部署一堆组件，而是先明确：

- 第一版主要观察哪些服务
- 第一版最关心哪些异常
- 出问题时第一眼最希望看到什么

对于当前项目，建议第一版目标统一为：

- 观察对象：`gateway-portal`、`blog-service`
- 重点异常：服务不可用、5xx 升高、JVM 内存高、错误日志爆发
- 首页摘要重点：健康状态、QPS、错误率、最近错误

#### 2. 再让应用真正产出指标与日志

这是整个监控模块的第一层基础。

需要先完成：

- 应用暴露 `/actuator/prometheus`
- 应用日志稳定输出到容器标准输出
- 日志里具备基本的可关联字段

如果这一层没有完成，后面的 Prometheus、Loki、Grafana 以及前端运维页面都无法真正落地。

#### 3. 再部署监控栈

推荐顺序：

1. `Prometheus`
2. `Loki + Promtail`
3. `Grafana`

原因：

- Prometheus 最容易先验证“指标已经开始被采集”
- Loki 解决“出了问题能查日志”
- Grafana 负责把指标和日志统一联查

#### 4. 再做前端运维模块

前端不是第一优先级，但可以提前做结构设计。

更适合学习项目的方式是：

- 先定义页面结构与信息层级
- 先用 mock 数据把页面框架跑通
- 再逐步接真实接口

这样做的好处是：

- 前端不会被后端和监控栈完全阻塞
- 你能更早形成对“监控信息应该怎么组织”的理解
- 页面设计可以跟后端能力一起逐步迭代

#### 5. 最后再做聚合诊断与故障演练

真正完整的监控闭环，不是只有图表，而是“遇到异常时可以辅助判断原因”。

所以最后才建议做：

- 联查视图
- 诊断摘要
- 故障演练
- 验收记录

> [!tip]
> 对当前阶段来说，正确顺序不是“先画前端图表”，而是 `先有数据 -> 再保证能查 -> 再做摘要展示 -> 最后做诊断辅助`。

### 前端运维模块中的 PLG 展示定位

结合当前项目的前端定位，`PLG` 更适合放在前端的“运维模块”中，而不是做成首页公开展示的大监控屏。

建议把它理解成两层展示：

- 首页运维卡：只展示摘要与入口
- 运维子页：承载真正的观测摘要、监控信息和深度入口

#### 首页中的展示方式

首页 `运维` 卡建议只承担轻量信息：

- 一个整体状态标签，例如 `Stable`
- 一句整体状态说明，例如 `Online`
- 2 到 4 个入口，例如 `Cluster`、`Diagnostics`、`Grafana`
- 一句轻量监控摘要，例如“2 services healthy, logs searchable”

首页不建议放：

- 实时日志流
- 大表格
- 复杂监控图表
- 控制台式大屏布局

这样做的原因是首页在产品定位上更偏门户，不应被重型运维内容占满。

#### 运维子页中的展示方式

建议新增一个运维子页面，将 `PLG` 作为真正的监控模块承载页，例如：

- `/ops/observability`

这个页面的角色不是替代 Grafana，而是成为“运维门户中的观测工作台”。

它更适合承担：

- 健康摘要
- 核心指标摘要
- 日志摘要
- 告警或异常提示
- Grafana 深入分析入口

#### 推荐的页面结构

`/ops/observability` 第一版建议采用 5 个区块：

1. 顶部概览区
   展示环境、最后刷新时间、Prometheus/Loki/Grafana 状态
2. 服务状态区
   展示 `gateway-portal` 与 `blog-service` 的健康状态
3. 指标摘要区
   展示 QPS、错误率、JVM 堆内存、重启次数等关键指标
4. 日志摘要区
   展示最近错误数量与最近几条错误日志片段
5. 深入分析入口区
   提供 `Open Grafana Dashboard`、`Open Grafana Explore`、`查看诊断报告` 等入口

#### Grafana 与前端自建页面的分工

这一步很重要，建议一开始就想清楚。

前端运维模块负责：

- 展示最关键的摘要信息
- 给出统一入口
- 建立“先看哪里”的信息顺序
- 承接未来 `Ops-Core` 的诊断能力

Grafana 负责：

- 深度查询指标
- 联查日志与指标
- 切换时间窗口
- 承担更细的调试和排障工作

也就是说：

- 前端页面不是完整替代 Grafana
- 前端页面是“更适合项目语境的运维摘要层”
- Grafana 是“专业排障工具层”

#### 第一版前端实现策略

为了降低实现难度，建议采用：

- 第一步：先做页面结构
- 第二步：先接 mock 数据
- 第三步：等真实接口稳定后再替换数据源

这种方式很适合当前仓库，因为现有前端已经有 `Ops` 相关页面、类型定义和 mock 数据结构，可以沿着现有风格继续扩展。

### 建议拆成的学习任务

为了避免一次做太多，建议你把本阶段拆成 5 个小任务：

1. 读懂当前项目里 `Actuator`、`Deployment`、`Service` 配置
2. 改造应用，让指标端点可访问
3. 部署 Prometheus 并验证目标抓取
4. 部署 Loki + Promtail 并验证日志检索
5. 在 Grafana 中完成最小联查与一次异常演练

### 建议记录的学习产物

为了让这一步不仅“做过”，而是真正沉淀经验，建议你每完成一阶段都记录：

- 做了什么
- 遇到了什么错误
- 如何定位
- 最终怎么修复
- 哪些概念仍然不理解

建议把这些记录继续沉淀在 `docs/plg/` 下，形成你自己的学习轨迹。

### 常见坑与阶段性问题

下面把常见问题按阶段重新整理，便于在实施时快速对照。

#### 应用接入阶段

常见问题：

- 只加了 `Actuator`，但没加 Prometheus registry
- 暴露了 `health`，却没暴露 `prometheus`
- 指标端点可访问，但指标内容看不懂
- 日志虽然有输出，但字段不统一，后续很难筛选
- 日志没有进入容器标准输出，而是写在应用本地文件里

#### Prometheus 阶段

常见问题：

- Prometheus 抓取地址写错成外网地址
- 服务在集群里可访问，但抓取路径写错
- Service 名称、端口、路径三者不一致
- Target 显示 `DOWN`，实际问题往往出在 endpoint 或 Service 配置
- 抓取频率过高，观测系统本身开始反向占资源

#### Loki / Promtail 阶段

常见问题：

- Loki 能收到日志，但标签维度设计过多
- 把高基数字段当成 label，导致查询和存储压力上升
- Promtail 没有正确采到容器日志
- Loki 里确实有日志，但 Grafana 中不会筛选标签
- 只关注“有没有日志”，忽略了“日志是否便于定位问题”

#### Grafana 阶段

常见问题：

- Grafana 面板很多，但没有任何一个真正服务排障
- 时间窗口没有统一，导致日志与指标难以对齐
- 第一版就做很多变量和复杂查询，最后自己也难维护
- 只会看图，不知道如何把图和日志关联起来

#### 前端运维模块阶段

常见问题：

- 一开始就想完全替代 Grafana，导致实现范围失控
- 页面里堆太多图表，最终变成控制台大屏
- 没有摘要层，用户进入页面后不知道先看哪里
- 过早绑定真实接口，导致前端开发被后端进度完全卡住

### 本项目的资源控制建议

由于 `PDR` 已明确这是单机 `8GB` 资源约束环境，本阶段建议坚持以下原则：

- Prometheus 抓取间隔先固定为 `30s`
- 日志保留时间先控制在 `3~7 天`
- 第一版只监控核心业务服务
- Dashboard 数量控制在最少必要范围
- 告警先只保留最重要的 2~3 条

### 本阶段完成后你应该具备的能力

如果这一版顺利做完，你应该至少能掌握：

- 知道 Prometheus 为什么能抓到 Spring Boot 指标
- 知道 Loki 是怎么从 K8s 容器采集日志的
- 知道 Grafana 为什么适合做联查入口
- 知道如何把“某个报错时段”的日志和指标串起来看
- 知道这个项目后续怎样继续扩展到 `Ops-Core` 的发布诊断能力

### 下一步建议

本计划完成后，下一篇文档建议继续拆成更细的执行清单，例如：

- `PLG-01-应用指标接入清单`
- `PLG-02-Prometheus 部署清单`
- `PLG-03-Loki 与 Promtail 部署清单`
- `PLG-04-Grafana 最小看板清单`

> [!success]
> 对当前学习目标来说，“跑通并理解链路”比“做得很重很全”更重要。只要你能完成一次从应用指标到日志联查的闭环，这一步就已经非常有价值。
