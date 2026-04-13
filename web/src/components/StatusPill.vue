<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  label: string;
  tone?: "normal" | "warning" | "danger";
}>();

const classes = computed(() => {
  switch (props.tone) {
    case "warning":
      return "border-amber-200 bg-amber-50 text-amber-700";
    case "danger":
      return "border-rose-200 bg-rose-50 text-rose-700";
    default:
      return "border-emerald-200 bg-emerald-50 text-emerald-700";
  }
});
</script>

<template>
  <span
    class="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.2em]"
    :class="classes"
  >
    <span
      class="h-2.5 w-2.5 rounded-full animate-pulse"
      :class="tone === 'danger' ? 'bg-rose-500' : tone === 'warning' ? 'bg-amber-500' : 'bg-emerald-500'"
    />
    {{ label }}
  </span>
</template>
