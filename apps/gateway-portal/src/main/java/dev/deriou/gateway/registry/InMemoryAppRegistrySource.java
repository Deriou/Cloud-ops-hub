package dev.deriou.gateway.registry;

import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InMemoryAppRegistrySource implements AppRegistrySource {

    @Override
    public List<RegisteredApp> listApps() {
        return List.of(
                new RegisteredApp(
                        "gateway-portal",
                        "Gateway Portal",
                        "/",
                        "UP",
                        "统一公网入口",
                        1,
                        URI.create("http://localhost:8080/actuator/health")
                )
        );
    }
}
