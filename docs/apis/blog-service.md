# Blog-Service API

Base path: `/api/v1/blog`

## 1. 文章

- `GET /posts`
  - 描述：分页查询文章
  - 权限：Guest/Master
- `GET /posts/{id}`
  - 描述：文章详情（优先命中渲染缓存，返回 `markdownContent` 与 `renderedHtml`）
  - 权限：Guest/Master
- `POST /posts`
  - 描述：创建文章（Markdown）
  - 权限：Master
- `PUT /posts/{id}`
  - 描述：更新文章并刷新渲染缓存
  - 权限：Master
- `DELETE /posts/{id}`
  - 描述：删除文章
  - 权限：Master

## 2. 标签与分类

- `GET /tags`
- `POST /tags`
- `GET /categories`
- `POST /categories`

写接口仅 Master 可调用。

## 3. 搜索

- `GET /search?q={keyword}`
  - 描述：站内全文搜索（生产默认走 MySQL Fulltext，测试环境走 H2 降级实现）
  - 参数：`q`、`pageNo`、`pageSize`
  - 权限：Guest/Master

## 4. 图片资源

- `POST /assets/images`
  - 描述：上传图片到 OSS 或本地持久卷
  - 权限：Master
- `GET /assets/images/{key}`
  - 描述：读取图片资源
  - 权限：Guest/Master

## 5. 缓存策略

- 渲染结果基于文章 `id + updateTime` 作为缓存键
- 缓存未命中时执行渲染并回填 Caffeine
- 渲染异常时降级返回安全 HTML，避免详情接口直接失败
