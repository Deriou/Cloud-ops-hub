# L1：K3s 部署实操手册（手动版）

## 学习目标

你将手动完成以下能力：

- 在 8G ECS 上安装并运行 K3s
- 部署 Cloud-Ops-Hub 的基础服务（gateway/blog/web）
- 使用 Ingress 暴露访问入口
- 理解 ConfigMap、Secret、Deployment、Service 的最小用法

## 前置条件

- ECS: Ubuntu/CentOS 均可（以下命令偏 Ubuntu）
- 已安装 Docker（可选，用于本地构建镜像）
- 已有仓库代码

## Step 1：安装 K3s

```bash
curl -sfL https://get.k3s.io | sh -
sudo kubectl get nodes
```

验收标准：

- `kubectl get nodes` 显示 `Ready`

## Step 2：准备命名空间与基础目录

```bash
kubectl create namespace cloud-ops
mkdir -p ~/cloud-ops-manifests
```

验收标准：

- `kubectl get ns cloud-ops` 可见

## Step 3：创建配置与密钥

手动创建 Secret（示例）：

```bash
kubectl -n cloud-ops create secret generic cloud-ops-secret \
  --from-literal=OPS_AUTH_MASTER_KEY=dev-master-key \
  --from-literal=BLOG_DATASOURCE_PASSWORD=replace_me
```

手动创建 ConfigMap（可写成 yaml）：

- blog 数据源 URL
- 服务端口
- 运行模式参数

验收标准：

- `kubectl -n cloud-ops get secret,configmap`

## Step 4：部署 MySQL（学习阶段先单实例）

建议先部署一个最小 MySQL（StatefulSet 或 Deployment + PVC）。

关键点：

- 数据库名：`db_blog`
- 初始化表结构：使用 `apps/blog-service/src/test/resources/schema.sql`
- 搜索索引：`apps/blog-service/src/main/resources/db/mysql/search-schema.sql`

验收标准：

- blog-service 能连通 MySQL
- 基础 CRUD 正常

## Step 5：部署 gateway/blog/web

每个服务都手动写 Deployment + Service，至少包含：

- `resources.requests/limits`
- `readinessProbe` / `livenessProbe`
- 环境变量注入（ConfigMap + Secret）

推荐初始资源（8G 单机）：

- gateway: request `200Mi`, limit `512Mi`
- blog: request `300Mi`, limit `768Mi`
- web: request `100Mi`, limit `256Mi`

验收标准：

- `kubectl -n cloud-ops get pod` 全部 `Running`
- `kubectl -n cloud-ops get svc` 可见服务

## Step 6：配置 Ingress（无需等域名）

K3s 默认包含 Traefik。你可：

- 先用 Traefik Ingress 验证路由
- 或后续替换为 Nginx Ingress（建议 L2 前完成）

域名未下发时：

- 用 `curl http://<ECS_IP>/...` 验证
- 或本地 `hosts` 指向 ECS IP

验收标准：

- 前端可访问
- gateway API 可访问
- blog API 可访问

## Step 7：最小回归检查

按顺序手测：

1. `GET /api/v1/gateway/apps`
2. `POST /api/v1/gateway/guest-tokens`（带 Master Key）
3. blog 标签/分类创建
4. blog 文章创建、详情、搜索

验收标准：

- A/B/C 阶段接口在 K3s 环境可复现通过

## 常见故障排查

- Pod 启动失败：`kubectl describe pod <pod>`
- 日志查看：`kubectl logs -f <pod>`
- 探针失败：检查 Actuator 与端口映射
- 内存不足：优先下调并发与缓存大小，避免一次部署过多组件

