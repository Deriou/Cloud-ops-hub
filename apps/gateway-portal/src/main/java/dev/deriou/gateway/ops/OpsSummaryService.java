package dev.deriou.gateway.ops;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpsSummaryService {

    static final String NODE_CPU_USAGE_QUERY = "1 - avg(rate(node_cpu_seconds_total{job=\"node-exporter\", mode=\"idle\"}[5m]))";
    static final String NODE_MEMORY_USAGE_QUERY = "1 - (sum(node_memory_MemAvailable_bytes{job=\"node-exporter\"}) / sum(node_memory_MemTotal_bytes{job=\"node-exporter\"}))";
    static final String SERVICE_UP_QUERY = "sum(up{namespace=\"cloud-ops\", service=~\"gateway-portal|blog-service\"})";

    private static final Logger log = LoggerFactory.getLogger(OpsSummaryService.class);
    private static final int TREND_MINUTES = 30;
    private static final String TREND_STEP = "5m";

    private final PrometheusClient prometheusClient;
    private final OpsProperties opsProperties;

    public OpsSummaryService(PrometheusClient prometheusClient, OpsProperties opsProperties) {
        this.prometheusClient = Objects.requireNonNull(prometheusClient, "prometheusClient must not be null");
        this.opsProperties = Objects.requireNonNull(opsProperties, "opsProperties must not be null");
    }

    public ClusterSummary getClusterSummary() {
        Instant checkedAt = Instant.now();
        return new ClusterSummary(
                opsProperties.getCluster().getName(),
                opsProperties.getCluster().getRegion(),
                checkedAt,
                List.of(
                        percentageStat("CPU 利用率", NODE_CPU_USAGE_QUERY, checkedAt),
                        percentageStat("内存使用率", NODE_MEMORY_USAGE_QUERY, checkedAt),
                        serviceAvailabilityStat(checkedAt)
                )
        );
    }

    private OpsStat percentageStat(String label, String promQl, Instant checkedAt) {
        try {
            double value = prometheusClient.queryInstant(promQl);
            List<Double> trend = prometheusClient.queryRange(
                    promQl,
                    checkedAt.minusSeconds(TREND_MINUTES * 60L),
                    checkedAt,
                    TREND_STEP
            ).stream().map(this::toPercent).toList();
            double percent = toPercent(value);
            return new OpsStat(label, formatPercent(percent), trend, toneForPercent(percent));
        } catch (Exception ex) {
            log.warn("Failed to load ops metric: label={}, query={}", label, promQl, ex);
            return unavailableStat(label);
        }
    }

    private OpsStat serviceAvailabilityStat(Instant checkedAt) {
        int expectedServiceCount = Math.max(1, opsProperties.getCluster().getExpectedServiceCount());
        try {
            double upCount = prometheusClient.queryInstant(SERVICE_UP_QUERY);
            List<Double> trend = prometheusClient.queryRange(
                    SERVICE_UP_QUERY,
                    checkedAt.minusSeconds(TREND_MINUTES * 60L),
                    checkedAt,
                    TREND_STEP
            ).stream()
                    .map(sample -> Math.min(100.0, sample / expectedServiceCount * 100.0))
                    .toList();
            String value = Math.round(upCount) + "/" + expectedServiceCount;
            return new OpsStat("服务可用", value, trend, upCount >= expectedServiceCount ? "normal" : "danger");
        } catch (Exception ex) {
            log.warn("Failed to load service availability metric: query={}", SERVICE_UP_QUERY, ex);
            return unavailableStat("服务可用");
        }
    }

    private OpsStat unavailableStat(String label) {
        return new OpsStat(label, "N/A", List.of(), "danger");
    }

    private double toPercent(double ratio) {
        if (!Double.isFinite(ratio)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, ratio * 100.0));
    }

    private String formatPercent(double percent) {
        return Math.round(percent) + "%";
    }

    private String toneForPercent(double percent) {
        if (percent >= 85.0) {
            return "danger";
        }
        if (percent >= 70.0) {
            return "warning";
        }
        return "normal";
    }
}
