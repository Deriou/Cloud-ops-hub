# Cloud-Ops-Hub 学习路径（手动实操版）

本目录用于指导你以“手动操作优先”的方式学习 Cloud-Native 运维开发。

建议执行顺序：

1. `L0-local-vs-ecs-decision.md`
2. `L1-k3s-deploy-handbook.md`
3. `L2-observability-plg-handbook.md`
4. `L3-jenkins-on-k3s-handbook.md`

学习原则：

- 每一步都先手动执行，再考虑自动化
- 每一步都有“验收标准”，未通过不进入下一步
- 先跑通最小闭环，再做优化

/home/deriou/                     # 运维用户 home（日常操作都在这）
  projects/
    Cloud-ops-hub/                # git clone 的仓库（代码 + 部署清单）

/opt/cloud-ops/                   # 运行时数据（不随仓库走的东西）
  mysql-data/                     # MySQL 持久化数据
  jenkins-home/                   # Jenkins 数据（后续）
  loki-data/                      # Loki 日志存储（后续）

注意事项
日常操作用 deriou，需要系统权限时 sudo
代码/清单只在 ~/projects/Cloud-ops-hub/ 里改，改完 git pull/push 同步
运行时数据放 /opt/cloud-ops/，K8s PV/PVC 挂载指向这里
不要在服务器上直接编辑仓库代码（除非紧急热修），保持"本地开发 -> push -> 服务器 pull -> apply"的流程
kubectl 权限：确保 deriou 能访问 kubeconfig：
sudo cp /etc/rancher/k3s/k3s.yaml /home/deriou/.kube/config
sudo chown deriou:deriou /home/deriou/.kube/config
验证：

kubectl get nodes
一句话总结
代码在 ~/projects/Cloud-ops-hub/
数据在 /opt/cloud-ops/
操作用 deriou
系统级用 sudo
/root/ 保持干净