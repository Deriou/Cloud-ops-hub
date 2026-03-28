package dev.deriou.gateway.health;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops.gateway.health")
public class HealthProperties {

    private Duration timeout = Duration.ofMillis(1500);
    private Duration cacheTtl = Duration.ofSeconds(10);

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        if (timeout != null) {
            this.timeout = timeout;
        }
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        if (cacheTtl != null) {
            this.cacheTtl = cacheTtl;
        }
    }
}
