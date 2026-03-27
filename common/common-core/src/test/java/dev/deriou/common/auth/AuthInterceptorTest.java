package dev.deriou.common.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.deriou.common.api.ApiResponse;
import dev.deriou.common.config.AuthConfig;
import dev.deriou.common.config.WebMvcConfig;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = AuthInterceptorTest.TestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ops.auth.master-key=test-master-key",
        "ops.auth.guest-token-ttl-seconds=1"
})
class AuthInterceptorTest {

    private static final String HEADER_NAME = "X-Ops-Key";
    private static final String MASTER_KEY = "test-master-key";
    private static final String GUEST_TOKEN_ID = "guest-token-1";
    private static final String GUEST_TOKEN_VALUE = "guest-token-value";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GuestTokenStore guestTokenStore;

    @BeforeEach
    void setUp() {
        guestTokenStore.put(GUEST_TOKEN_ID, GUEST_TOKEN_VALUE);
    }

    @Test
    void no_key_should_return_401() throws Exception {
        mockMvc.perform(get("/api/test/auth/resource"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("unauthorized"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void master_key_should_allow_post_put_delete() throws Exception {
        mockMvc.perform(post("/api/test/auth/resource").header(HEADER_NAME, MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("POST"));

        mockMvc.perform(put("/api/test/auth/resource").header(HEADER_NAME, MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("PUT"));

        mockMvc.perform(delete("/api/test/auth/resource").header(HEADER_NAME, MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("DELETE"));
    }

    @Test
    void guest_key_should_allow_get_only() throws Exception {
        mockMvc.perform(get("/api/test/auth/resource").header(HEADER_NAME, GUEST_TOKEN_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.method").value("GET"));
    }

    @Test
    void guest_key_write_request_should_return_403() throws Exception {
        mockMvc.perform(post("/api/test/auth/resource").header(HEADER_NAME, GUEST_TOKEN_VALUE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("forbidden"));
    }

    @Test
    void expired_guest_key_should_return_401() throws Exception {
        Thread.sleep(1200L);

        mockMvc.perform(get("/api/test/auth/resource").header(HEADER_NAME, GUEST_TOKEN_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("unauthorized"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AuthConfig.class, WebMvcConfig.class, TestController.class})
    static class TestApplication {
    }

    @RestController
    @RequestMapping("/api/test/auth")
    static class TestController {

        @GetMapping("/resource")
        ApiResponse<Map<String, String>> get() {
            return ApiResponse.success(Map.of("method", "GET"));
        }

        @PostMapping("/resource")
        ApiResponse<Map<String, String>> post() {
            return ApiResponse.success(Map.of("method", "POST"));
        }

        @PutMapping("/resource")
        ApiResponse<Map<String, String>> put() {
            return ApiResponse.success(Map.of("method", "PUT"));
        }

        @DeleteMapping("/resource")
        ApiResponse<Map<String, String>> deleteResource() {
            return ApiResponse.success(Map.of("method", "DELETE"));
        }
    }
}
