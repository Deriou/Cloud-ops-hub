<script setup lang="ts">
import AppGridCard, { type AppDirectoryCard } from "@/components/AppGridCard.vue";
import StatePanel from "@/components/StatePanel.vue";
import { curatedApps } from "@/content/site";
import { formatDateTime, toneForStatus } from "@/lib/format";
import { usePortalStore } from "@/stores/portal";
import { computed, onMounted } from "vue";

const portalStore = usePortalStore();

const entries = computed<AppDirectoryCard[]>(() =>
  curatedApps.map((definition) => {
    const matchedRegistry =
      portalStore.sortedRegistry.find((item) => definition.matchKeys.includes(item.appKey)) ??
      portalStore.sortedRegistry.find((item) => definition.matchKeys.includes(item.title.toLowerCase()));
    const matchedHealth = matchedRegistry ? portalStore.healthMap.get(matchedRegistry.appKey) : undefined;
    const route = matchedRegistry?.route && matchedRegistry.route.startsWith("/") ? matchedRegistry.route : definition.fallbackRoute;

    return {
      title: matchedRegistry?.title ?? definition.title,
      description: matchedRegistry?.description ?? definition.description,
      route,
      routeLabel: matchedRegistry?.route ?? definition.fallbackRoute,
      statusLabel: matchedHealth?.status ?? matchedRegistry?.status ?? "CURATED",
      tone: toneForStatus(matchedHealth?.status ?? matchedRegistry?.status ?? "UP"),
      summary: matchedHealth?.message ?? definition.summary,
      appKey: matchedRegistry?.appKey ?? definition.matchKeys[0] ?? definition.title.toLowerCase(),
      checkedAt: matchedHealth?.checkedAt ? formatDateTime(matchedHealth.checkedAt) : undefined,
      ctaLabel: route === "/apps" ? "当前目录" : "进入应用",
      current: route === "/apps"
    };
  })
);

onMounted(() => {
  void portalStore.ensureHydrated();
});
</script>

<template>
  <section class="grid gap-4">
    <article class="cloud-card px-6 py-6 lg:px-7">
      <p class="eyebrow">Apps</p>
      <div class="mt-3 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 class="text-[2rem] font-extrabold tracking-tight text-slate-900">Portal 应用目录</h1>
          <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
            这里承接业务应用与产品入口，和运维工具分开；敏感入口后续再结合 key 与鉴权策略逐步接入。
          </p>
        </div>
        <span class="rounded-full bg-sky-100 px-4 py-2 text-xs font-semibold uppercase tracking-[0.18em] text-sky-700">
          Business Directory
        </span>
      </div>
    </article>

    <StatePanel
      v-if="portalStore.errorMessage"
      title="Gateway snapshot"
      :message="portalStore.errorMessage"
      tone="warning"
      :trace-id="portalStore.errorTraceId"
    />

    <div class="grid gap-4 lg:grid-cols-2">
      <AppGridCard v-for="entry in entries" :key="entry.appKey" :entry="entry" />
    </div>

    <article class="cloud-card px-6 py-6 lg:px-7">
      <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p class="eyebrow">Directory Notes</p>
          <h2 class="mt-2 text-xl font-bold text-slate-900">当前目录边界</h2>
        </div>
        <span class="rounded-full bg-slate-100 px-4 py-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
          No Grafana / Jenkins
        </span>
      </div>
      <p class="mt-4 text-sm leading-7 text-slate-600">
        `/apps` 只保留产品与应用目录，像 Grafana、Jenkins 这类运维与观测工具仍然放在首页运维卡与 Ops 子页里，不和应用目录混放。
      </p>
    </article>
  </section>
</template>
