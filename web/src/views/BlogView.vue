<script setup lang="ts">
import { fetchCategories, fetchPosts, fetchTags, searchPosts } from "@/api/blog";
import StatusPill from "@/components/StatusPill.vue";
import { formatDateTime } from "@/lib/format";
import type { PagedResponse } from "@/types/api";
import type { PostSummary, TaxonomyItem } from "@/types/blog";
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

const keyword = ref(typeof route.query.q === "string" ? route.query.q : "");
const postsPage = ref<PagedResponse<PostSummary>>({
  page: 1,
  size: 6,
  total: 0,
  totalPages: 0,
  records: []
});
const tags = ref<TaxonomyItem[]>([]);
const categories = ref<TaxonomyItem[]>([]);
const loading = ref(false);
const errorMessage = ref("");

async function loadTaxonomy() {
  try {
    const [tagList, categoryList] = await Promise.all([fetchTags(), fetchCategories()]);
    tags.value = tagList;
    categories.value = categoryList;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "读取标签与分类失败";
  }
}

async function loadPosts(page = 1) {
  loading.value = true;
  errorMessage.value = "";

  try {
    postsPage.value = keyword.value
      ? await searchPosts(keyword.value, page, 6)
      : await fetchPosts(page, 6);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "读取文章列表失败";
  } finally {
    loading.value = false;
  }
}

function submitSearch() {
  void router.replace({
    path: "/blog",
    query: keyword.value ? { q: keyword.value } : {}
  });
}

function quickSearch(item: TaxonomyItem) {
  keyword.value = item.name;
  submitSearch();
}

const pageTitle = computed(() => (keyword.value ? `搜索结果: ${keyword.value}` : "最新文章"));

watch(
  () => route.query.q,
  (value) => {
    keyword.value = typeof value === "string" ? value : "";
    void loadPosts(1);
  }
);

onMounted(() => {
  void Promise.all([loadTaxonomy(), loadPosts()]);
});
</script>

<template>
  <section class="grid gap-6 xl:grid-cols-[1.7fr_1fr]">
    <div class="grid gap-6">
      <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
        <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Blog service</p>
        <div class="mt-3 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 class="text-2xl font-semibold text-white">文章浏览与搜索</h2>
            <p class="mt-2 text-sm leading-6 text-slate-300">
              对接 `posts`、`search`、`tags`、`categories` 四类只读接口，先完成 Guest 模式友好的内容浏览链路。
            </p>
          </div>
          <StatusPill :label="keyword ? 'search mode' : 'list mode'" :tone="keyword ? 'warning' : 'normal'" />
        </div>

        <form class="mt-5 grid gap-3 md:grid-cols-[minmax(0,1fr)_auto]" @submit.prevent="submitSearch">
          <input
            v-model.trim="keyword"
            type="search"
            placeholder="搜索文章标题或正文关键词"
            class="rounded-2xl border border-white/10 bg-slate-900/80 px-4 py-3 text-sm text-white outline-none transition-all duration-300 ease-in-out placeholder:text-slate-500 focus:border-cyan-400/40"
          />
          <button
            type="submit"
            class="rounded-2xl border border-cyan-400/30 bg-cyan-400/10 px-5 py-3 text-sm text-cyan-100 transition-all duration-300 ease-in-out hover:bg-cyan-400/20"
          >
            查询
          </button>
        </form>
      </article>

      <section class="grid gap-4">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Content list</p>
            <h3 class="mt-2 text-2xl font-semibold text-white">{{ pageTitle }}</h3>
          </div>
          <p class="font-mono text-sm text-slate-400">{{ postsPage.total }} records</p>
        </div>

        <div v-if="loading" class="rounded-3xl border border-white/10 bg-white/5 p-6 text-slate-300">
          正在加载文章列表...
        </div>

        <div v-else-if="errorMessage" class="rounded-3xl border border-rose-400/30 bg-rose-400/10 p-6 text-rose-100">
          {{ errorMessage }}
        </div>

        <article
          v-for="post in postsPage.records"
          :key="post.id"
          class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md transition-all duration-300 ease-in-out hover:-translate-y-1 hover:shadow-xl"
        >
          <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <RouterLink :to="`/blog/posts/${post.id}`" class="text-xl font-semibold text-white hover:text-cyan-200">
                {{ post.title }}
              </RouterLink>
              <p class="mt-2 text-sm text-slate-400">{{ post.slug }}</p>
            </div>
            <span class="font-mono text-sm text-slate-400">{{ formatDateTime(post.updateTime) }}</span>
          </div>
          <p class="mt-4 text-sm leading-7 text-slate-300">{{ post.summary }}</p>
        </article>

        <div class="flex items-center justify-between rounded-3xl border border-white/10 bg-white/5 p-4 text-sm text-slate-300">
          <button
            type="button"
            class="rounded-full border border-white/10 px-4 py-2 transition-all duration-300 ease-in-out hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="postsPage.page <= 1 || loading"
            @click="loadPosts(postsPage.page - 1)"
          >
            上一页
          </button>
          <span class="font-mono">page {{ postsPage.page }} / {{ Math.max(postsPage.totalPages, 1) }}</span>
          <button
            type="button"
            class="rounded-full border border-white/10 px-4 py-2 transition-all duration-300 ease-in-out hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="postsPage.page >= postsPage.totalPages || loading || postsPage.totalPages === 0"
            @click="loadPosts(postsPage.page + 1)"
          >
            下一页
          </button>
        </div>
      </section>
    </div>

    <aside class="grid gap-6">
      <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
        <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Tag entry</p>
        <h3 class="mt-2 text-xl font-semibold text-white">标签入口</h3>
        <div class="mt-4 flex flex-wrap gap-2">
          <button
            v-for="tag in tags"
            :key="tag.id"
            type="button"
            class="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-sm text-slate-200 transition-all duration-300 ease-in-out hover:border-cyan-400/30 hover:bg-cyan-400/10"
            @click="quickSearch(tag)"
          >
            {{ tag.name }}
          </button>
        </div>
      </article>

      <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
        <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Category entry</p>
        <h3 class="mt-2 text-xl font-semibold text-white">分类入口</h3>
        <div class="mt-4 grid gap-3">
          <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            class="rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-left text-sm text-slate-200 transition-all duration-300 ease-in-out hover:border-cyan-400/30 hover:bg-cyan-400/10"
            @click="quickSearch(category)"
          >
            <p class="font-medium">{{ category.name }}</p>
            <p class="mt-1 font-mono text-xs text-slate-500">{{ category.slug }}</p>
          </button>
        </div>
      </article>
    </aside>
  </section>
</template>
