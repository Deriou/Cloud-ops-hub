# L0：本地还是 8G ECS 先做

## 结论（你的当前情况）

你现在可以直接在 `8G ECS` 上开始做，不需要等待域名审核完成。

原因：

- K3s、Ingress、Jenkins、Prometheus、Loki、Grafana 都可以先用 IP 访问
- 域名和 HTTPS 是后置增强，不是学习阻塞项
- 你的目标是运维学习，ECS 环境更接近真实生产约束（8G 单机）

## 推荐策略

采用“双环境分工”最稳：

- 本地（macOS/Windows）做代码开发、单测、前端联调
- ECS 做 K3s 部署、监控、CI/CD、资源调优

## 域名未就绪的替代方案

1. 直接用 ECS 公网 IP + NodePort（最简单）
2. 用 Ingress + `/etc/hosts` 做本地域名映射
3. 临时使用 `nip.io` / `sslip.io`（如 `grafana.<ip>.nip.io`）

## 当前阶段的最小资源预算（8G）

建议先部署：

- `gateway-portal`、`blog-service`、`web`
- `prometheus`、`loki`、`grafana`
- `jenkins`（后置到 L3）

避免一次性上太多组件，先确保稳定。

## 什么时候再引入正式域名/TLS

- 你完成 L1 与 L2 并稳定运行 3~5 天后
- 再接入证书（如 cert-manager）和域名路由

