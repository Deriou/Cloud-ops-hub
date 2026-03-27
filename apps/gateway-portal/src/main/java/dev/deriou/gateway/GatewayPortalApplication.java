package dev.deriou.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"dev.deriou.gateway", "dev.deriou.common"})
public class GatewayPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayPortalApplication.class, args);
    }
}
