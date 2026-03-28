package dev.deriou.blog.converter;

import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.dto.post.PostDetailResponse;
import dev.deriou.blog.dto.post.PostSummaryResponse;
import dev.deriou.blog.dto.taxonomy.TaxonomyResponse;
import java.util.List;

public final class PostConverter {

    private PostConverter() {
    }

    public static PostSummaryResponse toSummary(PostEntity entity) {
        return new PostSummaryResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getSlug(),
                entity.getSummary(),
                entity.getUpdateTime()
        );
    }

    public static PostDetailResponse toDetail(
            PostEntity entity,
            String renderedHtml,
            List<TaxonomyResponse> tags,
            List<TaxonomyResponse> categories
    ) {
        return new PostDetailResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getSlug(),
                entity.getMarkdownContent(),
                renderedHtml,
                entity.getSummary(),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                tags,
                categories
        );
    }
}
