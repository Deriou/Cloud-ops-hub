package dev.deriou.gateway.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpsSummaryServiceTest {

    @Mock
    private PrometheusClient prometheusClient;

    private OpsSummaryService opsSummaryService;

    @BeforeEach
    void setUp() {
        OpsProperties opsProperties = new OpsProperties();
        opsProperties.getCluster().setName("k3s-test");
        opsProperties.getCluster().setRegion("cn-test");
        opsProperties.getCluster().setExpectedServiceCount(2);
        opsSummaryService = new OpsSummaryService(prometheusClient, opsProperties);
    }

    @Test
    void summary_should_map_prometheus_metrics_to_frontend_stats() {
        given(prometheusClient.queryInstant(OpsSummaryService.NODE_CPU_USAGE_QUERY)).willReturn(0.123);
        given(prometheusClient.queryRange(eq(OpsSummaryService.NODE_CPU_USAGE_QUERY), any(Instant.class), any(Instant.class), eq("5m")))
                .willReturn(List.of(0.10, 0.11, 0.123));
        given(prometheusClient.queryInstant(OpsSummaryService.NODE_MEMORY_USAGE_QUERY)).willReturn(0.72);
        given(prometheusClient.queryRange(eq(OpsSummaryService.NODE_MEMORY_USAGE_QUERY), any(Instant.class), any(Instant.class), eq("5m")))
                .willReturn(List.of(0.60, 0.68, 0.72));
        given(prometheusClient.queryInstant(OpsSummaryService.SERVICE_UP_QUERY)).willReturn(2.0);
        given(prometheusClient.queryRange(eq(OpsSummaryService.SERVICE_UP_QUERY), any(Instant.class), any(Instant.class), eq("5m")))
                .willReturn(List.of(2.0, 2.0, 2.0));

        ClusterSummary summary = opsSummaryService.getClusterSummary();

        assertThat(summary.clusterName()).isEqualTo("k3s-test");
        assertThat(summary.region()).isEqualTo("cn-test");
        assertThat(summary.checkedAt()).isNotNull();
        assertThat(summary.stats()).hasSize(3);
        assertThat(summary.stats().get(0).label()).isEqualTo("CPU 利用率");
        assertThat(summary.stats().get(0).value()).isEqualTo("12%");
        assertThat(summary.stats().get(0).trend()).containsExactly(10.0, 11.0, 12.3);
        assertThat(summary.stats().get(0).tone()).isEqualTo("normal");
        assertThat(summary.stats().get(1).label()).isEqualTo("内存使用率");
        assertThat(summary.stats().get(1).value()).isEqualTo("72%");
        assertThat(summary.stats().get(1).tone()).isEqualTo("warning");
        assertThat(summary.stats().get(2).label()).isEqualTo("服务可用");
        assertThat(summary.stats().get(2).value()).isEqualTo("2/2");
        assertThat(summary.stats().get(2).trend()).containsExactly(100.0, 100.0, 100.0);
        assertThat(summary.stats().get(2).tone()).isEqualTo("normal");
    }

    @Test
    void prometheus_failure_should_degrade_single_stat_without_failing_summary() {
        given(prometheusClient.queryInstant(OpsSummaryService.NODE_CPU_USAGE_QUERY))
                .willThrow(new IllegalStateException("prometheus unavailable"));
        given(prometheusClient.queryInstant(OpsSummaryService.NODE_MEMORY_USAGE_QUERY)).willReturn(0.35);
        given(prometheusClient.queryRange(eq(OpsSummaryService.NODE_MEMORY_USAGE_QUERY), any(Instant.class), any(Instant.class), eq("5m")))
                .willReturn(List.of(0.30, 0.35));
        given(prometheusClient.queryInstant(OpsSummaryService.SERVICE_UP_QUERY)).willReturn(1.0);
        given(prometheusClient.queryRange(eq(OpsSummaryService.SERVICE_UP_QUERY), any(Instant.class), any(Instant.class), eq("5m")))
                .willReturn(List.of(2.0, 1.0));

        ClusterSummary summary = opsSummaryService.getClusterSummary();

        assertThat(summary.stats().get(0).value()).isEqualTo("N/A");
        assertThat(summary.stats().get(0).tone()).isEqualTo("danger");
        assertThat(summary.stats().get(1).value()).isEqualTo("35%");
        assertThat(summary.stats().get(2).value()).isEqualTo("1/2");
        assertThat(summary.stats().get(2).tone()).isEqualTo("danger");
    }
}
