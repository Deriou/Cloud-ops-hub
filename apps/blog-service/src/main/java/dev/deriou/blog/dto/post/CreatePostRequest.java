package dev.deriou.blog.dto.post;

import java.util.List;

public record CreatePostRequest(
        String title,
        String slug,
        String markdownContent,
        String summary,
        List<Long> tagIds,
        List<Long> categoryIds
) {
}
