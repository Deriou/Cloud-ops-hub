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
  <section class="grid gap-6">
    <article class="cloud-card p-6">
      <p class="eyebrow">Ops shell</p>
      <h2 class="mt-3 text-2xl font-semibold text-ink">集群摘要</h2>
      <p class="mt-3 text-sm leading-6 text-ink-soft">
        当前默认使用 mock adapter，后续 D1 完成后可直接切换到 `/api/v1/ops/clusters/summary`。
      </p>
    </article>

    <StatePanel v-if="loading" title="Cluster loading" message="正在汇总集群状态..." />
    <StatePanel v-else-if="errorMessage" title="Cluster error" :message="errorMessage" tone="danger" :trace-id="errorTraceId" />

    <template v-else-if="summary">
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <MetricCard v-for="stat in summary.stats" :key="stat.label" :stat="stat" />
      </div>

      <article class="cloud-card p-6">
        <div class="grid gap-4 md:grid-cols-3">
          <div>
            <p class="eyebrow">cluster</p>
            <p class="mt-2 font-mono text-lg text-ink">{{ summary.clusterName }}</p>
          </div>
          <div>
            <p class="eyebrow">region</p>
            <p class="mt-2 font-mono text-lg text-ink">{{ summary.region }}</p>
          </div>
          <div>
            <p class="eyebrow">checkedAt</p>
            <p class="mt-2 font-mono text-lg text-ink">{{ formatFullDateTime(summary.checkedAt) }}</p>
          </div>
        </div>
      </article>
    </template>
  </section>
</template>
