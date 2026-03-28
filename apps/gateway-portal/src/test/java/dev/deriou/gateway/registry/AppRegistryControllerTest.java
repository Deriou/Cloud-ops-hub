package dev.deriou.gateway.registry;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.deriou.common.auth.AuthInterceptor;
import dev.deriou.common.auth.GuestTokenStore;
import dev.deriou.common.config.AuthConfig;
import dev.deriou.common.config.WebMvcConfig;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppRegistryController.class)
@Import({AuthConfig.class, WebMvcConfig.class, AppRegistryService.class})
@TestPropertySource(properties = {
        "ops.auth.master-key=test-master-key",
        "ops.auth.guest-token-ttl-seconds=300",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class AppRegistryControllerTest {

    private static final String HEADER_NAME = AuthInterceptor.HEADER_NAME;
    private static final String GUEST_TOKEN_ID = "app-registry-guest-token";
    private static final String GUEST_TOKEN_VALUE = "app-registry-guest-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GuestTokenStore guestTokenStore;

    @MockBean
    private AppRegistrySource appRegistrySource;

    @BeforeEach
    void setUp() {
        guestTokenStore.put(GUEST_TOKEN_ID, GUEST_TOKEN_VALUE);
    }

    @Test
    void get_apps_should_return_empty_list_when_no_data() throws Exception {
        given(appRegistrySource.listApps()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/gateway/apps").header(HEADER_NAME, GUEST_TOKEN_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void get_apps_should_return_expected_fields() throws Exception {
        given(appRegistrySource.listApps()).willReturn(List.of(
                new RegisteredApp("ops-core", "Ops Core", "/ops", "UP", "运维任务与诊断中心", 20, URI.create("http://ops-core/actuator/health")),
                new RegisteredApp("blog-service", "Blog Service", "/blog", "UP", "内容与 SEO 服务", 10, URI.create("http://blog-service/actuator/health"))
        ));

        mockMvc.perform(get("/api/v1/gateway/apps").header(HEADER_NAME, GUEST_TOKEN_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].appKey").value("blog-service"))
                .andExpect(jsonPath("$.data[0].title").value("Blog Service"))
                .andExpect(jsonPath("$.data[0].route").value("/blog"))
                .andExpect(jsonPath("$.data[0].status").value("UP"))
                .andExpect(jsonPath("$.data[0].description").value("内容与 SEO 服务"))
                .andExpect(jsonPath("$.data[1].appKey").value("ops-core"));
    }

    @Test
    void response_should_follow_api_response_contract() throws Exception {
        given(appRegistrySource.listApps()).willReturn(List.of(
                new RegisteredApp(
                        "gateway-portal",
                        "Gateway Portal",
                        "/",
                        "UP",
                        "统一公网入口",
                        1,
                        URI.create("http://gateway-portal/actuator/health")
                )
        ));

        mockMvc.perform(get("/api/v1/gateway/apps").header(HEADER_NAME, GUEST_TOKEN_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].appKey").value("gateway-portal"));
    }
}
