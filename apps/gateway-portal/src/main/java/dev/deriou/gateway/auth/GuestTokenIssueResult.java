package dev.deriou.gateway.auth;

import java.time.Instant;

public record GuestTokenIssueResult(String tokenId, String token, Instant expireAt) {
}
