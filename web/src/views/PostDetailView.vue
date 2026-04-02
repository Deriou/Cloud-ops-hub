<script setup lang="ts">
import { fetchPostDetail } from "@/api/blog";
import StatusPill from "@/components/StatusPill.vue";
import { formatDateTime } from "@/lib/format";
import type { PostDetail } from "@/types/blog";
import { onMounted, ref, watch } from "vue";
import { RouterLink } from "vue-router";

const props = defineProps<{
  postId: string;
}>();

const post = ref<PostDetail | null>(null);
const loading = ref(false);
const errorMessage = ref("");

async function loadPost() {
  loading.value = true;
  errorMessage.value = "";

  try {
    post.value = await fetchPostDetail(props.postId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "读取文章详情失败";
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
      <RouterLink to="/blog" class="text-sm text-cyan-200 hover:text-cyan-100">返回文章列表</RouterLink>
      <StatusPill label="rendered html" tone="normal" />
    </div>

    <article v-if="loading" class="rounded-3xl border border-white/10 bg-white/5 p-6 text-slate-300">
      正在加载文章详情...
    </article>

    <article v-else-if="errorMessage" class="rounded-3xl border border-rose-400/30 bg-rose-400/10 p-6 text-rose-100">
      {{ errorMessage }}
    </article>

    <article v-else-if="post" class="grid gap-6">
      <header class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
        <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Blog detail</p>
        <h1 class="mt-3 text-3xl font-semibold text-white">{{ post.title }}</h1>
        <p class="mt-3 text-sm leading-7 text-slate-300">{{ post.summary }}</p>
        <div class="mt-5 flex flex-wrap gap-4 text-sm text-slate-400">
          <span class="font-mono">created {{ formatDateTime(post.createTime) }}</span>
          <span class="font-mono">updated {{ formatDateTime(post.updateTime) }}</span>
          <span class="font-mono">{{ post.slug }}</span>
        </div>
        <div class="mt-5 flex flex-wrap gap-2">
          <StatusPill v-for="tag in post.tags" :key="tag.id" :label="tag.name" tone="normal" />
          <StatusPill v-for="category in post.categories" :key="`category-${category.id}`" :label="category.name" tone="warning" />
        </div>
      </header>

      <article class="rounded-3xl border border-border bg-panel p-6 shadow-soft backdrop-blur-md">
        <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Rendered content</p>
        <div class="article-content mt-5" v-html="post.renderedHtml" />
      </article>
    </article>
  </section>
</template>
