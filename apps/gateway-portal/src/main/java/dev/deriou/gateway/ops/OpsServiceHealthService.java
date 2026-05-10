package dev.deriou.gateway.ops;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpsServiceHealthService {

    private static final Logger log = LoggerFactory.getLogger(OpsServiceHealthService.class);
    private static final String SOURCE = "prometheus";
    private static final List<ServiceTarget> SERVICE_TARGETS = List.of(
            new ServiceTarget("gateway-portal", "Gateway Portal"),
            new ServiceTarget("blog-service", "Blog Service")
    );

    private final PrometheusClient prometheusClient;

    public OpsServiceHealthService(PrometheusClient prometheusClient) {
        this.prometheusClient = Objects.requireNonNull(prometheusClient, "prometheusClient must not be null");
    }

    public List<ServiceHealth> listServiceHealth() {
        Instant checkedAt = Instant.now();
        return SERVICE_TARGETS.stream()
                .map(target -> loadServiceHealth(target, checkedAt))
                .toList();
    }

    private ServiceHealth loadServiceHealth(ServiceTarget target, Instant checkedAt) {
        String query = "up{namespace=\"cloud-ops\", service=\"" + target.name() + "\"}";
        try {
            double up = prometheusClient.queryInstant(query);
            boolean available = up >= 1.0;
            return new ServiceHealth(
                    target.name(),
                    target.displayName(),
                    available ? "UP" : "DOWN",
                    Math.round(up) + "/1",
                    SOURCE,
                    checkedAt,
                    available ? "Prometheus target is up" : "Prometheus target is down"
            );
        } catch (Exception ex) {
            log.warn("Failed to load service health metric: service={}, query={}", target.name(), query, ex);
            return new ServiceHealth(
                    target.name(),
                    target.displayName(),
                    "UNKNOWN",
                    "N/A",
                    SOURCE,
                    checkedAt,
                    "Prometheus query unavailable"
            );
        }
    }

    private record ServiceTarget(String name, String displayName) {
    }
}
