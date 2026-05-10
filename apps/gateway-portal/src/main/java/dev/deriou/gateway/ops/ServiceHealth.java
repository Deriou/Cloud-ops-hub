package dev.deriou.gateway.ops;

import java.time.Instant;

public record ServiceHealth(
        String name,
        String displayName,
        String status,
        String value,
        String source,
        Instant checkedAt,
        String detail
) {
}
