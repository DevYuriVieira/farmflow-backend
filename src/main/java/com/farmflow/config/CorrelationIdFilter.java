package com.farmflow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String MDC_CORRELATION_ID_KEY = "correlationId";
    public static final String MDC_TRACEPARENT_KEY = "traceparent";

    private static final java.util.regex.Pattern CORRELATION_ID_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId) || !CORRELATION_ID_PATTERN.matcher(correlationId).matches()) {
            correlationId = UUID.randomUUID().toString();
        }

        String traceParent = request.getHeader(TRACEPARENT_HEADER);
        if (!StringUtils.hasText(traceParent) || !W3CTraceContextUtils.isValidW3CTraceParent(traceParent)) {
            traceParent = W3CTraceContextUtils.generateTraceParent(correlationId);
        }

        MDC.put(MDC_CORRELATION_ID_KEY, correlationId);
        MDC.put(MDC_TRACEPARENT_KEY, traceParent);

        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        response.setHeader(TRACEPARENT_HEADER, traceParent);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID_KEY);
            MDC.remove(MDC_TRACEPARENT_KEY);
        }
    }
}
