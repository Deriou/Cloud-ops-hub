# Web 前端更新 AI 操作手册

本文已精简为入口文档。

统一 CI/CD 日常操作请查看：

```text
docs/cicd/CICD_OPERATION_RUNBOOK.md
```

项目完成度评估请查看：

```text
docs/cicd/PROJECT_COMPLETION_ASSESSMENT.md
```

后端 gateway/blog 首次接入过程请查看：

```text
docs/cicd/GATEWAY_BLOG_PIPELINE_DEPLOYMENT_GUIDE.md
```

## Web 更新最小流程

```bash
git pull origin main
```

作用：同步 Jenkins 自动回写的 Deployment image tag。

```bash
cd web
npm run build
cd ..
```

作用：验证前端构建。

```bash
git add web
git commit -m "feat(web): update frontend"
git push origin main
```

作用：提交前端变更。

发布：

```text
Jenkins -> cloud-ops-web-pipeline -> Build Now
```

发布后：

```bash
git pull origin main
curl -I http://deriou.com/
curl -I http://deriou.com/ops/cluster
```

作用：同步 Jenkins 回写并验证页面。

## Web 发布禁止事项

- 不要手动改 `infra/k8s/base/web/deployment.yaml` 的镜像 tag。
- 不要用 `kubectl set image` 替代 Jenkins 发布。
- 不要把 GitHub token、ACR 密码、Ops Key 写入仓库。
- 不要打开 Webhook 或把 Jenkins 暴露到公网。
