<script setup lang="ts">
import { siteMeta, siteNavigation } from "@/content/site";
import { computed } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

const route = useRoute();

const activeNav = computed(() => siteNavigation.find((item) => item.match(route.path))?.to ?? "/");
</script>

<template>
  <div class="page-body min-h-screen pb-24 text-slate-900 md:pb-8">
    <div class="mx-auto flex min-h-screen max-w-[1220px] flex-col px-4 py-4 lg:px-6 lg:py-5">
      <header class="mb-4 flex items-center justify-between gap-4">
        <RouterLink to="/" class="text-[1.75rem] font-extrabold tracking-tight text-sky-500">
          {{ siteMeta.title }}
        </RouterLink>

        <nav class="hidden items-center gap-7 lg:flex">
          <RouterLink
            v-for="item in siteNavigation"
            :key="item.to"
            :to="item.to"
            class="text-sm font-semibold text-slate-500 transition hover:text-sky-500"
            :class="activeNav === item.to ? 'text-sky-500' : ''"
          >
            {{ item.label }}
          </RouterLink>
          <a
            :href="siteMeta.githubUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="rounded-full bg-sky-500 px-6 py-2.5 text-sm font-bold text-white shadow-lg shadow-sky-500/20 transition hover:bg-sky-600"
          >
            GitHub
          </a>
        </nav>
      </header>

      <main class="flex-1">
        <RouterView />
      </main>

      <footer class="pb-3 pt-4 text-center text-xs text-slate-500">
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer" class="transition hover:text-slate-700">
          {{ siteMeta.icpLabel }}
        </a>
      </footer>
    </div>

    <nav class="cloud-card fixed inset-x-3 bottom-3 z-20 grid grid-cols-5 gap-2 px-2 py-2 md:hidden">
      <RouterLink
        v-for="item in siteNavigation"
        :key="`mobile-${item.to}`"
        :to="item.to"
        class="rounded-[1.1rem] px-2 py-2 text-center text-[11px] font-semibold transition"
        :class="activeNav === item.to ? 'bg-sky-500 text-white shadow-float' : 'text-slate-500'"
      >
        {{ item.label }}
      </RouterLink>
    </nav>
  </div>
</template>
