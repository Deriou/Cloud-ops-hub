<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  points: number[];
}>();

const gradientId = `sparkline-gradient-${Math.random().toString(36).slice(2, 8)}`;

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
    <defs>
      <linearGradient :id="gradientId" x1="0%" x2="100%" y1="0%" y2="0%">
        <stop offset="0%" stop-color="currentColor" stop-opacity="0.28" />
        <stop offset="100%" stop-color="currentColor" stop-opacity="0.02" />
      </linearGradient>
    </defs>
    <path d="M 0 100 L 100 100" fill="none" stroke="rgba(125, 145, 170, 0.24)" stroke-width="2" />
    <path v-if="path" :d="`${path} L 100 100 L 0 100 Z`" :fill="`url(#${gradientId})`" opacity="0.75" />
    <path v-if="path" :d="path" fill="none" stroke="currentColor" stroke-linecap="round" stroke-width="3.5" />
  </svg>
</template>
