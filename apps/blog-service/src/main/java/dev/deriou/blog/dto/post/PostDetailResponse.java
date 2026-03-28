package dev.deriou.blog.dto.post;

import dev.deriou.blog.dto.taxonomy.TaxonomyResponse;
import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
        Long id,
        String title,
        String slug,
        String markdownContent,
        String renderedHtml,
        String summary,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        List<TaxonomyResponse> tags,
        List<TaxonomyResponse> categories
) {
}
