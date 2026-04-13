# Frontend Update Handoff

## 1. 文档目的

本文件用于把当前对话中已经确认的前端更新目标整理成可直接实施的交接文档。后续窗口接手时，应以本文件为主规范，配合以下文件一起使用：

- `docs/web/webpdr.md`
- `docs/web/index.html`
- `docs/web/photo.jpg`

实现原则：

- 保留 `docs/web/index.html` 当前蓝白云岛 Bento 风格
- 比现有 `web/` 实现更精简，不再做“功能过满”的控制台式首页
- 首页以单屏可读为第一优先级
- 先把首页与壳层设计对齐，再逐步收敛 Blog / Ops 详情页

## 2. 最终产品目标

目标不是再做一个复杂看板，而是做一个轻量个人门户。

首页只回答五件事：

- 我是谁
- 我写什么
- 我做过什么
- 我有哪些应用
- 我如何管理这些系统

首页必须在桌面端尽量一屏内读完核心信息，不依赖滚动理解结构。

## 3. 当前已确认信息

### 3.1 个人信息

- 页面标题：`Deriou的个人编程笔记`
- 名称：`Deriou`
- 一句话简介：`写代码、记笔记、在云上折腾`
- GitHub 用户名：`Deriou`
- GitHub 链接：`https://github.com/Deriou`
- 邮箱：`guqiang982@gmail.com`
- 头像素材：`docs/web/photo.jpg`

### 3.2 首页模块边界

- 博客：单独首页模块，展示入口与最新文章
- 项目展示：只展示 GitHub 作品仓库
- 应用集合：当前只放 `Portal`
- 运维：放 Gateway / Blog 管理能力、健康、后续 Jenkins / Grafana 入口

### 3.3 当前固定内容

- 项目展示只展示：`Deriou/Cloud-ops-hub`
- 应用集合当前只展示：`Portal`
- `Portal` 文案：`有哪些应用、状态如何、从哪进`
- 谏言文案：`The coding was tough. Nearly killed me.`
- 首页暂时只放 Blog 入口，Blog 虽由 Gateway-Portal 管理托管，但首页仍保留博客卡

## 4. 当前代码现状

当前 `web/` 已有 Vue 3 + TypeScript + Tailwind 结构，但实现方向和当前目标仍有偏差。

### 4.1 现有页面与路由

当前路由：

- `/`
- `/blog`
- `/blog/posts/:postId`
- `/ops/cluster`
- `/ops/workloads`
- `/ops/pipelines`
- `/ops/diagnostics`

说明：

- 路由结构可以保留
- 首页与全局壳层需要重新贴近 `docs/web/index.html`
- 当前没有单独 `Projects` / `Apps` 页面，短期可先保留首页入口占位，不强制本轮新增

### 4.2 当前实现问题

- 首页信息过多，仍偏“控制台总览”
- 顶栏和首页原型不一致
- 个人信息区仍是旧内容
- 项目展示、应用集合、运维边界没有完全按最新版规范收束
- 页面缩放仍比目标稿更满
- 当前首页的数据来源和展示内容过多，需要收敛为“摘要 + 入口”

## 5. 目标信息架构

首页固定为 6+1 结构：

1. 顶栏
2. 左侧个人信息卡
3. 博客卡
4. 项目展示卡
5. 应用集合卡
6. 运维卡
7. 底部谏言卡

布局要求：

- 左侧个人卡占一列
- 右侧四张卡为 2x2
- 谏言卡横跨右侧两列底部
- 1440px 左右宽度下优先做到首屏完整可读
- 页面整体比当前实现缩小一档

## 6. 视觉设计规范

### 6.1 视觉方向

- 浅色背景
- 蓝白配色
- 大圆角
- 云岛卡片
- 轻阴影
- 少量 hover 浮起

### 6.2 必须保留

- `docs/web/index.html` 当前的整体排版方向
- 紧凑但不拥挤的云岛卡片感
- 简洁顶栏
- 左个人卡 + 右模块矩阵 + 底部谏言卡的版式

### 6.3 必须删减

- 首页过多状态指标
- 与内容无关的装饰数据块
- 大段系统解释文案
- 过多的 tags / stats / contributions 型信息
- 强控制台感的顶部状态区

### 6.4 字号与间距

- 再比当前 `web/` 实现小一档
- 谏言卡使用正常大标题字号，不要超大 Hero 文案
- 顶栏高度紧凑
- 卡片标题大小统一

## 7. 顶栏设计

### 7.1 目标结构

- 左侧只显示文字标题：`Deriou的个人编程笔记`
- 右侧导航：`Home`、`Blog`、`Projects`、`Apps`、`Ops`
- 最右侧主按钮：`GitHub`

### 7.2 约束

- 删除左上角图标
- 不放搜索框
- 不放 access mode / health badge / refresh 这类控制台状态控件
- GitHub 按钮直接外链到 `https://github.com/Deriou`

### 7.3 代码影响

主要修改：

- `web/src/components/AppShell.vue`

处理原则：

- 保留全局壳层职责
- 改写成文档原型的顶栏结构
- 桌面端优先，移动端保留简洁底部导航

## 8. 首页详细设计

### 8.1 个人信息卡

展示内容：

- 头像：`docs/web/photo.jpg`
- 名称：`Deriou`
- 简介：`写代码、记笔记、在云上折腾`
- 联系方式：GitHub、邮箱
- 技术栈：`Kubernetes`、`Go`、`Java`、`Vue`、`DevOps`

不再展示：

- Contributions
- Hot Tags 大区块
- 多统计卡
- 过多说明文案

实现建议：

- 使用静态配置驱动
- 联系方式做成两个 pill/button
- 技术栈保持 5 个以内

### 8.2 博客卡

展示内容：

- 标题：博客
- 文章总数
- 最新文章标题
- 更新时间
- “进入文章”动作

数据来源：

- `GET /api/v1/blog/posts?pageNo=1&pageSize=1`

实现原则：

- 只取首页摘要
- 不在首页放标签列表
- 不在首页放搜索框

### 8.3 项目展示卡

展示内容：

- 标题：项目展示
- 仓库：`Deriou/Cloud-ops-hub`
- 简短说明：`Main distribution hub`
- GitHub 外链动作

实现原则：

- 当前只放一个仓库卡
- 不做多项目轮播或列表扩展
- 可先静态配置，不强制接 GitHub API

### 8.4 应用集合卡

展示内容：

- 标题：应用集合
- 当前单一入口：`Portal`
- 描述：`有哪些应用、状态如何、从哪进`

实现原则：

- 当前首页只展示一个应用入口
- Blog 不放进应用集合卡
- 后续如果扩展应用，也保持低密度入口卡样式

数据来源建议：

- 首版可静态
- 后续可由 Gateway 注册表映射出 Portal 入口

### 8.5 运维卡

展示内容：

- 标题：运维
- 状态标签：如 `Stable`
- 一句整体状态：如 `Online`
- 两个管理摘要块：如 `Gateway`、`Blog Admin`
- 底部可保留 `Jenkins`、`Grafana`、`Auth Later` 标签

实现原则：

- 运维卡只展示摘要与入口语义
- 不展示大表格、日志流、复杂图表
- Jenkins / Grafana 明确归运维，不进入应用集合

数据来源建议：

- Gateway 健康摘要
- Blog 管理能力入口状态
- 其余入口可以先静态占位

### 8.6 谏言卡

固定文案：

- `The coding was tough. Nearly killed me.`

设计要求：

- 使用正常大标题大小
- 蓝底白字
- 横跨右侧底部
- 明显比当前实现更轻，不要抢占过多视觉空间

## 9. 详情页设计约束

本轮重点是首页，但 Blog / Ops 子页也需要同步做风格收敛，避免首页和内页断层。

### 9.1 Blog 页面

保留：

- 搜索
- 列表
- 分页
- 详情页

调整：

- 与首页统一字体、圆角、颜色、间距
- 降低控制台感
- 更强调阅读感

主要文件：

- `web/src/views/BlogView.vue`
- `web/src/views/PostDetailView.vue`

### 9.2 Ops 页面

保留页面：

- Cluster
- Workloads
- Pipelines
- Diagnostics

调整：

- 保留结构，不在视觉上喧宾夺主
- 从深色控制台式局部表达，调整为更统一的浅色门户体系
- 仅日志片段、终端区域可保留深色块

主要文件：

- `web/src/views/OpsClusterView.vue`
- `web/src/views/OpsWorkloadsView.vue`
- `web/src/views/OpsPipelinesView.vue`
- `web/src/views/OpsDiagnosticsView.vue`

## 10. 组件与状态设计

### 10.1 建议保留并重用的组件

- `StatusPill.vue`
- `StatePanel.vue`
- `MetricCard.vue`

### 10.2 建议改造的组件

- `AppShell.vue`
- `AppGridCard.vue`

### 10.3 首页推荐拆分

如需提高可维护性，建议新增以下组件：

- `ProfileCard.vue`
- `BlogHeroCard.vue`
- `ProjectShowcaseCard.vue`
- `AppCollectionCard.vue`
- `OpsOverviewCard.vue`
- `ManifestoCard.vue`

如果时间有限，也可先保留在 `DashboardView.vue` 内，但必须按模块分段清晰组织。

### 10.4 数据策略

- 个人信息：静态常量
- 项目展示：静态常量
- 应用集合：静态常量，后续可转 Gateway 数据
- 博客：首页摘要接口
- 运维：首页摘要接口 + 少量静态入口

不要在首页做：

- 全局大状态 store 扩张
- 多数据源复杂级联依赖
- 大量并行请求后的复杂降级逻辑

## 11. 实施顺序

### Phase 1：更新首页原型与壳层

目标：

- 让 `web/` 实现和 `docs/web/index.html` 一致

任务：

- 改 `AppShell.vue` 顶栏结构
- 改首页整体缩放和布局
- 统一首页模块结构
- 去掉与目标不一致的信息块

### Phase 2：替换首页真实内容

任务：

- 接入头像与个人信息
- 替换标题、GitHub、邮箱
- 项目展示收敛为单仓库
- 应用集合收敛为单 Portal
- 谏言文案替换

### Phase 3：收敛首页数据来源

任务：

- 博客首页只读一条最新文章
- 运维首页只展示摘要
- 首页不再显示多余 tags / stats / registry 列表

### Phase 4：统一详情页视觉

任务：

- Blog 列表页和详情页统一风格
- Ops 四个页面风格统一
- 保留功能，但弱化控制台感

### Phase 5：验收与清理

任务：

- 清理无用首页组件逻辑
- 清理首页不再需要的旧数据块
- 自测桌面端首屏完整性

## 12. 具体文件修改建议

优先修改：

- `web/src/components/AppShell.vue`
- `web/src/views/DashboardView.vue`
- `web/src/style.css`

第二批修改：

- `web/src/views/BlogView.vue`
- `web/src/views/PostDetailView.vue`
- `web/src/views/OpsClusterView.vue`
- `web/src/views/OpsWorkloadsView.vue`
- `web/src/views/OpsPipelinesView.vue`
- `web/src/views/OpsDiagnosticsView.vue`

辅助修改：

- `web/src/lib/format.ts`
- `web/src/stores/portal.ts`
- `web/src/components/*`

文档与素材：

- `docs/web/webpdr.md`
- `docs/web/index.html`
- `docs/web/photo.jpg`

## 13. 不该做的事

- 不要把首页继续做成总控台
- 不要重新引入复杂统计区块
- 不要把 Blog 塞回应用集合
- 不要把 Jenkins / Grafana 放到应用集合
- 不要为了“看起来丰富”增加无意义数据
- 不要让首页依赖过多异步接口才能完整显示

## 14. 验收标准

### 14.1 首页

- 桌面端尽量首屏看完全部核心内容
- 顶栏左侧只有标题，没有图标
- 右侧主按钮是 GitHub
- 个人信息与文案全部为已确认内容
- 谏言卡不再显得过大
- 项目展示只有 `Deriou/Cloud-ops-hub`
- 应用集合只有 `Portal`
- Blog 仍单独显示
- 运维与应用集合边界明确

### 14.2 视觉

- 与 `docs/web/index.html` 当前方向一致
- 比当前 `web/` 实现更轻、更小、更透气
- 首页不再有“太满”的感觉

### 14.3 代码

- 不破坏现有 Blog / Ops 基础路由
- 组件职责更清晰
- 首页逻辑明显收敛
- 结构能被下一个窗口继续维护

## 15. 实施时的默认决策

如果实现过程中没有新的用户输入，默认按以下策略处理：

- GitHub 与邮箱直接写死到首页静态配置
- 项目展示使用静态仓库配置
- Portal 使用静态入口配置
- Blog 首页摘要从真实 Blog 接口读取
- 运维首页摘要保留轻量数据映射
- Jenkins / Grafana 先做入口语义，不实现首页级鉴权流程

## 16. 交接备注

后续窗口接手时，应优先把 `docs/web/index.html` 当作视觉参照，把 `docs/web/webpdr.md` 和本文件当作行为与实现规范。若出现冲突，优先级如下：

1. 本文件的实施约束
2. `docs/web/webpdr.md` 的产品边界
3. `docs/web/index.html` 的视觉参考
4. 当前 `web/` 代码现状
