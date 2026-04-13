# Deployment Playbook

本文档用于 Cloud-Ops-Hub 在 ECS + K3s 环境的日常发布操作，重点说明“代码提交后如何让页面真正生效”。

## 1. 环境约定

- 代码仓库：`~/projects/Cloud-ops-hub`
- K8s 命名空间：`cloud-ops`
- 私有镜像仓库（ACR）：`crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub`
- 部署方式：Kustomize（`kubectl apply -k infra/k8s/base/`）

## 2. 发布流程总览

```text
本地改代码 -> git commit/push -> ECS git pull
-> docker build (新镜像) -> docker push (ACR)
-> 更新 K8s deployment 镜像tag -> kubectl apply
-> kubectl get pods / logs 验证 -> 线上访问验证
```

## 3. 本地提交与推送

### 3.1 查看改动

```bash
git status
git diff
```

### 3.2 提交代码

```bash
git add -A
git commit -m "feat(web): your change summary"
git push
```

建议 commit 规范：`<type>(<scope>): <subject>`，如：

- `feat(web): ...`
- `fix(infra): ...`
- `docs(learning): ...`

## 4. ECS 上拉取最新代码

```bash
cd ~/projects/Cloud-ops-hub
git pull
```

## 5. 如何让“前端页面生效”

重点：**仅 git push 不会自动让线上页面更新**。

因为前端是容器镜像运行，页面是否生效取决于：

1. 是否构建了新镜像
2. 是否推送到 ACR
3. K8s Deployment 是否切到新 tag
4. Pod 是否重建成功

### 5.1 构建并推送 web 镜像

```bash
# 构建
docker build --platform linux/amd64 \
  -t crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/web:0.0.3 \
  web/

# 推送
docker push crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/web:0.0.3
```

### 5.2 更新 Deployment 镜像 tag

编辑文件：`infra/k8s/base/web/deployment.yaml`

```yaml
containers:
  - name: web
    image: crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/web:0.0.3
```

### 5.3 应用清单

```bash
kubectl apply -k infra/k8s/base/
```

### 5.4 验证

```bash
kubectl -n cloud-ops get pod
kubectl -n cloud-ops rollout status deploy/web
kubectl -n cloud-ops logs deploy/web --tail=50
```

浏览器强刷（避免缓存）：

- Mac: `Cmd + Shift + R`
- Windows: `Ctrl + F5`

## 6. 后端服务发布（gateway/blog）

### 6.1 构建镜像

```bash
# gateway-portal
docker build --platform linux/amd64 \
  -f apps/gateway-portal/Dockerfile \
  -t crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/gateway-portal:0.0.3 .

# blog-service
docker build --platform linux/amd64 \
  -f apps/blog-service/Dockerfile \
  -t crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/blog-service:0.0.3 .
```

### 6.2 推送镜像

```bash
docker push crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/gateway-portal:0.0.3
docker push crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/blog-service:0.0.3
```

### 6.3 更新 deployment.yaml 中的镜像 tag 并 apply

```bash
kubectl apply -k infra/k8s/base/
```

## 7. K8s 常用验证命令

```bash
# 查看服务状态
kubectl -n cloud-ops get pod,svc,ingress

# 观察滚动发布
kubectl -n cloud-ops get pod -w

# 查看单个服务日志
kubectl -n cloud-ops logs deploy/gateway-portal --tail=100
kubectl -n cloud-ops logs deploy/blog-service --tail=100

# 查看上一次崩溃日志
kubectl -n cloud-ops logs deploy/blog-service --tail=100 --previous
```

## 8. 线上访问验证

```bash
curl http://deriou.com/
curl http://deriou.com/api/v1/gateway/apps
curl http://deriou.com/api/v1/blog/posts
```

## 9. 典型故障与处理

### 9.1 镜像拉取失败（ImagePullBackOff）

- 检查镜像是否已推送成功
- 检查 tag 是否一致
- 检查 `imagePullSecrets` 是否存在：

```bash
kubectl -n cloud-ops get secret acr-secret
```

### 9.2 服务启动失败（CrashLoopBackOff）

- 先看 `logs --previous`
- 再看 `describe pod` 事件
- 常见原因：配置缺失、数据库连接失败、DNS 解析失败

### 9.3 域名可解析但访问拒绝

- 检查 ECS 安全组 80/443
- 检查 Traefik Pod 是否 Running
- 检查 Ingress 是否存在

## 10. 发布检查清单（建议每次发布前后都执行）

### 发布前

- [ ] 本地变更已 commit/push
- [ ] 镜像 tag 递增（避免混淆）
- [ ] 目标镜像已 push 到 ACR

### 发布后

- [ ] `kubectl -n cloud-ops get pod` 全部 Running
- [ ] 网关、博客、前端可访问
- [ ] 关键 API 可返回 200
- [ ] 日志无连续报错

## 11. 推荐习惯

1. 一次发布只改一个主题（便于回滚）
2. 镜像 tag 使用递增版本（如 `0.0.3`）
3. 不使用 `latest` 作为生产部署 tag
4. 每次发布后记录“变更内容 + 验证结果”
5. 先手动跑通，再做 Jenkins 自动化
