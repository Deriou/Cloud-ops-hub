# Obsidian 博客发布方案规范

## 1. 文档目的

本文档用于定义 Cloud-Ops-Hub 博客模块的完整发布方案，覆盖：

- Obsidian 本地笔记如何作为唯一内容源
- `blog-service` 如何接收、规范化、存储与渲染笔记
- 前端博客页如何读取并展示发布后的文章
- 图片、双链、分类标签、取消发布等关键行为的统一规则
- 后续实施顺序与上线注意事项

本文档以当前 V1 落地方案为主，并保留少量后续演进建议。

## 2. 已确认的产品决策

当前阶段明确采用以下方案：

- 博客是纯公开发布站
- 不提供网页端正文编辑能力
- Obsidian 是唯一内容源
- 本地笔记继续保留原目录结构
- 同步器扫描带 `noteId` 的 Markdown 笔记，并按 `publish` 决定公开或下线
- 文件夹路径只用于推导默认分类，不直接决定网站 URL
- V1 公开文章详情继续使用 ID 路由：`/blog/posts/:id`
- `slug` 保留为服务端内部兼容字段，不进入作者工作流
- 双链在 V1 暂不转换，原样上传并按普通文本显示
- 图片规模按当前仓库量级设计，优先本地持久化方案
- 取消 `publish: true` 时，文章执行“下线”而不是物理删除

## 3. 当前系统基线

当前博客模块已经具备以下基础能力：

- 文章列表、详情、分页、搜索
- 标签与分类查询
- Markdown 渲染与缓存
- 博客公开 GET 匿名可访问，写接口基于 `X-Ops-Key` 鉴权

当前详情页展示内容包括：

- 标题
- 摘要
- 创建时间
- 更新时间
- 标签
- 分类
- 渲染后的 HTML

当前渲染器使用标准 CommonMark。  
这意味着 Obsidian 的 `[[双链]]`、`![[图片]]` 等语法不能直接依赖现有渲染器。
V1 中：

- `![[图片]]` 在导入阶段转换
- `[[双链]]` 暂不转换，原样进入正文

## 4. 总体架构

目标架构如下：

```text
Obsidian Vault
  -> 扫描带 noteId 的 Markdown
  -> 解析 frontmatter / 正文 / 图片引用
  -> 调用 blog-service 导入接口
  -> blog-service 执行规范化、upsert、渲染、资产落盘
  -> web 前端按公开文章查询并展示
```

职责边界如下：

- Obsidian 插件或本地同步脚本：负责扫描、解析、转换、上传
- `blog-service`：负责校验、入库、分类标签补齐、图片管理、渲染输出
- `web`：负责公开展示，不参与内容编辑

## 5. 内容源规则

### 5.1 内容源原则

- 本地 Obsidian Vault 是唯一权威内容源
- 服务器上的文章是“发布结果”，不是编辑主源
- 网页端不允许改正文，避免与本地笔记冲突

### 5.2 发布范围

同步程序扫描整个 Vault，但只处理满足以下条件的笔记：

- 文件扩展名为 `.md`
- frontmatter 中存在 `noteId`
- 非 `.excalidraw.md`
- 非模板文件
- 非明确排除目录中的临时文件

建议默认排除：

- `.obsidian/`
- `DRAFT/`
- `Excalidraw/`
- 二进制文件目录

说明：

- `DRAFT/` 是否完全排除，可由同步器配置决定
- `publish: true` 会导入为 `published`
- `publish: false` 或未填写 `publish` 时，同步器会将该笔记导入为 `draft`

## 6. Frontmatter 规范

### 6.1 必填字段

```yaml
---
publish: true
noteId: 8d8e48af-5ea6-4d81-8f36-8a50d0a83624
title: Docker 基础概念与常用命令
summary: 从镜像、容器到常用命令的入门整理
tags: [docker, devops]
categories: [cloud-ops]
---
```

字段说明：

- `publish`
  - 是否参与公开发布
- `noteId`
  - 本地笔记的稳定唯一标识
  - 一篇笔记一旦生成，不应再修改
  - 用于服务端 upsert，不依赖标题和文件名
- `title`
  - 公开展示标题
- `summary`
  - 列表页和详情页摘要
- `tags`
  - 文章主题标签，建议手动维护
- `categories`
  - 文章分类，可手动指定；若缺失，可由同步器按路径推导默认值

### 6.2 建议可选字段

```yaml
---
publish: true
noteId: 8d8e48af-5ea6-4d81-8f36-8a50d0a83624
title: Docker 基础概念与常用命令
summary: 从镜像、容器到常用命令的入门整理
tags: [docker, devops]
categories: [cloud-ops]
createdAt: 2024-01-10T21:00:00+08:00
coverImage: docker-basics-cover.png
---
```

说明：

- `createdAt`
  - 原始笔记或文章内容的创建时间
  - 建议用于保留早期笔记的原始时间
- `coverImage`
  - 预留封面字段，第一版可不实现

### 6.3 Frontmatter 约束

- `noteId` 必须全 Vault 唯一
- `tags`、`categories` 统一使用小写短语或统一命名风格
- `summary` 不宜过长，建议 `40-140` 字
- `title` 允许修改
- 对于历史笔记，建议补 `createdAt`

补充说明：

- 作者不需要手工维护 `slug`
- 导入文章时，服务端自动生成内部兼容 slug：`obsidian-<noteId>`

### 6.4 时间字段建议

第一版时间模型建议尽量简化，只使用公开页已经存在的两个字段：

- `createTime`
  - 文章原始创建时间
- `updateTime`
  - 文章最近一次导入更新的时间

推荐规则：

- `createTime` 优先来自 frontmatter 的 `createdAt`
- 若未提供 `createdAt`，首次导入时使用服务端当前时间
- `updateTime` 在每次成功导入更新时写入当前时间

注意：

- 不建议将“导入那一刻”覆盖历史文章的 `createTime`
- 不建议仅依赖文件系统 `mtime`，因为补 frontmatter 会改变文件修改时间
- 对历史笔记，最好做一次性 `createdAt` 补录或批量回填

### 6.5 最简最终模板

最简可用模板如下：

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

推荐使用规则：

- 新文章至少填写：`publish`、`noteId`、`title`、`summary`
- `tags` 建议始终填写，便于前端展示与搜索聚合
- `categories` 建议填写；若未填写，可由同步器按路径推导默认分类
- 历史笔记建议补 `createdAt`
- 若是普通新文章且不在意原始创建时间，可先不写 `createdAt`

最简新文章模板如下：

```yaml
---
publish: true
noteId: 3a28cf27-ff43-4cd5-8d4a-e807c6720c67
title: K3s 单节点部署记录
summary: 在单节点 ECS 上完成 K3s 与基础服务部署的实战记录
tags: [k3s, kubernetes, devops]
categories: [cloud-ops]
---
```

## 7. 路径、分类与标签规则

### 7.1 路径不等于公开结构

本地路径主要服务于：

- 个人知识管理
- 同步排查
- 默认分类推导

本地路径不直接等于：

- 网站 URL
- 网站面包屑
- 前端分类结构

例如：

```text
Ops/docker/基础.md
```

不要求网站展示为：

```text
/blog/Ops/docker/基础
```

该笔记最终公开展示的内容，以 frontmatter 为准：

- `title`
- `summary`
- `tags`
- `categories`

### 7.2 默认分类推导

若笔记未显式填写 `categories`，同步器可按路径推导默认分类。

建议初始映射：

- `Ops/**` -> `cloud-ops`
- `Java/**` -> `java`
- `code/**` -> `programming`

可选的二级增强：

- `Ops/docker/**` -> `docker`
- `Ops/Kubernetes/**` -> `kubernetes`
- `Ops/nginx/**` -> `nginx`

建议规则：

- 一级分类用于大的站点分组
- 二级分类如需启用，应控制数量，避免分类爆炸
- 标签仍然用于表达细主题，优先手动维护

### 7.3 标签与分类职责

- 分类：文章归属，数量少，结构稳定
- 标签：文章主题，数量可多，表达更灵活

推荐：

- 分类控制在 `3-8` 个主类以内
- 标签允许细分技术点

## 8. 文章身份与链接规则

### 8.1 身份规则

文章存在三种不同标识：

- `noteId`
  - 本地同步身份
  - 永久稳定
- `slug`
  - 服务端内部兼容字段
  - 导入时自动生成为 `obsidian-<noteId>`
- `id`
  - 数据库内部主键
  - 同时作为当前 V1 前端公开详情路由参数

### 8.2 URL 规则

V1 公开 URL 继续使用：

```text
/blog/posts/:id
```

原因：

- 现有前后端已经稳定使用 ID 路由
- 这次发布链路优先解决内容导入、图片和时间语义，不额外引入 slug 路由迁移风险
- 可以避免前后端同时改详情查询方式

### 8.3 Slug 规则

V1 中：

- 作者不填写 `slug`
- 服务端自动生成内部兼容值：`obsidian-<noteId>`
- `slug` 不作为当前前端公开 URL

### 8.4 后续演进

若后续希望切换到正式博客风格 URL，可再单独演进到：

```text
/blog/:slug
```

但这不属于当前 V1 范围。

### 8.5 时间语义规则

当前代码中的 `createTime` 与 `updateTime` 若直接沿用现状，会在创建和更新时写入 `LocalDateTime.now()`，即导入时刻。  
这不适合历史笔记发布场景。

目标语义建议如下：

- `createTime`
  - 存“原始内容创建时间”
- `updateTime`
  - 存“最近一次导入更新时间”

导入规则建议：

- 首次导入时：
  - 若 frontmatter 含 `createdAt`，则写入 `createTime`
  - 否则可使用一次性回填结果
  - 若仍无可用值，再退化为首次导入时间
  - `updateTime` 写入首次导入时间
- 更新导入时：
  - 默认保留原 `createTime`
  - `updateTime` 在每次成功导入更新时写入当前时间

公开页推荐展示：

- 创建时间：`createTime`
- 更新时间：`updateTime`

## 9. 发布状态与取消发布规则

### 9.1 状态定义

文章建议至少支持以下状态：

- `draft`
- `published`
- `archived`

### 9.2 状态来源

第一版可按以下规则处理：

- `publish: true` -> `published`
- `publish: false` 或移除 `publish` -> `draft`

### 9.3 取消发布行为

当本地笔记移除 `publish: true` 时：

- 服务端将文章状态更新为 `draft`
- 文章不再出现在公开列表
- 访客访问该文章时返回 `404` 或“未公开”
- 不执行物理删除

原因：

- 避免误操作造成内容永久丢失
- 便于重新发布
- 保留同步记录与资源引用关系

### 9.4 删除行为

第一版不推荐“自动根据本地缺失直接删库”。

建议仅在以下场景手动删除：

- 确认是错误导入
- 确认内容已废弃且不再恢复

## 10. 图片与资源规范

### 10.1 当前假设

当前附件目录约为：

- `175` 个文件
- 总大小约 `97MB`

此规模适合第一版采用本地持久化方案，无需一开始引入复杂对象存储流程。

### 10.2 本地写法

本地 Obsidian 笔记继续使用现有习惯：

```md
![[Pasted image 20260330192330.png]]
```

或：

```md
![[a4c729b0-3f61-4cd1-b51d-f7f3d0822c99.png]]
```

### 10.3 导入转换规则

同步器在上传前需要：

1. 扫描正文中的 Obsidian 图片引用
2. 在附件目录中找到实际文件
3. 计算文件 hash
4. 调用服务端图片上传接口
5. 将正文中的 `![[...]]` 替换为标准 Markdown 图片链接

示例：

```md
![[Pasted image 20260330192330.png]]
```

转换为：

```md
![Pasted image 20260330192330](/api/v1/blog/assets/images/sha256-xxxx.png)
```

### 10.4 服务端资源策略

建议图片 key 组成：

- `sha256` 摘要
- 原始扩展名

示例：

```text
sha256-a4f3...c2.png
```

建议行为：

- 相同内容的图片不重复存储
- 同名不同内容的图片按 hash 区分
- 图片文件与文章正文解耦存储

### 10.5 图片存储位置

第一版建议：

- 存储到本地持久卷
- K8s 中使用 PVC 挂载到 `blog-service`

后续可扩展：

- OSS / S3 兼容存储

### 10.6 图片注意事项

- 不要在上传过程中回写本地原始笔记
- 正文转换仅作用于上传 payload
- `.excalidraw.md` 不直接发布
- 若使用 Excalidraw 图，建议先导出 PNG 再引用

## 11. 双链规范

### 11.1 第一版目标

V1 中双链不做转换。

当前处理规则：

- `[[文件名]]` 原样保留
- `[[文件名|显示名]]` 原样保留
- 页面中按普通文本显示
- 不应因为正文中存在双链而导致导入失败或页面报错

### 11.2 当前已知限制

V1 暂不支持：

- `[[文件名]]` 到站内链接的转换
- `[[文件名|显示名]]` 的显示文本转换
- `[[文件名#标题]]`
- `[[文件名^块引用]]`
- 反向链接展示
- 知识图谱

### 11.3 后续增强

后续若要恢复双链功能，建议分阶段实现：

1. 先支持 `[[文件名]]`、`[[文件名|显示名]]` 转换为站内链接
2. 再支持标题锚点和块引用跳转
3. 最后再考虑反向链接与知识图谱

## 12. Markdown 规范化规则

由于当前服务端使用标准 CommonMark 渲染，导入前需要进行规范化。

### 12.1 需要在导入阶段处理的语法

- frontmatter 解析
- Obsidian 图片引用 `![[...]]`
- 可选：任务列表、脚注、表格扩展

说明：

- V1 不处理 Obsidian 双链 `[[...]]`
- `[[...]]` 原样进入 `markdownContent`

### 12.2 建议策略

同步器负责将 Obsidian Markdown 转为“标准 Markdown 再上传”。

服务端负责：

- 存储规范化后的 `markdownContent`
- 渲染出公开展示所需 HTML

### 12.3 安全策略

详情页当前使用 `v-html` 渲染服务端 HTML。  
因此建议服务端对渲染结果增加安全约束：

- 限制危险 HTML 注入
- 统一对外输出可控 HTML

第一版若仍依赖 CommonMark 默认渲染，应在上线前确认原始 HTML 的安全边界。

## 13. 同步协议设计

### 13.1 同步模式

推荐提供两种同步能力：

- 同步当前笔记
- 批量同步全部带 `noteId` 的笔记

### 13.2 请求模型

建议新增导入接口：

```text
POST /api/v1/blog/import/notes:batch
```

请求体示例：

```json
{
  "notes": [
    {
      "noteId": "8d8e48af-5ea6-4d81-8f36-8a50d0a83624",
      "sourcePath": "Ops/docker/基础.md",
      "title": "Docker 基础概念与常用命令",
      "summary": "从镜像、容器到常用命令的入门整理",
      "markdownContent": "## Docker\n...",
      "tags": ["docker", "devops"],
      "categories": ["cloud-ops"],
      "createdAt": "2024-01-10T21:00:00+08:00",
      "contentHash": "sha256:xxxx",
      "publish": true
    }
  ]
}
```

### 13.3 返回模型

建议返回每篇笔记的处理结果：

```json
{
  "created": 3,
  "updated": 5,
  "skipped": 2,
  "failed": 1,
  "results": [
    {
      "noteId": "8d8e48af-5ea6-4d81-8f36-8a50d0a83624",
      "postId": 12,
      "action": "updated",
      "message": "content changed"
    }
  ]
}
```

### 13.4 Upsert 规则

服务端按 `noteId` 执行 upsert：

- 已存在同 `noteId` -> 更新
- 不存在 -> 创建

同时校验：

- 标题允许变化
- `sourcePath` 可变化
- 内部 `slug` 由服务端基于 `noteId` 自动生成

### 13.5 变更跳过规则

若 `contentHash` 与服务端记录一致，且元数据无变化，则可直接 `skipped`。

## 14. 后端设计规范

### 14.1 数据模型建议

文章表建议增加以下字段：

- `note_id`
- `status`
- `source_path`
- `content_hash`
- `cover_image`
- `last_sync_time`

说明：

- `note_id`：同步唯一键
- `status`：公开状态控制
- `source_path`：追踪来源
- `content_hash`：增量同步判断
- `last_sync_time`：记录最近一次成功同步时间

### 14.2 分类与标签写入

当前文章接口依赖 `tagIds/categoryIds`。  
为适配 Obsidian，同步接口建议改为按名称工作：

- 若标签不存在，则自动创建
- 若分类不存在，则自动创建
- 再在内部建立关系

### 14.3 公开查询规则

所有公开读接口默认只返回：

- `status = published`

管理接口如需存在，可单独增加，但当前阶段不对外暴露网页管理台。

### 14.4 图片接口

建议补齐：

- `POST /api/v1/blog/assets/images`
- `GET /api/v1/blog/assets/images/{key}`

上传接口职责：

- 接收图片二进制或 multipart 文件
- 计算 hash
- 若已存在则直接返回已有 key
- 若不存在则写入存储并返回访问地址

### 14.5 鉴权建议

当前博客公开页改为匿名读取公开 GET 接口，包括：

- `GET /api/v1/blog/posts`
- `GET /api/v1/blog/posts/{id}`
- `GET /api/v1/blog/search`
- `GET /api/v1/blog/tags`
- `GET /api/v1/blog/categories`
- `GET /api/v1/blog/assets/images/{key}`

以下写接口继续基于 `X-Ops-Key`：

- `POST /api/v1/blog/import/notes:batch`
- `POST /api/v1/blog/assets/images`
- `POST /api/v1/blog/posts`
- `PUT /api/v1/blog/posts/{id}`
- `POST /api/v1/blog/tags`
- `POST /api/v1/blog/categories`

第一版继续复用现有主密钥即可；公开博客页不再依赖 `guest token` 或前端注入 `VITE_OPS_KEY`。

建议后续演进：

- `BLOG_PUBLISH_KEY`
- 仅允许调用导入与资源上传接口

### 14.6 删除接口

第一版不要求暴露公开删除语义。  
若需要后台清理，可保留内部删除接口。

### 14.7 时间字段与导入逻辑改造清单

以下清单用于指导 `blog-service` 从当前 CRUD 结构升级到可支持 Obsidian 导入。

#### 数据库与实体层

- `post` 表新增 `note_id`
  - 类型建议：`varchar(64)` 或 `varchar(128)`
  - 约束：唯一索引
- `post` 表新增 `status`
  - 类型建议：`varchar(32)`
  - 值建议：`draft` / `published` / `archived`
- `post` 表新增 `source_path`
  - 用于追踪来源文件相对路径
- `post` 表新增 `content_hash`
  - 用于增量同步跳过判断
- `post` 表新增 `last_sync_time`
  - 记录最近一次成功导入时间
- `post` 表保留现有 `create_time`、`update_time`
  - `create_time`：原始内容创建时间
  - `update_time`：最近一次成功更新内容的时间
- `PostEntity` 补齐以上字段

#### DTO 与接口层

- 新增导入请求 DTO，例如 `ImportNoteRequest`
- 新增批量导入请求 DTO，例如 `BatchImportNotesRequest`
- `ImportNoteRequest` 建议至少包含：
  - `noteId`
  - `sourcePath`
  - `title`
  - `summary`
  - `markdownContent`
  - `tags`
  - `categories`
  - `createdAt`
  - `contentHash`
  - `publish`
- 新增导入结果 DTO，例如 `ImportNoteResult`
- 新增批量导入返回 DTO，包含：
  - `created`
  - `updated`
  - `skipped`
  - `failed`
  - `results`
- 新增接口：
  - `POST /api/v1/blog/import/notes:batch`

#### Service 层改造

- 新增 `ImportService` 或在 `PostService` 中增加独立导入入口
- 导入入口不要复用当前面向 `tagIds/categoryIds` 的创建接口
- 导入逻辑按 `noteId` 做 upsert
- 首次导入时：
  - 若 `createdAt` 有值，则写入 `createTime`
  - 若 `createdAt` 无值，则写入当前时间
  - `updateTime` 写入当前时间
  - `status` 按 `publish` 设置
- 再次导入时：
  - 先按 `noteId` 查询文章
  - 若 `contentHash` 相同且关键元数据无变化，则返回 `skipped`
  - 若有变更，则保留原 `createTime`
  - 更新正文、摘要、标题、分类、标签
  - 将 `updateTime` 更新为当前时间
  - 将 `lastSyncTime` 更新为当前时间
- `publish: true` 时将 `status` 设为 `published`
- `publish: false` 时将 `status` 设为 `draft`
- 分类与标签按名称自动补齐，不要求调用方传 ID
- 内部 `slug` 固定生成为 `obsidian-<noteId>`

#### 公开查询逻辑

- 列表接口仅查询 `status = published`
- 详情接口对游客仅读取 `status = published`
- 若文章为 `draft`，公开访问返回 `404` 或业务错误
- 后续若需要管理态读取，可在内部接口中放开

#### 渲染与缓存逻辑

- 导入更新正文前，先驱逐旧缓存
- 更新成功后，后续详情读取再重新渲染
- `contentHash` 相同被跳过时，不刷新 `updateTime`
- 详情页继续返回 `createTime` 与 `updateTime`

#### 迁移与兼容处理

- 为历史 `post` 记录回填 `note_id`
  - 若线上还没有真实内容，可允许先为空并仅对新导入文章强制要求
- 为历史 `post` 记录回填 `status`
  - 默认可设为 `published`
- 老的 `POST /posts`、`PUT /posts/{id}` 可以继续保留
  - 但作为后台手工接口
  - 不作为 Obsidian 导入主链路

#### 测试清单

- 首次导入新文章：`createTime` 正确使用 `createdAt`
- 首次导入无 `createdAt` 的文章：`createTime` 回退到导入时间
- 重复导入未变更文章：返回 `skipped`，`updateTime` 不变
- 导入正文变更文章：保留 `createTime`，刷新 `updateTime`
- 将 `publish: true` 改为 `false`：文章下线但不删除
- 标签/分类不存在时可自动创建
- 导入结果返回 `postId`，便于后续直接打开公开详情页

## 15. 前端展示规范

### 15.1 当前已具备的展示能力

- 列表页搜索
- 分页
- 标签入口
- 分类入口
- 详情页基础阅读

### 15.2 第一版前端目标

在现有基础上补强文章内容展示质量：

- 图片样式
- 表格样式
- 代码块样式
- 移动端阅读宽度与溢出控制
- 链接样式一致性

### 15.3 后续建议功能

推荐迭代顺序：

1. 继续完善图片、表格、标题锚点样式
2. 支持目录 TOC
3. 支持代码高亮
4. 评估是否迁移到 slug 路由
5. 评估是否加入最小双链跳转

### 15.4 前端注意事项

- 详情页使用 `v-html`，需确认渲染安全边界
- 图片可能较宽，需要限制最大宽度
- 表格需要横向滚动容器
- 代码块需要处理长行换行或滚动
- 当前 V1 保持 `/blog/posts/:id`，不需要为本次上线改路由

## 16. Obsidian 插件或同步器规范

### 16.1 第一版实现建议

优先顺序：

1. 先做本地同步脚本
2. 再做 Obsidian 插件按钮封装

原因：

- 先把同步协议跑通，风险更低
- 规则稳定后，再做插件更省返工

### 16.2 最小功能集

同步器需要支持：

- 扫描所有带 `noteId` 的 Markdown 笔记
- 解析 frontmatter
- 解析图片引用
- 批量上传笔记
- 输出成功/失败/跳过结果

说明：

- V1 不做双链转换

### 16.3 本地状态文件

建议同步器维护本地状态文件，用于提升增量同步效率。

可记录：

- `noteId`
- `lastContentHash`
- `lastSyncAt`
- `lastResult`

说明：

- 本地状态文件是优化项，不是最终一致性的唯一依据
- 最终一致性仍以服务端记录为准

## 17. 部署与存储要求

### 17.1 K8s 存储

若采用本地图片存储，需为 `blog-service` 提供持久卷：

- PVC 挂载图片目录
- Pod 重建后资源不丢失

### 17.2 配置项建议

`blog-service` 建议增加以下配置：

- 图片存储模式：`local` / `oss`
- 本地图片根目录
- multipart 上传大小限制

说明：

- V1 继续复用现有 `X-Ops-Key` 主密钥
- 该主密钥仅用于导入、上传和其他写接口
- 公开博客页读取文章、搜索、标签、分类与图片时不需要 `X-Ops-Key`
- 暂不单独引入发布专用 Key

### 17.3 备份建议

至少备份：

- MySQL 数据库
- 图片持久卷目录
- Obsidian 原始 Vault

## 18. 分阶段实施计划

### Phase 1：内容规范落地

- 整理 Obsidian 笔记
- 为准备发布的文章补全 frontmatter
- 约定 `noteId`、标签与分类风格

验收：

- 至少 `10-20` 篇文章可满足发布规范

### Phase 2：后端导入能力

- 新增 `notes:batch` 导入接口
- 新增图片上传接口
- 新增文章状态与同步字段
- 公开查询仅返回 `published`

验收：

- 单篇与批量导入可成功
- 取消发布后文章可下线

### Phase 3：本地同步脚本

- 扫描带 `noteId` 的 Markdown
- 转换图片
- 批量上传并输出结果

验收：

- 重复执行不会产生重复文章
- 未变更文章可跳过
- 正文中的 `[[双链]]` 不影响导入与展示

### Phase 4：前端展示优化

- 优化文章内容样式
- 补强移动端体验

验收：

- 公开博客页体验达到可正式使用状态

### Phase 5：Obsidian 插件

- 封装同步脚本逻辑
- 提供“一键同步当前笔记/全部带 `noteId` 的笔记”

验收：

- 本地一键发布工作流可稳定使用

## 19. 风险与注意事项

### 19.1 内容规范不稳定

风险：

- 早期标签、分类、frontmatter 规则频繁变动，导致返工

对策：

- 先小规模试运行
- 先发一批精选文章验证规则

### 19.2 误发布

风险：

- 不适合公开的笔记被误标记 `publish: true`

对策：

- 同步前打印待发布列表
- 第一版只手动触发发布，不做自动后台监听

### 19.3 图片丢失

风险：

- 引用存在但附件实际缺失

对策：

- 同步前执行资源校验
- 缺失图片时将该笔记标记失败

### 19.4 双链未转换

风险：

- 正文中的 `[[双链]]` 目前不会变成可点击的站内链接

对策：

- V1 先接受该限制
- 后续若需要再单独实现双链转换能力

## 20. 最终推荐结论

当前最适合 Cloud-Ops-Hub 的博客发布方案是：

- 使用 Obsidian 作为唯一内容源
- 保持原有本地目录结构不变
- 通过 `publish` 控制公开发布状态
- 使用 `noteId` 作为同步唯一键
- 使用 ID 路由作为当前公开文章地址
- `slug` 仅作为服务端内部兼容字段
- 标签手动维护，分类允许由路径推导默认值
- 图片在同步阶段自动上传并替换引用
- 双链在 V1 暂不转换
- 取消发布执行“下线”而不是删除
- 前端只负责展示，不承担编辑职责

该方案兼顾：

- 你当前的 Obsidian 使用习惯
- 现有博客模块的实现基础
- 后续继续扩展到插件化、一键发布与更强内容展示能力的空间
