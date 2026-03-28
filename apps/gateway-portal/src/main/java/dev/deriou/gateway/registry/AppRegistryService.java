package dev.deriou.gateway.registry;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AppRegistryService {

    private static final Comparator<RegisteredApp> APP_ORDER = Comparator
            .comparing(RegisteredApp::sortOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(RegisteredApp::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(RegisteredApp::appKey, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final AppRegistrySource appRegistrySource;

    public AppRegistryService(AppRegistrySource appRegistrySource) {
        this.appRegistrySource = appRegistrySource;
    }

    public List<AppMeta> listApps() {
        return listRegisteredApps()
                .stream()
                .map(RegisteredApp::toAppMeta)
                .toList();
    }

    public List<RegisteredApp> listRegisteredApps() {
        return appRegistrySource.listApps()
                .stream()
                .sorted(APP_ORDER)
                .toList();
    }

    public Optional<RegisteredApp> findApp(String appKey) {
        if (appKey == null || appKey.isBlank()) {
            return Optional.empty();
        }

        return listRegisteredApps()
                .stream()
                .filter(app -> app.appKey().equalsIgnoreCase(appKey))
                .findFirst();
    }
}
