package dev.deriou.blog.search;

public record SearchQuery(
        String keyword,
        long pageNo,
        long pageSize
) {
}
