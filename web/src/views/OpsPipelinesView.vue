<script setup lang="ts">
import { fetchPipelineRuns } from "@/api/ops";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { toUiError } from "@/lib/errors";
import { formatFullDateTime, toneForStatus } from "@/lib/format";
import type { PipelineRun } from "@/types/ops";
import { onMounted, ref } from "vue";

const runs = ref<PipelineRun[]>([]);
const loading = ref(false);
const errorMessage = ref("");
const errorTraceId = ref("");

async function loadRuns() {
  loading.value = true;
  errorMessage.value = "";
  errorTraceId.value = "";

  try {
    runs.value = await fetchPipelineRuns();
  } catch (error) {
    const uiError = toUiError(error, "读取流水线运行状态失败");
    errorMessage.value = uiError.message;
    errorTraceId.value = uiError.traceId;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadRuns();
});
</script>

<template>
  <section class="grid gap-4">
    <article class="cloud-card px-6 py-6 lg:px-7">
      <p class="eyebrow">Ops / Pipelines</p>
      <h1 class="mt-3 text-[2rem] font-extrabold tracking-tight text-slate-900">Jenkins 运行页</h1>
      <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-600">状态链路继续保留，但卡片层级与说明文案收敛到统一的门户体系里。</p>
    </article>

    <StatePanel v-if="loading" title="Pipeline loading" message="正在同步流水线执行状态..." />
    <StatePanel v-else-if="errorMessage" title="Pipeline unavailable" :message="errorMessage" tone="danger" :trace-id="errorTraceId" />
    <StatePanel v-else-if="runs.length === 0" title="No runs" message="最近没有可展示的流水线运行记录。" />

    <div v-else class="grid gap-4 xl:grid-cols-3">
      <article v-for="run in runs" :key="run.runId" class="cloud-card p-6">
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="font-mono text-sm text-slate-500">{{ run.runId }}</p>
            <h2 class="mt-2 text-[1.35rem] font-bold tracking-tight text-slate-900">{{ run.jobName }}</h2>
          </div>
          <StatusPill :label="run.status" :tone="toneForStatus(run.status)" />
        </div>

        <div class="mt-5 grid gap-3 text-sm text-slate-600">
          <div class="flex justify-between gap-3">
            <span>触发人</span>
            <span class="text-slate-900">{{ run.triggerBy }}</span>
          </div>
          <div class="flex justify-between gap-3">
            <span>开始时间</span>
            <span class="font-mono text-slate-900">{{ formatFullDateTime(run.startedAt) }}</span>
          </div>
          <div class="flex justify-between gap-3">
            <span>耗时</span>
            <span class="font-mono text-slate-900">{{ run.duration }}</span>
          </div>
        </div>

        <div v-if="run.stages?.length" class="mt-6 grid gap-3">
          <div v-for="stage in run.stages" :key="`${run.runId}-${stage.name}`" class="sub-card px-4 py-3">
            <div class="flex items-center justify-between gap-3">
              <div>
                <p class="text-sm font-semibold text-slate-900">{{ stage.name }}</p>
                <p class="mt-1 text-xs text-slate-500">{{ stage.duration ?? "waiting" }}</p>
              </div>
              <StatusPill :label="stage.status" :tone="toneForStatus(stage.status)" />
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
