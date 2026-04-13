<script setup lang="ts">
import { fetchClusterSummary } from "@/api/ops";
import MetricCard from "@/components/MetricCard.vue";
import StatePanel from "@/components/StatePanel.vue";
import { toUiError } from "@/lib/errors";
import { formatFullDateTime } from "@/lib/format";
import type { ClusterSummary } from "@/types/ops";
import { onMounted, ref } from "vue";

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
