package dev.deriou.gateway.ops;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PrometheusHttpClient implements PrometheusClient {

    private final RestClient restClient;

    public PrometheusHttpClient(RestClient.Builder restClientBuilder, OpsProperties opsProperties) {
        Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null");
        Objects.requireNonNull(opsProperties, "opsProperties must not be null");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) opsProperties.getPrometheus().getTimeout().toMillis());
        requestFactory.setReadTimeout((int) opsProperties.getPrometheus().getTimeout().toMillis());
        this.restClient = restClientBuilder
                .baseUrl(opsProperties.getPrometheus().getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public double queryInstant(String promQl) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", promQl)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode result = requireSuccess(response)
                .path("data")
                .path("result");
        if (!result.isArray() || result.isEmpty()) {
            throw new IllegalStateException("Prometheus query returned no data");
        }
        return parsePrometheusValue(result.get(0).path("value"));
    }

    @Override
    public List<Double> queryRange(String promQl, Instant start, Instant end, String step) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query_range")
                        .queryParam("query", promQl)
                        .queryParam("start", start.toString())
                        .queryParam("end", end.toString())
                        .queryParam("step", step)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode result = requireSuccess(response)
                .path("data")
                .path("result");
        if (!result.isArray() || result.isEmpty()) {
            throw new IllegalStateException("Prometheus range query returned no data");
        }

        JsonNode values = result.get(0).path("values");
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalStateException("Prometheus range query returned no samples");
        }

        List<Double> samples = new ArrayList<>();
        values.forEach(valueNode -> samples.add(parsePrometheusValue(valueNode)));
        return samples;
    }

    private JsonNode requireSuccess(JsonNode response) {
        if (response == null || !"success".equals(response.path("status").asText())) {
            throw new IllegalStateException("Prometheus query failed");
        }
        return response;
    }

    private double parsePrometheusValue(JsonNode valueNode) {
        if (!valueNode.isArray() || valueNode.size() < 2) {
            throw new IllegalStateException("Prometheus value is malformed");
        }
        return Double.parseDouble(valueNode.get(1).asText());
    }
}
