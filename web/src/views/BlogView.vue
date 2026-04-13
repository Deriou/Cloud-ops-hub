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
    postsPage.value = keyword.value
      ? await searchPosts(keyword.value, page, 6)
      : await fetchPosts(page, 6);
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
  <section class="grid gap-6 xl:grid-cols-[1.7fr_1fr]">
    <div class="grid gap-6">
      <article class="cloud-card p-6">
        <p class="eyebrow">Blog service</p>
        <div class="mt-3 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 class="text-2xl font-semibold text-ink">文章浏览与搜索</h2>
            <p class="mt-2 text-sm leading-6 text-ink-soft">
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
            class="rounded-[1.2rem] border border-sky-100 bg-white/85 px-4 py-3 text-sm text-ink outline-none transition-all duration-300 ease-in-out placeholder:text-ink-soft focus:border-sky-300"
          />
          <button
            type="submit"
            class="rounded-[1.2rem] border border-sky-200 bg-sky-500 px-5 py-3 text-sm font-semibold text-white transition-all duration-300 ease-in-out hover:-translate-y-0.5 hover:bg-sky-600"
          >
            查询
          </button>
        </form>
      </article>

      <section class="grid gap-4">
        <div class="flex items-center justify-between">
          <div>
            <p class="eyebrow">Content list</p>
            <h3 class="mt-2 text-2xl font-semibold text-ink">{{ pageTitle }}</h3>
          </div>
          <p class="font-mono text-sm text-ink-soft">{{ postsPage.total }} records</p>
        </div>

        <StatePanel v-if="loading" title="Blog loading" message="正在加载文章列表..." />
        <StatePanel v-else-if="errorMessage" title="Blog error" :message="errorMessage" tone="danger" :trace-id="errorTraceId" />
        <StatePanel v-else-if="postsPage.records.length === 0" title="No posts" message="当前筛选条件下没有可显示的文章。" />

        <article
          v-for="post in postsPage.records"
          :key="post.id"
          class="cloud-card p-6 transition-all duration-300 ease-in-out hover:-translate-y-1 hover:shadow-float"
        >
          <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <RouterLink :to="`/blog/posts/${post.id}`" class="text-xl font-semibold text-ink hover:text-sky-700">
                {{ post.title }}
              </RouterLink>
              <p class="mt-2 text-sm text-ink-soft">{{ post.slug }}</p>
            </div>
            <span class="font-mono text-sm text-ink-soft">{{ formatDateTime(post.updateTime) }}</span>
          </div>
          <p class="mt-4 text-sm leading-7 text-ink-soft">{{ post.summary }}</p>
        </article>

        <div class="cloud-card-soft flex items-center justify-between p-4 text-sm text-ink-soft">
          <button
            type="button"
            class="rounded-full border border-sky-100 px-4 py-2 transition-all duration-300 ease-in-out hover:bg-white disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="postsPage.page <= 1 || loading"
            @click="changePage(postsPage.page - 1)"
          >
            上一页
          </button>
          <span class="font-mono">page {{ postsPage.page }} / {{ Math.max(postsPage.totalPages, 1) }}</span>
          <button
            type="button"
            class="rounded-full border border-sky-100 px-4 py-2 transition-all duration-300 ease-in-out hover:bg-white disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="postsPage.page >= postsPage.totalPages || loading || postsPage.totalPages === 0"
            @click="changePage(postsPage.page + 1)"
          >
            下一页
          </button>
        </div>
      </section>
    </div>

    <aside class="grid gap-6">
      <article class="cloud-card p-6">
        <p class="eyebrow">Tag entry</p>
        <h3 class="mt-2 text-xl font-semibold text-ink">标签入口</h3>
        <StatePanel
          v-if="taxonomyError"
          class="mt-4"
          title="Taxonomy error"
          :message="taxonomyError"
          tone="warning"
          :trace-id="taxonomyTraceId"
        />
        <div class="mt-4 flex flex-wrap gap-2">
          <button
            v-for="tag in tags"
            :key="tag.id"
            type="button"
            class="rounded-full border border-sky-100 bg-white/75 px-3 py-2 text-sm text-ink-soft transition-all duration-300 ease-in-out hover:-translate-y-0.5 hover:border-sky-200 hover:bg-white"
            @click="quickSearch(tag)"
          >
            {{ tag.name }}
          </button>
        </div>
      </article>

      <article class="cloud-card p-6">
        <p class="eyebrow">Category entry</p>
        <h3 class="mt-2 text-xl font-semibold text-ink">分类入口</h3>
        <div class="mt-4 grid gap-3">
          <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            class="rounded-[1.3rem] border border-sky-100 bg-white/75 px-4 py-3 text-left text-sm text-ink transition-all duration-300 ease-in-out hover:-translate-y-0.5 hover:border-sky-200 hover:bg-white"
            @click="quickSearch(category)"
          >
            <p class="font-medium">{{ category.name }}</p>
            <p class="mt-1 font-mono text-xs text-ink-soft">{{ category.slug }}</p>
          </button>
        </div>
      </article>
    </aside>
  </section>
</template>
