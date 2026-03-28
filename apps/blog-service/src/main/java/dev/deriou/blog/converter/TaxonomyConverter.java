package dev.deriou.blog.converter;

import dev.deriou.blog.domain.entity.CategoryEntity;
import dev.deriou.blog.domain.entity.TagEntity;
import dev.deriou.blog.dto.taxonomy.TaxonomyResponse;

public final class TaxonomyConverter {

    private TaxonomyConverter() {
    }

    public static TaxonomyResponse toResponse(TagEntity entity) {
        return new TaxonomyResponse(entity.getId(), entity.getName(), entity.getSlug());
    }

    public static TaxonomyResponse toResponse(CategoryEntity entity) {
        return new TaxonomyResponse(entity.getId(), entity.getName(), entity.getSlug());
    }
}
