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
  <section class="grid gap-6">
    <article class="cloud-card p-6">
      <p class="eyebrow">Async trigger shell</p>
      <h2 class="mt-3 text-2xl font-semibold text-ink">Jenkins 运行页</h2>
      <p class="mt-3 text-sm leading-6 text-ink-soft">
        该页面先把状态机视图和布局定下来，D2 完成后可直接接入真实的 `runId` 查询结果。
      </p>
    </article>

    <StatePanel v-if="loading" title="Pipeline loading" message="正在同步流水线执行状态..." />
    <StatePanel v-else-if="errorMessage" title="Pipeline error" :message="errorMessage" tone="danger" :trace-id="errorTraceId" />
    <StatePanel v-else-if="runs.length === 0" title="No runs" message="最近没有可展示的流水线运行记录。" />

    <div v-else class="grid gap-4 xl:grid-cols-3">
      <article
        v-for="run in runs"
        :key="run.runId"
        class="cloud-card p-6"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="font-mono text-sm text-ink-soft">{{ run.runId }}</p>
            <h3 class="mt-2 text-xl font-semibold text-ink">{{ run.jobName }}</h3>
          </div>
          <StatusPill :label="run.status" :tone="toneForStatus(run.status)" />
        </div>
        <div class="mt-5 grid gap-3 text-sm text-ink-soft">
          <div class="flex justify-between gap-3">
            <span class="text-ink-soft">触发人</span>
            <span class="text-ink">{{ run.triggerBy }}</span>
          </div>
          <div class="flex justify-between gap-3">
            <span class="text-ink-soft">开始时间</span>
            <span class="font-mono text-ink">{{ formatFullDateTime(run.startedAt) }}</span>
          </div>
          <div class="flex justify-between gap-3">
            <span class="text-ink-soft">耗时</span>
            <span class="font-mono text-ink">{{ run.duration }}</span>
          </div>
        </div>

        <div v-if="run.stages?.length" class="mt-6 grid gap-3">
          <div
            v-for="stage in run.stages"
            :key="`${run.runId}-${stage.name}`"
            class="rounded-[1.3rem] border border-sky-100 bg-sky-50/65 px-4 py-3"
          >
            <div class="flex items-center justify-between gap-3">
              <div>
                <p class="text-sm font-semibold text-ink">{{ stage.name }}</p>
                <p class="mt-1 text-xs text-ink-soft">{{ stage.duration ?? "waiting" }}</p>
              </div>
              <StatusPill :label="stage.status" :tone="toneForStatus(stage.status)" />
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
