package dev.deriou.gateway.registry;

import java.util.List;

public interface AppRegistrySource {

    List<RegisteredApp> listApps();
}
