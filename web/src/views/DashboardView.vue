<script setup lang="ts">
import MetricCard from "@/components/MetricCard.vue";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { fetchPosts, fetchTags } from "@/api/blog";
import { fetchClusterSummary } from "@/api/ops";
import { toUiError } from "@/lib/errors";
import { clampTrend, formatDateTime, formatFullDateTime, toneForStatus } from "@/lib/format";
import { usePortalStore } from "@/stores/portal";
import type { PostSummary, TaxonomyItem } from "@/types/blog";
import type { ClusterSummary, OpsStat } from "@/types/ops";
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

const portalStore = usePortalStore();
const latestPost = ref<PostSummary | null>(null);
const postTotal = ref(0);
const tags = ref<TaxonomyItem[]>([]);
const clusterSummary = ref<ClusterSummary | null>(null);
const blogLoading = ref(false);
const opsLoading = ref(false);
const blogError = ref("");
const blogTraceId = ref("");
const opsError = ref("");
const opsTraceId = ref("");

const featuredProjects = [
  {
    name: "Cloud-ops-hub",
    description: "Mono-repo distribution hub for gateway, blog, ops and web delivery.",
    status: "active"
  },
  {
    name: "Kube-Metric-Ex",
    description: "Lightweight Prometheus extension with pragmatic metrics for single-node learning clusters.",
    status: "stable"
  },
  {
    name: "Ops Notes",
    description: "Personal runbooks, deployment learnings and incident recovery snippets.",
    status: "curating"
  }
];

async function loadBlogSnapshot() {
  blogLoading.value = true;
  blogError.value = "";
  blogTraceId.value = "";

  const [postResult, tagResult] = await Promise.allSettled([fetchPosts(1, 3), fetchTags()]);

  if (postResult.status === "fulfilled") {
    latestPost.value = postResult.value.records[0] ?? null;
    postTotal.value = postResult.value.total;
  } else {
    const uiError = toUiError(postResult.reason, "读取博客数据失败");
    blogError.value = uiError.message;
    blogTraceId.value = uiError.traceId;
  }

  if (tagResult.status === "fulfilled") {
    tags.value = tagResult.value.slice(0, 6);
  } else if (!blogError.value) {
    const uiError = toUiError(tagResult.reason, "读取标签失败");
    blogError.value = uiError.message;
    blogTraceId.value = uiError.traceId;
  }

  blogLoading.value = false;
}

async function loadOpsSummary() {
  opsLoading.value = true;
  opsError.value = "";
  opsTraceId.value = "";

  try {
    clusterSummary.value = await fetchClusterSummary();
  } catch (error) {
    const uiError = toUiError(error, "读取集群摘要失败");
    opsError.value = uiError.message;
    opsTraceId.value = uiError.traceId;
  } finally {
    opsLoading.value = false;
  }
}

const stats = computed(() => {
  const total = portalStore.registry.length;
  const upCount = portalStore.healthList.filter((item) => item.status === "UP").length;
  const degradedCount = portalStore.healthList.filter((item) => item.status === "DEGRADED").length;
  const downCount = portalStore.healthList.filter((item) => item.status === "DOWN").length;
  const tagCount = tags.value.length;

  return [
    { label: "应用总数", value: String(total), trend: clampTrend([1, 1, 2, 2, 3, Math.max(total, 1)]), tone: "normal" as const },
    { label: "健康实例", value: String(upCount), trend: clampTrend([0, 1, 1, 2, 2, Math.max(upCount, 1)]), tone: "normal" as const },
    { label: "标签热度", value: String(tagCount), trend: clampTrend([1, 2, 3, 3, 4, Math.max(tagCount, 1)]), tone: "normal" as const },
    {
      label: "告警实例",
      value: String(degradedCount + downCount),
      trend: clampTrend([0, 0, 1, 1, degradedCount, degradedCount + downCount]),
      tone: downCount > 0 ? ("danger" as const) : ("warning" as const)
    }
  ] satisfies OpsStat[];
});

const sortedApps = computed(() =>
  [...portalStore.registry].sort((left, right) => left.sortOrder - right.sortOrder || left.title.localeCompare(right.title))
);

const quickApps = computed(() => sortedApps.value.slice(0, 4));
const opsHighlights = computed(() => clusterSummary.value?.stats.slice(0, 3) ?? []);
const systemTone = computed(() => {
  const downCount = portalStore.healthList.filter((item) => item.status === "DOWN").length;
  const degradedCount = portalStore.healthList.filter((item) => item.status === "DEGRADED").length;
  return downCount > 0 ? "danger" : degradedCount > 0 ? "warning" : "normal";
});
const systemLabel = computed(() => {
  const total = portalStore.healthList.length;
  const upCount = portalStore.healthList.filter((item) => item.status === "UP").length;
  return total > 0 ? `${upCount}/${total} online` : "waiting";
});
const accessModeTone = computed(() => {
  if (portalStore.accessMode === "admin") {
    return "warning";
  }

  if (portalStore.accessMode === "guest") {
    return "normal";
  }

  return "danger";
});

onMounted(() => {
  if (!portalStore.hydrated && !portalStore.isLoading) {
    void portalStore.hydrate();
  }

  void loadBlogSnapshot();
  void loadOpsSummary();
});
</script>

<template>
  <section class="grid gap-6 lg:grid-cols-12">
    <article class="cloud-card p-6 lg:col-span-4 xl:p-8">
      <div class="flex flex-col items-center text-center">
        <div class="flex h-28 w-28 items-center justify-center rounded-full bg-gradient-to-br from-sky-400 to-cyan-500 text-4xl font-bold text-white shadow-float">
          D
        </div>
        <p class="eyebrow mt-6">Developer profile</p>
        <h2 class="mt-3 text-3xl font-bold text-ink">zhonger</h2>
        <p class="mt-2 text-sm font-medium text-ink-soft">Developer, maintainer and cloud-native learner</p>
      </div>

      <div class="mt-8 grid grid-cols-3 gap-3">
        <div class="cloud-card-soft p-4 text-center">
          <p class="text-xs uppercase tracking-[0.2em] text-ink-soft">Apps</p>
          <p class="mt-3 font-mono text-2xl font-semibold text-ink">{{ sortedApps.length }}</p>
        </div>
        <div class="cloud-card-soft p-4 text-center">
          <p class="text-xs uppercase tracking-[0.2em] text-ink-soft">Posts</p>
          <p class="mt-3 font-mono text-2xl font-semibold text-ink">{{ postTotal }}</p>
        </div>
        <div class="cloud-card-soft p-4 text-center">
          <p class="text-xs uppercase tracking-[0.2em] text-ink-soft">Tags</p>
          <p class="mt-3 font-mono text-2xl font-semibold text-ink">{{ tags.length }}</p>
        </div>
      </div>

      <div class="mt-8 rounded-[1.6rem] border border-sky-100 bg-sky-50/75 p-5">
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="eyebrow">Current focus</p>
            <p class="mt-2 text-lg font-semibold text-ink">Single-pane cloud-native workspace</p>
          </div>
          <StatusPill :label="systemLabel" :tone="systemTone" />
        </div>
        <p class="mt-3 text-sm leading-6 text-ink-soft">
          以轻量的蓝白云岛布局整合博客、应用入口与运维状态，让日常浏览和排障保持在同一体验里。
        </p>
      </div>

      <div class="mt-8">
        <div class="flex items-center justify-between">
          <p class="eyebrow">Hot tags</p>
          <RouterLink to="/blog" class="text-sm font-medium text-sky-700 hover:text-sky-900">进入博客</RouterLink>
        </div>
        <div class="mt-4 flex flex-wrap gap-2">
          <span
            v-for="tag in tags"
            :key="tag.id"
            class="rounded-full border border-sky-100 bg-white/80 px-3 py-2 text-xs font-medium text-ink-soft"
          >
            {{ tag.name }}
          </span>
          <span v-if="!blogLoading && tags.length === 0" class="text-sm text-ink-soft">标签数据准备中</span>
        </div>
      </div>
    </article>

    <div class="grid gap-6 lg:col-span-8 md:grid-cols-2">
      <section class="cloud-card p-5 md:col-span-2 xl:p-6">
        <div class="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
          <div>
            <p class="eyebrow">Core metrics</p>
            <h2 class="mt-2 text-2xl font-semibold text-ink">门户核心指标</h2>
          </div>
          <p class="text-sm text-ink-soft">来源于 Gateway 注册表、Blog 摘要与系统健康聚合。</p>
        </div>
        <div class="mt-5 grid gap-4 xl:grid-cols-4 md:grid-cols-2">
          <MetricCard v-for="item in stats" :key="item.label" :stat="item" />
        </div>
      </section>

      <article class="cloud-card p-6">
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="eyebrow">Latest entry</p>
            <h3 class="mt-2 text-xl font-semibold text-ink">最新博客</h3>
          </div>
          <p class="font-mono text-sm text-ink-soft">{{ postTotal }} posts</p>
        </div>

        <div class="mt-5">
          <StatePanel v-if="blogLoading" title="Blog sync" message="正在读取最新文章与标签摘要..." />
          <StatePanel
            v-else-if="blogError && !latestPost"
            title="Blog error"
            :message="blogError"
            tone="danger"
            :trace-id="blogTraceId"
          />
          <div v-else-if="latestPost" class="rounded-[1.6rem] border border-sky-100 bg-white/75 p-5">
            <p class="eyebrow">Updated {{ formatDateTime(latestPost.updateTime) }}</p>
            <RouterLink :to="`/blog/posts/${latestPost.id}`" class="mt-3 block text-2xl font-semibold text-ink hover:text-sky-700">
              {{ latestPost.title }}
            </RouterLink>
            <p class="mt-4 text-sm leading-7 text-ink-soft">{{ latestPost.summary }}</p>
            <div class="mt-5 flex items-center justify-between gap-3">
              <span class="cloud-badge px-3 py-1.5 text-xs font-medium">Guest readable</span>
              <RouterLink :to="`/blog/posts/${latestPost.id}`" class="text-sm font-semibold text-sky-700 hover:text-sky-900">
                阅读全文
              </RouterLink>
            </div>
          </div>
          <StatePanel v-else title="Blog waiting" message="暂无文章摘要，后端就绪后会自动补全这里。" />
        </div>
      </article>

      <article class="cloud-card p-6">
        <div>
          <p class="eyebrow">Featured projects</p>
          <h3 class="mt-2 text-xl font-semibold text-ink">项目展示</h3>
        </div>
        <div class="mt-5 grid gap-3">
          <article v-for="project in featuredProjects" :key="project.name" class="cloud-card-soft p-4">
            <div class="flex items-start justify-between gap-3">
              <div>
                <p class="text-base font-semibold text-ink">{{ project.name }}</p>
                <p class="mt-2 text-sm leading-6 text-ink-soft">{{ project.description }}</p>
              </div>
              <span class="cloud-badge px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.18em]">
                {{ project.status }}
              </span>
            </div>
          </article>
        </div>
      </article>

      <article class="cloud-card p-6">
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="eyebrow">App shortcuts</p>
            <h3 class="mt-2 text-xl font-semibold text-ink">常用应用</h3>
          </div>
          <StatusPill :label="portalStore.accessMode || 'unknown'" :tone="accessModeTone" />
        </div>

        <div class="mt-5">
          <StatePanel
            v-if="portalStore.isLoading && sortedApps.length === 0"
            title="Gateway sync"
            message="正在同步应用注册表与健康状态..."
          />
          <StatePanel
            v-else-if="portalStore.errorMessage && sortedApps.length === 0"
            title="Gateway error"
            :message="portalStore.errorMessage"
            tone="danger"
            :trace-id="portalStore.errorTraceId"
          />
          <div v-else class="grid gap-3 sm:grid-cols-2">
            <RouterLink
              v-for="app in quickApps"
              :key="app.appKey"
              :to="app.route.startsWith('/') ? app.route : '/'"
              class="rounded-[1.4rem] border border-sky-100 bg-white/75 p-4 transition-all duration-300 hover:-translate-y-1 hover:shadow-float"
            >
              <div class="flex items-center justify-between gap-3">
                <div class="flex items-center gap-3">
                  <div class="flex h-10 w-10 items-center justify-center rounded-2xl bg-sky-50 font-mono text-sm font-semibold text-sky-700">
                    {{ app.title.slice(0, 1).toUpperCase() }}
                  </div>
                  <div>
                    <p class="text-sm font-semibold text-ink">{{ app.title }}</p>
                    <p class="mt-1 text-xs text-ink-soft">{{ app.description }}</p>
                  </div>
                </div>
                <StatusPill
                  :label="portalStore.healthMap.get(app.appKey)?.status ?? app.status"
                  :tone="toneForStatus(portalStore.healthMap.get(app.appKey)?.status ?? app.status)"
                />
              </div>
            </RouterLink>
          </div>
        </div>
      </article>

      <article class="cloud-card p-6">
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="eyebrow">Ops overview</p>
            <h3 class="mt-2 text-xl font-semibold text-ink">运维总览</h3>
          </div>
          <RouterLink to="/ops/cluster" class="text-sm font-semibold text-sky-700 hover:text-sky-900">查看详情</RouterLink>
        </div>

        <div class="mt-5">
          <StatePanel v-if="opsLoading" title="Ops sync" message="正在拉取集群摘要与核心指标..." />
          <StatePanel
            v-else-if="opsError"
            title="Ops error"
            :message="opsError"
            tone="danger"
            :trace-id="opsTraceId"
          />
          <div v-else-if="clusterSummary" class="grid gap-4">
            <div class="rounded-[1.6rem] border border-sky-100 bg-white/75 p-5">
              <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p class="text-lg font-semibold text-ink">{{ clusterSummary.clusterName }}</p>
                  <p class="mt-2 text-sm text-ink-soft">{{ clusterSummary.region }}</p>
                </div>
                <span class="cloud-badge px-3 py-1.5 text-xs font-medium">
                  checked {{ formatFullDateTime(clusterSummary.checkedAt) }}
                </span>
              </div>
            </div>

            <div class="grid gap-3">
              <div
                v-for="item in opsHighlights"
                :key="item.label"
                class="flex items-center justify-between rounded-[1.3rem] border border-sky-100 bg-sky-50/60 px-4 py-3"
              >
                <div>
                  <p class="text-sm font-medium text-ink">{{ item.label }}</p>
                  <p class="mt-1 text-xs text-ink-soft">1h trend snapshot</p>
                </div>
                <span class="font-mono text-lg font-semibold text-ink">{{ item.value }}</span>
              </div>
            </div>

            <div class="flex flex-wrap gap-2">
              <RouterLink to="/ops/cluster" class="cloud-badge px-3 py-2 text-sm font-medium">Cluster</RouterLink>
              <RouterLink to="/ops/workloads" class="cloud-badge px-3 py-2 text-sm font-medium">Workloads</RouterLink>
              <RouterLink to="/ops/diagnostics" class="cloud-badge px-3 py-2 text-sm font-medium">Diagnosis</RouterLink>
            </div>
          </div>
          <StatePanel v-else title="Ops waiting" message="集群摘要暂未返回，可继续浏览其他模块。" />
        </div>
      </article>

      <article class="rounded-[2rem] bg-sky-500 p-6 text-white shadow-soft md:col-span-2">
        <p class="eyebrow !text-sky-100/80">Manifesto</p>
        <p class="mt-4 text-2xl font-medium leading-relaxed lg:text-3xl">
          "Stay hungry, stay foolish. Exploring the boundaries of cloud-native possibilities."
        </p>
        <p class="mt-5 text-sm leading-6 text-sky-50/90">
          用一个能日常使用的门户，把学习、编码、发布和观察系统状态串成持续反馈回路。
        </p>
      </article>
    </div>
  </section>
</template>
