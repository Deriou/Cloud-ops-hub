package dev.deriou.gateway.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.deriou.common.auth.AuthInterceptor;
import dev.deriou.common.auth.GuestTokenStore;
import dev.deriou.common.config.AuthConfig;
import dev.deriou.common.config.WebMvcConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccessModeController.class)
@Import({AuthConfig.class, WebMvcConfig.class})
@TestPropertySource(properties = {
        "ops.auth.master-key=test-master-key",
        "ops.auth.guest-token-ttl-seconds=300",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class AccessModeControllerTest {

    private static final String HEADER_NAME = AuthInterceptor.HEADER_NAME;
    private static final String MASTER_KEY = "test-master-key";
    private static final String GUEST_TOKEN_ID = "guest-mode-token";
    private static final String GUEST_TOKEN_VALUE = "guest-mode-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GuestTokenStore guestTokenStore;

    @BeforeEach
    void setUp() {
        guestTokenStore.put(GUEST_TOKEN_ID, GUEST_TOKEN_VALUE);
    }

    @Test
    void guest_key_should_return_guest_access_mode() throws Exception {
        mockMvc.perform(get("/api/v1/gateway/access-mode").header(HEADER_NAME, GUEST_TOKEN_VALUE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.message").value("success"))
            .andExpect(jsonPath("$.data.mode").value("guest"))
            .andExpect(jsonPath("$.traceId").isString())
            .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void master_key_should_return_admin_access_mode() throws Exception {
        mockMvc.perform(get("/api/v1/gateway/access-mode").header(HEADER_NAME, MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.mode").value("admin"));
    }
}
