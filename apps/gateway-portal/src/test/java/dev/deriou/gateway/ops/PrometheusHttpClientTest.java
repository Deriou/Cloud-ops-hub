package dev.deriou.gateway.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.DefaultUriBuilderFactory;

class PrometheusHttpClientTest {

    @Test
    void query_instant_should_encode_promql_label_selector_as_query_value() {
        String promQl = "sum(up{namespace=\"cloud-ops\", service=~\"gateway-portal|blog-service\"})";

        URI uri = PrometheusHttpClient.instantQueryUri(new DefaultUriBuilderFactory("http://prometheus.test").builder(), promQl);

        assertThat(uri.getPath()).isEqualTo("/api/v1/query");
        assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8)).isEqualTo("query=" + promQl);
    }

    @Test
    void query_range_should_encode_promql_label_selector_as_query_value() {
        String promQl = "1 - (sum(node_memory_MemAvailable_bytes{job=\"node-exporter\"}) / sum(node_memory_MemTotal_bytes{job=\"node-exporter\"}))";
        Instant start = Instant.parse("2026-05-10T08:00:00Z");
        Instant end = Instant.parse("2026-05-10T08:30:00Z");

        URI uri = PrometheusHttpClient.rangeQueryUri(new DefaultUriBuilderFactory("http://prometheus.test").builder(), promQl, start, end, "5m");

        assertThat(uri.getPath()).isEqualTo("/api/v1/query_range");
        assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8))
                .isEqualTo("query=" + promQl + "&start=2026-05-10T08:00:00Z&end=2026-05-10T08:30:00Z&step=5m");
    }
}
