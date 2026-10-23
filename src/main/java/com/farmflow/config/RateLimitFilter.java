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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SIZE_MS = 60_000L;
    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 60;
    private static final int AUTH_MAX_REQUESTS_PER_MINUTE = 15;
    private static final int MAX_CACHE_SIZE = 10000;

    private static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/actuator/health",
            "/health",
            "/healthz",
            "/live",
            "/ready"
    );

    private final Map<String, SlidingWindowBucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = resolvePath(request);
        if (path.isEmpty()) {
            return false;
        }
        for (String exempt : EXEMPT_PREFIXES) {
            if (path.equals(exempt) || path.startsWith(exempt + "/")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        String uri = request.getRequestURI();
        String path = resolvePath(request);
        boolean isAuthRoute = path.equals("/auth") || path.startsWith("/auth/");
        int maxRequests = isAuthRoute ? AUTH_MAX_REQUESTS_PER_MINUTE : DEFAULT_MAX_REQUESTS_PER_MINUTE;
        String bucketKey = clientIp + (isAuthRoute ? ":auth" : ":general");

        long now = System.currentTimeMillis();
        long currentWindow = now / WINDOW_SIZE_MS;
        double elapsedFraction = (double) (now % WINDOW_SIZE_MS) / WINDOW_SIZE_MS;
        long resetSeconds = Math.max(1L, (WINDOW_SIZE_MS - (now % WINDOW_SIZE_MS)) / 1000L);

        evictStaleEntries(currentWindow);

        SlidingWindowBucket bucket = buckets.compute(bucketKey, (key, existing) -> {
            if (existing == null) {
                return new SlidingWindowBucket(currentWindow, new AtomicInteger(1), 0);
            }
            if (existing.window() == currentWindow) {
                existing.currentCount().incrementAndGet();
                return existing;
            }
            if (existing.window() == currentWindow - 1) {
                return new SlidingWindowBucket(currentWindow, new AtomicInteger(1), existing.currentCount().get());
            }
            return new SlidingWindowBucket(currentWindow, new AtomicInteger(1), 0);
        });

        int currentCount = bucket.currentCount().get();
        int previousCount = bucket.previousCount();
        double estimatedCount = (previousCount * (1.0 - elapsedFraction)) + currentCount;

        if (estimatedCount > maxRequests) {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(resetSeconds));
            response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetSeconds));
            response.setContentType("application/problem+json");

            Map<String, Object> problem = new LinkedHashMap<>();
            problem.put("type", "urn:problem-type:farmflow:rate-limit-exceeded");
            problem.put("title", "Too Many Requests");
            problem.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
            problem.put("detail", "Rate limit quota exceeded. Maximum " + maxRequests + " requests per minute allowed.");
            problem.put("instance", uri != null ? uri : "");
            problem.put("retryAfter", resetSeconds);
            problem.put("traceId", correlationId);
            problem.put("timestamp", Instant.now().toString());

            response.getWriter().write(objectMapper.writeValueAsString(problem));
            return;
        }

        int remaining = Math.max(0, (int) Math.floor(maxRequests - estimatedCount));
        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetSeconds));

        filterChain.doFilter(request, response);
    }

    private void evictStaleEntries(long currentWindow) {
        if (buckets.size() > MAX_CACHE_SIZE) {
            buckets.entrySet().removeIf(entry -> entry.getValue().window() < currentWindow - 1);
        }
    }

    String resolvePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            String stripped = uri.substring(contextPath.length());
            return stripped.startsWith("/") ? stripped : "/" + stripped;
        }
        return uri;
    }

    String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "0.0.0.0";
        }
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr.trim();
        }
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return remoteAddr.trim();
        }
        String[] hops = xfHeader.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isBlank() && !isTrustedProxy(hop)) {
                return hop;
            }
        }
        return hops[0].trim();
    }

    boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String cleanIp = ip.trim();
        if ("127.0.0.1".equals(cleanIp) || "0:0:0:0:0:0:0:1".equals(cleanIp) || "::1".equals(cleanIp)) {
            return true;
        }
        if (cleanIp.startsWith("10.") || cleanIp.startsWith("192.168.")) {
            return true;
        }
        if (cleanIp.startsWith("172.")) {
            String[] parts = cleanIp.split("\\.");
            if (parts.length >= 2) {
                try {
                    int secondOctet = Integer.parseInt(parts[1]);
                    return secondOctet >= 16 && secondOctet <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private record SlidingWindowBucket(long window, AtomicInteger currentCount, int previousCount) {
    }
}