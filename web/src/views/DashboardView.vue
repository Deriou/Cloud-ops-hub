<script setup lang="ts">
import { fetchPosts } from "@/api/blog";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { curatedApps, featuredProjects, manifesto, opsOverview, profile, siteMeta } from "@/content/site";
import { toUiError } from "@/lib/errors";
import { formatDateTime } from "@/lib/format";
import { usePortalStore } from "@/stores/portal";
import type { PostSummary } from "@/types/blog";
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

const portalStore = usePortalStore();
const latestPost = ref<PostSummary | null>(null);
const postTotal = ref(0);
const blogLoading = ref(false);
const blogError = ref("");
const blogTraceId = ref("");

async function loadBlogSnapshot() {
  blogLoading.value = true;
  blogError.value = "";
  blogTraceId.value = "";

  try {
    const page = await fetchPosts(1, 1);
    latestPost.value = page.records[0] ?? null;
    postTotal.value = page.total;
  } catch (error) {
    const uiError = toUiError(error, "读取最新文章失败");
    blogError.value = uiError.message;
    blogTraceId.value = uiError.traceId;
  } finally {
    blogLoading.value = false;
  }
}

const portalEntry = curatedApps[0];

const opsSummary = computed(() => ({
  statusLabel: portalStore.hydrated ? portalStore.healthSummary.headline : opsOverview.statusLabel,
  statusTone: portalStore.hydrated ? portalStore.healthSummary.tone : ("normal" as const),
  onlineLabel: portalStore.hydrated ? portalStore.healthSummary.label : opsOverview.summary
}));

onMounted(() => {
  void loadBlogSnapshot();
  void portalStore.ensureHydrated();
});
</script>

<template>
  <section class="grid gap-4 lg:grid-cols-12">
    <article class="cloud-card p-7 lg:col-span-4">
      <div class="flex flex-col items-center text-center">
        <img
          :src="profile.avatarUrl"
          :alt="`${profile.name} avatar`"
          class="h-28 w-28 rounded-full object-cover shadow-lg shadow-sky-500/15"
        />
        <h1 class="mt-6 text-[2.3rem] font-extrabold tracking-tight text-slate-900">{{ profile.name }}</h1>
        <p class="mt-2 text-base font-medium text-slate-600">{{ profile.tagline }}</p>
        <p class="mt-4 max-w-xs text-sm leading-6 text-slate-500">{{ profile.intro }}</p>
      </div>

      <div class="mt-7">
        <h2 class="text-sm font-bold uppercase tracking-[0.2em] text-slate-500">Contact</h2>
        <div class="mt-4 flex flex-wrap justify-center gap-3 lg:justify-start">
          <a
            :href="siteMeta.githubUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="rounded-full bg-slate-100 px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-sky-50 hover:text-sky-600"
          >
            {{ siteMeta.githubUsername }}
          </a>
          <a
            :href="`mailto:${siteMeta.email}`"
            class="rounded-full bg-slate-100 px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-sky-50 hover:text-sky-600"
          >
            {{ siteMeta.email }}
          </a>
        </div>
      </div>

      <div class="mt-7">
        <h2 class="text-sm font-bold uppercase tracking-[0.2em] text-slate-500">Tech Stack</h2>
        <div class="mt-4 flex flex-wrap gap-2">
          <span
            v-for="item in profile.techStack"
            :key="item"
            class="rounded-full bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-700"
          >
            {{ item }}
          </span>
        </div>
      </div>
    </article>

    <div class="grid gap-4 lg:col-span-8 md:grid-cols-2">
      <article class="cloud-card p-6">
        <div class="mb-5 flex items-center justify-between gap-3">
          <div>
            <p class="eyebrow">Blog</p>
            <h2 class="mt-2 text-[1.85rem] font-bold tracking-tight text-slate-900">博客</h2>
          </div>
          <span class="rounded-full bg-sky-100 px-3 py-1 text-sm font-semibold text-sky-600">{{ postTotal }} Posts</span>
        </div>

        <StatePanel v-if="blogLoading" title="Latest entry" message="正在读取最新文章摘要..." />
        <StatePanel
          v-else-if="blogError && !latestPost"
          title="Blog unavailable"
          :message="blogError"
          tone="danger"
          :trace-id="blogTraceId"
        />

        <div v-else-if="latestPost" class="sub-card p-5">
          <p class="eyebrow">Latest Entry</p>
          <RouterLink :to="`/blog/posts/${latestPost.id}`" class="mt-4 block text-[1.45rem] font-bold leading-tight text-slate-900 hover:text-sky-600">
            {{ latestPost.title }}
          </RouterLink>
          <div class="mt-4 flex items-center justify-between gap-3 text-sm text-slate-500">
            <span class="font-mono">{{ formatDateTime(latestPost.updateTime) }}</span>
            <RouterLink :to="`/blog/posts/${latestPost.id}`" class="font-semibold text-sky-600 transition hover:text-sky-700">
              进入文章
            </RouterLink>
          </div>
        </div>

        <StatePanel v-else title="Latest entry" message="暂无可展示的文章摘要。" />
      </article>

      <article class="cloud-card p-6">
        <div class="mb-5 flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-full bg-white text-sky-500 shadow-sm">⌘</div>
          <h2 class="text-[1.85rem] font-bold tracking-tight text-slate-900">项目展示</h2>
        </div>

        <div class="space-y-3">
          <a
            v-for="project in featuredProjects"
            :key="project.name"
            :href="project.href"
            target="_blank"
            rel="noopener noreferrer"
            class="sub-card flex items-center gap-4 p-5 transition hover:-translate-y-0.5 hover:border-sky-200"
          >
            <div class="text-xl text-amber-600">⌘</div>
            <div class="min-w-0 flex-1">
              <div class="text-lg font-bold text-slate-900">{{ project.name }}</div>
              <div class="truncate font-mono text-xs text-slate-500">{{ project.description }}</div>
            </div>
            <div class="text-slate-300">↗</div>
          </a>
        </div>

        <RouterLink to="/projects" class="mt-5 inline-flex items-center gap-2 text-sm font-bold text-sky-500 hover:text-sky-600">
          View Projects <span>→</span>
        </RouterLink>
      </article>

      <article class="cloud-card p-6">
        <div class="mb-5 flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-full bg-white text-sky-500 shadow-sm">▦</div>
          <h2 class="text-[1.85rem] font-bold tracking-tight text-slate-900">应用集合</h2>
        </div>

        <RouterLink
          to="/apps"
          class="sub-card flex min-h-[136px] flex-col items-center justify-center p-5 text-center transition hover:-translate-y-0.5 hover:border-sky-200"
        >
          <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-sky-50 text-sky-500">▦</div>
          <div class="text-sm font-bold text-slate-900">{{ portalEntry.title }}</div>
          <div class="mt-1 max-w-[210px] text-xs leading-5 text-slate-500">{{ portalEntry.description }}</div>
        </RouterLink>
      </article>

      <article class="cloud-card p-6">
        <div class="mb-5 flex items-center justify-between gap-3">
          <div class="flex items-center gap-3">
            <div class="flex h-11 w-11 items-center justify-center rounded-full bg-white text-sky-500 shadow-sm">☁</div>
            <h2 class="text-[1.85rem] font-bold tracking-tight text-slate-900">运维</h2>
          </div>
          <StatusPill :label="opsSummary.statusLabel" :tone="opsSummary.statusTone" />
        </div>

        <p class="text-sm font-semibold text-slate-800">{{ opsSummary.onlineLabel }}</p>
        <p class="mt-2 text-sm leading-6 text-slate-500">{{ opsOverview.description }}</p>

        <div class="mt-5 grid gap-3">
          <div v-for="entry in opsOverview.entries" :key="entry.title" class="sub-card p-4">
            <p class="text-sm font-bold text-slate-900">{{ entry.title }}</p>
            <p class="mt-2 text-sm leading-6 text-slate-600">{{ entry.description }}</p>
          </div>
        </div>

        <div class="mt-4 flex flex-wrap gap-2">
          <span
            v-for="tag in opsOverview.tags"
            :key="tag"
            class="rounded-full bg-slate-100 px-3 py-1.5 text-xs font-medium text-slate-600"
          >
            {{ tag }}
          </span>
        </div>

        <RouterLink to="/ops/cluster" class="mt-5 inline-flex items-center gap-2 text-sm font-bold text-sky-500 hover:text-sky-600">
          进入 Ops <span>→</span>
        </RouterLink>
      </article>

      <article class="rounded-[2rem] bg-sky-500 p-6 text-white shadow-soft md:col-span-2">
        <p class="eyebrow !text-sky-100/80">Manifesto</p>
        <p class="mt-4 text-[1.8rem] font-bold leading-tight lg:text-[2rem]">{{ manifesto }}</p>
      </article>
    </div>
  </section>
</template>
