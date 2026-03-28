package dev.deriou.gateway.auth;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class UuidTokenGenerator implements TokenGenerator {

    @Override
    public String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
