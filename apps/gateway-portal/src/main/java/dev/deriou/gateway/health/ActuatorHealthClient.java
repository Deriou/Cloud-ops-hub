package dev.deriou.gateway.health;

import java.net.URI;
import java.util.Objects;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ActuatorHealthClient implements HealthClient {

    private final RestClient restClient;

    public ActuatorHealthClient(RestClient.Builder restClientBuilder, HealthProperties healthProperties) {
        Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null");
        Objects.requireNonNull(healthProperties, "healthProperties must not be null");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) healthProperties.getTimeout().toMillis());
        requestFactory.setReadTimeout((int) healthProperties.getTimeout().toMillis());
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    @Override
    public ProbeResult fetchHealth(URI healthEndpoint) {
        HealthPayload payload = restClient.get()
                .uri(healthEndpoint)
                .retrieve()
                .body(HealthPayload.class);
        return new ProbeResult(payload != null ? payload.status() : null);
    }

    private record HealthPayload(String status) {
    }
}
