# ECS 首次初始化清单（逐命令解释版）

适用场景：Cloud-Ops-Hub 在阿里云 `8GB` 单节点 ECS 的首次接入。  
执行原则：先安全基线，再平台安装；每执行一段就做一次验证。

---

## 0. 使用说明

- 下文命令默认在 ECS 终端执行。
- 涉及 Ubuntu 与 CentOS 的命令会分开给出。
- 每条命令后都附了“解释”，便于学习理解。

---

## 1. 基线信息采集（第一步必须做）

```bash
hostnamectl
```
解释：查看主机名、内核、虚拟化信息，确认你连的是目标服务器。

```bash
uname -a
```
解释：查看完整内核版本，后续排查 K3s/内核模块问题会用到。

```bash
cat /etc/os-release
```
解释：确认发行版（Ubuntu/CentOS），决定后续使用 `apt` 还是 `yum`。

```bash
free -h
```
解释：查看内存总量与占用，确认 8GB 基线与当前空闲内存。

```bash
df -h
```
解释：查看磁盘容量和挂载点，提前发现空间不足风险。

```bash
ip a
```
解释：查看网卡与 IP，确认公网/私网地址。

```bash
ss -lntp
```
解释：查看当前监听端口和进程，避免后续端口冲突。

---

## 2. 系统更新与基础工具安装

### Ubuntu / Debian

```bash
apt update && apt upgrade -y
```
解释：更新包索引并升级系统补丁，修复已知漏洞和兼容问题。

```bash
apt install -y curl wget git vim htop jq unzip ca-certificates gnupg lsb-release
```
解释：安装后续运维与调试高频工具，避免流程中断。

### CentOS / RHEL

```bash
yum update -y
```
解释：升级系统软件包，保持基础环境在安全状态。

```bash
yum install -y curl wget git vim htop jq unzip ca-certificates gnupg2
```
解释：安装常用运维工具与证书组件。

---

## 3. 时区与时间同步

```bash
timedatectl set-timezone Asia/Shanghai
```
解释：统一时区，避免日志、监控、告警时间对不上。

```bash
timedatectl status
```
解释：检查时区和 NTP 状态是否正确生效。

```bash
timedatectl set-ntp true
```
解释：开启系统自动校时，降低分布式组件时间漂移问题。

---

## 4. 创建运维用户（推荐）

```bash
useradd -m -s /bin/bash opsadmin
```
解释：创建普通运维用户，避免长期以 root 直接操作。

```bash
passwd opsadmin
```
解释：设置该用户密码（即使后续主要用 SSH key，也建议保留应急方式）。

```bash
usermod -aG sudo opsadmin 2>/dev/null || usermod -aG wheel opsadmin
```
解释：给用户 sudo 权限；Ubuntu 用 `sudo` 组，CentOS 常见 `wheel` 组。

```bash
id opsadmin
```
解释：确认用户与组是否正确创建。

```bash
mkdir -p /home/opsadmin/.ssh
```
解释：创建新用户 SSH 目录。

```bash
chmod 700 /home/opsadmin/.ssh
```
解释：设置目录权限，SSH 对权限要求严格。

```bash
cp /root/.ssh/authorized_keys /home/opsadmin/.ssh/authorized_keys
```
解释：复制已可用的公钥授权，保证新用户可免密登录。

```bash
chown -R opsadmin:opsadmin /home/opsadmin/.ssh
```
解释：修正目录归属，避免 SSH 拒绝读取密钥。

```bash
chmod 600 /home/opsadmin/.ssh/authorized_keys
```
解释：设置授权文件为仅用户可读写，符合 SSH 安全要求。

---

## 5. SSH 安全加固（确认 key 登录成功后再改）

```bash
vim /etc/ssh/sshd_config
```
解释：编辑 SSH 服务配置文件。

建议修改项：

- `PermitRootLogin prohibit-password`  
  解释：禁止 root 密码登录，仅允许 key（或直接设为 `no`）。
- `PasswordAuthentication no`  
  解释：禁用密码登录，降低暴力破解风险。
- `PubkeyAuthentication yes`  
  解释：明确启用公钥认证。

```bash
sshd -t && systemctl restart sshd
```
解释：先做配置语法检查，正确后重启 SSH 服务，避免错误配置导致失联。

---

## 6. 防火墙与安全组最小放行

建议只开放：

- `22/tcp`（来源限制为你的办公公网 IP）
- `80/tcp`、`443/tcp`（后续业务入口）

```bash
ufw allow from <YOUR_IP>/32 to any port 22 proto tcp
```
解释：仅允许你的 IP 访问 SSH，降低扫描攻击面。

```bash
ufw allow 80/tcp
```
解释：放通 HTTP 入口，供 Ingress/临时服务访问。

```bash
ufw allow 443/tcp
```
解释：放通 HTTPS 入口，为后续证书和域名做准备。

```bash
ufw enable
```
解释：启用 UFW 防火墙策略。

```bash
ufw status verbose
```
解释：确认规则已按预期生效。

---

## 7. K3s 前置设置（Swap + 内核参数）

```bash
swapoff -a
```
解释：立即关闭 swap，K8s 不建议启用 swap。

```bash
sed -ri '/\sswap\s/s/^#?/#/' /etc/fstab
```
解释：注释掉 fstab 中 swap 项，防止重启后 swap 自动开启。

```bash
free -h
```
解释：确认 swap 已为 0。

```bash
cat >/etc/modules-load.d/k8s.conf <<'EOF'
overlay
br_netfilter
EOF
```
解释：设置开机自动加载 K8s 常用内核模块。

```bash
modprobe overlay
```
解释：立即加载 `overlay`，用于容器文件系统。

```bash
modprobe br_netfilter
```
解释：启用网桥流量进入 iptables，K8s 网络策略依赖它。

```bash
cat >/etc/sysctl.d/99-k8s.conf <<'EOF'
net.bridge.bridge-nf-call-iptables = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward = 1
EOF
```
解释：写入 K8s 常用内核网络参数。

```bash
sysctl --system
```
解释：立即加载所有 sysctl 配置。

```bash
lsmod | grep -E 'overlay|br_netfilter'
```
解释：检查模块是否真的加载成功。

```bash
sysctl net.ipv4.ip_forward
```
解释：确认 IP 转发已开启。

---

## 8. 安装 K3s（单节点）

```bash
curl -sfL https://get.k3s.io | sh -
```
解释：下载安装并启动 K3s 服务（默认单节点模式）。

```bash
systemctl status k3s --no-pager
```
解释：确认 K3s 服务是否正常运行。

```bash
kubectl get nodes -o wide
```
解释：确认节点注册成功并处于 `Ready`。

```bash
mkdir -p $HOME/.kube
```
解释：准备当前用户 kubeconfig 目录。

```bash
cp /etc/rancher/k3s/k3s.yaml $HOME/.kube/config
```
解释：复制 K3s 生成的集群访问配置到用户目录。

```bash
chown $(id -u):$(id -g) $HOME/.kube/config
```
解释：修复文件权限，避免普通用户访问失败。

```bash
export KUBECONFIG=$HOME/.kube/config
```
解释：指定 kubectl 使用当前用户配置。

```bash
kubectl get pods -A
```
解释：检查系统命名空间组件是否已启动。

---

## 9. Cloud-Ops-Hub 运行前准备

```bash
kubectl create namespace cloud-ops
```
解释：创建业务独立命名空间，隔离资源与配置。

```bash
kubectl get ns cloud-ops
```
解释：确认命名空间创建成功。

```bash
kubectl -n cloud-ops create secret generic cloud-ops-secret \
  --from-literal=OPS_AUTH_MASTER_KEY=replace_me \
  --from-literal=BLOG_DATASOURCE_PASSWORD=replace_me
```
解释：创建基础敏感配置，避免明文写进代码仓库。

---

## 10. 日志与资源巡检

```bash
ls /etc/logrotate.d
```
解释：确认系统有日志轮转机制，防止日志撑爆磁盘。

```bash
free -h
```
解释：持续观察内存使用趋势，尤其是 8G 单机环境。

```bash
df -h
```
解释：观察磁盘剩余空间，防止存储告警和服务异常。

```bash
kubectl top node 2>/dev/null || true
```
解释：查看节点资源（若 metrics-server 未安装，命令不会中断流程）。

```bash
journalctl --since "1 hour ago" -p err --no-pager | tail -n 100
```
解释：快速查看最近 1 小时系统错误日志。

---

## 11. 初始化完成判定（DoD）

满足以下条件说明首次初始化成功：

- 可以使用 `opsadmin` + SSH key 稳定登录
- 密码登录已关闭（或仅保留应急策略）
- K3s 单节点状态为 `Ready`
- `cloud-ops` 命名空间和基础 Secret 已创建
- swap 已关闭、关键内核参数生效
- 基础巡检命令可正常执行

---

## 12. 下一步学习路径

初始化完成后，按顺序继续：

1. `docs/learning/L1-k3s-deploy-handbook.md`
2. `docs/learning/L2-observability-plg-handbook.md`
3. `docs/learning/L3-jenkins-on-k3s-handbook.md`

