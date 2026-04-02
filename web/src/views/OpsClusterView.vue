<script setup lang="ts">
import { fetchClusterSummary } from "@/api/ops";
import MetricCard from "@/components/MetricCard.vue";
import { formatDateTime } from "@/lib/format";
import type { ClusterSummary } from "@/types/ops";
import { onMounted, ref } from "vue";

const summary = ref<ClusterSummary | null>(null);
const loading = ref(false);
const errorMessage = ref("");

async function loadSummary() {
  loading.value = true;
  errorMessage.value = "";

  try {
    summary.value = await fetchClusterSummary();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "读取集群摘要失败";
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
    <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
      <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Ops shell</p>
      <h2 class="mt-3 text-2xl font-semibold text-white">集群摘要</h2>
      <p class="mt-3 text-sm leading-6 text-slate-300">
        当前默认使用 mock adapter，后续 D1 完成后可直接切换到 `/api/v1/ops/clusters/summary`。
      </p>
    </article>

    <div v-if="loading" class="rounded-3xl border border-white/10 bg-white/5 p-6 text-slate-300">
      正在汇总集群状态...
    </div>

    <div v-else-if="errorMessage" class="rounded-3xl border border-rose-400/30 bg-rose-400/10 p-6 text-rose-100">
      {{ errorMessage }}
    </div>

    <template v-else-if="summary">
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <MetricCard v-for="stat in summary.stats" :key="stat.label" :stat="stat" />
      </div>

      <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
        <div class="grid gap-4 md:grid-cols-3">
          <div>
            <p class="text-xs uppercase tracking-[0.24em] text-slate-500">cluster</p>
            <p class="mt-2 font-mono text-lg text-white">{{ summary.clusterName }}</p>
          </div>
          <div>
            <p class="text-xs uppercase tracking-[0.24em] text-slate-500">region</p>
            <p class="mt-2 font-mono text-lg text-white">{{ summary.region }}</p>
          </div>
          <div>
            <p class="text-xs uppercase tracking-[0.24em] text-slate-500">checkedAt</p>
            <p class="mt-2 font-mono text-lg text-white">{{ formatDateTime(summary.checkedAt) }}</p>
          </div>
        </div>
      </article>
    </template>
  </section>
</template>
