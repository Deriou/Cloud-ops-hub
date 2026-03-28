package dev.deriou.gateway.health;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.deriou.common.api.ResultCode;
import dev.deriou.common.auth.AuthInterceptor;
import dev.deriou.common.auth.GuestTokenStore;
import dev.deriou.common.config.AuthConfig;
import dev.deriou.common.config.WebMvcConfig;
import dev.deriou.common.exception.BizException;
import dev.deriou.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import({AuthConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "ops.auth.master-key=test-master-key",
        "ops.auth.guest-token-ttl-seconds=300",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class HealthControllerTest {

    private static final String HEADER_NAME = AuthInterceptor.HEADER_NAME;
    private static final String GUEST_TOKEN_ID = "health-guest-token";
    private static final String GUEST_TOKEN_VALUE = "health-guest-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GuestTokenStore guestTokenStore;

    @MockBean
    private HealthAggregationService healthAggregationService;

    @BeforeEach
    void setUp() {
        guestTokenStore.put(GUEST_TOKEN_ID, GUEST_TOKEN_VALUE);
    }

    @Test
    void aggregate_endpoint_should_return_partial_status_not_500() throws Exception {
        given(healthAggregationService.listAppHealths()).willReturn(List.of(
                new AppHealth("blog-service", "UP", "UP", "service is healthy", Instant.parse("2026-03-28T10:15:30Z")),
                new AppHealth("ops-core", "DEGRADED", null, "health probe failed: timeout", Instant.parse("2026-03-28T10:15:30Z"))
        ));

        mockMvc.perform(get("/api/v1/gateway/apps/health").header(HEADER_NAME, GUEST_TOKEN_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].appKey").value("blog-service"))
                .andExpect(jsonPath("$.data[0].status").value("UP"))
                .andExpect(jsonPath("$.data[1].appKey").value("ops-core"))
                .andExpect(jsonPath("$.data[1].status").value("DEGRADED"));
    }

    @Test
    void missing_app_should_return_biz_error() throws Exception {
        given(healthAggregationService.getAppHealth("missing"))
                .willThrow(new BizException(ResultCode.BIZ_ERROR, "app not found: missing"));

        mockMvc.perform(get("/api/v1/gateway/apps/missing/health").header(HEADER_NAME, GUEST_TOKEN_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ_ERROR"))
                .andExpect(jsonPath("$.message").value("app not found: missing"));
    }
}
