package dev.deriou.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void success_should_fill_ok_code_message_and_data() {
        ApiResponse<String> response = ApiResponse.success("gateway");

        assertEquals(ResultCode.OK.getCode(), response.getCode());
        assertEquals(ResultCode.OK.getDefaultMessage(), response.getMessage());
        assertEquals("gateway", response.getData());
    }

    @Test
    void fail_should_fill_error_code_message_and_null_data() {
        ApiResponse<Void> response = ApiResponse.fail(ResultCode.BIZ_ERROR, "article not found");

        assertEquals(ResultCode.BIZ_ERROR.getCode(), response.getCode());
        assertEquals("article not found", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void trace_id_should_exist_when_building_response() {
        ApiResponse<Void> response = ApiResponse.fail(ResultCode.SYSTEM_ERROR, null);

        assertNotNull(response.getTraceId());
    }
}
