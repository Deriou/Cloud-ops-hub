package dev.deriou.gateway.ops;

import java.util.List;

public record OpsStat(
        String label,
        String value,
        List<Double> trend,
        String tone
) {
}
