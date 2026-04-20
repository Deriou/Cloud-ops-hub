# Blog Obsidian 发布 SOP

本文档用于指导 Cloud-Ops-Hub 的 Obsidian 博客发布链路上线与日常使用。

当前方案约定：

- 文章详情继续使用 ID 路由：`/blog/posts/:id`
- Obsidian 是唯一内容源，网页端不编辑正文
- 图片通过 `![[图片名.png]]` 自动上传并替换为服务器地址
- `[[双链]]` 暂不转换，上传后会作为普通文本显示
- 导入接口使用现有主密钥 `X-Ops-Key`

---

## 1. 你接下来要做的事情

### 1.1 一次性工作

1. 整理准备发布的 Obsidian 笔记，补齐 frontmatter
2. 备份线上 `db_blog`
3. 执行博客表结构迁移 SQL
4. 在 K3s 节点准备图片存储目录
5. 构建并推送新的 `blog-service` 镜像
6. 构建并推送新的 `web` 镜像
7. 更新 K8s Deployment 中的镜像 tag
8. 应用 K8s 清单并确认 Pod 正常
9. 先同步 1-3 篇测试笔记
10. 验证网页展示正常后，再执行全量同步

### 1.2 日常工作

1. 在 Obsidian 里写或修改笔记
2. 保证 frontmatter 正确
3. 先对单篇笔记做 dry-run
4. 执行单篇同步
5. 浏览器验收
6. 需要时再执行全量同步

---

## 2. Obsidian 笔记最小规范

### 2.1 最简 frontmatter 模板

```yaml
---
publish: true
noteId: 8d8e48af-5ea6-4d81-8f36-8a50d0a83624
title: Docker 基础概念与常用命令
summary: 从镜像、容器到常用命令的入门整理
tags: [docker, devops]
categories: [cloud-ops]
createdAt: 2024-01-10T21:00:00+08:00
---
```

### 2.2 字段说明

- `publish`
  - 作用：是否进入博客同步流程
- `noteId`
  - 作用：Obsidian 笔记和数据库文章的唯一绑定键
  - 要求：一篇笔记生成一次后不要再改
- `title`
  - 作用：网页展示标题
- `summary`
  - 作用：列表页摘要
- `tags`
  - 作用：自动创建并绑定文章标签
- `categories`
  - 作用：自动创建并绑定文章分类
- `createdAt`
  - 作用：首次导入时写入 `createTime`
  - 说明：后续重复同步不会覆盖已有 `createTime`

### 2.3 当前已知限制

- `![[图片.png]]`：支持，会自动上传并替换
- `[[双链]]`：暂不支持转换，会原样进入正文
- `.excalidraw.md`：同步脚本会跳过

---

## 3. 一次性上线 SOP

以下步骤建议在 ECS 或你的运维终端上执行。

### 3.1 进入项目目录

```bash
cd ~/projects/Cloud-ops-hub
```

作用：
- 进入项目根目录，后续 Docker、kubectl、SQL、脚本命令都以这里为基准

### 3.2 查看当前改动

```bash
git status
git pull
```

作用：
- `git status`：确认当前工作区状态
- `git pull`：拉取最新代码，确保服务器使用的是当前实现

### 3.3 备份线上博客数据库

```bash
kubectl -n cloud-ops exec deploy/mysql -- sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" db_blog' > ~/db_blog_backup_$(date +%F_%H%M%S).sql
```

作用：
- 从 K8s 中的 `mysql` 容器导出 `db_blog`
- 输出为本机备份文件，便于迁移失败时回滚

### 3.4 执行博客迁移 SQL

```bash
kubectl -n cloud-ops exec -i deploy/mysql -- sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" db_blog' < apps/blog-service/src/main/resources/db/mysql/obsidian-publishing-migration.sql
```

作用：
- 为 `post` 表新增以下字段：
  - `note_id`
  - `status`
  - `source_path`
  - `content_hash`
  - `last_sync_time`
- 同时补充索引：
  - `uk_post_note_id`
  - `idx_post_status`

说明：
- 当前迁移 SQL 已改为“先查询 `information_schema` 再动态执行”的兼容写法
- 这样可以避免依赖某些 MySQL 版本不支持的：
  - `ADD COLUMN IF NOT EXISTS`
  - `CREATE INDEX IF NOT EXISTS`
- 这份 SQL 可以重复执行；如果字段或索引已经存在，会自动跳过对应步骤

如果你在服务器上之前已经执行过旧版 SQL，请先确保代码已更新：

```bash
cd ~/projects/Cloud-ops-hub
git pull
```

作用：
- 拉取最新迁移脚本，避免继续使用旧版不兼容 SQL

### 3.5 检查数据库迁移结果

```bash
kubectl -n cloud-ops exec -it deploy/mysql -- sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "use db_blog; desc post;"'
```

作用：
- 查看 `post` 表结构，确认新增字段已存在

再检查索引：

```bash
kubectl -n cloud-ops exec -it deploy/mysql -- sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "use db_blog; show index from post;"'
```

作用：
- 确认以下索引已经创建：
  - `uk_post_note_id`
  - `idx_post_status`

预期新增字段：

- `note_id`
- `status`
- `source_path`
- `content_hash`
- `last_sync_time`

### 3.6 准备博客图片目录

```bash
sudo mkdir -p /opt/cloud-ops/blog-assets
sudo ls -ld /opt/cloud-ops/blog-assets
```

作用：
- 创建博客图片持久化目录
- 检查目录是否存在，后续会由 `hostPath PV` 挂载给 `blog-service`

### 3.7 本地构建前校验后端

```bash
./apps/gateway-portal/mvnw -f pom.xml -pl apps/blog-service -am test -Dtest=PostControllerTest,PostServiceTest,SearchControllerTest,SearchServiceTest,NoteImportControllerTest,ImageAssetControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

作用：
- 只回归 `blog-service` 相关关键测试
- 确认文章查询、导入、图片上传、搜索逻辑正常

### 3.8 本地构建前校验前端

```bash
cd ~/projects/Cloud-ops-hub/web
npm run build
cd ~/projects/Cloud-ops-hub
```

作用：
- 先验证前端可以正常打包
- `cd` 回项目根目录，为后续 Docker 构建做准备

### 3.9 设置镜像仓库和发布 tag

```bash
export REGISTRY=crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub
export BLOG_TAG=0.0.4-obsidian-v1
export WEB_TAG=0.0.5-obsidian-v1
```

作用：
- 统一本次发布使用的镜像仓库和 tag
- 避免在后续命令中反复手写完整镜像名

说明：
- 请把 `BLOG_TAG`、`WEB_TAG` 换成你这次实际要发布的版本号
- 不建议使用 `latest`

### 3.10 构建 blog-service 镜像

```bash
docker build --platform linux/amd64 \
  -f apps/blog-service/Dockerfile \
  -t ${REGISTRY}/blog-service:${BLOG_TAG} \
  .
```

作用：
- 从项目根目录构建 `blog-service` 镜像
- 指定 `linux/amd64`，确保和 ECS / K3s 运行环境一致

### 3.11 推送 blog-service 镜像

```bash
docker push ${REGISTRY}/blog-service:${BLOG_TAG}
```

作用：
- 把新的 `blog-service` 镜像推送到阿里云 ACR

### 3.12 构建 web 镜像

```bash
docker build --platform linux/amd64 \
  -t ${REGISTRY}/web:${WEB_TAG} \
  web/
```

作用：
- 构建前端 `web` 镜像

### 3.13 推送 web 镜像

```bash
docker push ${REGISTRY}/web:${WEB_TAG}
```

作用：
- 把新的 `web` 镜像推送到阿里云 ACR

### 3.14 修改 K8s Deployment 镜像 tag

需要手动编辑以下文件：

- `infra/k8s/base/blog/deployment.yaml`
- `infra/k8s/base/web/deployment.yaml`

修改示例：

```yaml
image: crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/blog-service:0.0.2-obsidian-v1
image: crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/web:0.0.3-obsidian-v1
```

作用：
- 告诉 K8s 本次部署要拉取哪一个新镜像

### 3.15 应用 K8s 清单

```bash
kubectl apply -k infra/k8s/base/
```

作用：
- 一次性应用命名空间下所有基础资源
- 包括：
  - `blog-service` 新环境变量
  - `blog-assets` PV/PVC
  - 新的 Deployment 配置

### 3.16 等待滚动发布完成

```bash
kubectl -n cloud-ops rollout status deploy/blog-service
kubectl -n cloud-ops rollout status deploy/web
```

作用：
- 等待 `blog-service` 和 `web` 完成滚动更新

### 3.17 检查 Pod 状态

```bash
kubectl -n cloud-ops get pod
kubectl -n cloud-ops get pvc
kubectl get pv
```

作用：
- 确认服务 Pod 已进入 `Running`
- 确认 `blog-assets-pvc` 和 `blog-assets-pv` 绑定成功

### 3.18 检查服务日志

```bash
kubectl -n cloud-ops logs deploy/blog-service --tail=100
kubectl -n cloud-ops logs deploy/web --tail=100
```

作用：
- 查看本次启动日志，确认没有配置错误、数据库连接错误、文件目录权限错误

---

## 4. 首批笔记同步 SOP

建议第一次只同步 1-3 篇笔记，先完成验收。

### 4.1 先做 dry-run

```bash
node scripts/obsidian-publish.mjs \
  --vault /Users/Deriou/Documents/Deriou \
  --base-url https://deriou.com \
  --ops-key '你的OPS主密钥' \
  --file '/Users/Deriou/Documents/Deriou/Ops/docker/基础.md' \
  --dry-run
```

作用：
- 只解析笔记，不真正发请求
- 预览：
  - 读取到的 frontmatter
  - 图片替换后的 Markdown
  - 最终要提交的 JSON 数据

### 4.2 同步单篇笔记

```bash
node scripts/obsidian-publish.mjs \
  --vault /Users/Deriou/Documents/Deriou \
  --base-url https://deriou.com \
  --ops-key '你的OPS主密钥' \
  --file '/Users/Deriou/Documents/Deriou/Ops/docker/基础.md'
```

作用：
- 上传这篇笔记引用到的图片
- 调用 `POST /api/v1/blog/import/notes:batch`
- 如果这篇笔记首次导入，会返回 `created`
- 如果内容变了，会返回 `updated`
- 如果内容未变化，会返回 `skipped`

### 4.3 同步全部带 `noteId` 的笔记

```bash
node scripts/obsidian-publish.mjs \
  --vault /Users/Deriou/Documents/Deriou \
  --base-url https://deriou.com \
  --ops-key '你的OPS主密钥'
```

作用：
- 扫描整个 vault
- 仅处理带 `noteId` 的 Markdown 笔记
- 依据 frontmatter 中的 `publish` 状态决定上线或下线

说明：
- 脚本当前逻辑是：没有 `noteId` 的笔记直接跳过
- `publish: false` 会把文章改成 `draft`，不会物理删除

---

## 5. 上线后验收 SOP

### 5.1 检查文章列表接口

```bash
curl -H 'X-Ops-Key: 你的OPS主密钥' \
  'https://deriou.com/api/v1/blog/posts?pageNo=1&pageSize=5'
```

作用：
- 验证公开列表接口能正常返回文章
- 确认返回数据中只包含 `published` 文章

### 5.2 检查文章详情接口

```bash
curl -H 'X-Ops-Key: 你的OPS主密钥' \
  'https://deriou.com/api/v1/blog/posts/1'
```

作用：
- 验证文章详情接口可用
- 确认返回字段包含：
  - `createTime`
  - `updateTime`
  - `renderedHtml`
  - `tags`
  - `categories`

### 5.3 检查图片链接

```bash
curl -I 'https://deriou.com/api/v1/blog/assets/images/替换成真实图片key'
```

作用：
- 验证图片资源接口可公开访问
- 预期应返回 `200 OK`

### 5.4 浏览器人工验收

手工打开以下页面：

- `https://deriou.com/blog`
- `https://deriou.com/blog/posts/<文章ID>`

检查项：

- 标题、摘要、创建时间、更新时间显示正常
- 图片可见且宽度正常
- 代码块有滚动，不会撑爆布局
- 表格在移动端可横向滚动
- 标签、分类可正常展示
- `[[双链]]` 即使未转换，也不会导致页面报错

---

## 6. 日常发文 SOP

### 6.1 修改笔记

在 Obsidian 中直接修改正文和 frontmatter。

作用：
- Obsidian 是唯一内容源，网页端不维护正文

### 6.2 单篇 dry-run

```bash
node scripts/obsidian-publish.mjs \
  --vault /Users/Deriou/Documents/Deriou \
  --base-url https://deriou.com \
  --ops-key '你的OPS主密钥' \
  --file '/Users/Deriou/Documents/Deriou/你要发布的笔记.md' \
  --dry-run
```

作用：
- 在真正同步前先确认图片、frontmatter、内容摘要都正确

### 6.3 单篇同步

```bash
node scripts/obsidian-publish.mjs \
  --vault /Users/Deriou/Documents/Deriou \
  --base-url https://deriou.com \
  --ops-key '你的OPS主密钥' \
  --file '/Users/Deriou/Documents/Deriou/你要发布的笔记.md'
```

作用：
- 只发布当前这一篇，适合日常发文

### 6.4 打开网页验收

```bash
open 'https://deriou.com/blog'
```

作用：
- 直接打开博客页做人工检查

说明：
- 如果你是在 Linux 服务器操作，没有 `open` 命令，可以改用浏览器手动访问

### 6.5 批量同步

```bash
node scripts/obsidian-publish.mjs \
  --vault /Users/Deriou/Documents/Deriou \
  --base-url https://deriou.com \
  --ops-key '你的OPS主密钥'
```

作用：
- 在你一次性整理了多篇笔记之后，统一做批量同步

---

## 7. 回滚与排障 SOP

### 7.1 查看 blog-service 当前状态

```bash
kubectl -n cloud-ops get pod
kubectl -n cloud-ops describe pod -l app=blog-service
kubectl -n cloud-ops logs deploy/blog-service --tail=100
kubectl -n cloud-ops logs deploy/blog-service --tail=100 --previous
```

作用：
- 查看当前 Pod、事件链、当前日志、上一次崩溃日志

### 7.2 回滚镜像版本

处理方法：

1. 把 `infra/k8s/base/blog/deployment.yaml` 中的镜像 tag 改回旧版本
2. 把 `infra/k8s/base/web/deployment.yaml` 中的镜像 tag 改回旧版本
3. 再次执行：

```bash
kubectl apply -k infra/k8s/base/
kubectl -n cloud-ops rollout status deploy/blog-service
kubectl -n cloud-ops rollout status deploy/web
```

作用：
- 用旧镜像重新部署服务

### 7.3 数据库回滚

```bash
kubectl -n cloud-ops exec -i deploy/mysql -- sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" db_blog' < ~/db_blog_backup_你备份时的文件名.sql
```

作用：
- 用之前导出的备份恢复数据库

注意：
- 数据库恢复会覆盖当前数据，只在确认需要回退时执行

### 7.4 迁移 SQL 报语法不支持

典型报错：

```text
ERROR 1064 (42000): ... near 'if not exists ...'
```

原因：
- 你的 MySQL 版本不支持某些 DDL 语法，例如：
  - `ADD COLUMN IF NOT EXISTS`
  - `CREATE INDEX IF NOT EXISTS`

处理步骤：

1. 先更新仓库代码

```bash
cd ~/projects/Cloud-ops-hub
git pull
```

作用：
- 拉取已经改成兼容版的迁移脚本

2. 重新执行迁移

```bash
kubectl -n cloud-ops exec -i deploy/mysql -- sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" db_blog' < apps/blog-service/src/main/resources/db/mysql/obsidian-publishing-migration.sql
```

作用：
- 使用兼容版 SQL 重新迁移 `post` 表

3. 再执行字段和索引检查

```bash
kubectl -n cloud-ops exec -it deploy/mysql -- sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "use db_blog; desc post;"'
kubectl -n cloud-ops exec -it deploy/mysql -- sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "use db_blog; show index from post;"'
```

作用：
- 确认迁移已经成功落库

---

## 8. 推荐执行顺序

### 8.1 首次上线

1. 补 frontmatter
2. 备份数据库
3. 跑迁移 SQL
4. 准备 `/opt/cloud-ops/blog-assets`
5. 跑后端测试
6. 跑前端 build
7. 构建并推送镜像
8. 修改 Deployment tag
9. `kubectl apply -k infra/k8s/base/`
10. 先单篇 dry-run
11. 先单篇同步
12. 验证网页
13. 再批量同步

### 8.2 日常发布

1. 改 Obsidian 笔记
2. 单篇 dry-run
3. 单篇同步
4. 打开网页验收
5. 需要时再全量同步

---

## 9. 相关文件位置

- 方案规范：[BLOG_OBSIDIAN_PUBLISHING_SPEC.md](/Users/Deriou/projects/Cloud-ops-hub/docs/BLOG_OBSIDIAN_PUBLISHING_SPEC.md:1)
- API 说明：[blog-service.md](/Users/Deriou/projects/Cloud-ops-hub/docs/apis/blog-service.md:1)
- 数据库迁移 SQL：[obsidian-publishing-migration.sql](/Users/Deriou/projects/Cloud-ops-hub/apps/blog-service/src/main/resources/db/mysql/obsidian-publishing-migration.sql:1)
- 本地同步脚本：[obsidian-publish.mjs](/Users/Deriou/projects/Cloud-ops-hub/scripts/obsidian-publish.mjs:1)
- Blog K8s Deployment：[deployment.yaml](/Users/Deriou/projects/Cloud-ops-hub/infra/k8s/base/blog/deployment.yaml:1)
- Blog 图片 PV/PVC：[pv-pvc.yaml](/Users/Deriou/projects/Cloud-ops-hub/infra/k8s/base/blog/pv-pvc.yaml:1)
