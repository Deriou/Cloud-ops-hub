package dev.deriou.common.api;

import dev.deriou.common.observability.TraceContext;
import java.util.Objects;

public final class ApiResponse<T> {
    private final String code;
    private final String message;
    private final T data;
    private final String traceId;

    private ApiResponse(ResultCode resultCode, String message, T data) {
        this.code = Objects.requireNonNull(resultCode, "resultCode must not be null").getCode();
        this.message = resolveMessage(resultCode, message);
        this.data = data;
        this.traceId = TraceContext.currentTraceId().orElseGet(TraceContext::generateTraceId);
        TraceContext.recordResultCode(this.code);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultCode.OK, ResultCode.OK.getDefaultMessage(), data);
    }

    public static <T> ApiResponse<T> fail(ResultCode code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }

    private static String resolveMessage(ResultCode resultCode, String message) {
        if (message == null || message.isBlank()) {
            return resultCode.getDefaultMessage();
        }

        return message;
    }
}
