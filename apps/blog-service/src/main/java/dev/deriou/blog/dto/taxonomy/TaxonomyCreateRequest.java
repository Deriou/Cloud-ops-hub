package dev.deriou.blog.dto.taxonomy;

public record TaxonomyCreateRequest(
        String name,
        String slug
) {
}
