package dev.deriou.gateway.auth;

import dev.deriou.common.auth.GuestTokenStore;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GuestTokenService {

    private static final Logger log = LoggerFactory.getLogger(GuestTokenService.class);

    private final GuestTokenStore guestTokenStore;
    private final TokenGenerator tokenGenerator;

    public GuestTokenService(GuestTokenStore guestTokenStore, TokenGenerator tokenGenerator) {
        this.guestTokenStore = Objects.requireNonNull(guestTokenStore, "guestTokenStore must not be null");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null");
    }

    public GuestTokenIssueResult createGuestToken() {
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String tokenValue = tokenGenerator.generateToken();
        GuestTokenStore.GuestTokenRecord record = guestTokenStore.put(tokenId, tokenValue);
        log.info("Guest token created: tokenId={}, expireAt={}", record.tokenId(), record.expireAt());
        return new GuestTokenIssueResult(record.tokenId(), record.tokenValue(), record.expireAt());
    }

    public void revokeGuestToken(String tokenId) {
        guestTokenStore.revoke(tokenId);
        log.info("Guest token revoked: tokenId={}", tokenId);
    }
}
