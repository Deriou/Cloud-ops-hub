<script setup lang="ts">
import { fetchDiagnosisReports } from "@/api/ops";
import StatusPill from "@/components/StatusPill.vue";
import { toneForStatus } from "@/lib/format";
import type { DiagnosisReport } from "@/types/ops";
import { onMounted, ref } from "vue";

const reports = ref<DiagnosisReport[]>([]);
const loading = ref(false);
const errorMessage = ref("");

async function loadReports() {
  loading.value = true;
  errorMessage.value = "";

  try {
    reports.value = await fetchDiagnosisReports();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "读取诊断报告失败";
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
    <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
      <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Diagnosis shell</p>
      <h2 class="mt-3 text-2xl font-semibold text-white">发布诊断报告</h2>
      <p class="mt-3 text-sm leading-6 text-slate-300">
        报告卡片已经按 `severity / summary / rootCause / suggestions` 结构组织，方便 D3 直接替换成真实返回。
      </p>
    </article>

    <div v-if="loading" class="rounded-3xl border border-white/10 bg-white/5 p-6 text-slate-300">
      正在收集诊断报告...
    </div>

    <div v-else-if="errorMessage" class="rounded-3xl border border-rose-400/30 bg-rose-400/10 p-6 text-rose-100">
      {{ errorMessage }}
    </div>

    <div v-else class="grid gap-4">
      <article
        v-for="report in reports"
        :key="report.reportId"
        class="grid gap-5 rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md xl:grid-cols-[1.2fr_0.8fr]"
      >
        <div>
          <div class="flex items-start justify-between gap-4">
            <div>
              <p class="font-mono text-sm text-slate-400">{{ report.reportId }}</p>
              <h3 class="mt-2 text-2xl font-semibold text-white">{{ report.summary }}</h3>
            </div>
            <StatusPill :label="report.severity" :tone="toneForStatus(report.severity === 'LOW' ? 'UP' : report.severity === 'MEDIUM' ? 'DEGRADED' : 'DOWN')" />
          </div>
          <div class="mt-5 rounded-2xl border border-white/10 bg-white/5 p-4">
            <p class="text-xs uppercase tracking-[0.24em] text-slate-500">root cause</p>
            <p class="mt-3 text-sm leading-7 text-slate-300">{{ report.rootCause }}</p>
          </div>
          <div class="mt-5 grid gap-3 md:grid-cols-2">
            <div class="rounded-2xl border border-white/10 bg-white/5 p-4">
              <p class="text-xs uppercase tracking-[0.24em] text-slate-500">metrics</p>
              <div class="mt-3 grid gap-2 text-sm text-slate-300">
                <div v-for="metric in report.metrics" :key="metric.label" class="flex justify-between gap-3">
                  <span>{{ metric.label }}</span>
                  <span class="font-mono">{{ metric.value }}</span>
                </div>
              </div>
            </div>
            <div class="rounded-2xl border border-white/10 bg-white/5 p-4">
              <p class="text-xs uppercase tracking-[0.24em] text-slate-500">suggestions</p>
              <ul class="mt-3 grid gap-2 text-sm text-slate-300">
                <li v-for="suggestion in report.suggestions" :key="suggestion">{{ suggestion }}</li>
              </ul>
            </div>
          </div>
        </div>

        <div class="rounded-2xl border border-white/10 bg-slate-950/70 p-4">
          <p class="text-xs uppercase tracking-[0.24em] text-slate-500">logs</p>
          <div class="mt-3 grid gap-3">
            <pre
              v-for="line in report.logs"
              :key="line"
              class="overflow-x-auto rounded-2xl border border-white/5 bg-black/30 p-3 font-mono text-xs text-slate-300"
            >{{ line }}</pre>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
