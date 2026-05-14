# PLG-07 告警闭环完成总结与演示 Runbook

## 1. 当前完成情况

当前项目已经完成 PLG 监控告警闭环：

```text
Spring Boot Actuator + Micrometer
-> Prometheus 抓取业务指标
-> Grafana Dashboard 展示服务状态
-> Grafana Unified Alerting 评估告警
-> Grafana Webhook Contact Point
-> 飞书群机器人通知
```

已验证的能力：

- `gateway-portal`、`blog-service` 已暴露 `/actuator/prometheus`。
- Prometheus 能抓取两个业务服务的 `up`、HTTP 请求、JVM 等指标。
- Grafana 已接入 Prometheus 和 Loki 数据源。
- Grafana Dashboard 能展示服务健康、请求量、5xx、延迟、JVM、日志趋势。
- Grafana 中已存在告警规则 `CloudOpsServiceDown`。
- 飞书机器人已收到 `firing` 告警消息。
- 服务恢复后，飞书机器人已收到 `resolved` 恢复消息。

本阶段可作为面试项目展示点：

- 不是只部署了监控页面，而是完成了“发现异常 -> 告警通知 -> 恢复通知”的闭环。
- 告警可通过 K8s scale 命令稳定复现。
- 飞书消息中包含服务名、命名空间、告警状态和 Grafana 链接。

## 2. 关键配置位置

Grafana Helm values：

```text
infra/helm/grafana/values-dev.yaml
```

Grafana Dashboard JSON：

```text
infra/helm/grafana/dashboards/cloud-ops-overview.json
```

详细部署文档：

```text
docs/plg/PLG_06_GRAFANA_FEISHU_ALERTING_PLAN.md
```

本演示文档：

```text
docs/plg/PLG_07_ALERTING_COMPLETION_DEMO_RUNBOOK.md
```

## 3. 告警规则说明

规则名称：

```text
CloudOpsServiceDown
```

PromQL：

```promql
up{namespace="cloud-ops", service=~"gateway-portal|blog-service"}
```

Grafana 判断逻辑：

```text
last(up) < 1
```

含义：

- `up=1` 表示 Prometheus 可以成功抓取服务指标。
- `up=0` 表示 Prometheus 抓取失败，通常代表服务不可用、Pod 不存在、Service 不通或 `/actuator/prometheus` 异常。
- 当前演示主要使用 `gateway-portal` 停止副本来触发 `up=0`。

告警参数：

```text
evaluate every: 30s
for: 1m
```

说明：

- Prometheus 抓取间隔为 `30s`。
- `for: 1m` 用于避免短暂抖动立即告警。
- 演示时从服务缩容到飞书收到消息，通常等待 `1~2` 分钟。

## 4. 飞书通知说明

飞书通知通道：

```text
Grafana Webhook Contact Point -> 飞书自定义群机器人
```

Contact point：

```text
feishu-cloud-ops
```

Secret：

```text
grafana-alerting-secret
```

Secret key：

```text
FEISHU_WEBHOOK_URL
```

飞书机器人安全策略：

```text
关键词校验：Cloud-Ops-Hub
```

注意：

- 不要把飞书 webhook URL 提交到 Git。
- 第一版不要启用飞书签名校验。
- Grafana 默认 webhook body 不兼容飞书，所以项目中使用 custom payload 生成飞书要求的 `msg_type/content` JSON。

关键配置：

```yaml
payload:
  template: '{{ "{{ template \"feishu.cloud_ops_payload\" . }}" }}'
```

作用：

- `payload` 保持 Grafana 12.2 需要的 `CustomPayload` 对象结构。
- `template` 显式调用 `feishu.cloud_ops_payload`。
- Helm 渲染后，Grafana 会生成飞书文本消息 JSON。

## 5. 演示前检查

### 5.1 确认 Grafana Pod 正常

```bash
sudo kubectl -n monitoring get pod -l app.kubernetes.io/instance=grafana -o wide
```

命令作用：

- 查看 Grafana Pod 是否为 `Running`。
- 确认 `READY` 是否为 `1/1`。

预期：

```text
READY   STATUS
1/1     Running
```

### 5.2 确认 Prometheus 能看到业务服务

```bash
sudo kubectl -n monitoring port-forward svc/prometheus-server 9090:80
```

命令作用：

- 临时把 Prometheus 暴露到本机 `localhost:9090`。
- 用于查看 Prometheus 查询结果和 targets。

在 Prometheus UI 查询：

```promql
up{namespace="cloud-ops", service=~"gateway-portal|blog-service"}
```

正常情况下：

```text
gateway-portal 1
blog-service 1
```

### 5.3 确认飞书 webhook 可用

```bash
FEISHU_WEBHOOK_URL=$(sudo kubectl -n monitoring get secret grafana-alerting-secret \
  -o jsonpath='{.data.FEISHU_WEBHOOK_URL}' | base64 -d)
```

命令作用：

- 从 K8s Secret 中读取飞书 webhook。
- 避免把 webhook 明文写入命令历史以外的文件。

```bash
curl -i -X POST \
  -H "Content-Type: application/json" \
  -d '{"msg_type":"text","content":{"text":"Cloud-Ops-Hub 飞书机器人直连测试"}}' \
  "$FEISHU_WEBHOOK_URL"
```

命令作用：

- 直接绕过 Grafana 测试飞书机器人。
- 如果这一步成功，说明飞书 webhook、关键词和 ECS 外网访问正常。

预期：

```text
HTTP/2 200
```

并且飞书群收到测试消息。

### 5.4 确认 Grafana 告警配置已渲染

```bash
sudo kubectl -n monitoring get configmap grafana -o yaml \
  | grep -n -A12 -B4 "payload"
```

命令作用：

- 查看当前集群里 Grafana 实际加载的 Contact point 配置。
- 确认 `payload.template` 是否为显式模板调用。

预期关键内容：

```yaml
payload:
  template: '{{ template "feishu.cloud_ops_payload" . }}'
```

## 6. 演示步骤

### 6.1 打开 Grafana Dashboard

访问：

```text
http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
```

观察：

- `gateway-portal` 为 `UP`。
- `blog-service` 为 `UP`。
- Dashboard 能看到业务请求量、JVM、日志趋势等面板。

建议截图：

```text
Grafana Dashboard 正常状态
```

### 6.2 触发告警

```bash
sudo kubectl -n cloud-ops scale deploy/gateway-portal --replicas=0
```

命令作用：

- 把 `gateway-portal` 副本数缩容到 0。
- 让 Prometheus 无法抓取 `gateway-portal` 指标。
- 触发 `CloudOpsServiceDown`。

查看 Deployment 状态：

```bash
sudo kubectl -n cloud-ops get deploy gateway-portal
```

命令作用：

- 确认 `gateway-portal` 当前副本数为 0。

等待：

```text
1~2 分钟
```

### 6.3 观察 Dashboard 异常

刷新 Grafana Dashboard。

预期：

```text
gateway-portal DOWN
blog-service UP
```

建议截图：

```text
Grafana Dashboard 中 gateway-portal DOWN
```

### 6.4 观察 Grafana Alert Rule

进入：

```text
Grafana -> Alerting -> Alert rules -> CloudOpsServiceDown
```

预期：

```text
Firing
```

建议截图：

```text
CloudOpsServiceDown Firing 状态
```

### 6.5 观察飞书告警

飞书群应收到类似消息：

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

建议截图：

```text
飞书 firing 告警消息
```

### 6.6 恢复服务

```bash
sudo kubectl -n cloud-ops scale deploy/gateway-portal --replicas=1
```

命令作用：

- 恢复 `gateway-portal` 副本数。

等待 Deployment 恢复：

```bash
sudo kubectl -n cloud-ops rollout status deploy/gateway-portal
```

命令作用：

- 等待 `gateway-portal` 新 Pod 启动完成。

### 6.7 观察恢复通知

等待：

```text
1~2 分钟
```

预期：

- Dashboard 中 `gateway-portal` 恢复为 `UP`。
- Grafana alert rule 恢复为 `Normal`。
- 飞书收到 `resolved` 恢复消息。

飞书恢复消息示例：

```text
Cloud-Ops-Hub 恢复
状态：resolved
规则：CloudOpsServiceDown
服务：gateway-portal
命名空间：cloud-ops
级别：critical
```

建议截图：

```text
飞书 resolved 恢复消息
```

## 7. 排错命令

### 7.1 Grafana Pod 异常

```bash
sudo kubectl -n monitoring get pod -l app.kubernetes.io/instance=grafana -o wide
```

命令作用：

- 查看是否有 `CrashLoopBackOff`、`ImagePullBackOff` 或 `Running 0/1`。

如果 Pod 崩溃：

```bash
sudo kubectl -n monitoring logs <grafana-pod-name> -c grafana --previous --tail=200
```

命令作用：

- 查看上一轮崩溃日志。
- 重点查 alerting provisioning、contact point、template、payload 相关错误。

### 7.2 飞书收不到消息

```bash
sudo kubectl -n monitoring logs deploy/grafana -c grafana --since=10m \
  | grep -Ei "webhook|notification|contact|failed|400|feishu|alert|response|body|error"
```

命令作用：

- 查看 Grafana 发送飞书 webhook 是否失败。
- 如果看到 `400 Bad Request`，优先检查飞书 webhook、关键词、签名校验和 payload 格式。

### 7.3 告警没有触发

查询 Prometheus：

```promql
up{namespace="cloud-ops", service=~"gateway-portal|blog-service"}
```

排查方向：

- 如果 `gateway-portal` 仍然是 `1`，说明服务没有真正停止或 Prometheus 还没刷新。
- 如果没有任何数据，说明 Prometheus scrape job 或 label 配置有问题。
- 如果 Dashboard 显示 DOWN 但 Alert rule 不 Firing，检查 Grafana Alert rule 的数据源和表达式。

### 7.4 Dashboard 丢失

如果访问：

```text
http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
```

显示 `Dashboard not found`，通常是 Helm upgrade 时漏了 `--set-file`。

重新部署：

```bash
helm upgrade --install grafana grafana/grafana \
  -n monitoring \
  -f infra/helm/grafana/values-dev.yaml \
  --set-file dashboards.default.cloud-ops-overview.json=infra/helm/grafana/dashboards/cloud-ops-overview.json \
  --version 8.15.0
```

命令作用：

- 重新注入 Dashboard JSON。
- 同时保留告警配置。

## 8. 演示收尾

演示结束后务必恢复服务：

```bash
sudo kubectl -n cloud-ops scale deploy/gateway-portal --replicas=1
sudo kubectl -n cloud-ops rollout status deploy/gateway-portal
```

确认服务恢复：

```bash
sudo kubectl -n cloud-ops get pod -l app=gateway-portal
```

确认 Grafana Dashboard：

```text
gateway-portal UP
blog-service UP
```

## 9. 建议保留的项目素材

建议保留以下截图，用于简历、答辩或面试讲解：

- Grafana Dashboard 正常状态。
- Grafana Dashboard 中 `gateway-portal DOWN`。
- Grafana Alert rule `CloudOpsServiceDown` 处于 `Firing`。
- 飞书 `Cloud-Ops-Hub 告警` 消息。
- 飞书 `Cloud-Ops-Hub 恢复` 消息。

讲解时可以总结为：

```text
我在 K3s 上完成了 Prometheus + Loki + Grafana 的可观测性闭环。
指标通过 Prometheus 抓取，日志通过 Loki 查询，Dashboard 用 Grafana 展示。
告警使用 Grafana Unified Alerting，不额外引入 Alertmanager。
服务不可用时，Grafana 根据 up 指标触发告警，并通过自定义 webhook payload 适配飞书机器人通知。
```
