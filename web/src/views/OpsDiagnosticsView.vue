<script setup lang="ts">
import { fetchDiagnosisReports } from "@/api/ops";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { toUiError } from "@/lib/errors";
import { toneForStatus } from "@/lib/format";
import type { DiagnosisReport } from "@/types/ops";
import { onMounted, ref } from "vue";

const reports = ref<DiagnosisReport[]>([]);
const loading = ref(false);
const errorMessage = ref("");
const errorTraceId = ref("");

async function loadReports() {
  loading.value = true;
  errorMessage.value = "";
  errorTraceId.value = "";

  try {
    reports.value = await fetchDiagnosisReports();
  } catch (error) {
    const uiError = toUiError(error, "读取诊断报告失败");
    errorMessage.value = uiError.message;
    errorTraceId.value = uiError.traceId;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadReports();
});
</script>

<template>
  <section class="grid gap-6">
    <article class="cloud-card p-6">
      <p class="eyebrow">Diagnosis shell</p>
      <h2 class="mt-3 text-2xl font-semibold text-ink">发布诊断报告</h2>
      <p class="mt-3 text-sm leading-6 text-ink-soft">
        报告卡片已经按 `severity / summary / rootCause / suggestions` 结构组织，方便 D3 直接替换成真实返回。
      </p>
    </article>

    <StatePanel v-if="loading" title="Diagnosis loading" message="正在收集诊断报告..." />
    <StatePanel v-else-if="errorMessage" title="Diagnosis error" :message="errorMessage" tone="danger" :trace-id="errorTraceId" />
    <StatePanel v-else-if="reports.length === 0" title="No reports" message="当前还没有诊断报告。" />

    <div v-else class="grid gap-4">
      <article
        v-for="report in reports"
        :key="report.reportId"
        class="grid gap-5 rounded-[2rem] border border-sky-100 bg-white/80 p-6 shadow-soft xl:grid-cols-[1.2fr_0.8fr]"
      >
        <div>
          <div class="flex items-start justify-between gap-4">
            <div>
              <p class="font-mono text-sm text-ink-soft">{{ report.reportId }}</p>
              <h3 class="mt-2 text-2xl font-semibold text-ink">{{ report.summary }}</h3>
            </div>
            <StatusPill :label="report.severity" :tone="toneForStatus(report.severity === 'LOW' ? 'UP' : report.severity === 'MEDIUM' ? 'DEGRADED' : 'DOWN')" />
          </div>
          <div class="mt-5 rounded-[1.4rem] border border-sky-100 bg-sky-50/65 p-4">
            <p class="eyebrow">root cause</p>
            <p class="mt-3 text-sm leading-7 text-ink-soft">{{ report.rootCause }}</p>
          </div>
          <div class="mt-5 grid gap-3 md:grid-cols-2">
            <div class="rounded-[1.4rem] border border-sky-100 bg-white/75 p-4">
              <p class="eyebrow">metrics</p>
              <div class="mt-3 grid gap-2 text-sm text-ink-soft">
                <div v-for="metric in report.metrics" :key="metric.label" class="flex justify-between gap-3">
                  <span>{{ metric.label }}</span>
                  <span class="font-mono text-ink">{{ metric.value }}</span>
                </div>
              </div>
            </div>
            <div class="rounded-[1.4rem] border border-sky-100 bg-white/75 p-4">
              <p class="eyebrow">suggestions</p>
              <ul class="mt-3 grid gap-2 text-sm text-ink-soft">
                <li v-for="suggestion in report.suggestions" :key="suggestion">{{ suggestion }}</li>
              </ul>
            </div>
          </div>
        </div>

        <div class="cloud-card-dark p-4">
          <p class="eyebrow !text-slate-400">logs</p>
          <div class="mt-3 grid gap-3">
            <pre
              v-for="line in report.logs"
              :key="line"
              class="overflow-x-auto rounded-[1.2rem] border border-white/10 bg-black/20 p-3 font-mono text-xs text-slate-200"
            >{{ line }}</pre>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
