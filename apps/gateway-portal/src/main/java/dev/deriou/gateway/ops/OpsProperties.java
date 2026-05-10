package dev.deriou.gateway.ops;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops")
public class OpsProperties {

    private final Prometheus prometheus = new Prometheus();
    private final Cluster cluster = new Cluster();

    public Prometheus getPrometheus() {
        return prometheus;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public static class Prometheus {
        private URI baseUrl = URI.create("http://prometheus-server.monitoring.svc.cluster.local");
        private Duration timeout = Duration.ofMillis(1500);

        public URI getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(URI baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class Cluster {
        private String name = "k3s-single-node";
        private String region = "cn-wulanchabu";
        private int expectedServiceCount = 2;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public int getExpectedServiceCount() {
            return expectedServiceCount;
        }

        public void setExpectedServiceCount(int expectedServiceCount) {
            this.expectedServiceCount = expectedServiceCount;
        }
    }
}
