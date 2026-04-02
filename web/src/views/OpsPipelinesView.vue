<script setup lang="ts">
import { fetchPipelineRuns } from "@/api/ops";
import StatusPill from "@/components/StatusPill.vue";
import { formatDateTime, toneForStatus } from "@/lib/format";
import type { PipelineRun } from "@/types/ops";
import { onMounted, ref } from "vue";

const runs = ref<PipelineRun[]>([]);
const loading = ref(false);
const errorMessage = ref("");

async function loadRuns() {
  loading.value = true;
  errorMessage.value = "";

  try {
    runs.value = await fetchPipelineRuns();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "读取流水线运行状态失败";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadRuns();
});
</script>

<template>
  <section class="grid gap-6">
    <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
      <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Async trigger shell</p>
      <h2 class="mt-3 text-2xl font-semibold text-white">Jenkins 运行页</h2>
      <p class="mt-3 text-sm leading-6 text-slate-300">
        该页面先把状态机视图和布局定下来，D2 完成后可直接接入真实的 `runId` 查询结果。
      </p>
    </article>

    <div v-if="loading" class="rounded-3xl border border-white/10 bg-white/5 p-6 text-slate-300">
      正在同步流水线执行状态...
    </div>

    <div v-else-if="errorMessage" class="rounded-3xl border border-rose-400/30 bg-rose-400/10 p-6 text-rose-100">
      {{ errorMessage }}
    </div>

    <div v-else class="grid gap-4 xl:grid-cols-3">
      <article
        v-for="run in runs"
        :key="run.runId"
        class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="font-mono text-sm text-slate-400">{{ run.runId }}</p>
            <h3 class="mt-2 text-xl font-semibold text-white">{{ run.jobName }}</h3>
          </div>
          <StatusPill :label="run.status" :tone="toneForStatus(run.status)" />
        </div>
        <div class="mt-5 grid gap-3 text-sm text-slate-300">
          <div class="flex justify-between gap-3">
            <span class="text-slate-500">触发人</span>
            <span>{{ run.triggerBy }}</span>
          </div>
          <div class="flex justify-between gap-3">
            <span class="text-slate-500">开始时间</span>
            <span class="font-mono">{{ formatDateTime(run.startedAt) }}</span>
          </div>
          <div class="flex justify-between gap-3">
            <span class="text-slate-500">耗时</span>
            <span class="font-mono">{{ run.duration }}</span>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
