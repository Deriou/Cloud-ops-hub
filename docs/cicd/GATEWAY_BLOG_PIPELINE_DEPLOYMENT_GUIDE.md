# Gateway / Blog 单模块 Jenkins 流水线部署文档

本文用于接入 `gateway-portal` 与 `blog-service` 两条后端单模块 Jenkins 流水线。

第一版目标是跑通闭环：

```text
Jenkins 手动触发
-> Checkout
-> Maven 模块构建
-> Docker 构建镜像
-> 推送 ACR
-> 回写 Kubernetes Deployment 镜像 tag
-> Jenkins 自动提交并推送 Git
-> kubectl apply
-> rollout 与实际镜像校验
```

暂不接入 Webhook，暂不做统一多模块智能 Pipeline，暂不强制跑测试。

## 1. 当前约定

| 项目 | 约定 |
| --- | --- |
| Jenkins Job | 两个独立 Pipeline Job：`cloud-ops-gateway-pipeline`、`cloud-ops-blog-pipeline` |
| Jenkinsfile | `Jenkinsfile.gateway`、`Jenkinsfile.blog` |
| 镜像仓库 | `crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub` |
| 镜像 tag | `0.0.7-gateway-${BUILD_NUMBER}-${gitShortSha}`、`0.0.7-blog-${BUILD_NUMBER}-${gitShortSha}` |
| 发布 namespace | `cloud-ops` |
| Jenkins namespace | `cicd` |
| 触发方式 | 手动触发 |
| 并发约定 | 不要同时运行 gateway 与 blog 两个发布 Job，避免两个 Job 同时回写并推送 main |

## 2. 前置检查

以下命令默认在项目根目录执行。

### 2.1 检查当前分支和工作区

```bash
git branch --show-current
```

作用：确认当前在 `main` 分支或准备从 `main` 创建变更。

```bash
git status --short
```

作用：确认当前有哪些未提交变更，避免把无关文件混入流水线接入提交。

### 2.2 准备根目录 Maven Wrapper

后端模块属于根目录 `pom.xml` 的 Maven reactor 子模块。流水线建议统一使用根目录 `./mvnw`。

当前仓库如果还没有根目录 `./mvnw`，可以先从现有 `apps/gateway-portal` wrapper 复制一份到根目录：

```bash
cp apps/gateway-portal/mvnw ./mvnw
```

作用：在仓库根目录提供 Linux/macOS 可执行 Maven Wrapper。

```bash
cp apps/gateway-portal/mvnw.cmd ./mvnw.cmd
```

作用：在仓库根目录提供 Windows Maven Wrapper，保持 wrapper 文件完整。

```bash
mkdir -p .mvn/wrapper
```

作用：创建根目录 Maven Wrapper 配置目录。

```bash
cp apps/gateway-portal/.mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.properties
```

作用：复制 Maven Wrapper 的发行版配置，让根目录 `./mvnw` 知道下载和使用哪个 Maven 版本。

```bash
chmod +x mvnw
```

作用：确保 Jenkins Linux 环境可以直接执行 `./mvnw`。

```bash
./mvnw -v
```

作用：验证根目录 Maven Wrapper 可执行。

### 2.3 验证 Maven 模块构建

```bash
./mvnw -pl apps/gateway-portal -am clean package -DskipTests -B
```

作用：只构建 `gateway-portal` 模块，并通过 `-am` 自动构建它依赖的 `common-core` 模块。

```bash
./mvnw -pl apps/blog-service -am clean package -DskipTests -B
```

作用：只构建 `blog-service` 模块，并通过 `-am` 自动构建它依赖的 `common-core` 模块。

参数说明：

| 参数 | 作用 |
| --- | --- |
| `-pl apps/gateway-portal` | 指定只构建 gateway 模块 |
| `-pl apps/blog-service` | 指定只构建 blog 模块 |
| `-am` | 同时构建被依赖模块，例如 `common-core` |
| `clean package` | 清理旧产物并打包 jar |
| `-DskipTests` | 第一版跳过测试，优先跑通发布闭环 |
| `-B` | Batch 模式，适合 Jenkins 日志输出 |

### 2.4 检查 Kubernetes 清单

```bash
test -f infra/k8s/base/gateway/deployment.yaml
```

作用：确认 gateway Deployment 清单存在。

```bash
test -f infra/k8s/base/blog/deployment.yaml
```

作用：确认 blog Deployment 清单存在。

```bash
grep -n "image:" infra/k8s/base/gateway/deployment.yaml
```

作用：查看 gateway 当前镜像地址，确认后续回写目标。

```bash
grep -n "image:" infra/k8s/base/blog/deployment.yaml
```

作用：查看 blog 当前镜像地址，确认后续回写目标。

### 2.5 检查 Jenkins 运行环境

当前 Jenkins 通过端口转发访问：

```bash
KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n cicd port-forward svc/jenkins 8080:8080
```

作用：把 K3s 内 `cicd` namespace 的 Jenkins Service 转发到本机 `http://127.0.0.1:8080`。

```bash
kubectl -n cicd exec deploy/jenkins -- docker version
```

作用：确认 Jenkins Pod 内可以访问 Docker CLI 和宿主机 Docker daemon。

```bash
kubectl -n cicd exec deploy/jenkins -- kubectl version --client
```

作用：确认 Jenkins Pod 内可以执行 `kubectl`。

```bash
kubectl -n cloud-ops auth can-i patch deployments --as=system:serviceaccount:cicd:jenkins
```

作用：确认 Jenkins ServiceAccount 可以更新 `cloud-ops` namespace 中的 Deployment。

## 3. Jenkins Credentials

继续复用 web 流水线已经使用的凭据。

| Credential ID | 类型 | 作用 |
| --- | --- | --- |
| `acr-cloud-ops-hub` | Username with password | 登录阿里云 ACR 并推送镜像 |
| `github-cloud-ops-hub-token` | Username with password | Jenkins 自动提交 Deployment 镜像 tag 并推送 GitHub |

第一版不需要新增只读业务接口凭据。

原因：

- gateway 的 `/api/v1/gateway/**` 需要 `X-Ops-Key`，没有凭据时不适合作为 Jenkins 第一版 smoke test。
- 第一版先用 `rollout status` 与实际镜像校验证明发布成功。
- blog 的公开 GET 接口可以后续再补 smoke test。

## 4. 新增 Jenkinsfile.gateway

在仓库根目录新增 `Jenkinsfile.gateway`。

```groovy
pipeline {
  agent any

  options {
    buildDiscarder(logRotator(numToKeepStr: '20'))
    disableConcurrentBuilds()
    skipDefaultCheckout(true)
    timestamps()
  }

  environment {
    ACR_REGISTRY = 'crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com'
    ACR_NAMESPACE = 'cloud-ops-hub'
    IMAGE_NAME = 'gateway-portal'
    IMAGE_VERSION_PREFIX = '0.0.7-gateway'
    K8S_NAMESPACE = 'cloud-ops'
    K8S_DEPLOYMENT = 'gateway-portal'
    DEPLOYMENT_FILE = 'infra/k8s/base/gateway/deployment.yaml'
    DOCKERFILE = 'apps/gateway-portal/Dockerfile'
    MAVEN_MODULE = 'apps/gateway-portal'
    GIT_PUSH_URL = 'https://github.com/Deriou/Cloud-ops-hub.git'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        sh '''
          set -eu
          git status --short
          git rev-parse --short=7 HEAD
        '''
      }
    }

    stage('Prepare Tag') {
      steps {
        script {
          env.GIT_SHORT_SHA = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
          env.IMAGE_TAG = "${env.IMAGE_VERSION_PREFIX}-${env.BUILD_NUMBER}-${env.GIT_SHORT_SHA}"
          env.IMAGE = "${env.ACR_REGISTRY}/${env.ACR_NAMESPACE}/${env.IMAGE_NAME}:${env.IMAGE_TAG}"
          currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.IMAGE_TAG}"
        }
        sh '''
          set -eu
          echo "IMAGE_TAG=${IMAGE_TAG}"
          echo "IMAGE=${IMAGE}"
        '''
      }
    }

    stage('Preflight') {
      steps {
        sh '''
          set -eu
          test -x ./mvnw
          test -f "${DOCKERFILE}"
          test -f "${DEPLOYMENT_FILE}"
          docker version
          kubectl version --client
          kubectl -n "${K8S_NAMESPACE}" auth can-i patch deployments
        '''
      }
    }

    stage('Maven Package') {
      steps {
        sh '''
          set -eu
          ./mvnw -pl "${MAVEN_MODULE}" -am clean package -DskipTests -B
        '''
      }
    }

    stage('Docker Login') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'acr-cloud-ops-hub',
          usernameVariable: 'ACR_USERNAME',
          passwordVariable: 'ACR_PASSWORD'
        )]) {
          sh '''
            set +x
            echo "${ACR_PASSWORD}" | docker login "${ACR_REGISTRY}" -u "${ACR_USERNAME}" --password-stdin
          '''
        }
      }
    }

    stage('Build Image') {
      steps {
        sh '''
          set -eu
          docker build --platform linux/amd64 -f "${DOCKERFILE}" -t "${IMAGE}" .
        '''
      }
    }

    stage('Push Image') {
      steps {
        sh '''
          set -eu
          docker push "${IMAGE}"
        '''
      }
    }

    stage('Update Manifest') {
      steps {
        sh '''
          set -eu
          sed -i -E "s#(image: )${ACR_REGISTRY}/${ACR_NAMESPACE}/${IMAGE_NAME}:.*#\\1${IMAGE}#" "${DEPLOYMENT_FILE}"
          grep -n "image:" "${DEPLOYMENT_FILE}"
        '''
      }
    }

    stage('Commit Manifest') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'github-cloud-ops-hub-token',
          usernameVariable: 'GITHUB_USERNAME',
          passwordVariable: 'GITHUB_TOKEN'
        )]) {
          sh '''
            set -eu
            git config user.name "cloud-ops-jenkins"
            git config user.email "jenkins@cloud-ops-hub.local"
            git add "${DEPLOYMENT_FILE}"

            if git diff --cached --quiet; then
              echo "No deployment image change to commit."
            else
              git commit -m "chore(cicd): bump gateway image ${IMAGE_TAG} [skip ci]"
            fi

            set +x
            askpass_file="$(pwd)/.git/jenkins-askpass.sh"
            cat > "${askpass_file}" <<'EOF'
#!/bin/sh
case "$1" in
  *Username*) echo "${GITHUB_USERNAME}" ;;
  *Password*) echo "${GITHUB_TOKEN}" ;;
esac
EOF
            chmod 700 "${askpass_file}"
            GIT_ASKPASS="${askpass_file}" GIT_TERMINAL_PROMPT=0 git push "${GIT_PUSH_URL}" HEAD:main
            rm -f "${askpass_file}"
          '''
        }
      }
    }

    stage('Deploy Gateway') {
      steps {
        sh '''
          set -eu
          kubectl apply -f "${DEPLOYMENT_FILE}"
        '''
      }
    }

    stage('Verify') {
      steps {
        sh '''
          set -eu
          kubectl -n "${K8S_NAMESPACE}" rollout status "deploy/${K8S_DEPLOYMENT}" --timeout=240s
          actual_image="$(kubectl -n "${K8S_NAMESPACE}" get deploy "${K8S_DEPLOYMENT}" -o jsonpath='{.spec.template.spec.containers[0].image}')"
          echo "Actual image: ${actual_image}"
          test "${actual_image}" = "${IMAGE}"
        '''
      }
    }
  }

  post {
    always {
      sh '''
        docker logout "${ACR_REGISTRY}" || true
        rm -f .git/jenkins-askpass.sh || true
      '''
    }
  }
}
```

关键命令说明：

| 命令 | 作用 |
| --- | --- |
| `./mvnw -pl "${MAVEN_MODULE}" -am clean package -DskipTests -B` | 构建 gateway 模块和依赖模块 |
| `docker build --platform linux/amd64 -f "${DOCKERFILE}" -t "${IMAGE}" .` | 从仓库根目录构建后端镜像，让 Dockerfile 可以复制根 `pom.xml` 与 `common-core` |
| `docker push "${IMAGE}"` | 推送 gateway 镜像到 ACR |
| `sed -i -E ... "${DEPLOYMENT_FILE}"` | 回写 `infra/k8s/base/gateway/deployment.yaml` 中的 image tag |
| `git add "${DEPLOYMENT_FILE}"` | 只暂存 gateway Deployment，避免混入无关文件 |
| `git commit -m "chore(cicd): bump gateway image ..."` | 记录本次发布镜像 tag |
| `git push "${GIT_PUSH_URL}" HEAD:main` | 把 Jenkins 回写结果推送到 GitHub main |
| `kubectl apply -f "${DEPLOYMENT_FILE}"` | 发布 gateway Deployment |
| `kubectl rollout status deploy/gateway-portal` | 等待 gateway 发布完成 |
| `test "${actual_image}" = "${IMAGE}"` | 校验线上 Deployment 镜像等于本次构建镜像 |

## 5. 新增 Jenkinsfile.blog

在仓库根目录新增 `Jenkinsfile.blog`。

```groovy
pipeline {
  agent any

  options {
    buildDiscarder(logRotator(numToKeepStr: '20'))
    disableConcurrentBuilds()
    skipDefaultCheckout(true)
    timestamps()
  }

  environment {
    ACR_REGISTRY = 'crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com'
    ACR_NAMESPACE = 'cloud-ops-hub'
    IMAGE_NAME = 'blog-service'
    IMAGE_VERSION_PREFIX = '0.0.7-blog'
    K8S_NAMESPACE = 'cloud-ops'
    K8S_DEPLOYMENT = 'blog-service'
    DEPLOYMENT_FILE = 'infra/k8s/base/blog/deployment.yaml'
    DOCKERFILE = 'apps/blog-service/Dockerfile'
    MAVEN_MODULE = 'apps/blog-service'
    GIT_PUSH_URL = 'https://github.com/Deriou/Cloud-ops-hub.git'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        sh '''
          set -eu
          git status --short
          git rev-parse --short=7 HEAD
        '''
      }
    }

    stage('Prepare Tag') {
      steps {
        script {
          env.GIT_SHORT_SHA = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
          env.IMAGE_TAG = "${env.IMAGE_VERSION_PREFIX}-${env.BUILD_NUMBER}-${env.GIT_SHORT_SHA}"
          env.IMAGE = "${env.ACR_REGISTRY}/${env.ACR_NAMESPACE}/${env.IMAGE_NAME}:${env.IMAGE_TAG}"
          currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.IMAGE_TAG}"
        }
        sh '''
          set -eu
          echo "IMAGE_TAG=${IMAGE_TAG}"
          echo "IMAGE=${IMAGE}"
        '''
      }
    }

    stage('Preflight') {
      steps {
        sh '''
          set -eu
          test -x ./mvnw
          test -f "${DOCKERFILE}"
          test -f "${DEPLOYMENT_FILE}"
          docker version
          kubectl version --client
          kubectl -n "${K8S_NAMESPACE}" auth can-i patch deployments
        '''
      }
    }

    stage('Maven Package') {
      steps {
        sh '''
          set -eu
          ./mvnw -pl "${MAVEN_MODULE}" -am clean package -DskipTests -B
        '''
      }
    }

    stage('Docker Login') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'acr-cloud-ops-hub',
          usernameVariable: 'ACR_USERNAME',
          passwordVariable: 'ACR_PASSWORD'
        )]) {
          sh '''
            set +x
            echo "${ACR_PASSWORD}" | docker login "${ACR_REGISTRY}" -u "${ACR_USERNAME}" --password-stdin
          '''
        }
      }
    }

    stage('Build Image') {
      steps {
        sh '''
          set -eu
          docker build --platform linux/amd64 -f "${DOCKERFILE}" -t "${IMAGE}" .
        '''
      }
    }

    stage('Push Image') {
      steps {
        sh '''
          set -eu
          docker push "${IMAGE}"
        '''
      }
    }

    stage('Update Manifest') {
      steps {
        sh '''
          set -eu
          sed -i -E "s#(image: )${ACR_REGISTRY}/${ACR_NAMESPACE}/${IMAGE_NAME}:.*#\\1${IMAGE}#" "${DEPLOYMENT_FILE}"
          grep -n "image:" "${DEPLOYMENT_FILE}"
        '''
      }
    }

    stage('Commit Manifest') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'github-cloud-ops-hub-token',
          usernameVariable: 'GITHUB_USERNAME',
          passwordVariable: 'GITHUB_TOKEN'
        )]) {
          sh '''
            set -eu
            git config user.name "cloud-ops-jenkins"
            git config user.email "jenkins@cloud-ops-hub.local"
            git add "${DEPLOYMENT_FILE}"

            if git diff --cached --quiet; then
              echo "No deployment image change to commit."
            else
              git commit -m "chore(cicd): bump blog image ${IMAGE_TAG} [skip ci]"
            fi

            set +x
            askpass_file="$(pwd)/.git/jenkins-askpass.sh"
            cat > "${askpass_file}" <<'EOF'
#!/bin/sh
case "$1" in
  *Username*) echo "${GITHUB_USERNAME}" ;;
  *Password*) echo "${GITHUB_TOKEN}" ;;
esac
EOF
            chmod 700 "${askpass_file}"
            GIT_ASKPASS="${askpass_file}" GIT_TERMINAL_PROMPT=0 git push "${GIT_PUSH_URL}" HEAD:main
            rm -f "${askpass_file}"
          '''
        }
      }
    }

    stage('Deploy Blog') {
      steps {
        sh '''
          set -eu
          kubectl apply -f "${DEPLOYMENT_FILE}"
        '''
      }
    }

    stage('Verify') {
      steps {
        sh '''
          set -eu
          kubectl -n "${K8S_NAMESPACE}" rollout status "deploy/${K8S_DEPLOYMENT}" --timeout=300s
          actual_image="$(kubectl -n "${K8S_NAMESPACE}" get deploy "${K8S_DEPLOYMENT}" -o jsonpath='{.spec.template.spec.containers[0].image}')"
          echo "Actual image: ${actual_image}"
          test "${actual_image}" = "${IMAGE}"
        '''
      }
    }
  }

  post {
    always {
      sh '''
        docker logout "${ACR_REGISTRY}" || true
        rm -f .git/jenkins-askpass.sh || true
      '''
    }
  }
}
```

关键命令说明：

| 命令 | 作用 |
| --- | --- |
| `./mvnw -pl "${MAVEN_MODULE}" -am clean package -DskipTests -B` | 构建 blog 模块和依赖模块 |
| `docker build --platform linux/amd64 -f "${DOCKERFILE}" -t "${IMAGE}" .` | 从仓库根目录构建后端镜像，让 Dockerfile 可以复制根 `pom.xml` 与 `common-core` |
| `docker push "${IMAGE}"` | 推送 blog 镜像到 ACR |
| `sed -i -E ... "${DEPLOYMENT_FILE}"` | 回写 `infra/k8s/base/blog/deployment.yaml` 中的 image tag |
| `git add "${DEPLOYMENT_FILE}"` | 只暂存 blog Deployment，避免混入无关文件 |
| `git commit -m "chore(cicd): bump blog image ..."` | 记录本次发布镜像 tag |
| `git push "${GIT_PUSH_URL}" HEAD:main` | 把 Jenkins 回写结果推送到 GitHub main |
| `kubectl apply -f "${DEPLOYMENT_FILE}"` | 发布 blog Deployment |
| `kubectl rollout status deploy/blog-service` | 等待 blog 发布完成 |
| `test "${actual_image}" = "${IMAGE}"` | 校验线上 Deployment 镜像等于本次构建镜像 |

## 6. 创建 Jenkins Job

### 6.1 创建 gateway Job

1. 打开 Jenkins：

```text
http://127.0.0.1:8080
```

作用：通过本地端口转发访问 K3s 内的 Jenkins。

2. 新建 Pipeline Job：

```text
Job name: cloud-ops-gateway-pipeline
Type: Pipeline
```

作用：为 `gateway-portal` 创建独立发布 Job。

3. 配置 Pipeline 来源：

```text
Definition: Pipeline script from SCM
SCM: Git
Repository URL: https://github.com/Deriou/Cloud-ops-hub.git
Credentials: github-cloud-ops-hub-token
Branch Specifier: */main
Script Path: Jenkinsfile.gateway
```

作用：让 Jenkins 从 GitHub main 分支读取 `Jenkinsfile.gateway`。

### 6.2 创建 blog Job

1. 新建 Pipeline Job：

```text
Job name: cloud-ops-blog-pipeline
Type: Pipeline
```

作用：为 `blog-service` 创建独立发布 Job。

2. 配置 Pipeline 来源：

```text
Definition: Pipeline script from SCM
SCM: Git
Repository URL: https://github.com/Deriou/Cloud-ops-hub.git
Credentials: github-cloud-ops-hub-token
Branch Specifier: */main
Script Path: Jenkinsfile.blog
```

作用：让 Jenkins 从 GitHub main 分支读取 `Jenkinsfile.blog`。

## 7. 首次发布顺序

建议先发布 gateway，再发布 blog。

原因：

- gateway 是入口服务，发布验证更直观。
- blog 依赖 MySQL、PVC、业务数据，验证点更多。
- 两个 Job 第一版约定不要同时运行，避免 Git 回写并发冲突。

### 7.1 发布 gateway

在 Jenkins 中点击：

```text
cloud-ops-gateway-pipeline -> Build Now
```

作用：手动触发 gateway 发布流水线。

发布完成后，在 ECS 上验证：

```bash
kubectl -n cloud-ops rollout status deploy/gateway-portal --timeout=240s
```

作用：确认 gateway Deployment 发布完成。

```bash
kubectl -n cloud-ops get deploy gateway-portal -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

作用：查看 gateway 当前实际镜像 tag。

```bash
kubectl -n cloud-ops get pod -l app=gateway-portal -o wide
```

作用：查看 gateway Pod 是否 Running，以及所在节点。

```bash
kubectl -n cloud-ops logs deploy/gateway-portal --tail=100
```

作用：查看 gateway 最近日志，确认启动过程没有异常。

### 7.2 发布 blog

在 Jenkins 中点击：

```text
cloud-ops-blog-pipeline -> Build Now
```

作用：手动触发 blog 发布流水线。

发布完成后，在 ECS 上验证：

```bash
kubectl -n cloud-ops rollout status deploy/blog-service --timeout=300s
```

作用：确认 blog Deployment 发布完成。

```bash
kubectl -n cloud-ops get deploy blog-service -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

作用：查看 blog 当前实际镜像 tag。

```bash
kubectl -n cloud-ops get pod -l app=blog-service -o wide
```

作用：查看 blog Pod 是否 Running，以及所在节点。

```bash
kubectl -n cloud-ops logs deploy/blog-service --tail=100
```

作用：查看 blog 最近日志，确认启动过程没有异常。

## 8. 外部访问验证

### 8.1 gateway

第一版不强制用 Jenkins 自动 curl gateway 业务接口，因为 gateway API 需要 `X-Ops-Key`。

可先验证 actuator：

```bash
kubectl -n cloud-ops exec deploy/gateway-portal -- wget -qO- http://127.0.0.1:8080/actuator/health
```

作用：从 gateway Pod 内部访问自身健康检查。

如果手头有 `X-Ops-Key`，可以人工验证业务接口：

```bash
curl -H "X-Ops-Key: <your-ops-key>" http://deriou.com/api/v1/gateway/apps
```

作用：通过 Ingress 验证 gateway 对外路由与业务接口。

### 8.2 blog

blog 部分 GET 接口是公开读接口，可以人工验证：

```bash
curl http://deriou.com/api/v1/blog/posts
```

作用：通过 Ingress 验证 blog 对外路由与文章列表接口。

```bash
curl http://deriou.com/api/v1/blog/tags
```

作用：通过 Ingress 验证 blog 标签接口。

```bash
curl http://deriou.com/api/v1/blog/categories
```

作用：通过 Ingress 验证 blog 分类接口。

## 9. Grafana / Prometheus / Loki 验证

发布后打开 Grafana：

```text
http://grafana.deriou.com/d/cloud-ops-overview/cloud-ops-overview
```

作用：查看 Cloud-Ops-Hub 总览 Dashboard。

建议观察：

| 指标 | 作用 |
| --- | --- |
| `up{service="gateway-portal"}` | 确认 Prometheus 能抓取 gateway 指标 |
| `up{service="blog-service"}` | 确认 Prometheus 能抓取 blog 指标 |
| 请求量 | 发布后是否有正常业务请求进入 |
| 5xx 比例 | 发布后是否产生服务端错误 |
| p95 延迟 | 发布后接口延迟是否异常 |
| Loki 日志趋势 | 发布后日志量和错误日志是否异常 |

也可以直接打开 Prometheus targets：

```text
http://prometheus.deriou.com/targets
```

作用：确认 `cloud-ops-gateway-portal` 与 `cloud-ops-blog-service` targets 为 UP。

## 10. 本地和 ECS 同步

Jenkins 成功回写 Deployment 并推送 main 后，本地需要拉取最新代码。

```bash
git pull origin main
```

作用：同步 Jenkins 自动提交的镜像 tag，避免本地后续提交覆盖线上 tag。

ECS 项目目录也建议按需同步：

```bash
git pull origin main
```

作用：让 ECS 上的仓库状态与 GitHub main 保持一致，避免手工 `kubectl apply` 时使用旧 tag。

## 11. 常见问题

### 11.1 Jenkins 报 `./mvnw: not found`

原因：

- 根目录没有 Maven Wrapper。
- Jenkinsfile 使用了 `./mvnw`，但仓库中只有 `apps/gateway-portal/mvnw`。

处理：

```bash
ls -la mvnw .mvn/wrapper/maven-wrapper.properties
```

作用：确认根目录 wrapper 是否存在。

如果不存在，按本文第 2.2 节补齐根目录 Maven Wrapper。

### 11.2 Jenkins 报 `./mvnw: Permission denied`

原因：

- `mvnw` 没有可执行权限。

处理：

```bash
chmod +x mvnw
```

作用：给 Maven Wrapper 增加可执行权限。

```bash
git add mvnw
git commit -m "chore: add root maven wrapper"
```

作用：提交权限变化，确保 Jenkins checkout 后也能执行。

### 11.3 Docker build 找不到根 pom 或 common-core

常见错误：

```text
COPY pom.xml .: file not found
COPY common/common-core/pom.xml common/common-core/: file not found
```

原因：

- 后端镜像构建不能用 `apps/gateway-portal` 或 `apps/blog-service` 作为 Docker build context。

正确命令：

```bash
docker build --platform linux/amd64 -f apps/gateway-portal/Dockerfile -t "$IMAGE" .
```

作用：从仓库根目录构建 gateway 镜像。

```bash
docker build --platform linux/amd64 -f apps/blog-service/Dockerfile -t "$IMAGE" .
```

作用：从仓库根目录构建 blog 镜像。

### 11.4 kubectl apply 权限不足

常见错误：

```text
forbidden: User "system:serviceaccount:cicd:jenkins" cannot patch resource "deployments"
```

检查：

```bash
kubectl -n cloud-ops auth can-i patch deployments --as=system:serviceaccount:cicd:jenkins
```

作用：确认 Jenkins ServiceAccount 是否有 Deployment patch 权限。

处理：

```bash
kubectl apply -k infra/k8s/cicd/jenkins
```

作用：重新应用 Jenkins RBAC 清单，确保 `cloud-ops-deployer-rbac.yaml` 生效。

### 11.5 Git push 失败

可能原因：

- gateway 与 blog 两个 Job 同时运行，两个 Job 都在回写 main。
- 人工在 GitHub main 上推送了新提交，Jenkins workspace 落后。

第一版处理方式：

1. 不要同时运行 `cloud-ops-gateway-pipeline` 与 `cloud-ops-blog-pipeline`。
2. 失败后重新触发当前 Job。
3. 本地和 ECS 执行 `git pull origin main`。

### 11.6 rollout 超时

检查 Pod：

```bash
kubectl -n cloud-ops get pod -l app=gateway-portal -o wide
```

作用：查看 gateway Pod 状态。

```bash
kubectl -n cloud-ops get pod -l app=blog-service -o wide
```

作用：查看 blog Pod 状态。

查看事件：

```bash
kubectl -n cloud-ops describe deploy gateway-portal
```

作用：查看 gateway Deployment 的事件、探针和镜像拉取状态。

```bash
kubectl -n cloud-ops describe deploy blog-service
```

作用：查看 blog Deployment 的事件、探针和镜像拉取状态。

查看日志：

```bash
kubectl -n cloud-ops logs deploy/gateway-portal --tail=200
```

作用：查看 gateway 启动日志。

```bash
kubectl -n cloud-ops logs deploy/blog-service --tail=200
```

作用：查看 blog 启动日志。

## 12. 简历表述建议

可以表述为：

```text
为 Cloud-Ops-Hub 设计并落地 Jenkins 单模块 CI/CD 发布闭环，完成 web、gateway-portal、blog-service 三个模块的独立 Pipeline。
每条流水线支持模块构建、Docker 镜像推送、Kubernetes Deployment 镜像 tag 回写、GitOps 化提交和 K3s rollout 校验。
在三条单模块流水线稳定后，为后续基于 changed paths 的多模块路径感知发布打下基础。
```

这体现的是工程演进：

```text
单模块闭环
-> 三模块发布能力
-> 多模块路径感知雏形
-> 后续统一智能 Pipeline
```
