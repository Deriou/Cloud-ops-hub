<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";

export interface AppDirectoryCard {
  title: string;
  description: string;
  route: string;
  routeLabel: string;
  statusLabel: string;
  tone: "normal" | "warning" | "danger";
  summary: string;
  appKey: string;
  checkedAt?: string;
  ctaLabel: string;
  current?: boolean;
}

const props = defineProps<{
  entry: AppDirectoryCard;
}>();

const cardClasses = computed(() =>
  props.entry.current
    ? "border-sky-200 bg-sky-50/92"
    : "border-slate-200/75 bg-white/88 transition-all duration-300 hover:-translate-y-0.5 hover:border-sky-200 hover:bg-white"
);
</script>

<template>
  <article class="sub-card flex h-full flex-col p-5" :class="cardClasses">
    <div class="flex items-start justify-between gap-4">
      <div>
        <p class="text-lg font-bold text-slate-900">{{ entry.title }}</p>
        <p class="mt-1 text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">{{ entry.appKey }}</p>
      </div>
      <span
        class="rounded-full px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.16em]"
        :class="
          entry.tone === 'danger'
            ? 'bg-rose-100 text-rose-700'
            : entry.tone === 'warning'
              ? 'bg-amber-100 text-amber-700'
              : 'bg-emerald-100 text-emerald-700'
        "
      >
        {{ entry.statusLabel }}
      </span>
    </div>

    <p class="mt-4 text-sm leading-6 text-slate-600">{{ entry.description }}</p>

    <div class="mt-5 rounded-[1.35rem] border border-slate-200/80 bg-slate-50/80 p-4">
      <p class="text-sm leading-6 text-slate-600">{{ entry.summary }}</p>
      <div class="mt-3 flex flex-wrap gap-3 text-xs text-slate-500">
        <span class="font-mono">{{ entry.routeLabel }}</span>
        <span v-if="entry.checkedAt" class="font-mono">{{ entry.checkedAt }}</span>
      </div>
    </div>

    <div class="mt-5 flex items-center justify-between gap-3">
      <span class="text-xs font-semibold uppercase tracking-[0.18em] text-sky-500">
        {{ entry.current ? "Directory" : "App Entry" }}
      </span>
      <RouterLink
        :to="entry.route"
        class="rounded-full bg-sky-500 px-4 py-2 text-sm font-semibold text-white transition hover:bg-sky-600"
      >
        {{ entry.ctaLabel }}
      </RouterLink>
    </div>
  </article>
</template>
