package dev.deriou.common.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops.auth")
public class AuthProperties {

    private static final long DEFAULT_GUEST_TOKEN_TTL_SECONDS = 300L;

    private String masterKey;

    private long guestTokenTtlSeconds = DEFAULT_GUEST_TOKEN_TTL_SECONDS;

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public long getGuestTokenTtlSeconds() {
        return guestTokenTtlSeconds;
    }

    public void setGuestTokenTtlSeconds(long guestTokenTtlSeconds) {
        this.guestTokenTtlSeconds = guestTokenTtlSeconds;
    }

    public Duration getGuestTokenTtl() {
        long safeSeconds = guestTokenTtlSeconds > 0 ? guestTokenTtlSeconds : DEFAULT_GUEST_TOKEN_TTL_SECONDS;
        return Duration.ofSeconds(safeSeconds);
    }
}
