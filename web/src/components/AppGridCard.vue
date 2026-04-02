<script setup lang="ts">
import StatusPill from "@/components/StatusPill.vue";
import { formatDateTime, initials, toneForStatus } from "@/lib/format";
import type { AppHealth, AppMeta } from "@/types/gateway";
import { computed } from "vue";

const props = defineProps<{
  app: AppMeta;
  health?: AppHealth;
}>();

const tone = computed(() => toneForStatus(props.health?.status ?? props.app.status));
</script>

<template>
  <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md transition-all duration-300 ease-in-out hover:-translate-y-1 hover:shadow-xl">
    <div class="flex items-start justify-between gap-4">
      <div class="flex items-center gap-4">
        <div class="flex h-12 w-12 items-center justify-center rounded-2xl border border-white/10 bg-white/5 font-mono text-sm text-slate-200">
          {{ initials(app.title) }}
        </div>
        <div>
          <p class="text-lg font-semibold text-white">{{ app.title }}</p>
          <p class="text-sm text-slate-400">{{ app.route }}</p>
        </div>
      </div>
      <StatusPill :label="health?.status ?? app.status" :tone="tone" />
    </div>

    <p class="mt-5 min-h-12 text-sm leading-6 text-slate-300">{{ app.description }}</p>

    <div class="mt-5 grid grid-cols-2 gap-3 text-sm text-slate-400">
      <div>
        <p class="uppercase tracking-[0.2em] text-slate-500">appKey</p>
        <p class="mt-1 font-mono text-slate-200">{{ app.appKey }}</p>
      </div>
      <div>
        <p class="uppercase tracking-[0.2em] text-slate-500">checkedAt</p>
        <p class="mt-1 font-mono text-slate-200">{{ health ? formatDateTime(health.checkedAt) : "-" }}</p>
      </div>
    </div>

    <div class="mt-4 rounded-2xl border border-white/5 bg-slate-900/70 p-4">
      <p class="text-xs uppercase tracking-[0.24em] text-slate-500">message</p>
      <p class="mt-2 text-sm text-slate-300">{{ health?.message ?? "等待健康探测结果" }}</p>
    </div>
  </article>
</template>
