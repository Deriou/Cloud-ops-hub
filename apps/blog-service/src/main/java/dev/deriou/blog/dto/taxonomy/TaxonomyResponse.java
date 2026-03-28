package dev.deriou.blog.dto.taxonomy;

public record TaxonomyResponse(
        Long id,
        String name,
        String slug
) {
}
