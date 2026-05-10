package dev.deriou.gateway.ops;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.deriou.common.config.AuthConfig;
import dev.deriou.common.config.WebMvcConfig;
import dev.deriou.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OpsServiceHealthController.class)
@Import({AuthConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "ops.auth.master-key=test-master-key",
        "ops.auth.guest-token-ttl-seconds=300",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class OpsServiceHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpsServiceHealthService opsServiceHealthService;

    @Test
    void service_health_endpoint_should_allow_public_get_without_ops_key() throws Exception {
        given(opsServiceHealthService.listServiceHealth()).willReturn(List.of(
                new ServiceHealth(
                        "gateway-portal",
                        "Gateway Portal",
                        "UP",
                        "1/1",
                        "prometheus",
                        Instant.parse("2026-05-10T08:00:00Z"),
                        "Prometheus target is up"
                ),
                new ServiceHealth(
                        "blog-service",
                        "Blog Service",
                        "UNKNOWN",
                        "N/A",
                        "prometheus",
                        Instant.parse("2026-05-10T08:00:00Z"),
                        "Prometheus query unavailable"
                )
        ));

        mockMvc.perform(get("/api/v1/ops/services/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].name").value("gateway-portal"))
                .andExpect(jsonPath("$.data[0].displayName").value("Gateway Portal"))
                .andExpect(jsonPath("$.data[0].status").value("UP"))
                .andExpect(jsonPath("$.data[0].value").value("1/1"))
                .andExpect(jsonPath("$.data[0].source").value("prometheus"))
                .andExpect(jsonPath("$.data[1].name").value("blog-service"))
                .andExpect(jsonPath("$.data[1].status").value("UNKNOWN"));
    }
}
