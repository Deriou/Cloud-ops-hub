<script setup lang="ts">
import { fetchClusterSummary } from "@/api/ops";
import MetricCard from "@/components/MetricCard.vue";
import StatePanel from "@/components/StatePanel.vue";
import { appConfig } from "@/lib/config";
import { toUiError } from "@/lib/errors";
import { formatFullDateTime } from "@/lib/format";
import type { ClusterSummary } from "@/types/ops";
import { onMounted, ref } from "vue";

const demoTraceId = "demo-loki-003";
const demoRequestCommand = `curl -i http://deriou.com/api/v1/blog/tags -H "X-Trace-Id: ${demoTraceId}"`;
const demoLogQl = `{namespace="cloud-ops"} |= "traceId=${demoTraceId}"`;
const prometheusPortForwardCommand =
  "ssh -L 9090:127.0.0.1:9090 deriou@8.145.50.162 && sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring port-forward svc/prometheus-server 9090:80 --address 127.0.0.1";

const summary = ref<ClusterSummary | null>(null);
const loading = ref(false);
const errorMessage = ref("");
const errorTraceId = ref("");

async function loadSummary() {
  loading.value = true;
  errorMessage.value = "";
  errorTraceId.value = "";

  try {
    summary.value = await fetchClusterSummary();
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
      <p class="eyebrow">Ops / Cluster</p>
      <h1 class="mt-3 text-[2rem] font-extrabold tracking-tight text-slate-900">集群摘要</h1>
      <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
        保留原有结构与数据接口，但回到更轻的门户表达，只展示关键摘要而不是控制台式总览。
      </p>
    </article>

    <StatePanel v-if="loading" title="Cluster loading" message="正在汇总集群状态..." />
    <StatePanel v-else-if="errorMessage" title="Cluster unavailable" :message="errorMessage" tone="danger" :trace-id="errorTraceId" />

    <template v-else-if="summary">
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <MetricCard v-for="stat in summary.stats" :key="stat.label" :stat="stat" />
      </div>

      <article class="cloud-card p-6">
        <div class="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p class="eyebrow">Observability</p>
            <h2 class="mt-2 text-[1.5rem] font-extrabold tracking-tight text-slate-900">可观测性入口</h2>
            <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
              只承载入口与排障说明：业务请求 -> traceId -> Loki 日志 -> Prometheus 指标 -> Grafana 看板。
            </p>
          </div>
          <a
            :href="appConfig.grafanaDashboardUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center justify-center rounded-full bg-sky-500 px-5 py-2.5 text-sm font-bold text-white shadow-lg shadow-sky-500/20 transition hover:bg-sky-600"
          >
            打开 Grafana 看板
          </a>
        </div>

        <div class="mt-5 grid gap-4 xl:grid-cols-3">
          <section class="sub-card flex h-full flex-col p-5">
            <p class="eyebrow">Grafana Dashboard</p>
            <h3 class="mt-3 text-lg font-bold text-slate-900">公网匿名只读看板</h3>
            <p class="mt-2 flex-1 text-sm leading-6 text-slate-600">
              展示服务健康、请求量、5xx、p95 延迟、JVM Heap 与错误日志趋势，作为面试演示主入口。
            </p>
            <a
              :href="appConfig.grafanaDashboardUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="mt-4 inline-flex w-fit items-center gap-2 text-sm font-bold text-sky-500 transition hover:text-sky-600"
            >
              查看 Cloud Ops Overview →
            </a>
          </section>

          <section class="sub-card flex h-full flex-col p-5">
            <p class="eyebrow">Prometheus Targets</p>
            <h3 class="mt-3 text-lg font-bold text-slate-900">内部抓取状态验证</h3>
            <p class="mt-2 text-sm leading-6 text-slate-600">
              Prometheus 不做公网裸露。需要查看 Targets 时，通过 SSH 隧道访问本机
              <span class="font-mono text-slate-900">localhost:9090/targets</span>。
            </p>
            <pre class="mt-4 overflow-x-auto rounded-[1rem] border border-slate-200 bg-slate-50 p-3 font-mono text-xs leading-5 text-slate-600">{{ prometheusPortForwardCommand }}</pre>
          </section>

          <section class="sub-card flex h-full flex-col p-5">
            <p class="eyebrow">Trace LogQL</p>
            <h3 class="mt-3 text-lg font-bold text-slate-900">traceId 日志联查</h3>
            <p class="mt-2 text-sm leading-6 text-slate-600">
              使用真实业务接口制造日志，再通过 Loki 查询固定 traceId。访客页不暴露 Grafana Explore。
            </p>
            <pre class="mt-4 overflow-x-auto rounded-[1rem] border border-slate-200 bg-slate-50 p-3 font-mono text-xs leading-5 text-slate-600">{{ demoRequestCommand }}</pre>
            <pre class="mt-3 overflow-x-auto rounded-[1rem] border border-slate-200 bg-slate-50 p-3 font-mono text-xs leading-5 text-slate-600">{{ demoLogQl }}</pre>
          </section>
        </div>
      </article>

      <article class="cloud-card p-6">
        <div class="grid gap-4 md:grid-cols-3">
          <div class="sub-card p-4">
            <p class="eyebrow">Cluster</p>
            <p class="mt-2 font-mono text-lg text-slate-900">{{ summary.clusterName }}</p>
          </div>
          <div class="sub-card p-4">
            <p class="eyebrow">Region</p>
            <p class="mt-2 font-mono text-lg text-slate-900">{{ summary.region }}</p>
          </div>
          <div class="sub-card p-4">
            <p class="eyebrow">Checked At</p>
            <p class="mt-2 font-mono text-lg text-slate-900">{{ formatFullDateTime(summary.checkedAt) }}</p>
          </div>
        </div>
      </article>
    </template>
  </section>
</template>
