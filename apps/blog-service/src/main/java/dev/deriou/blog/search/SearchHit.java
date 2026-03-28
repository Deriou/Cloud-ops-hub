package dev.deriou.blog.search;

import java.time.LocalDateTime;

public record SearchHit(
        Long id,
        String title,
        String slug,
        String summary,
        LocalDateTime updateTime,
        double score
) {
}
