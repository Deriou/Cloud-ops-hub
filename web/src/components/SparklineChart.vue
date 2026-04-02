<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  points: number[];
}>();

const path = computed(() => {
  if (props.points.length === 0) {
    return "";
  }

  const max = Math.max(...props.points);
  const min = Math.min(...props.points);
  const range = Math.max(max - min, 1);

  return props.points
    .map((point, index) => {
      const x = (index / Math.max(props.points.length - 1, 1)) * 100;
      const y = 100 - ((point - min) / range) * 100;
      return `${index === 0 ? "M" : "L"} ${x} ${y}`;
    })
    .join(" ");
});
</script>

<template>
  <svg viewBox="0 0 100 100" class="h-16 w-full overflow-visible">
    <path d="M 0 100 L 100 100" fill="none" stroke="rgba(148,163,184,0.18)" stroke-width="2" />
    <path :d="path" fill="none" stroke="currentColor" stroke-linecap="round" stroke-width="3" />
  </svg>
</template>
