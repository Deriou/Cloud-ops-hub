package dev.deriou.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deriou.common.auth.AuthInterceptor;
import dev.deriou.common.auth.AuthProperties;
import dev.deriou.common.auth.GuestTokenStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

    @Bean
    GuestTokenStore guestTokenStore(AuthProperties authProperties) {
        return new GuestTokenStore(authProperties);
    }

    @Bean
    AuthInterceptor authInterceptor(
            AuthProperties authProperties,
            GuestTokenStore guestTokenStore,
            ObjectMapper objectMapper
    ) {
        return new AuthInterceptor(authProperties, guestTokenStore, objectMapper);
    }
}
