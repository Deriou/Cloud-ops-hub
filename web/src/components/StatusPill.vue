<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  label: string;
  tone?: "normal" | "warning" | "danger";
}>();

const classes = computed(() => {
  switch (props.tone) {
    case "warning":
      return "border-amber-400/30 bg-amber-400/10 text-amber-200";
    case "danger":
      return "border-rose-400/30 bg-rose-400/10 text-rose-200";
    default:
      return "border-emerald-400/30 bg-emerald-400/10 text-emerald-200";
  }
});
</script>

<template>
  <span
    class="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium uppercase tracking-[0.24em]"
    :class="classes"
  >
    <span
      class="h-2 w-2 rounded-full animate-pulse"
      :class="tone === 'danger' ? 'bg-rose-400' : tone === 'warning' ? 'bg-amber-300' : 'bg-emerald-400'"
    />
    {{ label }}
  </span>
</template>
