# STEP C3 - MySQL 全文搜索

## 目标

建立 Blog 站内搜索能力，支持分页与关键词查询。

## 前置条件

- C1 已完成文章主表
- MySQL 已建立全文索引

## 目录与类建议

- `apps/blog-service/src/main/java/.../search/SearchRepository.java`
- `apps/blog-service/src/main/java/.../search/SearchService.java`
- `apps/blog-service/src/main/java/.../search/SearchController.java`
- `apps/blog-service/src/test/java/.../search/SearchServiceTest.java`

## Red（先写失败测试）

1. `search_should_return_ranked_results_by_keyword`
2. `empty_keyword_should_return_validation_error`
3. `search_should_support_pagination`
4. `keyword_not_found_should_return_empty_list`

## Green（最小实现）

1. 建立查询接口：
   - `GET /api/v1/blog/search?q=...&pageNo=...&pageSize=...`
2. 对关键词做非空与长度校验。
3. 使用全文索引查询 + 分页返回。
4. 测试通过。

## Refactor（重构）

1. 将排序策略（相关度 + 时间）封装成独立方法。
2. 过滤高风险字符，防止非法查询输入。

## 通过标准（DoD）

- 搜索结果稳定可分页
- 参数校验行为明确
- 空结果场景不报错

## 执行命令清单

- `mvn -pl apps/blog-service test -Dtest=*SearchService*`
