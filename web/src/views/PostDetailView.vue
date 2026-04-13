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

watch(() => props.postId, () => {
  void loadPost();
});

onMounted(() => {
  void loadPost();
});
</script>

<template>
  <section class="grid gap-6">
    <div class="flex items-center justify-between">
      <RouterLink to="/blog" class="text-sm font-medium text-sky-700 hover:text-sky-900">返回文章列表</RouterLink>
      <StatusPill label="rendered html" tone="normal" />
    </div>

    <StatePanel v-if="loading" title="Post loading" message="正在加载文章详情..." />
    <StatePanel v-else-if="errorMessage" title="Post error" :message="errorMessage" tone="danger" :trace-id="errorTraceId" />

    <article v-else-if="post" class="grid gap-6">
      <header class="cloud-card p-6 lg:p-8">
        <p class="eyebrow">Blog detail</p>
        <h1 class="mt-3 text-3xl font-semibold text-ink lg:text-4xl">{{ post.title }}</h1>
        <p class="mt-4 max-w-4xl text-sm leading-7 text-ink-soft">{{ post.summary }}</p>
        <div class="mt-6 flex flex-wrap gap-4 text-sm text-ink-soft">
          <span class="font-mono">created {{ formatFullDateTime(post.createTime) }}</span>
          <span class="font-mono">updated {{ formatFullDateTime(post.updateTime) }}</span>
          <span class="font-mono">{{ post.slug }}</span>
        </div>
        <div class="mt-5 flex flex-wrap gap-2">
          <StatusPill v-for="tag in post.tags" :key="tag.id" :label="tag.name" tone="normal" />
          <StatusPill v-for="category in post.categories" :key="`category-${category.id}`" :label="category.name" tone="warning" />
        </div>
      </header>

      <article class="cloud-card p-6 lg:p-8">
        <p class="eyebrow">Rendered content</p>
        <div class="article-content mt-5" v-html="post.renderedHtml" />
      </article>
    </article>
  </section>
</template>
