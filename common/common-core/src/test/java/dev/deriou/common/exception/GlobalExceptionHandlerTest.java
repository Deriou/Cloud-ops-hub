package dev.deriou.common.exception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.deriou.common.api.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class TestController {

        @GetMapping("/test/biz-error")
        public void bizError() {
            throw new BizException(ResultCode.BIZ_ERROR, "article not found");
        }

        @GetMapping("/test/unknown-error")
        public void unknownError() {
            throw new RuntimeException(
                    "NullPointerException at com.example.Service.run(Service.java:42)");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void biz_exception_should_return_biz_error() throws Exception {
        mockMvc.perform(get("/test/biz-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ_ERROR"))
                .andExpect(jsonPath("$.message").value("article not found"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unknown_exception_should_return_system_error() throws Exception {
        mockMvc.perform(get("/test/unknown-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.message").value("system error"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void response_should_not_contain_stack_trace() throws Exception {
        String body = mockMvc.perform(get("/test/unknown-error"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("Exception"), "response must not leak exception class names");
        assertFalse(body.contains(".java"), "response must not leak source file references");
        assertFalse(body.contains("\tat "), "response must not leak stack trace lines");
    }
}
