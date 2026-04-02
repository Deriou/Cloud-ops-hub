<script setup lang="ts">
import StatusPill from "@/components/StatusPill.vue";
import { appConfig } from "@/lib/config";
import { toneForStatus } from "@/lib/format";
import { usePortalStore } from "@/stores/portal";
import { computed, onMounted } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

const route = useRoute();
const portalStore = usePortalStore();

const navigation = [
  { to: "/", label: "Portal", description: "Gateway 看板" },
  { to: "/blog", label: "Blog", description: "博客浏览与搜索" },
  { to: "/ops/cluster", label: "Cluster", description: "K3s 集群摘要" },
  { to: "/ops/workloads", label: "Workloads", description: "工作负载清单" },
  { to: "/ops/pipelines", label: "Pipelines", description: "流水线运行" },
  { to: "/ops/diagnostics", label: "Diagnosis", description: "发布诊断报告" }
];

const opsKeyState = computed(() => (appConfig.opsKey ? "已注入" : "未配置"));
const opsKeyTone = computed(() => (appConfig.opsKey ? "normal" : "warning"));
const accessTone = computed(() => toneForStatus(portalStore.accessMode === "admin" ? "UP" : "DEGRADED"));

onMounted(() => {
  if (!portalStore.hydrated && !portalStore.isLoading) {
    void portalStore.hydrate();
  }
});
</script>

<template>
  <div class="min-h-screen bg-[radial-gradient(circle_at_top,_rgba(56,189,248,0.16),_transparent_30%),linear-gradient(180deg,#020617_0%,#0f172a_35%,#020617_100%)] text-slate-100">
    <div class="mx-auto grid min-h-screen max-w-[1600px] gap-6 px-4 py-4 lg:grid-cols-[280px_minmax(0,1fr)] lg:px-6">
      <aside class="rounded-[28px] border border-border bg-slate-950/70 p-5 shadow-soft backdrop-blur-md">
        <div class="rounded-3xl border border-white/10 bg-white/5 p-5">
          <p class="text-xs uppercase tracking-[0.28em] text-cyan-200">Cloud Ops Hub</p>
          <h1 class="mt-3 text-2xl font-semibold text-white">SRE-first portal</h1>
          <p class="mt-3 text-sm leading-6 text-slate-300">
            先接通 Gateway 与 Blog，再为 D 阶段 Ops-Core 预留稳定的看板壳层。
          </p>
        </div>

        <div class="mt-5 grid gap-3">
          <div class="rounded-3xl border border-white/10 bg-white/5 p-4">
            <p class="text-xs uppercase tracking-[0.24em] text-slate-500">access mode</p>
            <div class="mt-3">
              <StatusPill :label="portalStore.accessMode" :tone="accessTone" />
            </div>
          </div>
          <div class="rounded-3xl border border-white/10 bg-white/5 p-4">
            <p class="text-xs uppercase tracking-[0.24em] text-slate-500">ops key</p>
            <div class="mt-3">
              <StatusPill :label="opsKeyState" :tone="opsKeyTone" />
            </div>
          </div>
        </div>

        <nav class="mt-6 grid gap-2">
          <RouterLink
            v-for="item in navigation"
            :key="item.to"
            :to="item.to"
            class="rounded-3xl border p-4 transition-all duration-300 ease-in-out"
            :class="route.path === item.to || route.path.startsWith(`${item.to}/`)
              ? 'border-cyan-400/30 bg-cyan-400/10 text-white'
              : 'border-white/10 bg-white/5 text-slate-300 hover:border-white/20 hover:bg-white/10'"
          >
            <p class="font-medium">{{ item.label }}</p>
            <p class="mt-1 text-sm text-slate-400">{{ item.description }}</p>
          </RouterLink>
        </nav>
      </aside>

      <main class="rounded-[28px] border border-border bg-slate-950/50 p-4 shadow-soft backdrop-blur-md lg:p-6">
        <header class="mb-6 flex flex-col gap-4 rounded-3xl border border-white/10 bg-white/5 p-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Portal UI</p>
            <p class="mt-2 text-2xl font-semibold text-white">Bento dashboard shell</p>
          </div>
          <div class="flex flex-wrap items-center gap-3 text-sm text-slate-300">
            <span class="rounded-full border border-white/10 px-3 py-1 font-mono">{{ route.fullPath }}</span>
            <button
              type="button"
              class="rounded-full border border-cyan-400/30 bg-cyan-400/10 px-4 py-2 text-cyan-100 transition-all duration-300 ease-in-out hover:bg-cyan-400/20"
              @click="portalStore.hydrate"
            >
              刷新 Gateway
            </button>
          </div>
        </header>

        <div v-if="portalStore.errorMessage" class="mb-6 rounded-3xl border border-amber-400/30 bg-amber-400/10 p-4 text-sm text-amber-100">
          {{ portalStore.errorMessage }}
        </div>

        <RouterView />
      </main>
    </div>
  </div>
</template>
