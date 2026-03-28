package dev.deriou.gateway.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.deriou.gateway.registry.AppRegistryService;
import dev.deriou.gateway.registry.AppRegistrySource;
import dev.deriou.gateway.registry.RegisteredApp;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class HealthAggregationServiceTest {

    @Mock
    private AppRegistrySource appRegistrySource;

    @Mock
    private HealthClient healthClient;

    private HealthAggregationService healthAggregationService;

    @BeforeEach
    void setUp() {
        HealthProperties healthProperties = new HealthProperties();
        healthProperties.setTimeout(Duration.ofMillis(1500));
        healthProperties.setCacheTtl(Duration.ofSeconds(10));

        AppRegistryService appRegistryService = new AppRegistryService(appRegistrySource);
        healthAggregationService = new HealthAggregationService(appRegistryService, healthClient, healthProperties);
    }

    @Test
    void single_service_up_should_map_to_healthy() {
        RegisteredApp blogService = registeredApp("blog-service", "http://blog-service/actuator/health");
        given(appRegistrySource.listApps()).willReturn(List.of(blogService));
        given(healthClient.fetchHealth(blogService.healthEndpoint())).willReturn(new HealthClient.ProbeResult("UP"));

        AppHealth health = healthAggregationService.getAppHealth("blog-service");

        assertThat(health.appKey()).isEqualTo("blog-service");
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.rawStatus()).isEqualTo("UP");
        assertThat(health.message()).isEqualTo("service is healthy");
        assertThat(health.checkedAt()).isNotNull();
    }

    @Test
    void timeout_should_map_to_degraded() {
        RegisteredApp opsCore = registeredApp("ops-core", "http://ops-core/actuator/health");
        given(appRegistrySource.listApps()).willReturn(List.of(opsCore));
        given(healthClient.fetchHealth(opsCore.healthEndpoint()))
                .willThrow(new ResourceAccessException("Read timed out"));

        AppHealth health = healthAggregationService.getAppHealth("ops-core");

        assertThat(health.status()).isEqualTo("DEGRADED");
        assertThat(health.rawStatus()).isNull();
        assertThat(health.message()).contains("Read timed out");
    }

    @Test
    void cache_hit_should_skip_remote_call() {
        RegisteredApp blogService = registeredApp("blog-service", "http://blog-service/actuator/health");
        given(appRegistrySource.listApps()).willReturn(List.of(blogService));
        given(healthClient.fetchHealth(blogService.healthEndpoint())).willReturn(new HealthClient.ProbeResult("UP"));

        AppHealth first = healthAggregationService.getAppHealth("blog-service");
        AppHealth second = healthAggregationService.getAppHealth("blog-service");

        assertThat(second).isEqualTo(first);
        verify(healthClient).fetchHealth(blogService.healthEndpoint());
        verifyNoMoreInteractions(healthClient);
    }

    @Test
    void partial_failure_should_return_partial_status_not_500() {
        RegisteredApp blogService = registeredApp("blog-service", "http://blog-service/actuator/health");
        RegisteredApp opsCore = registeredApp("ops-core", "http://ops-core/actuator/health");
        given(appRegistrySource.listApps()).willReturn(List.of(blogService, opsCore));
        given(healthClient.fetchHealth(blogService.healthEndpoint())).willReturn(new HealthClient.ProbeResult("UP"));
        given(healthClient.fetchHealth(opsCore.healthEndpoint()))
                .willThrow(new ResourceAccessException("Connection refused"));

        List<AppHealth> appHealths = healthAggregationService.listAppHealths();

        assertThat(appHealths).hasSize(2);
        Map<String, String> healthByAppKey = appHealths.stream()
                .collect(java.util.stream.Collectors.toMap(AppHealth::appKey, AppHealth::status));
        assertThat(healthByAppKey).containsEntry("blog-service", "UP");
        assertThat(healthByAppKey).containsEntry("ops-core", "DEGRADED");
    }

    private RegisteredApp registeredApp(String appKey, String healthEndpoint) {
        return new RegisteredApp(
                appKey,
                appKey,
                "/" + appKey,
                "UP",
                "test app",
                10,
                URI.create(healthEndpoint)
        );
    }
}
