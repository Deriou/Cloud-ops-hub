<script setup lang="ts">
import type { Component } from "vue";

withDefaults(
  defineProps<{
    icon: Component;
    title: string;
    eyebrow?: string;
    tone?: "sky" | "emerald" | "amber" | "violet";
  }>(),
  { tone: "sky" }
);

const toneClass: Record<string, string> = {
  sky: "from-sky-400 to-sky-600",
  emerald: "from-emerald-400 to-emerald-600",
  amber: "from-amber-400 to-amber-500",
  violet: "from-violet-400 to-violet-600"
};
</script>

<template>
  <div class="mb-5 flex items-center justify-between gap-3">
    <div class="flex min-w-0 items-center gap-3">
      <div
        :class="[
          'flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br text-white shadow-sm',
          toneClass[tone ?? 'sky']
        ]"
      >
        <component :is="icon" :size="22" :stroke-width="2.2" />
      </div>
      <div class="min-w-0">
        <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
        <h2 class="truncate text-[1.15rem] font-bold tracking-tight text-slate-900">{{ title }}</h2>
      </div>
    </div>
    <div v-if="$slots.action" class="shrink-0">
      <slot name="action" />
    </div>
  </div>
</template>
