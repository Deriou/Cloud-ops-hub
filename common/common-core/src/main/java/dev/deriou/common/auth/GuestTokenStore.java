package dev.deriou.common.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuestTokenStore {

    private static final Logger log = LoggerFactory.getLogger(GuestTokenStore.class);

    private final Cache<String, GuestTokenRecord> tokensByValue;
    private final Cache<String, String> tokenValuesById;
    private final Clock clock;
    private final long ttlSeconds;

    public GuestTokenStore(AuthProperties authProperties) {
        this(authProperties, Clock.systemUTC());
    }

    GuestTokenStore(AuthProperties authProperties, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttlSeconds = Objects.requireNonNull(authProperties, "authProperties must not be null")
                .getGuestTokenTtl().toSeconds();
        this.tokensByValue = Caffeine.newBuilder()
                .expireAfterWrite(authProperties.getGuestTokenTtl())
                .removalListener(this::onGuestTokenRemoved)
                .build();
        this.tokenValuesById = Caffeine.newBuilder()
                .expireAfterWrite(authProperties.getGuestTokenTtl())
                .build();
    }

    public GuestTokenRecord put(String tokenId, String tokenValue) {
        Instant expireAt = clock.instant().plusSeconds(ttlSeconds);
        GuestTokenRecord record = new GuestTokenRecord(tokenId, tokenValue, expireAt);
        tokenValuesById.put(tokenId, tokenValue);
        tokensByValue.put(tokenValue, record);
        return record;
    }

    public boolean contains(String tokenValue) {
        return get(tokenValue) != null;
    }

    public GuestTokenRecord get(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }
        return tokensByValue.getIfPresent(tokenValue);
    }

    public void revoke(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        String tokenValue = tokenValuesById.getIfPresent(tokenId);
        tokenValuesById.invalidate(tokenId);
        if (tokenValue != null) {
            tokensByValue.invalidate(tokenValue);
        }
    }

    private void onGuestTokenRemoved(String tokenValue, GuestTokenRecord record, RemovalCause cause) {
        if (record != null && cause == RemovalCause.EXPIRED) {
            log.info("Guest token expired: tokenId={}, expireAt={}", record.tokenId(), record.expireAt());
        }
    }

    public record GuestTokenRecord(String tokenId, String tokenValue, Instant expireAt) {
    }
}
