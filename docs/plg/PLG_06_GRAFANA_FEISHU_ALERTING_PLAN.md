# PLG-06 Grafana Unified Alerting + 飞书通知操作计划

## 1. 本阶段目标

本阶段目标是在当前 Prometheus、Loki、Grafana 已经跑通的基础上，补齐第一条最小告警闭环：

```text
Prometheus 指标
-> Grafana Unified Alerting 评估规则
-> Grafana Webhook Contact Point
-> 飞书群机器人通知
```

第一版只做一条服务不可用告警：

```promql
up{namespace="cloud-ops", service=~"gateway-portal|blog-service"} == 0
```

触发演示方式：

```bash
kubectl -n cloud-ops scale deploy/gateway-portal --replicas=0
```

恢复方式：

```bash
kubectl -n cloud-ops scale deploy/gateway-portal --replicas=1
```

本阶段完成后，应能证明：

- Grafana 能基于 Prometheus 数据源评估告警规则。
- `gateway-portal` 或 `blog-service` 不可抓取时，告警进入 `firing`。
- 飞书群能收到 `Cloud-Ops-Hub` 告警消息。
- 服务恢复后，告警进入 `resolved`。

## 2. 为什么选 Grafana Unified Alerting

本项目当前已经部署 Grafana，并且 Grafana 已接入：

- Prometheus 数据源
- Loki 数据源
- Cloud Ops Overview 看板

因此第一版告警建议使用 Grafana Unified Alerting，而不是先启用 Prometheus Alertmanager。

原因：

- 不需要新增 Alertmanager Pod。
- 不需要再处理 Alertmanager 镜像 ACR 化。
- 告警规则、Dashboard、数据源都能在 Grafana 中统一展示。
- 第一条 `up=0` 告警可以直接基于 Prometheus 数据源实现。
- 后续可以继续用 Loki 数据源做错误日志趋势告警。

Alertmanager 仍然是 Prometheus 体系里的标准方案，适合更复杂的分组、静默、路由和多接收人管理。但当前项目更需要一个低成本、可演示、可复现的最小闭环。

## 3. 第一版设计取舍

### 3.1 告警规则

规则名称：

```text
CloudOpsServiceDown
```

PromQL：

```promql
up{namespace="cloud-ops", service=~"gateway-portal|blog-service"} == 0
```

含义：

- Prometheus 抓不到 `gateway-portal` 或 `blog-service` 时触发。
- `service` label 来自当前 `infra/helm/prometheus/values-dev.yaml` 中的 static labels。
- 该规则比只写 `job=~"cloud-ops-.*"` 更利于通知中展示具体服务名。

建议参数：

```text
evaluate_every: 30s
for: 1m
```

说明：

- Prometheus 当前抓取间隔是 `30s`。
- `for: 1m` 可以避免一次短暂抖动就触发。
- 演示时从 scale 到 0 到飞书收到消息，一般需要等 `1~2` 分钟。

### 3.2 通知通道

第一版使用：

```text
Grafana Webhook Contact Point -> 飞书自定义机器人
```

飞书机器人建议使用：

```text
关键词校验
```

关键词建议：

```text
Cloud-Ops-Hub
```

不建议第一版启用飞书签名校验。

原因：

- 飞书签名校验通常需要请求体内包含 `timestamp` 和 `sign`。
- Grafana Webhook 的默认签名机制和飞书机器人签名格式不一定天然兼容。
- 如果强行做签名，很可能需要新增中转服务。
- 当前目标是完成最小通知闭环，不是先实现复杂通知网关。

### 3.3 配置方式

建议通过 Grafana provisioning 入仓，而不是只在 UI 里手动配置。

本项目当前采用 Grafana Helm chart 自带的 `alerting` values 方式：

```text
infra/helm/grafana/values-dev.yaml
```

也就是把这些 provisioning 内容直接写进 values：

```text
alerting.templates.yaml
alerting.contactpoints.yaml
alerting.policies.yaml
alerting.rules.yaml
```

Helm 渲染后，Grafana chart 会把它们变成 provisioning 文件并挂载到：

```text
/etc/grafana/provisioning/alerting/
```

这样做的好处是第一版文件最少、部署命令不变、配置仍然能进 Git。后续如果告警规则增多，再拆成独立目录也可以。

## 4. 需要准备的飞书内容

### 4.1 创建飞书群机器人

在飞书群里执行：

```text
群设置 -> 群机器人 -> 添加机器人 -> 自定义机器人
```

建议机器人名称：

```text
Cloud-Ops 告警机器人
```

安全设置建议：

```text
关键词：Cloud-Ops-Hub
```

完成后复制：

```text
Webhook URL
```

### 4.2 飞书 webhook 不入仓

飞书 webhook URL 属于敏感信息，不要提交到 Git。

推荐创建 Kubernetes Secret：

```bash
kubectl -n monitoring create secret generic grafana-alerting-secret \
  --from-literal=FEISHU_WEBHOOK_URL='<你的飞书机器人 Webhook URL>' \
  --dry-run=client -o yaml \
  | kubectl apply -f -
```

命令用途：

- 在 `monitoring` namespace 创建 `grafana-alerting-secret`。
- 保存飞书机器人 webhook。
- 使用 `--dry-run=client -o yaml | kubectl apply -f -`，重复执行也能更新 Secret。

检查：

```bash
kubectl -n monitoring get secret grafana-alerting-secret
```

命令用途：

- 确认 Secret 已存在。

## 5. 本仓库实际修改内容

> 注意：飞书自定义机器人需要 `msg_type` 和 `content` 这样的请求体。Grafana 默认 webhook 请求体不是飞书格式，所以本项目把 Grafana 镜像升级到 `grafana-oss:12.2.0-amd64`，使用 webhook custom payload 直接生成飞书文本消息。

### 5.1 Grafana 镜像版本

修改：

```text
infra/helm/grafana/values-dev.yaml
```

目标：

- 使用 `cloud-ops-hub/grafana-oss:12.2.0-amd64`。
- 保持 ACR 私有镜像来源，避免 ECS/K3s 节点拉取公网镜像失败。
- 获得 Grafana webhook custom payload 能力，直接适配飞书机器人。

需要提前把镜像推到 ACR：

```bash
docker pull --platform linux/amd64 grafana/grafana:12.2.0
docker tag grafana/grafana:12.2.0 \
  crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/grafana-oss:12.2.0-amd64
docker push crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/grafana-oss:12.2.0-amd64
```

命令用途：

- 拉取 linux/amd64 架构 Grafana 镜像。
- 打上 ACR 私有仓库 tag。
- 推送到 ACR，供 K3s 节点部署时拉取。

注意：

- 上游拉取源使用 `grafana/grafana:12.2.0`。
- 推到 ACR 后仍使用项目内约定的目标 tag：`cloud-ops-hub/grafana-oss:12.2.0-amd64`。
- 因此 `infra/helm/grafana/values-dev.yaml` 中的镜像配置不需要改仓库名，只要 ACR 中已有这个目标 tag 即可。

### 5.2 Secret 注入

修改：

```yaml
envFromSecret: grafana-alerting-secret
```

目标：

- 让 Grafana Pod 从 `grafana-alerting-secret` 读取环境变量。
- Contact point 里使用 `$FEISHU_WEBHOOK_URL`，避免 webhook 明文入仓。

### 5.3 Notification Template

写入：

```text
alerting.templates.yaml
```

目标：

- 定义 `feishu.cloud_ops_message`：生成飞书文本内容。
- 定义 `feishu.cloud_ops_payload`：生成飞书机器人需要的 JSON 请求体。
- 消息中包含 `Cloud-Ops-Hub`，满足飞书关键词校验。

通知内容大致如下：

```text
Cloud-Ops-Hub 告警
状态：firing
规则：CloudOpsServiceDown
服务：gateway-portal
命名空间：cloud-ops
级别：critical
说明：Prometheus 无法正常抓取业务服务，请检查 Pod、Service、/actuator/prometheus。
Grafana：http://grafana.deriou.com
```

### 5.4 Contact Point

写入：

```text
alerting.contactpoints.yaml
```

目标：

- webhook URL 从 Secret 注入，不硬编码到仓库。
- 使用 Grafana `webhook` contact point。
- 使用 `feishu.cloud_ops_payload` 作为 custom payload。
- 告警恢复时也发送 resolved 消息。

关键字段：

```yaml
settings:
  url: $FEISHU_WEBHOOK_URL
  httpMethod: POST
  payload:
    template: feishu.cloud_ops_payload
```

### 5.5 Notification Policy

写入：

```text
alerting.policies.yaml
```

目标：

- 让 `CloudOpsServiceDown` 这类告警发到飞书 contact point。
- 第一版不做复杂路由。

建议策略：

```text
所有 alertname=CloudOpsServiceDown 的告警 -> Feishu contact point
```

### 5.6 Alert Rule

写入：

```text
alerting.rules.yaml
```

规则建议：

```text
folder: Cloud Ops
group: service-availability
rule: CloudOpsServiceDown
datasource: Prometheus
evaluate every: 30s
for: 1m
```

PromQL：

```promql
up{namespace="cloud-ops", service=~"gateway-portal|blog-service"}
```

Grafana 规则里再通过 reduce + threshold 判断：

```text
last(up) < 1
```

这样比直接把 `== 0` 写进 PromQL 更适合 Grafana 的多条件规则模型，也便于保留 `service` label。

标签建议：

```text
severity=critical
category=availability
namespace=cloud-ops
```

注解建议：

```text
summary: Cloud-Ops-Hub service is down
description: Prometheus cannot scrape {{ $labels.service }} in cloud-ops namespace.
runbook_url: http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
```

## 6. Grafana Helm values 已调整的内容

当前 Grafana values 已包含：

```text
infra/helm/grafana/values-dev.yaml
```

已有能力：

- 使用 ACR `grafana-oss:12.2.0-amd64`
- 已配置 Prometheus datasource
- 已配置 Loki datasource
- 已关闭 sidecar alerts
- 已关闭 test framework
- 已关闭 image renderer

告警阶段需要新增：

```text
grafana-alerting-secret 挂载
alerting provisioning
```

本仓库选择：

```text
envFromSecret + alerting values
```

实施前可以检查 chart 是否支持 `alerting`：

```bash
helm show values grafana/grafana | grep -n "alerting" -A 20
```

命令用途：

- 确认当前 Grafana chart 版本支持 `alerting` values。
- 避免凭旧版本字段写错 values。

## 7. 推荐实施顺序

如果你已经完成了 Grafana 镜像推送到 ACR，ECS 上从本节开始即可，不需要再重复执行第 5.1 节的 `docker pull/tag/push`。

### 7.0 在 ECS 拉取最新仓库配置

```bash
cd ~/projects/Cloud-ops-hub
git pull
```

命令用途：

- 拉取最新的 `infra/helm/grafana/values-dev.yaml`。
- 拉取最新的 PLG-06 告警部署文档。
- 确保 ECS 上的 Helm values 已包含 `alerting` provisioning。

### 7.1 确认 Prometheus 查询结果

```bash
kubectl -n monitoring port-forward svc/prometheus-server 9090:80
```

命令用途：

- 在本地访问 Prometheus。

打开：

```text
http://localhost:9090
```

查询：

```promql
up{namespace="cloud-ops", service=~"gateway-portal|blog-service"}
```

预期：

```text
gateway-portal 1
blog-service 1
```

如果这里没有数据，不要继续做告警。先回到 Prometheus 抓取配置排查。

### 7.2 创建飞书 Secret

```bash
kubectl -n monitoring create secret generic grafana-alerting-secret \
  --from-literal=FEISHU_WEBHOOK_URL='<你的飞书机器人 Webhook URL>' \
  --dry-run=client -o yaml \
  | kubectl apply -f -
```

命令用途：

- 把飞书 webhook 安全放进 K8s Secret。

### 7.3 调整 Grafana values

修改：

```text
infra/helm/grafana/values-dev.yaml
```

目标：

- 将 Grafana 镜像版本切到 `12.2.0-amd64`。
- 通过 `envFromSecret` 读取飞书 webhook。
- 通过 `alerting` values 创建 notification template、contact point、notification policy、alert rule。
- 让 Grafana 能读取飞书 webhook URL。

### 7.4 预渲染 Grafana

```bash
helm template grafana grafana/grafana \
  -n monitoring \
  -f infra/helm/grafana/values-dev.yaml \
  --version 8.15.0 \
  > /tmp/grafana-rendered.yaml
```

命令用途：

- 确认 values 字段合法。
- 确认 alerting provisioning ConfigMap 已生成。
- 确认没有新增公网镜像。

检查镜像：

```bash
grep -n "image:" /tmp/grafana-rendered.yaml
```

预期：

```text
cloud-ops-hub/grafana-oss:12.2.0-amd64
```

如果出现 `busybox`、`curl` 或其他公网镜像，需要先关闭对应功能或同步到 ACR。

### 7.5 升级 Grafana

```bash
helm upgrade --install grafana grafana/grafana \
  -n monitoring \
  -f infra/helm/grafana/values-dev.yaml \
  --version 8.15.0
```

命令用途：

- 更新 Grafana 配置。
- 让 alerting provisioning 生效。

等待：

```bash
kubectl -n monitoring rollout status deploy/grafana
```

命令用途：

- 等待 Grafana Pod 更新完成。

### 7.6 在 Grafana UI 验证

打开：

```text
http://grafana.deriou.com
```

用管理员账号登录，检查：

```text
Alerting -> Alert rules
Alerting -> Contact points
Alerting -> Notification policies
```

预期：

- 能看到 `CloudOpsServiceDown`。
- 能看到飞书 contact point。
- Notification policy 已路由到飞书。

## 8. 演示触发

### 8.1 触发告警

```bash
kubectl -n cloud-ops scale deploy/gateway-portal --replicas=0
```

命令用途：

- 临时停止 `gateway-portal`。
- 让 Prometheus 抓取目标变成 `up=0`。
- 触发 `CloudOpsServiceDown` 告警。

等待：

```text
1~2 分钟
```

观察：

- Grafana Alert rules 中 `CloudOpsServiceDown` 进入 `Firing`。
- 飞书群收到包含 `Cloud-Ops-Hub` 的告警消息。

### 8.2 截图素材

建议截图：

- 飞书告警消息。
- Grafana Alert rule `Firing` 状态。
- Cloud Ops Overview 同一时间窗中 `gateway-portal` 状态异常。

这些素材可以用于简历项目讲解和答辩演示。

### 8.3 恢复服务

```bash
kubectl -n cloud-ops scale deploy/gateway-portal --replicas=1
```

命令用途：

- 恢复 `gateway-portal`。

等待发布恢复：

```bash
kubectl -n cloud-ops rollout status deploy/gateway-portal
```

命令用途：

- 确认 Deployment 已恢复可用。

等待 `1~2` 分钟后观察：

- Grafana Alert rule 回到 `Normal`。
- 如果通知策略启用了 resolved 通知，飞书收到恢复消息。

## 9. 常见问题

### 9.0 `helm template` 报 `undefined variable "$labels"`

原因：

- Grafana chart 会对 values 中的 `alerting` 内容执行 Helm `tpl`。
- Grafana 告警模板也使用 `{{ ... }}`。
- 如果直接写 `{{ $labels.service }}`，会被 Helm 当成自己的模板变量提前解析。

处理方式：

- 在 `infra/helm/grafana/values-dev.yaml` 中保留转义写法。
- 例如实际交给 Grafana 的 `{{ $labels.service }}`，在 Helm values 里应写成：

```yaml
description: 'Prometheus cannot scrape {{ "{{ $labels.service }}" }} in cloud-ops namespace.'
```

类似地，notification template 中的 `{{ .Status }}`、`{{ define ... }}` 也需要转义，避免 Helm 抢先解析。

### 9.1 飞书收不到消息

检查顺序：

```text
Grafana Contact point 测试是否成功
飞书机器人 webhook 是否正确
飞书机器人是否启用了关键词校验
Grafana 消息中是否包含 Cloud-Ops-Hub
ECS / Pod 是否能访问飞书 webhook 外网地址
```

如果飞书机器人启用了签名校验，第一版建议先关闭，改用关键词校验。

### 9.2 告警不触发

先在 Prometheus 查询：

```promql
up{namespace="cloud-ops", service=~"gateway-portal|blog-service"}
```

如果 scale 到 0 后仍然没有变成 `0`，检查：

- Prometheus Targets 页面。
- 当前 scrape interval。
- `service` label 是否存在。
- Grafana rule 使用的数据源是否是 `Prometheus`。

### 9.3 告警触发很慢

这是正常的。

当前链路包含：

```text
Prometheus scrape interval
Grafana evaluation interval
alert for duration
notification dispatch
```

如果 scrape interval 是 `30s`，规则 `for` 是 `1m`，等待 `1~2` 分钟是合理的。

### 9.4 告警恢复后飞书没有恢复消息

检查 contact point 或 notification policy 是否启用 resolved 通知。

如果第一版没有恢复通知，也不阻塞主目标。只要 Grafana UI 中状态能回到 `Normal`，告警规则本身就是有效的。

### 9.5 UI 手动配置和 provisioning 冲突

如果同一个 contact point 或 rule 同时由 UI 和 provisioning 管理，容易出现重复或不可编辑。

建议：

- 第一版可以先 UI 手动验证飞书 webhook。
- 稳定后以 provisioning 为准。
- 最终演示版本尽量保留入仓配置，避免 Pod 重建后丢失。

## 10. 本阶段完成标准

满足以下条件即可认为本阶段完成：

- Grafana 中存在 `CloudOpsServiceDown` alert rule。
- Grafana 中存在飞书 webhook contact point。
- `up{namespace="cloud-ops", service=~"gateway-portal|blog-service"}` 正常时为 `1`。
- `gateway-portal` scale 到 0 后，Grafana alert 进入 `Firing`。
- 飞书群收到告警消息。
- `gateway-portal` scale 回 1 后，Grafana alert 恢复为 `Normal`。
- 告警相关配置已纳入仓库，或已明确记录哪些部分仍由 UI 手动配置。

## 11. 后续增强

第一条告警跑通后，再考虑：

- `5xx` 比例升高告警
- JVM heap 使用率高位告警
- Loki 错误日志趋势告警
- Grafana contact point 多渠道路由
- 飞书签名校验或中转 webhook receiver
- 告警截图和处理流程写入 `docs/OPS_RUNBOOK.md`

建议优先级：

```text
先跑通服务不可用告警
再补 5xx 和 JVM
最后再做复杂通知安全和多渠道路由
```
