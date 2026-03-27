package dev.deriou.common.exception;

import dev.deriou.common.api.ApiResponse;
import dev.deriou.common.api.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        log.warn("BizException: code={}, message={}", ex.getResultCode().getCode(), ex.getMessage());
        ResultCode code = ex.getResultCode();
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.fail(code, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getHttpStatus())
                .body(ApiResponse.fail(ResultCode.SYSTEM_ERROR, ResultCode.SYSTEM_ERROR.getDefaultMessage()));
    }
}
