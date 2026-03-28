package dev.deriou.blog.dto.common;

import java.util.List;

public record PagedResponse<T>(
        long page,
        long size,
        long total,
        long totalPages,
        List<T> records
) {
}
