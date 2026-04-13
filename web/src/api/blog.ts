import { appConfig } from "@/lib/config";
import { requestApi } from "@/lib/http";
import type { PagedResponse } from "@/types/api";
import type { PostDetail, PostSummary, TaxonomyItem } from "@/types/blog";

export function fetchPosts(page = 1, size = 6): Promise<PagedResponse<PostSummary>> {
  return requestApi<PagedResponse<PostSummary>>(appConfig.blogBaseUrl, "/api/v1/blog/posts", {
    query: { pageNo: page, pageSize: size }
  });
}

export function fetchPostDetail(postId: string | number): Promise<PostDetail> {
  return requestApi<PostDetail>(appConfig.blogBaseUrl, `/api/v1/blog/posts/${postId}`);
}

export function searchPosts(keyword: string, pageNo = 1, pageSize = 6): Promise<PagedResponse<PostSummary>> {
  return requestApi<PagedResponse<PostSummary>>(appConfig.blogBaseUrl, "/api/v1/blog/search", {
    query: { q: keyword, pageNo, pageSize }
  });
}

export function fetchTags(): Promise<TaxonomyItem[]> {
  return requestApi<TaxonomyItem[]>(appConfig.blogBaseUrl, "/api/v1/blog/tags");
}

export function fetchCategories(): Promise<TaxonomyItem[]> {
  return requestApi<TaxonomyItem[]>(appConfig.blogBaseUrl, "/api/v1/blog/categories");
}
