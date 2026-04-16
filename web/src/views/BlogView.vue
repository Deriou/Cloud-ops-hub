<script setup lang="ts">
import { fetchCategories, fetchPosts, fetchTags, searchPosts } from "@/api/blog";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { toUiError } from "@/lib/errors";
import { formatDateTime } from "@/lib/format";
import type { PagedResponse } from "@/types/api";
import type { PostSummary, TaxonomyItem } from "@/types/blog";
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

const keyword = ref(typeof route.query.q === "string" ? route.query.q : "");
const errorTraceId = ref("");
const taxonomyError = ref("");
const taxonomyTraceId = ref("");
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
  taxonomyError.value = "";
  taxonomyTraceId.value = "";

  try {
    const [tagList, categoryList] = await Promise.all([fetchTags(), fetchCategories()]);
    tags.value = tagList;
    categories.value = categoryList;
  } catch (error) {
    const uiError = toUiError(error, "读取标签与分类失败");
    taxonomyError.value = uiError.message;
    taxonomyTraceId.value = uiError.traceId;
  }
}

async function loadPosts(page = 1) {
  loading.value = true;
  errorMessage.value = "";
  errorTraceId.value = "";

  try {
    postsPage.value = keyword.value ? await searchPosts(keyword.value, page, 6) : await fetchPosts(page, 6);
  } catch (error) {
    const uiError = toUiError(error, "读取文章列表失败");
    errorMessage.value = uiError.message;
    errorTraceId.value = uiError.traceId;
  } finally {
    loading.value = false;
  }
}

function submitSearch() {
  void router.replace({
    path: "/blog",
    query: keyword.value ? { q: keyword.value, page: "1" } : {}
  });
}

function changePage(page: number) {
  void router.replace({
    path: "/blog",
    query: {
      ...(keyword.value ? { q: keyword.value } : {}),
      ...(page > 1 ? { page: String(page) } : {})
    }
  });
}

function quickSearch(item: TaxonomyItem) {
  keyword.value = item.name;
  submitSearch();
}

const pageTitle = computed(() => (keyword.value ? `搜索结果: ${keyword.value}` : "最新文章"));

watch(
  () => [route.query.q, route.query.page],
  ([queryKeyword, queryPage]) => {
    keyword.value = typeof queryKeyword === "string" ? queryKeyword : "";
    const page = typeof queryPage === "string" ? Number.parseInt(queryPage, 10) : 1;
    void loadPosts(Number.isFinite(page) && page > 0 ? page : 1);
  },
  { immediate: true }
);

onMounted(() => {
  void loadTaxonomy();
});
</script>

<template>
  <section class="grid gap-4 xl:grid-cols-[1.65fr_0.95fr]">
    <div class="grid gap-4">
      <article class="cloud-card px-6 py-6 lg:px-7">
        <p class="eyebrow">Blog</p>
        <div class="mt-3 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h1 class="text-[2rem] font-extrabold tracking-tight text-slate-900">文章浏览与搜索</h1>
            <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-600">保留搜索、分页与 taxonomy 入口，但整体表达更偏阅读而不是控制台。</p>
          </div>
          <StatusPill :label="keyword ? 'Search' : 'Browse'" :tone="keyword ? 'warning' : 'normal'" />
        </div>

        <form class="mt-5 grid gap-3 md:grid-cols-[minmax(0,1fr)_auto]" @submit.prevent="submitSearch">
          <input
            v-model.trim="keyword"
            type="search"
            placeholder="搜索文章标题或正文关键词"
            class="rounded-[1.15rem] border border-slate-200 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-sky-300"
          />
          <button
            type="submit"
            class="rounded-[1.15rem] bg-sky-500 px-5 py-3 text-sm font-semibold text-white transition hover:bg-sky-600"
          >
            查询
          </button>
        </form>
      </article>

      <section class="grid gap-4">
        <div class="flex items-center justify-between gap-4">
          <div>
            <p class="eyebrow">Content List</p>
            <h2 class="mt-2 text-[1.8rem] font-bold tracking-tight text-slate-900">{{ pageTitle }}</h2>
          </div>
          <p class="font-mono text-sm text-slate-500">{{ postsPage.total }} records</p>
        </div>

        <StatePanel v-if="loading" title="Content loading" message="正在加载文章列表..." />
        <StatePanel v-else-if="errorMessage" title="Blog unavailable" :message="errorMessage" tone="danger" :trace-id="errorTraceId" />
        <StatePanel v-else-if="postsPage.records.length === 0" title="No posts" message="当前筛选条件下没有可显示的文章。" />

        <article
          v-for="post in postsPage.records"
          :key="post.id"
          class="cloud-card p-6 transition hover:-translate-y-0.5 hover:shadow-float"
        >
          <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <RouterLink :to="`/blog/posts/${post.id}`" class="text-[1.35rem] font-bold tracking-tight text-slate-900 hover:text-sky-600">
                {{ post.title }}
              </RouterLink>
            </div>
            <span class="font-mono text-sm text-slate-500">{{ formatDateTime(post.updateTime) }}</span>
          </div>
          <p class="mt-4 text-sm leading-7 text-slate-600">{{ post.summary }}</p>
        </article>

        <div class="sub-card flex items-center justify-between p-4 text-sm text-slate-500">
          <button
            type="button"
            class="rounded-full border border-slate-200 px-4 py-2 transition hover:bg-white disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="postsPage.page <= 1 || loading"
            @click="changePage(postsPage.page - 1)"
          >
            上一页
          </button>
          <span class="font-mono">page {{ postsPage.page }} / {{ Math.max(postsPage.totalPages, 1) }}</span>
          <button
            type="button"
            class="rounded-full border border-slate-200 px-4 py-2 transition hover:bg-white disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="postsPage.page >= postsPage.totalPages || loading || postsPage.totalPages === 0"
            @click="changePage(postsPage.page + 1)"
          >
            下一页
          </button>
        </div>
      </section>
    </div>

    <aside class="grid gap-4">
      <article class="cloud-card p-6">
        <p class="eyebrow">Tags</p>
        <div class="mt-3 flex items-center justify-between gap-4">
          <h3 class="text-xl font-bold text-slate-900">标签入口</h3>
          <span class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">{{ tags.length }} tags</span>
        </div>
        <StatePanel
          v-if="taxonomyError"
          class="mt-4"
          title="Taxonomy unavailable"
          :message="taxonomyError"
          tone="warning"
          :trace-id="taxonomyTraceId"
        />
        <div v-else class="mt-4 flex flex-wrap gap-2">
          <button
            v-for="tag in tags"
            :key="tag.id"
            type="button"
            class="rounded-full border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600 transition hover:-translate-y-0.5 hover:border-sky-200 hover:text-sky-600"
            @click="quickSearch(tag)"
          >
            {{ tag.name }}
          </button>
        </div>
      </article>

      <article class="cloud-card p-6">
        <p class="eyebrow">Categories</p>
        <div class="mt-3 flex items-center justify-between gap-4">
          <h3 class="text-xl font-bold text-slate-900">分类入口</h3>
          <span class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">{{ categories.length }} categories</span>
        </div>
        <div class="mt-4 grid gap-3">
          <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            class="sub-card px-4 py-3 text-left transition hover:-translate-y-0.5 hover:border-sky-200"
            @click="quickSearch(category)"
          >
            <p class="text-sm font-semibold text-slate-900">{{ category.name }}</p>
            <p class="mt-1 font-mono text-xs text-slate-400">{{ category.slug }}</p>
          </button>
        </div>
      </article>
    </aside>
  </section>
</template>
