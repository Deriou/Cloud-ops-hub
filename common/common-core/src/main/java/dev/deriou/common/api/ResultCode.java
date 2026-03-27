package dev.deriou.common.api;

public enum ResultCode {
    OK(200, "success"),
    BIZ_ERROR(400, "biz error"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    SYSTEM_ERROR(500, "system error");

    private final int httpStatus;
    private final String defaultMessage;

    ResultCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return name();
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
