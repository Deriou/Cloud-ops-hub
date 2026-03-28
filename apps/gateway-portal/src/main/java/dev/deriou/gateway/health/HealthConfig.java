package dev.deriou.gateway.health;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HealthProperties.class)
public class HealthConfig {
}
