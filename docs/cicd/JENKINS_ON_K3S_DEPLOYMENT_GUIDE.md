# Jenkins-on-K3s 部署指南

本文档用于在 ECS + K3s 环境中部署 Jenkins Controller，为后续 Cloud-Ops-Hub CI/CD Pipeline 做准备。

本阶段只完成 Jenkins 基础部署与可访问验证，不立即接入业务构建流水线。

## 1. 部署目标

完成后应具备：

- Jenkins 运行在 K3s 中。
- Jenkins Home 使用 PVC 持久化。
- Jenkins 使用独立 `cicd` namespace。
- Jenkins UI 可访问。
- Jenkins 可安装插件并保留配置。
- Jenkins 具备后续创建动态 Agent Pod 的基础权限。

## 2. 设计选择

| 设计项 | 选择 | 作用 |
| --- | --- | --- |
| 命名空间 | `cicd` | 将 CI/CD 组件与业务应用 `cloud-ops` 隔离，便于权限和资源管理。 |
| Jenkins 形态 | Controller 常驻 | Jenkins Controller 负责 UI、任务配置、凭据、Pipeline 调度。 |
| 构建执行位置 | 后续使用动态 Agent Pod | 避免 Controller 承担 Maven、npm、镜像构建等重任务。 |
| 数据持久化 | PVC 挂载 `/var/jenkins_home` | 保存插件、任务配置、凭据、构建历史，Pod 重建后不丢配置。 |
| 暴露方式 | 第一版建议 NodePort 或 port-forward | 先保证可访问，后续再接 Ingress 和域名。 |
| 权限模型 | ServiceAccount + RBAC | Jenkins 后续需要创建 Agent Pod，并对业务 namespace 执行部署。 |
| 资源限制 | 小规格 request/limit | 适配 8G 单机，避免 Jenkins 空闲时占用过多资源。 |
| 插件策略 | 最小插件集合 | 降低内存占用和维护复杂度。 |
| Secret 管理 | Jenkins Credentials / K8s Secret | Git token、ACR 密码、kubeconfig 不进入仓库。 |

## 3. 目录规划

建议后续将 Jenkins K8s 清单放在：

```text
infra/k8s/cicd/jenkins/
```

建议文件结构：

```text
infra/k8s/cicd/jenkins/
  namespace.yaml
  service-account.yaml
  rbac.yaml
  pvc.yaml
  deployment.yaml
  service.yaml
  kustomization.yaml
```

本文档先给出手动部署命令与资源模板。确认方案稳定后，再把模板落入仓库清单。

## 4. 指令表

所有命令默认在 ECS 项目根目录执行：

```bash
cd ~/projects/Cloud-ops-hub
```

如果你的 kubectl 需要 sudo，就保留 `sudo`；如果已经配置免 sudo，可以去掉 `sudo`。

| 指令 | 作用 | 预期结果 |
| --- | --- | --- |
| `pwd` | 确认当前目录。 | 输出 `~/projects/Cloud-ops-hub`。 |
| `sudo kubectl get nodes` | 检查 K3s 节点状态。 | 节点为 `Ready`。 |
| `sudo kubectl get ns` | 查看 namespace。 | 能看到 `cloud-ops`，后续会新增 `cicd`。 |
| `sudo kubectl create namespace cicd` | 创建 Jenkins 独立命名空间。 | 创建成功或提示已存在。 |
| `sudo kubectl -n cicd get pod` | 查看 Jenkins namespace 内 Pod。 | 初始为空，部署后出现 Jenkins Pod。 |
| `sudo kubectl apply -f <file>` | 应用单个 Kubernetes YAML。 | 资源被创建或更新。 |
| `sudo kubectl apply -k <dir>` | 应用 Kustomize 目录。 | 目录内资源被创建或更新。 |
| `sudo kubectl -n cicd get pvc` | 检查 Jenkins Home 持久卷声明。 | PVC 为 `Bound`。 |
| `sudo kubectl -n cicd get pod -w` | 观察 Jenkins Pod 启动过程。 | Pod 从 `Pending` 到 `Running`。 |
| `sudo kubectl -n cicd logs deploy/jenkins --tail=100` | 查看 Jenkins 启动日志。 | 能看到 Jenkins 初始化日志。 |
| `sudo kubectl -n cicd get svc` | 查看 Jenkins Service。 | 能看到 Jenkins UI 暴露端口。 |
| `sudo kubectl -n cicd port-forward svc/jenkins 8080:8080` | 临时本机转发 Jenkins UI。 | 可通过 `http://127.0.0.1:8080` 访问。 |
| `sudo kubectl -n cicd exec deploy/jenkins -- cat /var/jenkins_home/secrets/initialAdminPassword` | 获取 Jenkins 初始管理员密码。 | 输出一串初始化密码。 |
| `sudo kubectl -n cicd rollout status deploy/jenkins --timeout=180s` | 检查 Jenkins Deployment 是否完成发布。 | 输出 rollout 成功。 |
| `sudo kubectl -n cicd describe pod -l app=jenkins` | 排查 Jenkins Pod 启动失败原因。 | 可看到事件、调度、挂载、拉镜像错误。 |

## 5. 部署前检查

### 5.1 确认 K3s 正常

```bash
sudo kubectl get nodes
sudo kubectl get ns
sudo kubectl -n cloud-ops get pod,svc,ingress
```

要求：

- K3s node 为 `Ready`。
- `cloud-ops` namespace 存在。
- 当前业务服务状态基本正常。

### 5.2 确认默认 StorageClass

Jenkins Home 需要 PVC。先检查是否有默认 StorageClass：

```bash
sudo kubectl get storageclass
```

如果 K3s 默认启用了 `local-path`，通常会看到类似：

```text
local-path (default)
```

如果没有默认 StorageClass，则 Jenkins PVC 可能一直停留在 `Pending`，需要先补 StorageClass 或在 PVC 中指定可用的 `storageClassName`。

### 5.3 确认可拉取 Jenkins 镜像

Jenkins 默认镜像来自公网镜像仓库。国内 ECS 可能出现拉取慢或失败。

第一版可先使用：

```text
jenkins/jenkins:lts-jdk21
```

如果拉取失败，建议将 Jenkins 镜像同步到 ACR，再把 Deployment 中的 image 改为 ACR 地址。

## 6. Kubernetes 资源模板

以下模板可先保存为临时文件验证。确认无误后，再整理到 `infra/k8s/cicd/jenkins/`。

### 6.1 Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: cicd
```

作用：

- 创建独立的 Jenkins 命名空间。
- 避免 Jenkins 资源混入业务 `cloud-ops` namespace。

### 6.2 ServiceAccount

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: jenkins
  namespace: cicd
```

作用：

- Jenkins Pod 使用该身份访问 Kubernetes API。
- 后续 Kubernetes Plugin 创建 Agent Pod 时会依赖这个身份。

### 6.3 RBAC

第一版先给 Jenkins 在 `cicd` namespace 内管理 Agent Pod 的权限：

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: jenkins-agent-manager
  namespace: cicd
rules:
  - apiGroups: [""]
    resources: ["pods", "pods/exec", "pods/log", "services", "events", "secrets", "configmaps", "persistentvolumeclaims"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: jenkins-agent-manager
  namespace: cicd
subjects:
  - kind: ServiceAccount
    name: jenkins
    namespace: cicd
roleRef:
  kind: Role
  name: jenkins-agent-manager
  apiGroup: rbac.authorization.k8s.io
```

作用：

- 允许 Jenkins 在 `cicd` namespace 创建和清理动态 Agent Pod。
- 允许 Jenkins 读取 Pod 日志和事件，便于排查 Agent 启动失败。

后续发布业务应用时，还需要给 Jenkins 增加对 `cloud-ops` namespace 的部署权限。

### 6.4 PVC

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: jenkins-home
  namespace: cicd
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
```

作用：

- 持久化 `/var/jenkins_home`。
- 保存 Jenkins 插件、任务、凭据和构建记录。

说明：

- 10Gi 足够第一版使用。
- 后续要配置构建历史保留策略，避免长期膨胀。

### 6.5 Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jenkins
  namespace: cicd
  labels:
    app: jenkins
spec:
  replicas: 1
  selector:
    matchLabels:
      app: jenkins
  template:
    metadata:
      labels:
        app: jenkins
    spec:
      serviceAccountName: jenkins
      securityContext:
        fsGroup: 1000
      containers:
        - name: jenkins
          image: jenkins/jenkins:lts-jdk21
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
              name: http
            - containerPort: 50000
              name: agent
          env:
            - name: JAVA_OPTS
              value: "-Xms256m -Xmx768m -Djenkins.install.runSetupWizard=true"
          resources:
            requests:
              cpu: 100m
              memory: 300Mi
            limits:
              cpu: 1000m
              memory: 1024Mi
          volumeMounts:
            - name: jenkins-home
              mountPath: /var/jenkins_home
          readinessProbe:
            httpGet:
              path: /login
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 12
          livenessProbe:
            httpGet:
              path: /login
              port: 8080
            initialDelaySeconds: 180
            periodSeconds: 20
            timeoutSeconds: 5
            failureThreshold: 6
      volumes:
        - name: jenkins-home
          persistentVolumeClaim:
            claimName: jenkins-home
```

作用：

- 启动 Jenkins Controller。
- 限制 Jenkins 内存，适配单机环境。
- 挂载 PVC 到 Jenkins Home。
- 暴露 UI 端口 `8080` 和 Agent 通信端口 `50000`。

### 6.6 Service

第一版建议用 NodePort，方便在 ECS 上直接访问：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: jenkins
  namespace: cicd
  labels:
    app: jenkins
spec:
  type: NodePort
  selector:
    app: jenkins
  ports:
    - name: http
      port: 8080
      targetPort: 8080
      nodePort: 30080
    - name: agent
      port: 50000
      targetPort: 50000
```

作用：

- 暴露 Jenkins UI。
- 预留 Jenkins Agent 通信端口。

注意：

- 如果 ECS 安全组未放行 `30080`，公网无法访问。
- 若不想暴露公网，可以先不用 NodePort，改用 `kubectl port-forward`。

## 7. 推荐落地步骤

### Step 1：创建临时部署文件

建议先创建临时目录：

```bash
mkdir -p /tmp/cloud-ops-jenkins
```

作用：

- 先验证部署方案。
- 避免未验证的 YAML 直接进入仓库。

### Step 2：写入资源模板

将第 6 节的 YAML 保存到：

```text
/tmp/cloud-ops-jenkins/namespace.yaml
/tmp/cloud-ops-jenkins/service-account.yaml
/tmp/cloud-ops-jenkins/rbac.yaml
/tmp/cloud-ops-jenkins/pvc.yaml
/tmp/cloud-ops-jenkins/deployment.yaml
/tmp/cloud-ops-jenkins/service.yaml
```

作用：

- 形成可重复执行的部署材料。

### Step 3：应用资源

```bash
sudo kubectl apply -f /tmp/cloud-ops-jenkins/namespace.yaml
sudo kubectl apply -f /tmp/cloud-ops-jenkins/service-account.yaml
sudo kubectl apply -f /tmp/cloud-ops-jenkins/rbac.yaml
sudo kubectl apply -f /tmp/cloud-ops-jenkins/pvc.yaml
sudo kubectl apply -f /tmp/cloud-ops-jenkins/deployment.yaml
sudo kubectl apply -f /tmp/cloud-ops-jenkins/service.yaml
```

作用：

- 依次创建 Jenkins 运行所需资源。
- 分步执行便于定位失败点。

### Step 4：观察 PVC

```bash
sudo kubectl -n cicd get pvc
```

期望：

```text
jenkins-home   Bound
```

如果是 `Pending`，优先检查 StorageClass。

### Step 5：观察 Pod 启动

```bash
sudo kubectl -n cicd get pod -w
```

期望：

```text
jenkins-xxxxx   1/1   Running
```

如果长时间 `Pending`、`ContainerCreating`、`ImagePullBackOff` 或 `CrashLoopBackOff`，使用：

```bash
sudo kubectl -n cicd describe pod -l app=jenkins
sudo kubectl -n cicd logs deploy/jenkins --tail=200
```

### Step 6：确认 rollout

```bash
sudo kubectl -n cicd rollout status deploy/jenkins --timeout=180s
```

作用：

- 确认 Jenkins Deployment 已完成发布。

### Step 7：访问 Jenkins UI

方式一：NodePort

```bash
sudo kubectl -n cicd get svc jenkins
```

如果 `30080` 已暴露，可访问：

```text
http://<ECS公网IP>:30080
```

方式二：port-forward

```bash
sudo kubectl -n cicd port-forward svc/jenkins 8080:8080
```

然后访问：

```text
http://127.0.0.1:8080
```

如果你在本地电脑访问远端 ECS，可通过 SSH 隧道转发。

### Step 8：获取初始密码

```bash
sudo kubectl -n cicd exec deploy/jenkins -- cat /var/jenkins_home/secrets/initialAdminPassword
```

作用：

- 获取首次进入 Jenkins UI 需要的管理员初始化密码。

注意：

- 这个密码不要提交到 Git。
- 初始化完成并创建管理员用户后，不需要再保存该临时密码。

### Step 9：安装插件

第一版建议安装最小集合：

| 插件 | 作用 |
| --- | --- |
| Pipeline | 支持声明式 Pipeline 和 Jenkinsfile。 |
| Git | 从 Git 仓库 checkout 代码。 |
| Credentials Binding | 在 Pipeline 中安全使用凭据。 |
| Kubernetes | 创建动态 Agent Pod。 |
| Timestamper | 给构建日志增加时间戳，方便排障。 |
| Workspace Cleanup | 构建后清理 workspace，减少磁盘占用。 |

可暂缓：

- Blue Ocean：界面友好，但会增加插件体量。
- Docker Pipeline：如果第一版使用 Kaniko，不一定需要。

### Step 10：基础配置

初始化后建议设置：

- Jenkins URL。
- 管理员账号。
- 构建历史保留策略。
- Controller executors 设置为 `0` 或 `1`。
- 系统时区与日志时间。
- Kubernetes Cloud 先不急着配置，下一阶段单独处理。

## 8. 验收清单

完成本阶段后，应满足：

- [ ] `sudo kubectl get ns cicd` 存在。
- [ ] `sudo kubectl -n cicd get pvc` 中 `jenkins-home` 为 `Bound`。
- [ ] `sudo kubectl -n cicd get pod` 中 Jenkins 为 `Running`。
- [ ] `sudo kubectl -n cicd rollout status deploy/jenkins` 成功。
- [ ] Jenkins UI 可访问。
- [ ] 初始管理员账号已创建。
- [ ] 推荐插件已安装。
- [ ] 重启 Jenkins Pod 后配置不丢失。

重启验证：

```bash
sudo kubectl -n cicd delete pod -l app=jenkins
sudo kubectl -n cicd rollout status deploy/jenkins --timeout=180s
```

作用：

- 验证 PVC 是否真正持久化 Jenkins 配置。

## 9. 常见问题

### 9.1 PVC 一直 Pending

检查：

```bash
sudo kubectl get storageclass
sudo kubectl -n cicd describe pvc jenkins-home
```

常见原因：

- 没有默认 StorageClass。
- local-path provisioner 未运行。
- PVC 指定了不存在的 storageClassName。

### 9.2 Jenkins 镜像拉取失败

检查：

```bash
sudo kubectl -n cicd describe pod -l app=jenkins
```

常见原因：

- ECS 无法访问 Docker Hub。
- 镜像拉取超时。
- 网络或 DNS 异常。

应对：

- 将 `jenkins/jenkins:lts-jdk21` 同步到 ACR。
- Deployment 中改用 ACR 镜像地址。
- 如使用私有 ACR，给 `cicd` namespace 创建对应 imagePullSecret。

### 9.3 Jenkins 启动后反复重启

检查：

```bash
sudo kubectl -n cicd logs deploy/jenkins --tail=200
sudo kubectl -n cicd describe pod -l app=jenkins
```

常见原因：

- 内存 limit 太低。
- PVC 挂载权限异常。
- 插件初始化失败。

### 9.4 无法访问 Jenkins UI

检查：

```bash
sudo kubectl -n cicd get svc jenkins
sudo kubectl -n cicd get pod -o wide
sudo kubectl -n cicd logs deploy/jenkins --tail=100
```

常见原因：

- ECS 安全组未放行 NodePort。
- Jenkins Pod 尚未 Ready。
- 访问了错误端口。

临时解决：

```bash
sudo kubectl -n cicd port-forward svc/jenkins 8080:8080
```

### 9.5 初始密码文件不存在

可能原因：

- Jenkins 已经初始化完成。
- PVC 中已有旧数据。

检查：

```bash
sudo kubectl -n cicd logs deploy/jenkins --tail=200
```

如果是旧 Jenkins Home，可用已有管理员账号登录。

## 10. 下一阶段

Jenkins 基础部署完成后，进入下一阶段：

1. 配置 Kubernetes Cloud。
2. 配置动态 Agent Pod Template。
3. 准备 Git、ACR、K8s 凭据。
4. 创建最小 Pipeline。
5. 先发布 web，再接入 gateway 和 blog。

对应实施计划见：

```text
docs/cicd/JENKINS_ON_K3S_IMPLEMENTATION_PLAN.md
```
