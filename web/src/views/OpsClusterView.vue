<script setup lang="ts">
import { fetchClusterSummary, fetchServiceHealth } from "@/api/ops";
import MetricCard from "@/components/MetricCard.vue";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { appConfig } from "@/lib/config";
import { toUiError } from "@/lib/errors";
import { formatFullDateTime, toneForStatus } from "@/lib/format";
import type { ClusterSummary, ServiceHealth } from "@/types/ops";
import { onMounted, ref } from "vue";

const demoTraceId = "demo-loki-003";
const demoRequestCommand = `curl -i http://deriou.com/api/v1/blog/tags -H "X-Trace-Id: ${demoTraceId}"`;
const demoLogQl = `{namespace="cloud-ops"} |= "traceId=${demoTraceId}"`;
const prometheusPortForwardCommand =
  "ssh -L 9090:127.0.0.1:9090 deriou@8.145.50.162 && sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n monitoring port-forward svc/prometheus-server 9090:80 --address 127.0.0.1";
const gatewayLogCommand =
  'sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl -n cloud-ops logs deploy/gateway-portal --tail=200 | grep -E "Failed to load ops metric|Prometheus|ops"';
const releaseCards = [
  {
    jobName: "cloud-ops-web-pipeline",
    target: "web Deployment",
    role: "构建前端镜像，推送 ACR，回写 web Deployment image tag。",
    verifyCommand: "kubectl -n cloud-ops rollout status deploy/web --timeout=180s"
  },
  {
    jobName: "cloud-ops-gateway-pipeline",
    target: "gateway-portal Deployment",
    role: "构建 Gateway 镜像，发布只读聚合接口与公网 API 入口。",
    verifyCommand: "kubectl -n cloud-ops rollout status deploy/gateway-portal --timeout=240s"
  },
  {
    jobName: "cloud-ops-blog-pipeline",
    target: "blog-service Deployment",
    role: "构建 Blog 镜像，发布内容 API 与 Actuator 指标端点。",
    verifyCommand: "kubectl -n cloud-ops rollout status deploy/blog-service --timeout=300s"
  }
];

const summary = ref<ClusterSummary | null>(null);
const serviceHealth = ref<ServiceHealth[]>([]);
const loading = ref(false);
const serviceErrorMessage = ref("");
const serviceErrorTraceId = ref("");
const errorMessage = ref("");
const errorTraceId = ref("");

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
      <p class="eyebrow">Ops / Portal</p>
      <h1 class="mt-3 text-[2rem] font-extrabold tracking-tight text-slate-900">运维门户</h1>
      <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
        用一个轻量入口串起集群资源、服务健康、发布验证与可观测性排障，面向简历演示而不是复杂控制台操作。
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
            <p class="eyebrow">Service Health</p>
            <h2 class="mt-2 text-[1.5rem] font-extrabold tracking-tight text-slate-900">服务健康</h2>
            <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
              通过 Prometheus target 状态验证 gateway 与 blog 的真实可用性，web 静态站保留在发布验证链路中。
            </p>
          </div>
          <p class="font-mono text-xs text-slate-500">source=prometheus</p>
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
              <div>
                <p class="eyebrow">{{ service.name }}</p>
                <h3 class="mt-2 text-lg font-bold text-slate-900">{{ service.displayName }}</h3>
              </div>
              <StatusPill :label="service.status" :tone="toneForStatus(service.status)" />
            </div>
            <div class="mt-5 grid gap-3 text-sm text-slate-600">
              <div class="flex items-center justify-between gap-3">
                <span>Target</span>
                <span class="font-mono text-slate-900">{{ service.value }}</span>
              </div>
              <div class="flex items-center justify-between gap-3">
                <span>检查时间</span>
                <span class="font-mono text-slate-900">{{ formatFullDateTime(service.checkedAt) }}</span>
              </div>
              <p class="rounded-[1rem] border border-slate-200 bg-slate-50 px-3 py-2 text-xs leading-5 text-slate-600">
                {{ service.detail }}
              </p>
            </div>
          </section>
        </div>
      </article>

      <article class="cloud-card p-6">
        <div>
          <p class="eyebrow">Release Loop</p>
          <h2 class="mt-2 text-[1.5rem] font-extrabold tracking-tight text-slate-900">最近发布入口</h2>
          <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
            本轮不接 Jenkins API，只在门户中展示三条流水线职责与发布后验收命令，保证演示链路清晰可复现。
          </p>
        </div>

        <div class="mt-5 grid gap-4 xl:grid-cols-3">
          <section v-for="release in releaseCards" :key="release.jobName" class="sub-card flex h-full flex-col p-5">
            <p class="eyebrow">{{ release.target }}</p>
            <h3 class="mt-3 text-lg font-bold text-slate-900">{{ release.jobName }}</h3>
            <p class="mt-2 flex-1 text-sm leading-6 text-slate-600">{{ release.role }}</p>
            <pre class="mt-4 overflow-x-auto rounded-[1rem] border border-slate-200 bg-slate-50 p-3 font-mono text-xs leading-5 text-slate-600">{{ release.verifyCommand }}</pre>
          </section>
        </div>
      </article>

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
              展示服务健康、请求量、5xx、p95 延迟、JVM Heap、节点 CPU / 内存与错误日志趋势，作为面试演示主入口。
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
            <p class="eyebrow">Loki Trace</p>
            <h3 class="mt-3 text-lg font-bold text-slate-900">traceId 日志联查</h3>
            <p class="mt-2 text-sm leading-6 text-slate-600">
              使用真实业务接口制造日志，再通过 Loki 查询固定 traceId。访客页不暴露 Grafana Explore。
            </p>
            <pre class="mt-4 overflow-x-auto rounded-[1rem] border border-slate-200 bg-slate-50 p-3 font-mono text-xs leading-5 text-slate-600">{{ demoRequestCommand }}</pre>
            <pre class="mt-3 overflow-x-auto rounded-[1rem] border border-slate-200 bg-slate-50 p-3 font-mono text-xs leading-5 text-slate-600">{{ demoLogQl }}</pre>
          </section>
        </div>

        <section class="sub-card mt-4 p-5">
          <p class="eyebrow">Gateway Logs</p>
          <h3 class="mt-3 text-lg font-bold text-slate-900">聚合接口排障命令</h3>
          <p class="mt-2 text-sm leading-6 text-slate-600">
            当页面出现 N/A 或 UNKNOWN 时，优先查看 gateway 聚合 Prometheus 的失败日志。
          </p>
          <pre class="mt-4 overflow-x-auto rounded-[1rem] border border-slate-200 bg-slate-50 p-3 font-mono text-xs leading-5 text-slate-600">{{ gatewayLogCommand }}</pre>
        </section>
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
