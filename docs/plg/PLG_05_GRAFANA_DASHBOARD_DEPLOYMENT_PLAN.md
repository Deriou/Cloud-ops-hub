# PLG-05 Grafana 看板与公网只读部署操作计划

## 1. 本阶段目标

本阶段目标是在当前已经跑通的 Prometheus 与 Loki + Promtail 基础上，部署 Grafana，并把它作为项目的可观测性展示层。

当前已经完成：

- `gateway-portal`、`blog-service` 已暴露 `/actuator/prometheus`
- Prometheus 已抓取两个业务服务指标
- Loki 已部署成功
- Promtail 已采集 `cloud-ops` namespace 的业务日志
- 已验证能通过 `traceId=demo-loki-003` 查询到业务请求日志

本阶段要完成：

- 部署 Grafana
- 接入 Prometheus 数据源
- 接入 Loki 数据源
- 预置最小运维看板
- 通过公网匿名只读方式展示 Dashboard
- 前端门户只放 Grafana Dashboard 与 Prometheus Targets 链接

本阶段不做：

- 不在前端重做 Grafana 图表
- 不公开 Grafana Explore 入口
- 不把匿名用户配置成 Editor 或 Admin
- 不先追求复杂告警通知渠道

## 2. 展示架构

推荐最终访问结构：

```text
http://deriou.com/ops/cluster        -> 前端运维门户摘要页
http://grafana.deriou.com            -> Grafana 匿名只读 Dashboard
```

原因：

- `deriou.com` 继续承载当前 Vue 前端门户。
- `grafana.deriou.com` 单独承载 Grafana，避免 `/grafana` 子路径带来的静态资源、跳转、Cookie path、`root_url` 问题。
- 面试展示时更清晰：前端门户负责“入口与说明”，Grafana 负责“专业观测面板”。

集群内调用关系：

```text
Grafana -> Prometheus -> gateway-portal / blog-service metrics
Grafana -> Loki -> Promtail -> cloud-ops container logs
```

## 3. 关键设计约定

### 3.1 namespace

Grafana 继续部署到：

```text
monitoring
```

原因：

- Prometheus、Loki、Promtail 已经在 `monitoring`。
- 观测组件统一放在一个 namespace，便于排查与回滚。

### 3.2 公网访问策略

Grafana 公网打开，但只公开 Dashboard：

- 匿名用户角色：`Viewer`
- 不提供匿名编辑权限
- 不把 Explore 作为前端入口
- 管理员账号保留，用于后续调试数据源、看板和告警

注意：

- Grafana 的 `Viewer` 仍然可能看到 Dashboard 中展示出来的数据。
- 不要在 Dashboard 中展示敏感日志正文、密钥、Token、请求体。
- Loki 日志查询面板只展示错误趋势与有限示例，不做“无限制日志检索页面”。

### 3.3 镜像与架构

当前 ECS 是：

```text
linux/amd64
```

Grafana 镜像也必须同步 `linux/amd64` 版本到 ACR。

推荐新增镜像：

```text
grafana/grafana-oss:11.6.3
```

同步到 ACR 后建议使用项目统一 tag 风格：

```text
crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/grafana-oss:11.6.3-amd64
```

如果镜像架构错误，Pod 通常会报：

```text
exec format error
```

### 3.4 数据源地址

Grafana 在 `monitoring` namespace 内访问 Prometheus 和 Loki。

Prometheus 数据源 URL 推荐：

```text
http://prometheus-server.monitoring.svc.cluster.local
```

Loki 数据源 URL 推荐：

```text
http://loki-gateway.monitoring.svc.cluster.local
```

如果实际 Service 名称不同，以集群查询结果为准。

### 3.5 配置即代码

本项目建议把这些内容都放进仓库：

- Grafana Helm values
- Prometheus datasource provisioning
- Loki datasource provisioning
- Dashboard JSON
- 后续 alert rules

不要只在 Grafana UI 里手动点配置。原因：

- Pod 重建后配置容易丢。
- 面试时可以展示“可观测性配置也纳入版本管理”。
- 后续迁移、复现和回滚更简单。

## 4. 建议新增仓库文件

建议新增：

```text
infra/helm/grafana/values-dev.yaml
infra/helm/grafana/dashboards/cloud-ops-overview.json
```

可选新增：

```text
docs/plg/PLG_06_GRAFANA_ALERTING_PLAN.md
```

前端后续可改：

```text
web/src/views/OpsClusterView.vue
web/src/lib/config.ts
web/src/env.d.ts
web/.env.example
```

前端改造原则：

- 只放入口卡片和说明。
- 不重新实现 Grafana 图表。
- 不把 Loki Explore 暴露给匿名访客。

## 5. Grafana values 关键配置建议

`infra/helm/grafana/values-dev.yaml` 第一版建议包含这些能力：

```yaml
image:
  registry: crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com
  repository: cloud-ops-hub/grafana-oss
  tag: 11.6.3-amd64
  pullPolicy: IfNotPresent

imagePullSecrets:
  - name: acr-secret

service:
  type: ClusterIP

persistence:
  enabled: false

initChownData:
  enabled: false

admin:
  existingSecret: grafana-admin-secret
  userKey: admin-user
  passwordKey: admin-password

grafana.ini:
  server:
    domain: grafana.deriou.com
    root_url: http://grafana.deriou.com/
  auth.anonymous:
    enabled: true
    org_role: Viewer
  users:
    viewers_can_edit: false
  dashboards:
    default_home_dashboard_path: /var/lib/grafana/dashboards/default/cloud-ops-overview.json

datasources:
  datasources.yaml:
    apiVersion: 1
    datasources:
      - name: Prometheus
        type: prometheus
        access: proxy
        url: http://prometheus-server.monitoring.svc.cluster.local
        isDefault: true
      - name: Loki
        type: loki
        access: proxy
        url: http://loki-gateway.monitoring.svc.cluster.local

dashboardProviders:
  dashboardproviders.yaml:
    apiVersion: 1
    providers:
      - name: default
        orgId: 1
        folder: Cloud Ops
        type: file
        disableDeletion: false
        editable: true
        options:
          path: /var/lib/grafana/dashboards/default

dashboards: {}

testFramework:
  enabled: false
```

说明：

- `persistence.enabled=false` 适合当前学习阶段，但 Dashboard 和 Datasource 必须入仓。
- `initChownData.enabled=false` 用于关闭 chart 默认的权限修复 init container，避免额外拉取公网 `busybox` 镜像。
- `testFramework.enabled=false` 用于避免 Helm chart 渲染额外测试镜像。
- 管理员账号密码不提交到仓库，使用 `grafana-admin-secret` 注入。
- Dashboard JSON 独立放在 `infra/helm/grafana/dashboards/cloud-ops-overview.json`，部署时通过 `--set-file` 注入。

## 6. 最小 Dashboard 内容

Dashboard 名称建议：

```text
Cloud Ops Overview
```

Dashboard UID 建议固定：

```text
cloud-ops-overview
```

这样前端可以固定链接到：

```text
http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
```

### 6.1 服务健康

PromQL：

```promql
up{namespace="cloud-ops"}
```

用途：

- 展示 `gateway-portal` 与 `blog-service` 是否可被 Prometheus 抓取。
- 面试时可以说明 `up=1` 代表 Prometheus 抓取目标正常。

### 6.2 请求量

PromQL：

```promql
sum by (service) (
  rate(http_server_requests_seconds_count{namespace="cloud-ops", uri!~"/actuator.*"}[5m])
)
```

用途：

- 展示业务接口近 5 分钟 QPS。
- 过滤 `/actuator/**`，避免健康检查和监控抓取污染业务流量。

### 6.3 5xx 错误率

PromQL：

```promql
sum by (service) (
  rate(http_server_requests_seconds_count{namespace="cloud-ops", status=~"5..", uri!~"/actuator.*"}[5m])
)
/
clamp_min(
  sum by (service) (
    rate(http_server_requests_seconds_count{namespace="cloud-ops", uri!~"/actuator.*"}[5m])
  ),
  0.001
)
```

用途：

- 展示每个服务的 5xx 占比。
- 避免只看请求量而忽略错误比例。

### 6.4 JVM 内存

PromQL：

```promql
sum by (service, area) (
  jvm_memory_used_bytes{namespace="cloud-ops"}
)
```

用途：

- 展示 JVM heap / non-heap 内存使用趋势。
- 后续告警可以基于 used/max 比例扩展。

如果需要展示使用率，可补充：

```promql
sum by (service, area) (
  jvm_memory_used_bytes{namespace="cloud-ops"}
)
/
sum by (service, area) (
  jvm_memory_max_bytes{namespace="cloud-ops"} > 0
)
```

### 6.5 错误日志趋势

LogQL：

```logql
sum by (app) (
  count_over_time({namespace="cloud-ops"} |= "event=http_request" |= "status=5" [5m])
)
```

用途：

- 展示业务访问日志中 5xx 错误趋势。
- 让 Grafana 同时体现 Prometheus 指标和 Loki 日志价值。

### 6.6 traceId 排障示例

LogQL：

```logql
{namespace="cloud-ops"} |= "traceId=demo-loki-003"
```

用途：

- 面试展示“业务请求 -> 响应 traceId -> 后端日志 -> Loki 查询”的闭环。

注意：

- `traceId` 不要做 Loki label。
- `traceId` 属于高基数字段，用文本过滤查询即可。

## 7. 前置准备

### 7.1 准备 DNS

在域名解析控制台新增：

```text
grafana.deriou.com -> ECS 公网 IP
```

命令检查：

```bash
nslookup grafana.deriou.com
```

命令用途：

- 检查本机是否能解析 `grafana.deriou.com`。
- 验证 DNS 是否已经指向 ECS 公网 IP。

如果本机没有 `nslookup`，可以用：

```bash
dig grafana.deriou.com
```

命令用途：

- 查看 `grafana.deriou.com` 的 DNS 解析结果。
- 排查域名未生效或解析到错误 IP 的问题。

### 7.2 确认 monitoring namespace

```bash
kubectl get ns monitoring
```

命令用途：

- 确认监控组件所在 namespace 已存在。
- 如果 namespace 不存在，Grafana Helm 部署会失败。

如果不存在，创建它：

```bash
kubectl create namespace monitoring
```

命令用途：

- 创建 `monitoring` namespace，用于部署 Grafana。

### 7.3 确认 ACR Secret

```bash
kubectl -n monitoring get secret acr-secret
```

命令用途：

- 确认 `monitoring` namespace 中存在拉取 ACR 私有镜像所需的 `acr-secret`。
- 如果缺少该 Secret，Grafana Pod 会拉取镜像失败。

如果没有，需要从 `cloud-ops` namespace 复制或重新创建。

### 7.4 创建 Grafana 管理员 Secret

```bash
kubectl -n monitoring create secret generic grafana-admin-secret \
  --from-literal=admin-user=admin \
  --from-literal=admin-password='<替换成强密码>'
```

命令用途：

- 创建 Grafana 管理员账号 Secret。
- 避免把管理员密码提交到 Git 仓库。
- 让 Helm chart 通过 `admin.existingSecret` 读取管理员账号。

如果 Secret 已存在，需要先确认是否复用：

```bash
kubectl -n monitoring get secret grafana-admin-secret
```

命令用途：

- 检查 `grafana-admin-secret` 是否已经存在。
- 避免重复创建时报错。

### 7.5 确认 Prometheus Service

```bash
kubectl -n monitoring get svc | grep prometheus
```

命令用途：

- 查看 Prometheus 在集群内的 Service 名称。
- 确认 Grafana datasource 中的 Prometheus URL 是否正确。

预期重点关注：

```text
prometheus-server
```

如果实际名称不是 `prometheus-server`，需要调整 Grafana datasource URL。

### 7.6 确认 Loki Service

```bash
kubectl -n monitoring get svc | grep loki
```

命令用途：

- 查看 Loki 与 Loki Gateway 在集群内的 Service 名称。
- 确认 Grafana datasource 中的 Loki URL 是否正确。

预期重点关注：

```text
loki-gateway
```

如果实际名称不是 `loki-gateway`，需要调整 Grafana datasource URL。

## 8. 镜像准备

### 8.1 拉取 Grafana amd64 镜像

在能访问公网 Docker Hub 的机器执行：

```bash
docker pull --platform linux/amd64 grafana/grafana-oss:11.6.3
```

命令用途：

- 拉取 `linux/amd64` 架构的 Grafana OSS 镜像。
- 避免在 ECS 上出现 `exec format error`。

### 8.2 标记 ACR 镜像

```bash
docker tag grafana/grafana-oss:11.6.3 \
  crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/grafana-oss:11.6.3-amd64
```

命令用途：

- 给 Grafana 镜像打上项目 ACR 地址标签。
- 让 K3s 集群后续从 ACR 拉取镜像。

### 8.3 推送到 ACR

```bash
docker push crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/grafana-oss:11.6.3-amd64
```

命令用途：

- 把 Grafana 镜像推送到项目 ACR。
- 确保 ECS/K3s 不依赖公网 Docker Hub 拉取镜像。

## 9. Helm Chart 准备

### 9.1 添加 Grafana Helm repo

```bash
helm repo add grafana https://grafana.github.io/helm-charts
```

命令用途：

- 添加 Grafana 官方 Helm Chart 仓库。
- 让本机 Helm 可以安装 `grafana/grafana` chart。

### 9.2 更新 Helm repo

```bash
helm repo update
```

命令用途：

- 更新本机 Helm chart 索引。
- 确保后续能获取到最新的 Grafana chart 元数据。

### 9.3 查看可用 chart 版本

```bash
helm search repo grafana/grafana --versions | head
```

命令用途：

- 查看当前可用的 Grafana Helm chart 版本。
- 便于固定 chart 版本，避免以后 chart 默认行为变化。

建议：

- 部署命令中使用 `--version 8.15.0` 固定 chart 版本。
- 文档中记录 Grafana 镜像版本 `11.6.3` 和 Helm chart 版本 `8.15.0`。

## 10. 预渲染检查

在真正部署前，先执行：

```bash
helm template grafana grafana/grafana \
  -n monitoring \
  -f infra/helm/grafana/values-dev.yaml \
  --set-file dashboards.default.cloud-ops-overview.json=infra/helm/grafana/dashboards/cloud-ops-overview.json \
  --version 8.15.0 \
  > /tmp/grafana-rendered.yaml
```

命令用途：

- 在本地渲染 Grafana Kubernetes YAML。
- 提前检查 Helm values 是否能正常生成资源。
- 避免直接部署后才发现配置错误。

检查是否仍有公网镜像：

```bash
grep -n "image:" /tmp/grafana-rendered.yaml
```

命令用途：

- 查看渲染结果中所有镜像地址。
- 确认是否全部使用 ACR 镜像。

检查是否生成 Ingress：

```bash
grep -n "kind: Ingress" /tmp/grafana-rendered.yaml
```

命令用途：

- 确认 Grafana chart 是否按预期生成 Ingress。
- 如果没有生成，需要检查 `ingress.enabled` 配置。

## 11. 部署 Grafana

```bash
helm upgrade --install grafana grafana/grafana \
  -n monitoring \
  -f infra/helm/grafana/values-dev.yaml \
  --set-file dashboards.default.cloud-ops-overview.json=infra/helm/grafana/dashboards/cloud-ops-overview.json \
  --version 8.15.0
```

命令用途：

- 安装或升级 Grafana Helm release。
- 使用仓库中的 `values-dev.yaml` 作为部署配置。
- 把 Grafana 部署到 `monitoring` namespace。

等待 Pod 就绪：

```bash
kubectl -n monitoring rollout status deploy/grafana
```

命令用途：

- 等待 Grafana Deployment 完成滚动发布。
- 判断 Grafana Pod 是否已经进入可用状态。

查看资源：

```bash
kubectl -n monitoring get pod,svc,ingress | grep grafana
```

命令用途：

- 查看 Grafana Pod、Service、Ingress 是否已经创建。
- 快速确认部署资源是否完整。

查看日志：

```bash
kubectl -n monitoring logs deploy/grafana --tail=100
```

命令用途：

- 查看 Grafana 最近启动日志。
- 排查配置加载失败、datasource provisioning 失败、dashboard provisioning 失败等问题。

## 12. Ingress 配置建议

如果使用 Grafana chart 内置 Ingress，values 中建议配置：

```yaml
ingress:
  enabled: true
  ingressClassName: traefik
  annotations:
    traefik.ingress.kubernetes.io/router.entrypoints: web
  hosts:
    - grafana.deriou.com
  path: /
  pathType: Prefix
```

说明：

- Grafana 在 `monitoring` namespace，因此 Ingress 也建议由 Grafana chart 在 `monitoring` namespace 创建。
- 不建议在 `cloud-ops` namespace 的现有 Ingress 中直接转发到 `monitoring` namespace 的 Service，因为 Kubernetes Ingress backend 不能直接跨 namespace 引用 Service。

验证 Ingress：

```bash
kubectl -n monitoring describe ingress grafana
```

命令用途：

- 查看 Grafana Ingress 的 host、path、backend 是否正确。
- 排查域名访问不到 Grafana 时的 Ingress 配置问题。

## 13. 数据源验证

### 13.1 进入 Grafana UI

浏览器访问：

```text
http://grafana.deriou.com
```

验证目标：

- 匿名用户可以打开 Grafana。
- 默认进入 Dashboard 或 Dashboard 列表。
- 匿名用户不能编辑 Dashboard。

### 13.2 验证 Prometheus 数据源

如果用管理员登录 Grafana，可以在数据源页面测试 Prometheus。

也可以临时 port-forward：

```bash
kubectl -n monitoring port-forward svc/grafana 3000:80
```

命令用途：

- 在本机通过 `http://localhost:3000` 访问 Grafana。
- 当公网域名或 Ingress 未生效时，用于本地验证 Grafana。

在 Grafana 中执行 PromQL：

```promql
up
```

验证目标：

- 能看到 Prometheus 返回数据。
- 能看到 `gateway-portal` 和 `blog-service` 对应 target。

### 13.3 验证 Loki 数据源

在 Grafana 管理员视角中测试 Loki 数据源。

如果需要临时验证 LogQL，可查询：

```logql
{namespace="cloud-ops"}
```

验证目标：

- 能返回 `cloud-ops` namespace 日志。

继续验证 traceId：

```logql
{namespace="cloud-ops"} |= "traceId=demo-loki-003"
```

验证目标：

- 能查到之前已验证过的业务请求日志。
- 说明 Grafana -> Loki -> Promtail -> 业务日志链路完整。

注意：

- 不要用 `/actuator/**` 请求做 traceId 联查验证。
- `/actuator/**` 已做访问日志降噪，不一定写入业务访问日志。

## 14. Dashboard 验证

打开：

```text
http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
```

验证目标：

- 服务健康面板能看到 `gateway-portal`、`blog-service`
- 请求量面板有数据
- 5xx / 错误率面板能显示趋势
- JVM 内存面板能显示趋势
- 错误日志趋势面板能从 Loki 读取日志

如果请求量面板没有数据：

- 先访问真实业务接口制造流量。
- 不要访问 `/actuator/**`。

示例：

```bash
curl -i http://deriou.com/api/v1/blog/tags -H "X-Trace-Id: demo-loki-003"
```

命令用途：

- 调用真实业务接口制造 HTTP 请求指标。
- 使用固定 `X-Trace-Id` 方便后续在 Loki 中查询对应日志。

## 15. 前端门户入口建议

当前页面：

```text
http://deriou.com/ops/cluster
```

建议新增入口卡片：

- `Grafana 看板`
- `Prometheus Targets`
- `日志链路说明`

建议文案：

```text
Grafana 看板：查看服务健康、请求量、错误率、JVM 内存与错误日志趋势。
Prometheus Targets：查看业务服务指标抓取状态与抓取间隔。
日志联查：业务响应 traceId 可用于在 Loki 中定位对应后端日志。
```

建议链接：

```text
Grafana Dashboard: http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
Prometheus Targets: http://<Prometheus 可访问地址>/targets
```

注意：

- 前端不要提供 Grafana Explore 匿名入口。
- 如果 Prometheus 没有公网暴露，前端可以只写“通过 port-forward 访问”说明。

## 16. 第一批告警设计

告警建议放在 Grafana Dashboard 跑通之后再做。

第一批告警建议：

### 16.1 服务不可用

PromQL：

```promql
up{namespace="cloud-ops"} == 0
```

用途：

- 当 Prometheus 抓不到某个业务服务时触发。
- 面试时可以说明这是最基础的服务可用性告警。

### 16.2 5xx 比例升高

PromQL：

```promql
sum by (service) (
  rate(http_server_requests_seconds_count{namespace="cloud-ops", status=~"5..", uri!~"/actuator.*"}[5m])
)
/
clamp_min(
  sum by (service) (
    rate(http_server_requests_seconds_count{namespace="cloud-ops", uri!~"/actuator.*"}[5m])
  ),
  0.001
)
> 0.05
```

用途：

- 当某个服务 5xx 比例超过 5% 时触发。
- 比单纯统计错误数量更适合低流量学习项目。

### 16.3 JVM 内存持续高位

PromQL：

```promql
sum by (service) (
  jvm_memory_used_bytes{namespace="cloud-ops", area="heap"}
)
/
sum by (service) (
  jvm_memory_max_bytes{namespace="cloud-ops", area="heap"} > 0
)
> 0.8
```

用途：

- 当 JVM heap 使用率持续超过 80% 时触发。
- 适合作为 Java 服务资源压力告警。

第一版告警建议：

- 先在 Grafana 内建 Alerting 中跑通。
- Contact point 可以先不接外部通知。
- 后续再接邮件、钉钉或飞书。

## 17. 回滚与卸载

卸载 Grafana：

```bash
helm -n monitoring uninstall grafana
```

命令用途：

- 删除 Grafana Helm release 管理的资源。
- 用于回滚 Grafana 部署。

检查是否删除：

```bash
kubectl -n monitoring get pod,svc,ingress | grep grafana
```

命令用途：

- 确认 Grafana Pod、Service、Ingress 是否已删除。

注意：

- 当前建议 Grafana 不持久化。
- 如果 Dashboard 和 Datasource 已经入仓，重新部署可以恢复配置。
- 不要直接删除 `monitoring` namespace，因为 Prometheus、Loki、Promtail 也在里面。

## 18. 常见问题

### 18.1 Grafana Pod ImagePullBackOff

检查：

```bash
kubectl -n monitoring describe pod -l app.kubernetes.io/name=grafana
```

命令用途：

- 查看 Grafana Pod 事件。
- 排查镜像不存在、ACR Secret 缺失、镜像拉取权限错误等问题。

如果状态是 `Init:ImagePullBackOff`，继续检查 init container：

```bash
kubectl -n monitoring get pod -l app.kubernetes.io/name=grafana \
  -o jsonpath='{range .items[*].spec.initContainers[*]}{.name}{" -> "}{.image}{"\n"}{end}'
```

命令用途：

- 查看 Grafana Pod 渲染出的 init container 镜像。
- 判断是否仍有 `busybox`、`curl` 等公网镜像没有关闭。

处理方向：

- 确认镜像已推送到 ACR。
- 确认 `monitoring` namespace 有 `acr-secret`。
- 确认 values 中镜像地址和 tag 正确。
- 如果 init container 是 `init-chown-data`，确认 `infra/helm/grafana/values-dev.yaml` 中已有 `initChownData.enabled=false`，然后重新执行 `helm upgrade`。

### 18.2 Grafana 打开后跳转地址不对

检查 values：

```yaml
grafana.ini:
  server:
    domain: grafana.deriou.com
    root_url: http://grafana.deriou.com/
```

处理方向：

- 如果使用二级域名，`root_url` 应该是域名根路径。
- 不要同时配置成 `/grafana` 子路径。

### 18.3 Dashboard 没有 Prometheus 数据

检查 Prometheus 是否有数据：

```bash
kubectl -n monitoring port-forward svc/prometheus-server 9090:80
```

命令用途：

- 在本机通过 `http://localhost:9090` 访问 Prometheus。
- 验证 Prometheus 自身是否能查询业务指标。

Prometheus 查询：

```promql
up{namespace="cloud-ops"}
```

处理方向：

- 如果 Prometheus 有数据，检查 Grafana datasource。
- 如果 Prometheus 没数据，回到 Prometheus 阶段排查 targets。

### 18.4 Dashboard 没有 Loki 日志

检查 Loki 链路：

```bash
kubectl -n monitoring logs deploy/grafana --tail=100
```

命令用途：

- 查看 Grafana 是否有 Loki datasource 连接错误。

也可以 port-forward Loki Gateway：

```bash
kubectl -n monitoring port-forward svc/loki-gateway 3100:80
```

命令用途：

- 在本机暴露 Loki Gateway。
- 辅助验证 Grafana 之外的 Loki 查询能力。

处理方向：

- 确认 Loki datasource URL 是 `http://loki-gateway.monitoring.svc.cluster.local`。
- 确认 Promtail 仍在采集 `cloud-ops` namespace 日志。
- 确认使用真实业务接口制造日志，不要只访问 `/actuator/**`。

### 18.5 匿名用户可以编辑 Dashboard

检查：

```yaml
grafana.ini:
  auth.anonymous:
    enabled: true
    org_role: Viewer
  users:
    viewers_can_edit: false
```

处理方向：

- 匿名用户只能是 `Viewer`。
- 不要配置为 `Editor`。

## 19. 本阶段完成标准

满足以下条件，就可以认为 Grafana 展示阶段完成：

- `helm -n monitoring list` 显示 `grafana` 为 `deployed`
- `kubectl -n monitoring get pod` 显示 Grafana Pod 为 `Running`
- `http://grafana.deriou.com` 可访问
- 匿名用户只能查看 Dashboard，不能编辑
- Grafana Prometheus 数据源可用
- Grafana Loki 数据源可用
- Dashboard 能展示服务健康、请求量、5xx / 错误率、JVM 内存
- Dashboard 能展示 Loki 错误日志趋势
- 固定 `traceId=demo-loki-003` 能通过 Loki 查询到业务日志
- 前端 `/ops/cluster` 或后续 `/ops/observability` 提供 Grafana Dashboard 入口

## 20. 下一阶段衔接

Grafana 看板跑通后，再进入告警阶段。

建议顺序：

1. 把 Grafana datasource 和 dashboard 完全入仓。
2. 前端门户增加 Dashboard 入口。
3. 添加 Grafana 内建告警规则。
4. 验证 `up=0`、5xx 比例、JVM 内存三类告警。
5. 再考虑接入邮件、钉钉或飞书通知。

面试展示重点：

- 指标链路：应用指标 -> Prometheus -> Grafana
- 日志链路：应用日志 -> Promtail -> Loki -> Grafana
- 排障闭环：请求 traceId -> Loki 查询日志 -> Prometheus 查看指标
- 工程化：Helm values、datasource、dashboard、alert rule 配置入仓
