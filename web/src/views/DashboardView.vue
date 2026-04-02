<script setup lang="ts">
import AppGridCard from "@/components/AppGridCard.vue";
import MetricCard from "@/components/MetricCard.vue";
import { usePortalStore } from "@/stores/portal";
import { computed, onMounted } from "vue";

const portalStore = usePortalStore();

const stats = computed(() => {
  const total = portalStore.registry.length;
  const upCount = portalStore.healthList.filter((item) => item.status === "UP").length;
  const degradedCount = portalStore.healthList.filter((item) => item.status === "DEGRADED").length;
  const downCount = portalStore.healthList.filter((item) => item.status === "DOWN").length;

  return [
    { label: "应用总数", value: String(total), trend: [1, 1, 2, 2, 3, total], tone: "normal" as const },
    { label: "健康实例", value: String(upCount), trend: [0, 1, 1, 2, 2, upCount], tone: "normal" as const },
    { label: "降级实例", value: String(degradedCount), trend: [0, 0, 1, 0, 1, degradedCount], tone: "warning" as const },
    { label: "异常实例", value: String(downCount), trend: [0, 0, 0, 1, 1, downCount], tone: downCount > 0 ? ("danger" as const) : ("normal" as const) }
  ];
});

const sortedApps = computed(() =>
  [...portalStore.registry].sort((left, right) => left.sortOrder - right.sortOrder || left.title.localeCompare(right.title))
);

onMounted(() => {
  if (!portalStore.hydrated && !portalStore.isLoading) {
    void portalStore.hydrate();
  }
});
</script>

<template>
  <section class="grid gap-6">
    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      <MetricCard v-for="item in stats" :key="item.label" :stat="item" />
    </div>

    <div class="grid gap-6 xl:grid-cols-[2fr_1fr]">
      <section class="grid gap-4">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Gateway registry</p>
            <h2 class="mt-2 text-2xl font-semibold text-white">应用门户总览</h2>
          </div>
          <p class="text-sm text-slate-400">真实数据来自 `/api/v1/gateway/apps` 与 `/health`</p>
        </div>

        <div v-if="portalStore.isLoading" class="rounded-3xl border border-white/10 bg-white/5 p-6 text-slate-300">
          正在同步 Gateway 注册表与健康状态...
        </div>

        <div v-else class="grid gap-4 md:grid-cols-2">
          <AppGridCard
            v-for="app in sortedApps"
            :key="app.appKey"
            :app="app"
            :health="portalStore.healthMap.get(app.appKey)"
          />
        </div>
      </section>

      <aside class="grid gap-4">
        <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
          <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Mode insight</p>
          <h3 class="mt-3 text-xl font-semibold text-white">当前访问模式</h3>
          <p class="mt-3 text-sm leading-6 text-slate-300">
            已从 Gateway 读取到 `{{ portalStore.accessMode }}` 模式。你后面接博客写接口时，可以直接沿用这套
            `X-Ops-Key` 鉴权约束。
          </p>
        </article>

        <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
          <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Frontend contract</p>
          <h3 class="mt-3 text-xl font-semibold text-white">D 阶段预留点</h3>
          <ul class="mt-4 grid gap-3 text-sm text-slate-300">
            <li class="rounded-2xl border border-white/10 bg-white/5 p-4">D1: 集群摘要与工作负载列表接入 Ops 区域。</li>
            <li class="rounded-2xl border border-white/10 bg-white/5 p-4">D2: Jenkins 任务页从 mock 状态切到真实 runId 查询。</li>
            <li class="rounded-2xl border border-white/10 bg-white/5 p-4">D3: 发布诊断页替换为真实报告结构。</li>
          </ul>
        </article>
      </aside>
    </div>
  </section>
</template>
