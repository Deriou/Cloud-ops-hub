package dev.deriou.common.observability;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_ATTRIBUTE = "ops.traceId";
    public static final String RESULT_CODE_ATTRIBUTE = "ops.resultCode";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String METHOD_MDC_KEY = "method";
    public static final String PATH_MDC_KEY = "path";

    private static final int MAX_TRACE_ID_LENGTH = 128;
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");

    private TraceContext() {
    }

    public static String resolveTraceId(String candidate) {
        if (isValidTraceId(candidate)) {
            return candidate.trim();
        }

        return generateTraceId();
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void bind(HttpServletRequest request, String traceId) {
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        MDC.put(METHOD_MDC_KEY, request.getMethod());
        MDC.put(PATH_MDC_KEY, request.getRequestURI());
    }

    public static Optional<String> currentTraceId() {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        if (isValidTraceId(traceId)) {
            return Optional.of(traceId);
        }

        return currentRequest()
                .map(request -> request.getAttribute(TRACE_ID_ATTRIBUTE))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(TraceContext::isValidTraceId);
    }

    public static void recordResultCode(String resultCode) {
        currentRequest().ifPresent(request -> request.setAttribute(RESULT_CODE_ATTRIBUTE, resultCode));
    }

    public static Optional<String> currentResultCode(HttpServletRequest request) {
        Object resultCode = request.getAttribute(RESULT_CODE_ATTRIBUTE);
        if (resultCode instanceof String value && !value.isBlank()) {
            return Optional.of(value);
        }

        return Optional.empty();
    }

    public static void clear() {
        MDC.remove(TRACE_ID_MDC_KEY);
        MDC.remove(METHOD_MDC_KEY);
        MDC.remove(PATH_MDC_KEY);
    }

    private static Optional<HttpServletRequest> currentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return Optional.of(servletRequestAttributes.getRequest());
        }

        return Optional.empty();
    }

    private static boolean isValidTraceId(String traceId) {
        if (traceId == null) {
            return false;
        }

        String trimmed = traceId.trim();
        return !trimmed.isEmpty()
                && trimmed.length() <= MAX_TRACE_ID_LENGTH
                && TRACE_ID_PATTERN.matcher(trimmed).matches();
    }
}
