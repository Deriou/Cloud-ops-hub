package dev.deriou.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    private final String serviceName;

    public RequestTraceFilter(@Value("${spring.application.name:unknown-service}") String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String traceId = TraceContext.resolveTraceId(request.getHeader(TraceContext.TRACE_ID_HEADER));
        TraceContext.bind(request, traceId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!isActuatorRequest(request)) {
                logRequestCompleted(request, response, startedAt, traceId);
            }
            TraceContext.clear();
        }
    }

    private void logRequestCompleted(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt,
            String traceId
    ) {
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        String resultCode = TraceContext.currentResultCode(request)
                .orElse("HTTP_" + response.getStatus());

        log.info(
                "event=http_request service={} traceId={} method={} path={} status={} latencyMs={} resultCode={} msg=\"request completed\"",
                serviceName,
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                latencyMs,
                resultCode
        );
    }

    private boolean isActuatorRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri != null && ("/actuator".equals(requestUri) || requestUri.startsWith("/actuator/"));
    }
}
