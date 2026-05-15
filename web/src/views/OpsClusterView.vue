<script setup lang="ts">
import { fetchClusterSummary, fetchServiceHealth } from "@/api/ops";
import MetricCard from "@/components/MetricCard.vue";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { appConfig } from "@/lib/config";
import { toUiError } from "@/lib/errors";
import { toneForStatus } from "@/lib/format";
import type { ClusterSummary, ServiceHealth } from "@/types/ops";
import { Activity, ArrowUpRight, BarChart3, GitBranch, Server } from "lucide-vue-next";
import { computed, onMounted, ref } from "vue";

const summary = ref<ClusterSummary | null>(null);
const serviceHealth = ref<ServiceHealth[]>([]);
const loading = ref(false);
const serviceErrorMessage = ref("");
const serviceErrorTraceId = ref("");
const errorMessage = ref("");
const errorTraceId = ref("");

const healthyServiceCount = computed(() => serviceHealth.value.filter((service) => service.status === "UP").length);
const hasGrafanaLink = computed(() => appConfig.grafanaDashboardUrl.length > 0);

async function loadSummary() {
  loading.value = true;
  errorMessage.value = "";
  errorTraceId.value = "";
  serviceErrorMessage.value = "";
  serviceErrorTraceId.value = "";

  try {
    summary.value = await fetchClusterSummary();
    try {
      serviceHealth.value = await fetchServiceHealth();
    } catch (error) {
      const uiError = toUiError(error, "读取服务健康失败");
      serviceErrorMessage.value = uiError.message;
      serviceErrorTraceId.value = uiError.traceId;
      serviceHealth.value = [];
    }
  } catch (error) {
    const uiError = toUiError(error, "读取集群摘要失败");
    errorMessage.value = uiError.message;
    errorTraceId.value = uiError.traceId;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadSummary();
});
</script>

<template>
  <section class="grid gap-4">
    <article class="cloud-card px-6 py-6 lg:px-7">
      <p class="eyebrow">Ops</p>
      <div class="mt-3 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 class="text-[2rem] font-extrabold tracking-tight text-slate-900">运维状态</h1>
        </div>
        <span class="inline-flex w-fit items-center gap-2 rounded-full bg-emerald-100 px-4 py-2 text-xs font-semibold uppercase tracking-[0.18em] text-emerald-700">
          <Activity :size="15" />
          Public Status
        </span>
      </div>
    </article>

    <StatePanel v-if="loading" title="Cluster loading" message="正在汇总集群状态..." />
    <StatePanel v-else-if="errorMessage" title="Cluster unavailable" :message="errorMessage" tone="danger" :trace-id="errorTraceId" />

    <template v-else-if="summary">
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <MetricCard v-for="stat in summary.stats" :key="stat.label" :stat="stat" />
      </div>

      <article class="cloud-card p-6">
        <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p class="eyebrow">Service Health</p>
            <h2 class="mt-2 text-[1.5rem] font-extrabold tracking-tight text-slate-900">服务健康</h2>
          </div>
          <span class="font-mono text-sm text-slate-500">{{ healthyServiceCount }}/{{ serviceHealth.length }} online</span>
        </div>

        <StatePanel
          v-if="serviceErrorMessage"
          class="mt-5"
          title="Service health unavailable"
          :message="serviceErrorMessage"
          tone="danger"
          :trace-id="serviceErrorTraceId"
        />

        <div v-else class="mt-5 grid gap-4 md:grid-cols-2">
          <section v-for="service in serviceHealth" :key="service.name" class="sub-card p-5">
            <div class="flex items-start justify-between gap-4">
              <div class="min-w-0">
                <p class="eyebrow">{{ service.name }}</p>
                <h3 class="mt-2 truncate text-lg font-bold text-slate-900">{{ service.displayName }}</h3>
              </div>
              <StatusPill :label="service.status" :tone="toneForStatus(service.status)" />
            </div>
            <p class="mt-5 rounded-2xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-600">
              {{ service.status === "UP" ? "服务运行正常" : "等待恢复" }}
            </p>
          </section>
        </div>
      </article>

      <article class="cloud-card p-6">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p class="eyebrow">Observability</p>
            <h2 class="mt-2 text-[1.5rem] font-extrabold tracking-tight text-slate-900">可观测性</h2>
          </div>
          <a
            v-if="hasGrafanaLink"
            :href="appConfig.grafanaDashboardUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex w-fit items-center gap-2 rounded-full bg-sky-500 px-5 py-2.5 text-sm font-bold text-white shadow-lg shadow-sky-500/20 transition hover:bg-sky-600"
          >
            <BarChart3 :size="17" />
            Grafana
            <ArrowUpRight :size="16" />
          </a>
        </div>

        <div class="mt-5 grid gap-4 md:grid-cols-3">
          <section class="sub-card p-5">
            <div class="flex h-10 w-10 items-center justify-center rounded-2xl bg-sky-50 text-sky-500">
              <Server :size="21" />
            </div>
            <h3 class="mt-4 text-base font-bold text-slate-900">Runtime</h3>
            <p class="mt-2 text-sm leading-6 text-slate-600">K3s 单节点承载 Web、Gateway 与 Blog 服务。</p>
          </section>

          <section class="sub-card p-5">
            <div class="flex h-10 w-10 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600">
              <Activity :size="21" />
            </div>
            <h3 class="mt-4 text-base font-bold text-slate-900">Metrics</h3>
            <p class="mt-2 text-sm leading-6 text-slate-600">Prometheus 汇总服务可用性与基础资源指标。</p>
          </section>

          <section class="sub-card p-5">
            <div class="flex h-10 w-10 items-center justify-center rounded-2xl bg-amber-50 text-amber-600">
              <GitBranch :size="21" />
            </div>
            <h3 class="mt-4 text-base font-bold text-slate-900">Delivery</h3>
            <p class="mt-2 text-sm leading-6 text-slate-600">发布链路由 Jenkins 驱动，公开页只展示结果状态。</p>
          </section>
        </div>
      </article>
    </template>
  </section>
</template>
