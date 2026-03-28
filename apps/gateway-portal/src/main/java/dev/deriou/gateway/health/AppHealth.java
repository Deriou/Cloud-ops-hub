package dev.deriou.gateway.health;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "应用聚合健康状态")
public record AppHealth(
        @Schema(description = "应用唯一标识", example = "blog-service")
        String appKey,
        @Schema(description = "聚合状态", example = "UP")
        String status,
        @Schema(description = "下游 Actuator 原始状态", example = "UP")
        String rawStatus,
        @Schema(description = "状态说明", example = "service is healthy")
        String message,
        @Schema(description = "最近一次探活时间")
        Instant checkedAt
) {
}
