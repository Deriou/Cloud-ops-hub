# Blog-Service API

Base path: `/api/v1/blog`

## 1. 文章

- `GET /posts`
  - 描述：分页查询文章
  - 参数：`pageNo`、`pageSize`（兼容 `page`、`size`）
  - 权限：公开读（匿名可访问）
- `GET /posts/{id}`
  - 描述：文章详情（优先命中渲染缓存，返回 `markdownContent` 与 `renderedHtml`）
  - 权限：公开读（匿名可访问）
- `POST /posts`
  - 描述：创建文章（Markdown）
  - 权限：Master
- `PUT /posts/{id}`
  - 描述：更新文章并刷新渲染缓存
  - 权限：Master

## 2. 标签与分类

- `GET /tags`
  - 权限：公开读（匿名可访问）
- `POST /tags`
  - 权限：Master
- `GET /categories`
  - 权限：公开读（匿名可访问）
- `POST /categories`
  - 权限：Master

写接口仅 Master 可调用。

## 3. 搜索

- `GET /search?q={keyword}`
  - 描述：站内全文搜索（生产默认走 MySQL Fulltext，测试环境走 H2 降级实现）
  - 参数：`q`、`pageNo`、`pageSize`
  - 权限：公开读（匿名可访问）

## 4. 图片资源

- `POST /assets/images`
  - 描述：上传图片到本地持久卷，返回公开访问路径
  - 权限：Master
- `GET /assets/images/{key}`
  - 描述：读取图片资源（二进制响应）
  - 权限：公开读

## 5. Obsidian 导入

- `POST /import/notes:batch`
  - 描述：按 `noteId` 批量导入或更新 Obsidian 笔记
  - 权限：Master
  - 行为：
    - 首次导入：创建文章
    - 再次导入且正文/元数据变化：更新文章
    - 内容未变化：返回 `skipped`
    - `publish=false`：文章改为 `draft`，不再出现在公开列表

## 6. 公开查询规则

- 公开列表、详情、搜索仅返回 `status=published` 的文章
- 公开 GET 接口匿名可访问：`/posts`、`/posts/{id}`、`/search`、`/tags`、`/categories`、`/assets/images/{key}`
- 写接口继续要求 `X-Ops-Key`
- 手工创建文章默认 `published`
- 导入文章内部 `slug` 固定生成为 `obsidian-<noteId>`，前台仍使用 ID 路由访问详情

## 7. 缓存策略

- 渲染结果基于文章 `id + updateTime` 作为缓存键
- 缓存未命中时执行渲染并回填 Caffeine
- 渲染异常时降级返回安全 HTML，避免详情接口直接失败
