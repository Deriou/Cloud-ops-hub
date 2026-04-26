# L3：Jenkins-on-K3s 实操手册（手动版）

## 学习目标

你将手动实现：

- Jenkins 在 K3s 中运行
- 声明式 Pipeline 构建后端与前端
- 动态 Agent Pod 执行任务并自动销毁
- 路径感知构建（仅构建变更模块）

## 前置条件

- L1、L2 已完成
- 仓库代码可正常 `mvn test` 与 `npm run build`
- ACR 镜像仓库与 `acr-secret` 已可用
- 当前部署清单使用 `infra/k8s/base/`

当前发布纪律：

- 镜像 tag 不能只在 ECS 临时修改。
- 每次更新镜像 tag，都必须同步修改对应 `deployment.yaml` 并提交到 Git。
- ECS 发布时只执行 `git pull`、镜像构建/推送和 `kubectl apply`。

## Step 1：部署 Jenkins（最小可用）

部署重点：

- 持久化 Jenkins Home（PVC）
- 限制资源（8G 单机场景必须）
- 暴露方式（Ingress 或 NodePort）

建议初始资源：

- request `300Mi`, limit `1024Mi`

验收标准：

- Jenkins UI 可访问
- 可安装基础插件并保存配置

## Step 2：配置 K8s Cloud 与 Agent 模板

在 Jenkins 配置：

- Kubernetes Cloud
- Pod Template（maven/node 两类可先合并一类）

目标：

- 每次任务启动临时 Pod
- 任务结束后 Pod 自动销毁

验收标准：

- 执行一次 Pipeline 后，Agent Pod 被回收

## Step 3：创建最小 Pipeline

先做最小三阶段：

1. Checkout
2. Test / Build
3. Docker build / push ACR
4. Apply K8s manifests
5. Rollout 验证

验收标准：

- Pipeline 可完整跑通
- 失败时日志可定位原因

## Step 4：实现路径感知构建

思路：

- 读取 `git diff --name-only`
- 按路径决定是否执行某阶段
  - `apps/gateway-portal/**` -> gateway 构建
  - `apps/blog-service/**` -> blog 构建
  - `web/**` -> web 构建
  - `common/**` 或根 `pom.xml` -> 后端相关测试/构建
  - `infra/k8s/**` -> 只执行部署验证或 apply

验收标准：

- 修改单模块时，不触发其他模块构建

## Step 5：增加镜像构建与推送

要求：

- Multi-stage Dockerfile
- `linux/amd64` 目标
- 健康检查
- 镜像推送到 ACR：`crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub`
- Jenkins 凭据中保存 ACR 登录信息，不写入仓库

验收标准：

- 镜像可构建、可推送、可部署

## Step 6：部署与回滚

第一版建议从 web 模块开始：

1. 构建 `web:<递增版本>`
2. 推送到 ACR
3. 确认仓库中的 `infra/k8s/base/web/deployment.yaml` 已经指向同一 tag
4. 执行 `kubectl apply -k infra/k8s/base/`
5. 执行 `kubectl -n cloud-ops rollout status deploy/web`

验收标准：

- 线上 `/ops/cluster` 页面出现本次改动
- Grafana 在发布时间窗能看到 web Pod 重建期间的服务变化
- 失败时可通过 `kubectl rollout undo` 或回退 deployment tag 恢复

## Step 7：发布回写与可观测联动

将 Pipeline 运行结果回写到 Ops-Core（后续阶段）：

- 成功/失败状态
- 构建编号
- 耗时

并在 Grafana 上能看到构建期的服务变化。

## 8G 单机优化建议

- Jenkins 空闲时降低并发执行器
- 禁止长期驻留大体量 Agent
- 任务日志与构建产物设置清理策略
- 插件只保留最小集合，避免内存膨胀

## 学习验收清单

- 能手动触发并跑完 Pipeline
- 能证明 Agent Pod 自动销毁
- 能看到路径感知策略生效
- 能把一次发布与监控图表对应起来
