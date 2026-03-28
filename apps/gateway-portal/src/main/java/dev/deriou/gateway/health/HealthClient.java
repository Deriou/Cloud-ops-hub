package dev.deriou.gateway.health;

import java.net.URI;

public interface HealthClient {

    ProbeResult fetchHealth(URI healthEndpoint);

    record ProbeResult(String status) {
    }
}
