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
  sky: "bg-sky-500",
  emerald: "bg-emerald-500",
  amber: "bg-amber-500",
  violet: "bg-violet-500"
};
</script>

<template>
  <div class="mb-5 flex items-center justify-between gap-3">
    <div class="flex min-w-0 items-center gap-3">
      <div
        :class="[
          'flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-white',
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
