package dev.deriou.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deriou.common.auth.AuthInterceptor;
import dev.deriou.common.auth.GuestTokenStore;
import dev.deriou.common.config.AuthConfig;
import dev.deriou.common.config.WebMvcConfig;
import dev.deriou.gateway.controller.AccessModeController;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest({GuestTokenController.class, AccessModeController.class})
@Import({AuthConfig.class, WebMvcConfig.class, GuestTokenService.class, UuidTokenGenerator.class})
@TestPropertySource(properties = {
        "ops.auth.master-key=test-master-key",
        "ops.auth.guest-token-ttl-seconds=1",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class GuestTokenControllerTest {

    private static final String HEADER_NAME = AuthInterceptor.HEADER_NAME;
    private static final String MASTER_KEY = "test-master-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GuestTokenStore guestTokenStore;

    @Test
    void create_guest_token_should_return_token_and_expire_time() throws Exception {
        IssuedGuestToken issuedGuestToken = createGuestToken();

        assertThat(issuedGuestToken.tokenId()).isNotBlank();
        assertThat(issuedGuestToken.token()).isNotBlank();
        assertThat(issuedGuestToken.expireAt()).isAfter(Instant.now());
        assertThat(guestTokenStore.contains(issuedGuestToken.token())).isTrue();
    }

    @Test
    void revoked_token_should_be_invalid_immediately() throws Exception {
        IssuedGuestToken issuedGuestToken = createGuestToken();

        mockMvc.perform(get("/api/v1/gateway/access-mode").header(HEADER_NAME, issuedGuestToken.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("guest"));

        mockMvc.perform(delete("/api/v1/gateway/guest-tokens/{tokenId}", issuedGuestToken.tokenId())
                        .header(HEADER_NAME, MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.tokenId").value(issuedGuestToken.tokenId()))
                .andExpect(jsonPath("$.data.revoked").value(true));

        assertThat(guestTokenStore.contains(issuedGuestToken.token())).isFalse();

        mockMvc.perform(get("/api/v1/gateway/access-mode").header(HEADER_NAME, issuedGuestToken.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void token_should_expire_after_ttl() throws Exception {
        IssuedGuestToken issuedGuestToken = createGuestToken();

        Thread.sleep(1200L);

        mockMvc.perform(get("/api/v1/gateway/access-mode").header(HEADER_NAME, issuedGuestToken.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void guest_mode_write_api_should_be_rejected() throws Exception {
        IssuedGuestToken issuedGuestToken = createGuestToken();

        mockMvc.perform(post("/api/v1/gateway/guest-tokens").header(HEADER_NAME, issuedGuestToken.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("forbidden"));
    }

    @Test
    void revoke_missing_token_should_be_idempotent() throws Exception {
        mockMvc.perform(delete("/api/v1/gateway/guest-tokens/{tokenId}", "missing-token")
                        .header(HEADER_NAME, MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.tokenId").value("missing-token"))
                .andExpect(jsonPath("$.data.revoked").value(true));
    }

    private IssuedGuestToken createGuestToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/gateway/guest-tokens").header(HEADER_NAME, MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenId").isString())
                .andExpect(jsonPath("$.data.tokenId").isNotEmpty())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.expireAt").isString())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
        return new IssuedGuestToken(
                data.path("tokenId").asText(),
                data.path("token").asText(),
                Instant.parse(data.path("expireAt").asText())
        );
    }

    private record IssuedGuestToken(String tokenId, String token, Instant expireAt) {
    }
}
