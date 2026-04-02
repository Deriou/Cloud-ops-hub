<script setup lang="ts">
import StatusPill from "@/components/StatusPill.vue";
import { toneForStatus } from "@/lib/format";
import { fetchWorkloads } from "@/api/ops";
import type { WorkloadItem } from "@/types/ops";
import { onMounted, ref } from "vue";

const workloads = ref<WorkloadItem[]>([]);
const loading = ref(false);
const errorMessage = ref("");

async function loadWorkloads() {
  loading.value = true;
  errorMessage.value = "";

  try {
    workloads.value = await fetchWorkloads();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "读取工作负载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadWorkloads();
});
</script>

<template>
  <section class="grid gap-6">
    <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
      <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Workload inventory</p>
      <h2 class="mt-3 text-2xl font-semibold text-white">工作负载清单</h2>
      <p class="mt-3 text-sm leading-6 text-slate-300">
        列表结构已经和 D1 的“前端可直接消费状态摘要与工作负载列表”对齐，后面只需要替换数据源。
      </p>
    </article>

    <div v-if="loading" class="rounded-3xl border border-white/10 bg-white/5 p-6 text-slate-300">
      正在读取工作负载...
    </div>

    <div v-else-if="errorMessage" class="rounded-3xl border border-rose-400/30 bg-rose-400/10 p-6 text-rose-100">
      {{ errorMessage }}
    </div>

    <div v-else class="overflow-hidden rounded-3xl border border-border bg-panel shadow-soft backdrop-blur-md">
      <table class="min-w-full">
        <thead class="bg-white/5 text-left text-xs uppercase tracking-[0.24em] text-slate-500">
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
          <tr v-for="item in workloads" :key="`${item.namespace}-${item.name}`" class="border-t border-white/5">
            <td class="px-5 py-4 font-mono text-sm text-slate-400">{{ item.namespace }}</td>
            <td class="px-5 py-4 text-sm text-white">{{ item.name }}</td>
            <td class="px-5 py-4 text-sm text-slate-300">{{ item.kind }}</td>
            <td class="px-5 py-4"><StatusPill :label="item.status" :tone="toneForStatus(item.status)" /></td>
            <td class="px-5 py-4 font-mono text-sm text-slate-300">{{ item.pods }}</td>
            <td class="px-5 py-4 text-sm text-slate-300">{{ item.owner }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
