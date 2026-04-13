<script setup lang="ts">
import { fetchWorkloads } from "@/api/ops";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { toUiError } from "@/lib/errors";
import { toneForStatus } from "@/lib/format";
import type { WorkloadPage } from "@/types/ops";
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const workloadsPage = ref<WorkloadPage>({
  page: 1,
  size: 8,
  total: 0,
  totalPages: 0,
  records: []
});
const loading = ref(false);
const errorMessage = ref("");
const errorTraceId = ref("");

async function loadWorkloads(page = 1) {
  loading.value = true;
  errorMessage.value = "";
  errorTraceId.value = "";

  try {
    workloadsPage.value = await fetchWorkloads(page, 8);
  } catch (error) {
    const uiError = toUiError(error, "读取工作负载失败");
    errorMessage.value = uiError.message;
    errorTraceId.value = uiError.traceId;
  } finally {
    loading.value = false;
  }
}

function changePage(page: number) {
  void router.replace({
    path: route.path,
    query: page > 1 ? { page: String(page) } : {}
  });
}

watch(
  () => route.query.page,
  (value) => {
    const page = typeof value === "string" ? Number.parseInt(value, 10) : 1;
    void loadWorkloads(Number.isFinite(page) && page > 0 ? page : 1);
  },
  { immediate: true }
);
</script>

<template>
  <section class="grid gap-6">
    <article class="cloud-card p-6">
      <div class="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p class="eyebrow">Workload inventory</p>
          <h2 class="mt-3 text-2xl font-semibold text-ink">工作负载清单</h2>
          <p class="mt-3 text-sm leading-6 text-ink-soft">
            列表结构已经和 D1 的“前端可直接消费状态摘要与工作负载列表”对齐，后面只需要替换数据源。
          </p>
        </div>
        <p class="font-mono text-sm text-ink-soft">{{ workloadsPage.total }} workloads</p>
      </div>
    </article>

    <StatePanel v-if="loading" title="Workloads loading" message="正在读取工作负载..." />
    <StatePanel
      v-else-if="errorMessage"
      title="Workloads error"
      :message="errorMessage"
      tone="danger"
      :trace-id="errorTraceId"
    />
    <StatePanel v-else-if="workloadsPage.records.length === 0" title="No workloads" message="当前页暂无工作负载数据。" />

    <template v-else>
      <div class="grid gap-3 md:hidden">
        <article
          v-for="item in workloadsPage.records"
          :key="`${item.namespace}-${item.name}`"
          class="cloud-card p-5"
        >
          <div class="flex items-start justify-between gap-3">
            <div>
              <p class="text-base font-semibold text-ink">{{ item.name }}</p>
              <p class="mt-1 font-mono text-xs text-ink-soft">{{ item.namespace }} / {{ item.kind }}</p>
            </div>
            <StatusPill :label="item.status" :tone="toneForStatus(item.status)" />
          </div>
          <div class="mt-4 grid grid-cols-2 gap-3 text-sm text-ink-soft">
            <div>
              <p class="eyebrow">pods</p>
              <p class="mt-2 font-mono text-ink">{{ item.pods }}</p>
            </div>
            <div>
              <p class="eyebrow">owner</p>
              <p class="mt-2 text-ink">{{ item.owner }}</p>
            </div>
          </div>
        </article>
      </div>

      <div class="hidden overflow-hidden rounded-[2rem] border border-sky-100 bg-white/72 shadow-soft md:block">
        <table class="min-w-full">
          <thead class="bg-sky-50/70 text-left text-xs uppercase tracking-[0.24em] text-ink-soft">
            <tr>
              <th class="px-5 py-4">namespace</th>
              <th class="px-5 py-4">name</th>
              <th class="px-5 py-4">kind</th>
              <th class="px-5 py-4">status</th>
              <th class="px-5 py-4">pods</th>
              <th class="px-5 py-4">owner</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in workloadsPage.records" :key="`${item.namespace}-${item.name}`" class="border-t border-sky-100/80">
              <td class="px-5 py-4 font-mono text-sm text-ink-soft">{{ item.namespace }}</td>
              <td class="px-5 py-4 text-sm font-semibold text-ink">{{ item.name }}</td>
              <td class="px-5 py-4 text-sm text-ink-soft">{{ item.kind }}</td>
              <td class="px-5 py-4"><StatusPill :label="item.status" :tone="toneForStatus(item.status)" /></td>
              <td class="px-5 py-4 font-mono text-sm text-ink">{{ item.pods }}</td>
              <td class="px-5 py-4 text-sm text-ink-soft">{{ item.owner }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="cloud-card-soft flex items-center justify-between p-4 text-sm text-ink-soft">
        <button
          type="button"
          class="rounded-full border border-sky-100 px-4 py-2 transition-all duration-300 ease-in-out hover:bg-white disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="workloadsPage.page <= 1 || loading"
          @click="changePage(workloadsPage.page - 1)"
        >
          上一页
        </button>
        <span class="font-mono">page {{ workloadsPage.page }} / {{ Math.max(workloadsPage.totalPages, 1) }}</span>
        <button
          type="button"
          class="rounded-full border border-sky-100 px-4 py-2 transition-all duration-300 ease-in-out hover:bg-white disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="workloadsPage.page >= workloadsPage.totalPages || loading || workloadsPage.totalPages === 0"
          @click="changePage(workloadsPage.page + 1)"
        >
          下一页
        </button>
      </div>
    </template>
  </section>
</template>
