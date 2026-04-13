<script setup lang="ts">
import StatusPill from "@/components/StatusPill.vue";
import { toneForStatus } from "@/lib/format";
import { usePortalStore } from "@/stores/portal";
import { computed, onMounted } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

const route = useRoute();
const portalStore = usePortalStore();

const navigation = [
  { to: "/", label: "Portal", description: "门户总览" },
  { to: "/blog", label: "Blog", description: "内容浏览" },
  { to: "/ops/cluster", label: "Cluster", description: "集群摘要" },
  { to: "/ops/workloads", label: "Workloads", description: "工作负载" },
  { to: "/ops/pipelines", label: "Pipelines", description: "流水线状态" },
  { to: "/ops/diagnostics", label: "Diagnosis", description: "诊断报告" }
];

const accessLabel = computed(() => (portalStore.accessMode === "admin" ? "admin" : portalStore.accessMode === "guest" ? "guest" : "syncing"));
const accessTone = computed(() => {
  if (portalStore.accessMode === "admin") {
    return "warning";
  }
  return toneForStatus(portalStore.accessMode === "guest" ? "UP" : "DEGRADED");
});

const healthSummary = computed(() => {
  const total = portalStore.healthList.length;
  const healthy = portalStore.healthList.filter((item) => item.status === "UP").length;
  const degraded = portalStore.healthList.filter((item) => item.status === "DEGRADED").length;
  const down = portalStore.healthList.filter((item) => item.status === "DOWN").length;

  return {
    label: total > 0 ? `${healthy}/${total} healthy` : "waiting",
    tone: down > 0 ? "danger" : degraded > 0 ? "warning" : "normal"
  } as const;
});

function isActive(path: string): boolean {
  return path === "/" ? route.path === "/" : route.path.startsWith(path);
}

onMounted(() => {
  if (!portalStore.hydrated && !portalStore.isLoading) {
    void portalStore.hydrate();
  }
});
</script>

<template>
  <div class="page-body min-h-screen pb-24 text-ink md:pb-10">
    <div class="mx-auto flex min-h-screen max-w-[1440px] flex-col gap-6 px-4 py-5 lg:px-6">
      <header class="cloud-card px-5 py-5 lg:px-7">
        <div class="flex flex-col gap-5">
          <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div class="flex items-start gap-4">
              <div class="flex h-14 w-14 items-center justify-center rounded-[1.6rem] bg-sky-500 text-xl font-semibold text-white shadow-float">
                D
              </div>
              <div>
                <p class="eyebrow">Cloud Ops Hub</p>
                <h1 class="mt-2 text-2xl font-bold tracking-tight text-ink lg:text-3xl">Deriou 的个人编程笔记</h1>
                <p class="mt-2 max-w-2xl text-sm leading-6 text-ink-soft">
                  云原生开发、知识沉淀与运维观测集中在一处的轻量个人门户。
                </p>
              </div>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <StatusPill :label="accessLabel" :tone="accessTone" />
              <StatusPill :label="healthSummary.label" :tone="healthSummary.tone" />
              <button
                type="button"
                class="cloud-badge px-4 py-2 text-sm font-medium transition-all duration-300 hover:-translate-y-0.5 hover:bg-white"
                @click="portalStore.hydrate"
              >
                刷新门户状态
              </button>
            </div>
          </div>

          <nav class="flex gap-3 overflow-x-auto pb-1">
            <RouterLink
              v-for="item in navigation"
              :key="item.to"
              :to="item.to"
              class="min-w-[144px] rounded-[1.4rem] border px-4 py-3 transition-all duration-300"
              :class="isActive(item.to)
                ? 'border-sky-200 bg-sky-50 text-sky-800 shadow-float'
                : 'border-sky-100/80 bg-white/60 text-ink-soft hover:-translate-y-0.5 hover:bg-white'"
            >
              <p class="text-sm font-semibold">{{ item.label }}</p>
              <p class="mt-1 text-xs leading-5 opacity-80">{{ item.description }}</p>
            </RouterLink>
          </nav>
        </div>
      </header>

      <main class="cloud-card flex-1 px-4 py-5 lg:px-6 lg:py-6">
        <div class="mb-6 flex flex-col gap-3 border-b soft-divider pb-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p class="eyebrow">Portal UI</p>
            <p class="mt-2 text-2xl font-semibold text-ink">Blue-island dashboard shell</p>
          </div>
          <div class="flex flex-wrap items-center gap-3 text-sm text-ink-soft">
            <span class="cloud-badge px-3 py-1.5 font-mono">{{ route.fullPath }}</span>
            <span class="cloud-badge px-3 py-1.5">Guest-first experience</span>
          </div>
        </div>

        <div
          v-if="portalStore.errorMessage"
          class="mb-6 rounded-[1.6rem] border border-amber-200 bg-amber-50 px-5 py-4 text-sm text-amber-800"
        >
          <p>{{ portalStore.errorMessage }}</p>
          <p v-if="portalStore.errorTraceId" class="mt-2 font-mono text-xs">traceId: {{ portalStore.errorTraceId }}</p>
        </div>

        <RouterView />
      </main>

      <footer class="pb-3 text-center text-xs text-ink-soft">
        <a
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          class="transition-colors duration-300 hover:text-ink"
        >
          冀ICP备2026010164号
        </a>
      </footer>
    </div>

    <nav class="cloud-card fixed inset-x-3 bottom-3 z-20 grid grid-cols-6 gap-2 px-2 py-2 md:hidden">
      <RouterLink
        v-for="item in navigation"
        :key="`mobile-${item.to}`"
        :to="item.to"
        class="rounded-[1.2rem] px-2 py-2 text-center text-[11px] font-semibold transition-all duration-300"
        :class="isActive(item.to) ? 'bg-sky-500 text-white shadow-float' : 'text-ink-soft'"
      >
        {{ item.label }}
      </RouterLink>
    </nav>
  </div>
</template>
