package dev.deriou.gateway.ops;

import java.time.Instant;
import java.util.List;

public record ClusterSummary(
        String clusterName,
        String region,
        Instant checkedAt,
        List<OpsStat> stats
) {
}
