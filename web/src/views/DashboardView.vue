<script setup lang="ts">
import { fetchPosts } from "@/api/blog";
import { fetchClusterSummary, fetchServiceHealth } from "@/api/ops";
import CardHeader from "@/components/CardHeader.vue";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { curatedApps, featuredProjects, manifesto, opsOverview, profile, siteMeta } from "@/content/site";
import { toUiError } from "@/lib/errors";
import { formatDateTime, formatFullDateTime } from "@/lib/format";
import { usePortalStore } from "@/stores/portal";
import type { PostSummary } from "@/types/blog";
import type { ClusterSummary, ServiceHealth } from "@/types/ops";
import {
  Activity,
  ArrowRight,
  ExternalLink,
  FlaskConical,
  Github,
  Mail,
  Newspaper,
  Quote,
  Sparkles
} from "lucide-vue-next";
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
    <article class="cloud-card overflow-hidden lg:col-span-4">
      <div class="relative h-24 bg-gradient-to-r from-sky-400 to-sky-500">
        <div class="absolute -right-6 -top-8 h-28 w-28 rounded-full bg-white/20 blur-2xl" />
        <div class="absolute left-8 bottom-3 h-16 w-16 rounded-full bg-white/10 blur-xl" />
      </div>

      <div class="px-7 pb-7">
        <div class="-mt-14 flex flex-col items-center text-center">
          <img
            :src="profile.avatarUrl"
            :alt="`${profile.name} avatar`"
            class="h-28 w-28 rounded-full object-cover shadow-lg shadow-sky-500/20 ring-4 ring-white"
          />
          <h1 class="mt-4 text-[2.1rem] font-extrabold tracking-tight text-slate-900">{{ profile.name }}</h1>
          <span
            class="mt-3 inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700"
          >
            <span class="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" aria-hidden="true" />
            CS Student · Available
          </span>
          <p class="mt-3 text-base font-medium text-slate-600">{{ profile.tagline }}</p>
          <p class="mt-4 max-w-xs text-sm leading-6 text-slate-500">{{ profile.intro }}</p>
        </div>

        <div class="mt-7 border-t border-slate-200/70 pt-6">
          <p class="eyebrow">Contact</p>
          <div class="mt-4 flex flex-wrap justify-center gap-3 lg:justify-start">
            <a
              :href="siteMeta.githubUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center gap-2 rounded-full bg-slate-100 px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-sky-50 hover:text-sky-600"
            >
              <Github :size="16" />
              {{ siteMeta.githubUsername }}
            </a>
            <a
              :href="`mailto:${siteMeta.email}`"
              class="inline-flex items-center gap-2 rounded-full bg-slate-100 px-4 py-2 text-sm font-medium text-slate-600 transition hover:bg-sky-50 hover:text-sky-600"
            >
              <Mail :size="16" />
              {{ siteMeta.email }}
            </a>
          </div>
        </div>

        <div class="mt-6 border-t border-slate-200/70 pt-6">
          <p class="eyebrow">Tech Stack</p>
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
      </div>
    </article>

    <div class="grid gap-4 lg:col-span-8 md:grid-cols-2">
      <article class="cloud-card p-6">
        <CardHeader :icon="Newspaper" eyebrow="Writing" title="Blog">
          <template #action>
            <span class="rounded-full bg-sky-100 px-3 py-1 text-sm font-semibold text-sky-600">{{ postTotal }} posts</span>
          </template>
        </CardHeader>

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
        <CardHeader :icon="Github" eyebrow="Showcase" title="Projects">
          <template #action>
            <RouterLink
              to="/projects"
              class="inline-flex items-center gap-1 text-sm font-bold text-sky-500 hover:text-sky-600"
            >
              View all <ArrowRight :size="15" />
            </RouterLink>
          </template>
        </CardHeader>

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
      </article>

      <article class="cloud-card p-6">
        <CardHeader :icon="FlaskConical" eyebrow="Experimental" title="Apps" tone="violet">
          <template #action>
            <span class="rounded-full bg-violet-100 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wider text-violet-600">
              Beta
            </span>
          </template>
        </CardHeader>

        <p class="text-sm leading-6 text-slate-500">{{ portalEntry.description }}</p>

        <div class="mt-4 grid grid-cols-3 gap-2.5">
          <div
            v-for="n in 3"
            :key="n"
            class="flex flex-col items-center justify-center gap-1.5 rounded-2xl border border-dashed border-slate-300/80 bg-slate-50/50 py-5"
          >
            <Sparkles :size="18" class="text-slate-300" />
            <span class="text-[10px] font-medium text-slate-400">敬请期待</span>
          </div>
        </div>

        <RouterLink
          to="/apps"
          class="mt-5 inline-flex items-center gap-1.5 text-sm font-bold text-sky-500 hover:text-sky-600"
        >
          Open lab <ArrowRight :size="16" />
        </RouterLink>
      </article>

      <article class="cloud-card p-6">
        <CardHeader :icon="Activity" eyebrow="Live" title="Ops" tone="emerald">
          <template #action>
            <StatusPill :label="opsSummary.statusLabel" :tone="opsSummary.statusTone" />
          </template>
        </CardHeader>

        <p class="text-sm font-semibold text-slate-700">{{ opsSummary.onlineLabel }}</p>

        <div v-if="liveServices.length" class="mt-4 flex flex-wrap gap-x-4 gap-y-2">
          <span
            v-for="svc in liveServices"
            :key="svc.name"
            class="flex items-center gap-1.5 text-xs font-medium text-slate-600"
          >
            <span
              :class="['inline-block h-2 w-2 rounded-full', statusDotClass(svc.status)]"
              aria-hidden="true"
            />
            {{ svc.displayName }}
          </span>
        </div>

        <div v-if="liveSummary?.stats?.length" class="mt-4 grid grid-cols-3 gap-2.5">
          <div
            v-for="stat in liveSummary.stats"
            :key="stat.label"
            class="rounded-2xl bg-slate-50/70 px-3 py-2.5 text-center"
          >
            <div class="font-mono text-base font-bold text-slate-900">{{ stat.value }}</div>
            <div class="mt-0.5 truncate text-[10px] text-slate-400">{{ stat.label }}</div>
          </div>
        </div>

        <RouterLink
          to="/ops/cluster"
          class="mt-5 inline-flex items-center gap-1.5 text-sm font-bold text-sky-500 hover:text-sky-600"
        >
          Enter Ops <ArrowRight :size="16" />
        </RouterLink>
      </article>

      <article
        class="relative overflow-hidden rounded-[2rem] bg-gradient-to-br from-sky-500 to-sky-600 p-7 text-white shadow-soft md:col-span-2"
      >
        <div class="absolute -right-8 -top-10 h-40 w-40 rounded-full bg-white/10 blur-2xl" aria-hidden="true" />
        <Quote
          :size="64"
          class="absolute right-6 top-5 text-white/15"
          aria-hidden="true"
        />
        <div class="relative">
          <p class="eyebrow !text-sky-100/80">Manifesto</p>
          <p class="mt-4 max-w-2xl text-[1.7rem] font-bold leading-tight lg:text-[2rem]">{{ manifesto }}</p>
          <p class="mt-4 text-sm font-medium text-sky-100/90">— {{ profile.name }}</p>
        </div>
      </article>
    </div>
  </section>
</template>
