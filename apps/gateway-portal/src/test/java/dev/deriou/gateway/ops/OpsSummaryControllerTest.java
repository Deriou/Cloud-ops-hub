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

@WebMvcTest(OpsSummaryController.class)
@Import({AuthConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "ops.auth.master-key=test-master-key",
        "ops.auth.guest-token-ttl-seconds=300",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class OpsSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpsSummaryService opsSummaryService;

    @Test
    void summary_endpoint_should_allow_public_get_without_ops_key() throws Exception {
        given(opsSummaryService.getClusterSummary()).willReturn(new ClusterSummary(
                "k3s-single-node",
                "cn-wulanchabu",
                Instant.parse("2026-05-10T08:00:00Z"),
                List.of(
                        new OpsStat("CPU 利用率", "12%", List.of(10.0, 12.0), "normal"),
                        new OpsStat("内存使用率", "72%", List.of(70.0, 72.0), "warning"),
                        new OpsStat("服务可用", "2/2", List.of(100.0, 100.0), "normal")
                )
        ));

        mockMvc.perform(get("/api/v1/ops/clusters/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.clusterName").value("k3s-single-node"))
                .andExpect(jsonPath("$.data.region").value("cn-wulanchabu"))
                .andExpect(jsonPath("$.data.stats[0].label").value("CPU 利用率"))
                .andExpect(jsonPath("$.data.stats[0].value").value("12%"))
                .andExpect(jsonPath("$.data.stats[1].tone").value("warning"))
                .andExpect(jsonPath("$.data.stats[2].value").value("2/2"));
    }
}
