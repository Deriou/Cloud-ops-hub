# STEP C2 - Markdown 渲染与 Caffeine 缓存

## 目标

实现文章 Markdown -> HTML 渲染链路，并通过 Caffeine 建立高命中缓存。

## 前置条件

- C1 已完成
- 已选定 Markdown 渲染库

## 目录与类建议

- `apps/blog-service/src/main/java/.../render/MarkdownRenderer.java`
- `apps/blog-service/src/main/java/.../render/PostRenderService.java`
- `apps/blog-service/src/main/java/.../cache/PostRenderCache.java`
- `apps/blog-service/src/test/java/.../render/PostRenderServiceTest.java`

## Red（先写失败测试）

1. `first_request_should_render_and_cache_html`
2. `second_request_should_hit_cache`
3. `post_updated_should_invalidate_cache`
4. `invalid_markdown_should_return_safe_html_or_error`

断言重点：

- 渲染方法调用次数（验证缓存命中）
- 更新后缓存键变化（如 `postId + updatedAt`）

## Green（最小实现）

1. 定义 `PostRenderCache`（Caffeine，限制最大容量与 TTL）。
2. 实现 `PostRenderService#getRenderedHtml(postId)`：
   - 查缓存
   - 未命中则查文章 + 渲染 + 回填缓存
3. 在文章更新逻辑中触发缓存失效（或版本键变更）。
4. 全部测试通过。

## Refactor（重构）

1. 增加渲染耗时指标埋点（Micrometer timer）。
2. 抽象渲染策略接口，后续可扩展语法插件。

## 通过标准（DoD）

- 缓存命中逻辑被测试证明
- 文章更新后不会读取旧 HTML
- 渲染失败场景有统一处理

## 执行命令清单

- `mvn -pl apps/blog-service test -Dtest=*PostRenderService*`
