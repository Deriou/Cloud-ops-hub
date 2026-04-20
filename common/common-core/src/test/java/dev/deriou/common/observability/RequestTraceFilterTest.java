package dev.deriou.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deriou.common.api.ApiResponse;
import dev.deriou.common.api.ResultCode;
import dev.deriou.common.config.AuthConfig;
import dev.deriou.common.config.WebMvcConfig;
import dev.deriou.common.exception.BizException;
import dev.deriou.common.exception.GlobalExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = RequestTraceFilterTest.TestApplication.class)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "spring.application.name=common-trace-test",
        "ops.auth.master-key=test-master-key",
        "ops.auth.guest-token-ttl-seconds=300"
})
class RequestTraceFilterTest {

    private static final String OPS_KEY_HEADER = "X-Ops-Key";
    private static final String MASTER_KEY = "test-master-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generated_trace_id_should_be_returned_in_header_and_body() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test/trace").header(OPS_KEY_HEADER, MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceContext.TRACE_ID_HEADER))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        String headerTraceId = result.getResponse().getHeader(TraceContext.TRACE_ID_HEADER);
        String bodyTraceId = responseTraceId(result);

        assertThat(headerTraceId).matches("[0-9a-f]{32}");
        assertThat(bodyTraceId).isEqualTo(headerTraceId);
    }

    @Test
    void incoming_trace_id_should_be_reused_in_response_and_access_log(CapturedOutput output) throws Exception {
        String traceId = "client-trace-001";

        MvcResult result = mockMvc.perform(get("/api/test/trace")
                        .header(OPS_KEY_HEADER, MASTER_KEY)
                        .header(TraceContext.TRACE_ID_HEADER, traceId))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceContext.TRACE_ID_HEADER, traceId))
                .andReturn();

        assertThat(responseTraceId(result)).isEqualTo(traceId);
        assertThat(output).contains("event=http_request");
        assertThat(output).contains("service=common-trace-test");
        assertThat(output).contains("traceId=client-trace-001");
        assertThat(output).contains("method=GET");
        assertThat(output).contains("path=/api/test/trace");
        assertThat(output).contains("resultCode=OK");
    }

    @Test
    void auth_failure_should_share_trace_id_between_header_and_body() throws Exception {
        String traceId = "auth-denied-trace";

        MvcResult result = mockMvc.perform(get("/api/test/trace").header(TraceContext.TRACE_ID_HEADER, traceId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceContext.TRACE_ID_HEADER, traceId))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andReturn();

        assertThat(responseTraceId(result)).isEqualTo(traceId);
    }

    @Test
    void business_exception_should_share_trace_id_between_header_and_body() throws Exception {
        String traceId = "biz-error-trace";

        MvcResult result = mockMvc.perform(get("/api/test/biz-error")
                        .header(OPS_KEY_HEADER, MASTER_KEY)
                        .header(TraceContext.TRACE_ID_HEADER, traceId))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceContext.TRACE_ID_HEADER, traceId))
                .andExpect(jsonPath("$.code").value("BIZ_ERROR"))
                .andReturn();

        assertThat(responseTraceId(result)).isEqualTo(traceId);
    }

    @Test
    void non_api_response_should_fallback_to_http_status_in_access_log(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/test/plain-missing").header(OPS_KEY_HEADER, MASTER_KEY))
                .andExpect(status().isNotFound());

        assertThat(output).contains("event=http_request");
        assertThat(output).contains("path=/api/test/plain-missing");
        assertThat(output).contains("status=404");
        assertThat(output).contains("resultCode=HTTP_404");
    }

    @Test
    void actuator_request_should_return_trace_id_without_access_log(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceContext.TRACE_ID_HEADER));

        assertThat(output).doesNotContain("event=http_request");
        assertThat(output).doesNotContain("path=/actuator/health");
    }

    private String responseTraceId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("traceId").asText();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            AuthConfig.class,
            WebMvcConfig.class,
            GlobalExceptionHandler.class,
            RequestTraceFilter.class,
            TestController.class,
            TestActuatorController.class
    })
    static class TestApplication {
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test/trace")
        ApiResponse<Map<String, String>> trace() {
            return ApiResponse.success(Map.of("status", "ok"));
        }

        @GetMapping("/api/test/biz-error")
        ApiResponse<Void> bizError() {
            throw new BizException(ResultCode.BIZ_ERROR, "bad request");
        }

        @GetMapping("/api/test/plain-missing")
        ResponseEntity<String> plainMissing() {
            return ResponseEntity.status(404).body("missing");
        }
    }

    @RestController
    static class TestActuatorController {

        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }
    }
}
