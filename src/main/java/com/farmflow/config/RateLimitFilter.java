package com.farmflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final int RETRY_AFTER_SECONDS = 60;
    private final Map<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        long currentMinute = System.currentTimeMillis() / 60000;

        RequestCounter counter = requestCounts.compute(clientIp, (key, existing) -> {
            if (existing == null || existing.minute() != currentMinute) {
                return new RequestCounter(currentMinute, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (counter.count().get() > MAX_REQUESTS_PER_MINUTE) {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(RETRY_AFTER_SECONDS));
            response.setContentType("application/problem+json");

            Map<String, Object> problem = new LinkedHashMap<>();
            problem.put("type", "https://tools.ietf.org/html/rfc9457");
            problem.put("title", "Too Many Requests");
            problem.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
            problem.put("detail", "Rate limit quota exceeded. Maximum 60 requests per minute allowed.");
            problem.put("instance", request.getRequestURI());
            problem.put("retryAfter", RETRY_AFTER_SECONDS);
            problem.put("traceId", correlationId);
            problem.put("timestamp", Instant.now().toString());

            response.getWriter().write(objectMapper.writeValueAsString(problem));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    private record RequestCounter(long minute, AtomicInteger count) {
    }
}