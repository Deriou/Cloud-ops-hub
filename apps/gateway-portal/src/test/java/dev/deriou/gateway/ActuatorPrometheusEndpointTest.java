package dev.deriou.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.deriou.common.observability.TraceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorPrometheusEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prometheus_endpoint_should_expose_base_metrics() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists(TraceContext.TRACE_ID_HEADER))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceContext.TRACE_ID_HEADER))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        assertThat(responseBody)
                .contains("# HELP")
                .contains("application=\"gateway-portal\"")
                .containsAnyOf("jvm_memory", "process_cpu", "system_cpu");

        assertThat(responseBody)
                .contains("http_server_requests");
    }
}
