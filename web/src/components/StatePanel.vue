<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  title: string;
  message: string;
  tone?: "neutral" | "warning" | "danger";
  traceId?: string;
}>();

const panelClass = computed(() => {
  switch (props.tone) {
    case "warning":
      return "border-amber-200 bg-amber-50/90 text-amber-800";
    case "danger":
      return "border-rose-200 bg-rose-50/90 text-rose-800";
    default:
      return "border-sky-100 bg-white/75 text-ink-soft";
  }
});
</script>

<template>
  <article class="rounded-[1.6rem] border p-5 shadow-soft" :class="panelClass">
    <p class="eyebrow">{{ title }}</p>
    <p class="mt-3 text-sm leading-6">{{ message }}</p>
    <p v-if="traceId" class="mt-3 font-mono text-xs opacity-80">traceId: {{ traceId }}</p>
  </article>
</template>
