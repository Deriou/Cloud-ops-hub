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
  <article class="cloud-card-soft p-5 transition-all duration-300 ease-in-out hover:-translate-y-1 hover:shadow-float">
    <div class="flex items-start justify-between gap-4">
      <div>
        <p class="text-sm font-medium text-ink-soft">{{ stat.label }}</p>
        <p class="metric-value mt-3">{{ stat.value }}</p>
      </div>
      <StatusPill :label="toneLabel" :tone="stat.tone" />
    </div>
    <div class="mt-5" :class="stat.tone === 'danger' ? 'text-rose-500' : stat.tone === 'warning' ? 'text-amber-500' : 'text-sky-500'">
      <SparklineChart :points="stat.trend" />
    </div>
  </article>
</template>
