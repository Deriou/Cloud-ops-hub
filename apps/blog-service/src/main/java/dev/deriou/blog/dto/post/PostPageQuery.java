package dev.deriou.blog.dto.post;

public record PostPageQuery(
        long page,
        long size
) {
}
