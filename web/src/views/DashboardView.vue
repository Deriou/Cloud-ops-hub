<script setup lang="ts">
import { fetchPosts } from "@/api/blog";
import { fetchClusterSummary, fetchServiceHealth } from "@/api/ops";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { curatedApps, featuredProjects, manifesto, opsOverview, profile, siteMeta } from "@/content/site";
import { toUiError } from "@/lib/errors";
import { formatDateTime, formatFullDateTime } from "@/lib/format";
import { usePortalStore } from "@/stores/portal";
import type { PostSummary } from "@/types/blog";
import type { ClusterSummary, ServiceHealth } from "@/types/ops";
import { ArrowRight, Cloud, ExternalLink, Github, LayoutGrid } from "lucide-vue-next";
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";

const portalStore = usePortalStore();

const latestPost = ref<PostSummary | null>(null);
const recentPosts = ref<PostSummary[]>([]);
const postTotal = ref(0);
const blogLoading = ref(false);
const blogError = ref("");
const blogTraceId = ref("");

const liveSummary = ref<ClusterSummary | null>(null);
const liveServices = ref<ServiceHealth[]>([]);
const liveError = ref(false);
const liveLoaded = ref(false);

async function loadBlogSnapshot() {
  blogLoading.value = true;
  blogError.value = "";
  blogTraceId.value = "";

  try {
    const page = await fetchPosts(1, 4);
    latestPost.value = page.records[0] ?? null;
    recentPosts.value = page.records.slice(1, 4);
    postTotal.value = page.total;
  } catch (error) {
    const uiError = toUiError(error, "Failed to load posts");
    blogError.value = uiError.message;
    blogTraceId.value = uiError.traceId;
  } finally {
    blogLoading.value = false;
  }
}

async function loadLive() {
  try {
    const [summary, services] = await Promise.all([fetchClusterSummary(), fetchServiceHealth()]);
    liveSummary.value = summary;
    liveServices.value = services;
    liveError.value = false;
  } catch {
    liveError.value = true;
  } finally {
    liveLoaded.value = true;
  }
}

function statusDotClass(status: ServiceHealth["status"]): string {
  switch (status) {
    case "UP":
      return "bg-emerald-400";
    case "DOWN":
      return "bg-rose-500";
    default:
      return "bg-slate-300";
  }
}

const portalEntry = curatedApps[0];
const homepageProjects = computed(() => featuredProjects.slice(0, 2));

const opsSummary = computed(() => ({
  statusLabel: portalStore.hydrated ? portalStore.healthSummary.headline : opsOverview.statusLabel,
  statusTone: portalStore.hydrated ? portalStore.healthSummary.tone : ("normal" as const),
  onlineLabel: portalStore.hydrated ? portalStore.healthSummary.label : opsOverview.summary
}));

onMounted(() => {
  void loadBlogSnapshot();
  void loadLive();
  void portalStore.ensureHydrated();
});
</script>

<template>
  <header
    class="cloud-card mb-4 flex flex-col gap-3 px-5 py-3 md:flex-row md:items-center md:justify-between"
    aria-label="Live system status"
  >
    <template v-if="liveError">
      <p class="font-mono text-xs text-rose-500">System offline · retrying</p>
    </template>

    <template v-else-if="!liveLoaded">
      <div class="h-3 w-40 animate-pulse rounded-full bg-slate-200" />
      <div class="hidden h-3 w-60 animate-pulse rounded-full bg-slate-200 md:block" />
      <div class="hidden h-3 w-32 animate-pulse rounded-full bg-slate-200 md:block" />
    </template>

    <template v-else>
      <div class="flex flex-wrap items-center gap-4 font-mono text-xs">
        <span
          v-for="svc in liveServices"
          :key="svc.name"
          class="flex items-center gap-1.5"
        >
          <span
            :class="['inline-block h-2 w-2 rounded-full animate-pulse', statusDotClass(svc.status)]"
            aria-hidden="true"
          />
          <span class="text-slate-700">{{ svc.name }}</span>
        </span>
      </div>

      <span class="hidden h-3 w-px bg-slate-200 md:inline-block" aria-hidden="true" />

      <div
        v-if="liveSummary?.stats?.length"
        class="flex flex-wrap items-center gap-4 font-mono text-xs text-slate-600"
      >
        <span v-for="stat in liveSummary.stats" :key="stat.label">
          {{ stat.label }}
          <span class="text-slate-900">{{ stat.value }}</span>
        </span>
      </div>

      <span class="hidden h-3 w-px bg-slate-200 md:inline-block" aria-hidden="true" />

      <p class="font-mono text-[11px] text-slate-500">
        Updated {{ liveSummary ? formatFullDateTime(liveSummary.checkedAt) : "—" }}
      </p>
    </template>
  </header>

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
            class="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-medium text-slate-700"
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
            <h2 class="mt-2 text-[1.85rem] font-bold tracking-tight text-slate-900">Blog</h2>
          </div>
          <span class="rounded-full bg-sky-100 px-3 py-1 text-sm font-semibold text-sky-600">{{ postTotal }} posts</span>
        </div>

        <StatePanel v-if="blogLoading" title="Latest" message="Loading..." />
        <StatePanel
          v-else-if="blogError && !latestPost"
          title="Blog unavailable"
          :message="blogError"
          tone="danger"
          :trace-id="blogTraceId"
        />

        <template v-else-if="latestPost">
          <div class="sub-card p-5">
            <p class="eyebrow">Latest</p>
            <RouterLink
              :to="`/blog/posts/${latestPost.id}`"
              class="mt-4 block text-[1.45rem] font-bold leading-tight text-slate-900 hover:text-sky-600"
            >
              {{ latestPost.title }}
            </RouterLink>
            <div class="mt-4 flex items-center justify-between gap-3 text-sm text-slate-500">
              <span class="font-mono">{{ formatDateTime(latestPost.updateTime) }}</span>
              <RouterLink
                :to="`/blog/posts/${latestPost.id}`"
                class="inline-flex items-center gap-1 font-semibold text-sky-600 transition hover:text-sky-700"
              >
                Read <ArrowRight :size="14" />
              </RouterLink>
            </div>
          </div>

          <div v-if="recentPosts.length" class="mt-4 border-t border-slate-200/70 pt-4">
            <p class="eyebrow">Recent</p>
            <ul class="mt-3 divide-y divide-slate-200/70">
              <li
                v-for="post in recentPosts"
                :key="post.id"
                class="flex items-center justify-between gap-3 py-2.5"
              >
                <RouterLink
                  :to="`/blog/posts/${post.id}`"
                  class="truncate text-sm font-semibold text-slate-700 hover:text-sky-600"
                >
                  {{ post.title }}
                </RouterLink>
                <span class="shrink-0 font-mono text-[11px] text-slate-400">
                  {{ formatDateTime(post.updateTime) }}
                </span>
              </li>
            </ul>
          </div>
        </template>

        <StatePanel v-else title="Latest" message="No posts yet." />
      </article>

      <article class="cloud-card p-6">
        <div class="mb-5 flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-full bg-white text-sky-500 shadow-sm">
            <Github :size="22" :stroke-width="2.2" />
          </div>
          <h2 class="text-[1.85rem] font-bold tracking-tight text-slate-900">Projects</h2>
        </div>

        <div class="space-y-3">
          <a
            v-for="project in homepageProjects"
            :key="project.name"
            :href="project.href"
            target="_blank"
            rel="noopener noreferrer"
            class="sub-card flex flex-col gap-3 p-5 transition hover:-translate-y-0.5 hover:border-sky-200"
          >
            <div class="flex items-center gap-4">
              <Github :size="20" class="text-amber-600" />
              <div class="min-w-0 flex-1">
                <div class="text-lg font-bold text-slate-900">{{ project.name }}</div>
                <div class="truncate font-mono text-xs text-slate-500">{{ project.description }}</div>
              </div>
              <ExternalLink :size="16" class="text-slate-300" />
            </div>
            <div v-if="project.stack?.length" class="flex flex-wrap gap-1.5">
              <span
                v-for="tech in project.stack"
                :key="tech"
                class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-600"
              >
                {{ tech }}
              </span>
            </div>
          </a>
        </div>

        <RouterLink
          to="/projects"
          class="mt-5 inline-flex items-center gap-1.5 text-sm font-bold text-sky-500 hover:text-sky-600"
        >
          View all <ArrowRight :size="16" />
        </RouterLink>
      </article>

      <article class="cloud-card p-6">
        <div class="mb-5 flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-full bg-white text-sky-500 shadow-sm">
            <LayoutGrid :size="22" :stroke-width="2.2" />
          </div>
          <h2 class="text-[1.85rem] font-bold tracking-tight text-slate-900">Apps</h2>
        </div>

        <RouterLink
          to="/apps"
          class="sub-card flex min-h-[136px] flex-col items-center justify-center p-5 text-center transition hover:-translate-y-0.5 hover:border-sky-200"
        >
          <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-sky-50 text-sky-500">
            <LayoutGrid :size="22" />
          </div>
          <div class="text-sm font-bold text-slate-900">{{ portalEntry.title }}</div>
          <div class="mt-1 max-w-[210px] text-xs leading-5 text-slate-500">{{ portalEntry.description }}</div>
        </RouterLink>
      </article>

      <article class="cloud-card p-6">
        <div class="mb-5 flex items-center justify-between gap-3">
          <div class="flex items-center gap-3">
            <div class="flex h-11 w-11 items-center justify-center rounded-full bg-white text-sky-500 shadow-sm">
              <Cloud :size="22" :stroke-width="2.2" />
            </div>
            <h2 class="text-[1.85rem] font-bold tracking-tight text-slate-900">Ops</h2>
          </div>
          <StatusPill :label="opsSummary.statusLabel" :tone="opsSummary.statusTone" />
        </div>

        <p class="text-base font-semibold text-slate-800">{{ opsSummary.onlineLabel }}</p>

        <RouterLink
          to="/ops/cluster"
          class="mt-5 inline-flex items-center gap-1.5 text-sm font-bold text-sky-500 hover:text-sky-600"
        >
          Enter Ops <ArrowRight :size="16" />
        </RouterLink>
      </article>

      <article class="rounded-[2rem] bg-sky-500 p-6 text-white shadow-soft md:col-span-2">
        <p class="eyebrow !text-sky-100/80">Manifesto</p>
        <p class="mt-4 text-[1.8rem] font-bold leading-tight lg:text-[2rem]">{{ manifesto }}</p>
      </article>
    </div>
  </section>
</template>
