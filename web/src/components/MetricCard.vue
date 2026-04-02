<script setup lang="ts">
import SparklineChart from "@/components/SparklineChart.vue";
import StatusPill from "@/components/StatusPill.vue";
import type { OpsStat } from "@/types/ops";
import { computed } from "vue";

const props = defineProps<{
  stat: OpsStat;
}>();

const toneLabel = computed(() => {
  switch (props.stat.tone) {
    case "warning":
      return "注意";
    case "danger":
      return "风险";
    default:
      return "稳定";
  }
});
</script>

<template>
  <article class="rounded-3xl border border-border bg-panel p-5 shadow-soft backdrop-blur-md transition-all duration-300 ease-in-out hover:-translate-y-0.5 hover:shadow-xl">
    <div class="flex items-start justify-between gap-4">
      <div>
        <p class="text-sm text-slate-400">{{ stat.label }}</p>
        <p class="mt-2 font-mono text-3xl text-white">{{ stat.value }}</p>
      </div>
      <StatusPill :label="toneLabel" :tone="stat.tone" />
    </div>
    <div class="mt-5" :class="stat.tone === 'danger' ? 'text-rose-300' : stat.tone === 'warning' ? 'text-amber-300' : 'text-cyan-300'">
      <SparklineChart :points="stat.trend" />
    </div>
  </article>
</template>
