# STEP C1 - 文章/标签/分类主模型

## 目标

完成 Blog 核心数据模型（Post/Tag/Category）及基础查询写入链路。
另外 因为我的文章存在obsidian 通过文件夹分类 规划合适方式规划方便我以后上传 这里要提供方案询问我意见

## 前置条件

- API 统一规范已可复用
- MyBatis-Plus 基础依赖已就绪

## 目录与类建议

- `apps/blog-service/src/main/java/.../domain/entity/*`
- `apps/blog-service/src/main/java/.../mapper/*Mapper.java`
- `apps/blog-service/src/main/java/.../service/PostService.java`
- `apps/blog-service/src/main/java/.../controller/PostController.java`
- `apps/blog-service/src/test/java/.../post/PostServiceTest.java`

## Red（先写失败测试）

1. `create_post_with_tags_and_categories_should_persist_relations`
2. `list_posts_should_return_paged_result`
3. `get_post_detail_should_include_tags_and_categories`
4. `page_size_over_limit_should_be_rejected`

## Green（最小实现）

1. 定义数据表与实体：
   - `post`
   - `tag`
   - `category`
   - `post_tag`
   - `post_category`
2. 先实现最小 Service：
   - 创建文章并保存关联关系
   - 分页查询
   - 详情查询
3. Controller 按 `ApiResponse` 输出。
4. 跑测试通过。

## Refactor（重构）

1. 提取 DTO 与转换器，避免实体直接暴露。
2. 对分页查询加索引与排序字段约束，避免全表扫描。

## 通过标准（DoD）

- 主流程 CRUD 与关联关系正确
- 分页行为可预测，符合资源约束

## 执行命令清单

- `mvn -pl apps/blog-service test -Dtest=*PostService*`
