package dev.deriou.gateway.registry;

import java.net.URI;
import java.util.Objects;

public record RegisteredApp(
        String appKey,
        String title,
        String route,
        String status,
        String description,
        Integer sortOrder,
        URI healthEndpoint
) {

    public RegisteredApp {
        Objects.requireNonNull(appKey, "appKey must not be null");
        Objects.requireNonNull(healthEndpoint, "healthEndpoint must not be null");
    }

    public AppMeta toAppMeta() {
        return new AppMeta(appKey, title, route, status, description, sortOrder);
    }
}
