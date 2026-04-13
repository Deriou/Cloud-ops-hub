<script setup lang="ts">
import { featuredProjects } from "@/content/site";
import { RouterLink } from "vue-router";
</script>

<template>
  <section class="grid gap-4">
    <article class="cloud-card px-6 py-6 lg:px-7">
      <p class="eyebrow">Projects</p>
      <div class="mt-3 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 class="text-[2rem] font-extrabold tracking-tight text-slate-900">个人作品入口</h1>
          <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
            这里先只保留一个最核心的仓库入口，不做 GitHub API 接入，也不把页面扩成项目管理面板。
          </p>
        </div>
        <RouterLink to="/" class="text-sm font-semibold text-sky-600 transition hover:text-sky-700">返回首页</RouterLink>
      </div>
    </article>

    <div class="grid gap-4">
      <article v-for="project in featuredProjects" :key="project.name" class="cloud-card px-6 py-6 lg:px-7">
        <div class="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div class="max-w-3xl">
            <p class="eyebrow">Featured Repository</p>
            <h2 class="mt-3 text-[1.7rem] font-bold tracking-tight text-slate-900">{{ project.name }}</h2>
            <p class="mt-3 text-sm font-semibold text-sky-600">{{ project.description }}</p>
            <p class="mt-4 text-sm leading-7 text-slate-600">{{ project.summary }}</p>
          </div>

          <div class="flex flex-col gap-3 lg:min-w-[180px]">
            <a
              :href="project.href"
              target="_blank"
              rel="noopener noreferrer"
              class="rounded-full bg-sky-500 px-5 py-2.5 text-center text-sm font-semibold text-white transition hover:bg-sky-600"
            >
              View Repo
            </a>
            <span class="rounded-full bg-slate-100 px-4 py-2 text-center text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
              GitHub
            </span>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
