package dev.deriou.common.exception;

import dev.deriou.common.api.ResultCode;

public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getDefaultMessage());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message != null && !message.isBlank() ? message : resultCode.getDefaultMessage());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message, Throwable cause) {
        super(message != null && !message.isBlank() ? message : resultCode.getDefaultMessage(), cause);
        this.resultCode = resultCode;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }
}
