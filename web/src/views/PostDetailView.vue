<script setup lang="ts">
import { fetchPostDetail } from "@/api/blog";
import StatePanel from "@/components/StatePanel.vue";
import StatusPill from "@/components/StatusPill.vue";
import { toUiError } from "@/lib/errors";
import { formatFullDateTime } from "@/lib/format";
import type { PostDetail } from "@/types/blog";
import { onMounted, ref, watch } from "vue";
import { RouterLink } from "vue-router";

const props = defineProps<{
  postId: string;
}>();

const post = ref<PostDetail | null>(null);
const loading = ref(false);
const errorMessage = ref("");
const errorTraceId = ref("");

async function loadPost() {
  loading.value = true;
  errorMessage.value = "";
  errorTraceId.value = "";

  try {
    post.value = await fetchPostDetail(props.postId);
  } catch (error) {
    const uiError = toUiError(error, "读取文章详情失败");
    errorMessage.value = uiError.message;
    errorTraceId.value = uiError.traceId;
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.postId,
  () => {
    void loadPost();
  }
);

onMounted(() => {
  void loadPost();
});
</script>

<template>
  <section class="grid gap-4">
    <div class="flex items-center justify-between gap-4">
      <RouterLink to="/blog" class="text-sm font-semibold text-sky-600 hover:text-sky-700">返回文章列表</RouterLink>
      <span class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Reader View</span>
    </div>

    <StatePanel v-if="loading" title="Post loading" message="正在加载文章详情..." />
    <StatePanel v-else-if="errorMessage" title="Post unavailable" :message="errorMessage" tone="danger" :trace-id="errorTraceId" />

    <article v-else-if="post" class="grid gap-4">
      <header class="cloud-card p-6 lg:p-8">
        <p class="eyebrow">Blog Detail</p>
        <h1 class="mt-3 max-w-4xl text-[2.2rem] font-extrabold tracking-tight text-slate-900 lg:text-[2.8rem]">{{ post.title }}</h1>
        <p class="mt-4 max-w-4xl text-sm leading-7 text-slate-600">{{ post.summary }}</p>
        <div class="mt-6 flex flex-wrap gap-4 text-sm text-slate-500">
          <span class="font-mono">created {{ formatFullDateTime(post.createTime) }}</span>
          <span class="font-mono">updated {{ formatFullDateTime(post.updateTime) }}</span>
        </div>
        <div class="mt-5 flex flex-wrap gap-2">
          <StatusPill v-for="tag in post.tags" :key="tag.id" :label="tag.name" tone="normal" />
          <StatusPill v-for="category in post.categories" :key="`category-${category.id}`" :label="category.name" tone="warning" />
        </div>
      </header>

      <article class="cloud-card p-6 lg:p-8">
        <div class="article-content" v-html="post.renderedHtml" />
      </article>
    </article>
  </section>
</template>
