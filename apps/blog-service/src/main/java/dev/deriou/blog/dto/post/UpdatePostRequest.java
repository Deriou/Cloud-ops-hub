package dev.deriou.blog.dto.post;

import java.util.List;

public record UpdatePostRequest(
        String title,
        String slug,
        String markdownContent,
        String summary,
        List<Long> tagIds,
        List<Long> categoryIds
) {
}
