# K8s Manifests (Kustomize)

本目录用于集中管理 Cloud-Ops-Hub 的 K8s 部署清单，采用 **Kustomize** 组织：

- `base/`：通用资源（不含环境差异）
- `overlays/dev/`：开发环境覆盖（NodePort、调试参数等）
- `overlays/prod/`：生产环境覆盖（Ingress/TLS、资源更严格等，后续补齐）

## 快速使用（dev）

```bash
kubectl apply -k infra/k8s/overlays/dev
```

## 约定

- Secret **不入库**：仅提供 `*-secret.example.yaml`
- ConfigMap **可入库**：非敏感配置统一在这里版本化
- 命名空间统一：`cloud-ops`

