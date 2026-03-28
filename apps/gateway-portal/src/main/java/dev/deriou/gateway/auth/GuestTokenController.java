package dev.deriou.gateway.auth;

import dev.deriou.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "访客令牌", description = "Gateway Guest Token 的签发与吊销接口")
@RestController
@RequestMapping("/api/v1/gateway/guest-tokens")
public class GuestTokenController {

    private final GuestTokenService guestTokenService;

    public GuestTokenController(GuestTokenService guestTokenService) {
        this.guestTokenService = guestTokenService;
    }

    @Operation(summary = "签发访客令牌", description = "生成只读 Guest Token，并返回 tokenId、token 与过期时间")
    @PostMapping
    public ApiResponse<GuestTokenIssueResult> createGuestToken() {
        return ApiResponse.success(guestTokenService.createGuestToken());
    }

    @Operation(summary = "吊销访客令牌", description = "按 tokenId 吊销 Guest Token，重复吊销按成功处理")
    @DeleteMapping("/{tokenId}")
    public ApiResponse<Map<String, Object>> revokeGuestToken(@PathVariable String tokenId) {
        guestTokenService.revokeGuestToken(tokenId);
        return ApiResponse.success(Map.of("tokenId", tokenId, "revoked", true));
    }
}
