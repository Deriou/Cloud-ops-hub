package dev.deriou.gateway.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpsServiceHealthServiceTest {

    @Mock
    private PrometheusClient prometheusClient;

    private OpsServiceHealthService opsServiceHealthService;

    @BeforeEach
    void setUp() {
        opsServiceHealthService = new OpsServiceHealthService(prometheusClient);
    }

    @Test
    void service_up_metric_should_map_to_up() {
        given(prometheusClient.queryInstant("up{namespace=\"cloud-ops\", service=\"gateway-portal\"}"))
                .willReturn(1.0);
        given(prometheusClient.queryInstant("up{namespace=\"cloud-ops\", service=\"blog-service\"}"))
                .willReturn(1.0);

        List<ServiceHealth> services = opsServiceHealthService.listServiceHealth();

        assertThat(services).hasSize(2);
        assertThat(services.get(0).name()).isEqualTo("gateway-portal");
        assertThat(services.get(0).displayName()).isEqualTo("Gateway Portal");
        assertThat(services.get(0).status()).isEqualTo("UP");
        assertThat(services.get(0).value()).isEqualTo("1/1");
        assertThat(services.get(0).source()).isEqualTo("prometheus");
        assertThat(services.get(0).checkedAt()).isNotNull();
        assertThat(services.get(0).detail()).isEqualTo("Prometheus target is up");
    }

    @Test
    void service_zero_metric_should_map_to_down() {
        given(prometheusClient.queryInstant("up{namespace=\"cloud-ops\", service=\"gateway-portal\"}"))
                .willReturn(0.0);
        given(prometheusClient.queryInstant("up{namespace=\"cloud-ops\", service=\"blog-service\"}"))
                .willReturn(1.0);

        List<ServiceHealth> services = opsServiceHealthService.listServiceHealth();

        assertThat(services.get(0).status()).isEqualTo("DOWN");
        assertThat(services.get(0).value()).isEqualTo("0/1");
        assertThat(services.get(0).detail()).isEqualTo("Prometheus target is down");
        assertThat(services.get(1).status()).isEqualTo("UP");
    }

    @Test
    void prometheus_failure_should_degrade_single_service_to_unknown() {
        given(prometheusClient.queryInstant("up{namespace=\"cloud-ops\", service=\"gateway-portal\"}"))
                .willThrow(new IllegalStateException("prometheus unavailable"));
        given(prometheusClient.queryInstant("up{namespace=\"cloud-ops\", service=\"blog-service\"}"))
                .willReturn(1.0);

        List<ServiceHealth> services = opsServiceHealthService.listServiceHealth();

        assertThat(services.get(0).status()).isEqualTo("UNKNOWN");
        assertThat(services.get(0).value()).isEqualTo("N/A");
        assertThat(services.get(0).detail()).isEqualTo("Prometheus query unavailable");
        assertThat(services.get(1).status()).isEqualTo("UP");
    }
}
