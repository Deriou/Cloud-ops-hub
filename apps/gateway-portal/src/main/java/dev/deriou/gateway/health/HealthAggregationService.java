package dev.deriou.gateway.health;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.deriou.common.api.ResultCode;
import dev.deriou.common.exception.BizException;
import dev.deriou.gateway.registry.AppRegistryService;
import dev.deriou.gateway.registry.RegisteredApp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HealthAggregationService {

    private final AppRegistryService appRegistryService;
    private final HealthClient healthClient;
    private final Cache<String, AppHealth> healthCache;

    @Autowired
    public HealthAggregationService(
            AppRegistryService appRegistryService,
            HealthClient healthClient,
            HealthProperties healthProperties
    ) {
        this(
                appRegistryService,
                healthClient,
                Caffeine.newBuilder()
                        .expireAfterWrite(healthProperties.getCacheTtl())
                        .build()
        );
    }

    HealthAggregationService(
            AppRegistryService appRegistryService,
            HealthClient healthClient,
            Cache<String, AppHealth> healthCache
    ) {
        this.appRegistryService = Objects.requireNonNull(appRegistryService, "appRegistryService must not be null");
        this.healthClient = Objects.requireNonNull(healthClient, "healthClient must not be null");
        this.healthCache = Objects.requireNonNull(healthCache, "healthCache must not be null");
    }

    public AppHealth getAppHealth(String appKey) {
        RegisteredApp app = appRegistryService.findApp(appKey)
                .orElseThrow(() -> new BizException(ResultCode.BIZ_ERROR, "app not found: " + appKey));
        return getOrLoadHealth(app);
    }

    public List<AppHealth> listAppHealths() {
        return appRegistryService.listRegisteredApps()
                .stream()
                .map(this::getOrLoadHealth)
                .toList();
    }

    private AppHealth getOrLoadHealth(RegisteredApp app) {
        return healthCache.get(app.appKey(), key -> fetchHealth(app));
    }

    private AppHealth fetchHealth(RegisteredApp app) {
        try {
            HealthClient.ProbeResult probeResult = healthClient.fetchHealth(app.healthEndpoint());
            String rawStatus = normalizeStatus(probeResult.status());
            AppHealthStatus mappedStatus = AppHealthStatus.fromActuatorStatus(rawStatus);
            return new AppHealth(
                    app.appKey(),
                    mappedStatus.name(),
                    rawStatus,
                    resolveMessage(mappedStatus, rawStatus),
                    Instant.now()
            );
        } catch (Exception ex) {
            return new AppHealth(
                    app.appKey(),
                    AppHealthStatus.DEGRADED.name(),
                    null,
                    resolveErrorMessage(ex),
                    Instant.now()
            );
        }
    }

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        return rawStatus.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveMessage(AppHealthStatus status, String rawStatus) {
        return switch (status) {
            case UP -> "service is healthy";
            case DOWN -> "service is unhealthy";
            case DEGRADED -> "service is degraded";
            case UNKNOWN -> "unknown actuator status: " + rawStatus;
        };
    }

    private String resolveErrorMessage(Exception ex) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "health probe failed";
        }
        return "health probe failed: " + ex.getMessage();
    }
}
