package dev.deriou.blog.search;

import java.util.List;

public record SearchResultPage(
        long total,
        List<SearchHit> hits
) {
}
