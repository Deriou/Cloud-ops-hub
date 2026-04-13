<script setup lang="ts">
import StatusPill from "@/components/StatusPill.vue";
import { formatDateTime, initials, toneForStatus } from "@/lib/format";
import type { AppHealth, AppMeta } from "@/types/gateway";
import { computed } from "vue";

const props = defineProps<{
  app: AppMeta;
  health?: AppHealth;
}>();

const tone = computed(() => toneForStatus(props.health?.status ?? props.app.status));
</script>

<template>
  <article class="cloud-card-soft p-6 transition-all duration-300 ease-in-out hover:-translate-y-1 hover:shadow-float">
    <div class="flex items-start justify-between gap-4">
      <div class="flex items-center gap-4">
        <div class="flex h-12 w-12 items-center justify-center rounded-2xl border border-sky-100 bg-sky-50 font-mono text-sm font-semibold text-sky-700">
          {{ initials(app.title) }}
        </div>
        <div>
          <p class="text-lg font-semibold text-ink">{{ app.title }}</p>
          <p class="text-sm text-ink-soft">{{ app.route }}</p>
        </div>
      </div>
      <StatusPill :label="health?.status ?? app.status" :tone="tone" />
    </div>

    <p class="mt-5 min-h-12 text-sm leading-6 text-ink-soft">{{ app.description }}</p>

    <div class="mt-5 grid grid-cols-2 gap-3 text-sm text-ink-soft">
      <div>
        <p class="eyebrow">appKey</p>
        <p class="mt-1 font-mono text-sm text-ink">{{ app.appKey }}</p>
      </div>
      <div>
        <p class="eyebrow">checkedAt</p>
        <p class="mt-1 font-mono text-sm text-ink">{{ health ? formatDateTime(health.checkedAt) : "-" }}</p>
      </div>
    </div>

    <div class="mt-4 rounded-[1.35rem] border border-sky-100/80 bg-white/75 p-4">
      <p class="eyebrow">message</p>
      <p class="mt-2 text-sm text-ink-soft">{{ health?.message ?? "等待健康探测结果" }}</p>
    </div>
  </article>
</template>
