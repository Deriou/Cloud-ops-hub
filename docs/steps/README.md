# Cloud-Ops-Hub Steps 指南

本目录将 `docs/TDD_EXECUTION_PLAN.md` 的每个步骤展开为独立执行文档。

使用方式：

1. 严格按编号顺序执行（A1 -> A2 -> ... -> D3）
2. 每个步骤必须完成 Red -> Green -> Refactor
3. 当前步骤未通过测试，不进入下一步

## 阶段 A：common 底座

- `STEP-A1-api-response.md`
- `STEP-A2-global-exception.md`
- `STEP-A3-auth-interceptor.md`

## 阶段 B：Gateway-Portal

- `STEP-B1-app-registry-query.md`
- `STEP-B2-health-aggregation.md`
- `STEP-B3-guest-mode-management.md`

## 阶段 C：Blog-Service

- `STEP-C1-post-tag-category.md`
- `STEP-C2-markdown-cache.md`
- `STEP-C3-fulltext-search.md`

## 阶段 D：Ops-Core

- `STEP-D1-k8s-status-collector.md`
- `STEP-D2-jenkins-trigger-async.md`
- `STEP-D3-release-diagnosis-engine.md`
