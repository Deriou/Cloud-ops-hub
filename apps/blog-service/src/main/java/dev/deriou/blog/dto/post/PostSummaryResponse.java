package dev.deriou.blog.dto.post;

import java.time.LocalDateTime;

public record PostSummaryResponse(
        Long id,
        String title,
        String slug,
        String summary,
        LocalDateTime updateTime
) {
}
