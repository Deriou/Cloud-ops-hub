<script setup lang="ts">
import { Beaker, CheckSquare, Cpu, Fingerprint } from "lucide-vue-next";

const plannedApps = [
  {
    title: "Release Checklist",
    label: "Prototype",
    description: "发布前后的轻量核对清单，适合沉淀 Jenkins、K3s 与前端发布流程。",
    icon: CheckSquare
  },
  {
    title: "Trace Playground",
    label: "Planned",
    description: "用模拟链路展示 traceId 如何串起请求、日志、指标和看板。",
    icon: Fingerprint
  },
  {
    title: "Resource Planner",
    label: "Planned",
    description: "按 CPU、内存和副本数估算单节点 Kubernetes 资源余量。",
    icon: Cpu
  }
] as const;
</script>

<template>
  <section class="grid gap-4">
    <article class="cloud-card px-6 py-6 lg:px-7">
      <p class="eyebrow">Apps</p>
      <div class="mt-3 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 class="text-[2rem] font-extrabold tracking-tight text-slate-900">应用实验室</h1>
        </div>
        <span class="inline-flex w-fit items-center gap-2 rounded-full bg-sky-100 px-4 py-2 text-xs font-semibold uppercase tracking-[0.18em] text-sky-700">
          <Beaker :size="15" />
          Lab
        </span>
      </div>
    </article>

    <div class="grid gap-4 lg:grid-cols-3">
      <article
        v-for="app in plannedApps"
        :key="app.title"
        class="sub-card flex min-h-[230px] flex-col p-5 transition-all duration-300 hover:-translate-y-0.5 hover:border-sky-200"
      >
        <div class="flex items-start justify-between gap-4">
          <div class="flex h-11 w-11 items-center justify-center rounded-2xl bg-sky-50 text-sky-500">
            <component :is="app.icon" :size="22" :stroke-width="2.2" />
          </div>
          <span class="rounded-full bg-slate-100 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
            {{ app.label }}
          </span>
        </div>

        <h2 class="mt-5 text-xl font-bold tracking-tight text-slate-900">{{ app.title }}</h2>
        <p class="mt-3 flex-1 text-sm leading-7 text-slate-600">{{ app.description }}</p>

        <button
          type="button"
          disabled
          class="mt-5 inline-flex w-fit cursor-not-allowed items-center justify-center rounded-full bg-slate-100 px-4 py-2 text-sm font-semibold text-slate-400"
        >
          Coming soon
        </button>
      </article>
    </div>
  </section>
</template>
