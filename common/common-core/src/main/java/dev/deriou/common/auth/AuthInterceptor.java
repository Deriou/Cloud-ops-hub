package dev.deriou.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deriou.common.api.ApiResponse;
import dev.deriou.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Ops-Key";
    public static final String ACCESS_MODE_ATTRIBUTE = "ops.accessMode";
    public static final String ADMIN_MODE = "admin";
    public static final String GUEST_MODE = "guest";

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final AuthProperties authProperties;
    private final GuestTokenStore guestTokenStore;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(
            AuthProperties authProperties,
            GuestTokenStore guestTokenStore,
            ObjectMapper objectMapper
    ) {
        this.authProperties = Objects.requireNonNull(authProperties, "authProperties must not be null");
        this.guestTokenStore = Objects.requireNonNull(guestTokenStore, "guestTokenStore must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (isPublicImageReadRequest(request)) {
            request.setAttribute(ACCESS_MODE_ATTRIBUTE, GUEST_MODE);
            return true;
        }

        String opsKey = request.getHeader(HEADER_NAME);
        if (opsKey == null || opsKey.isBlank()) {
            log.warn("Access denied: missing ops key, method={}, path={}", request.getMethod(), request.getRequestURI());
            return writeFailure(response, ResultCode.UNAUTHORIZED);
        }

        if (isMasterKey(opsKey)) {
            request.setAttribute(ACCESS_MODE_ATTRIBUTE, ADMIN_MODE);
            return true;
        }

        if (isGuestKey(opsKey)) {
            if (isReadOnlyRequest(request.getMethod())) {
                request.setAttribute(ACCESS_MODE_ATTRIBUTE, GUEST_MODE);
                return true;
            }

            log.warn("Access denied: guest write blocked, method={}, path={}", request.getMethod(), request.getRequestURI());
            return writeFailure(response, ResultCode.FORBIDDEN);
        }

        log.warn("Access denied: invalid or expired token, method={}, path={}", request.getMethod(), request.getRequestURI());
        return writeFailure(response, ResultCode.UNAUTHORIZED);
    }

    boolean isMasterKey(String opsKey) {
        String masterKey = authProperties.getMasterKey();
        return masterKey != null && !masterKey.isBlank() && masterKey.equals(opsKey);
    }

    boolean isGuestKey(String opsKey) {
        return guestTokenStore.contains(opsKey);
    }

    boolean isReadOnlyRequest(String method) {
        return HttpMethod.GET.matches(method);
    }

    boolean isPublicImageReadRequest(HttpServletRequest request) {
        return HttpMethod.GET.matches(request.getMethod())
                && request.getRequestURI() != null
                && request.getRequestURI().startsWith("/api/v1/blog/assets/images/");
    }

    private boolean writeFailure(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(resultCode.getHttpStatus());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(resultCode, resultCode.getDefaultMessage()));
        return false;
    }
}
