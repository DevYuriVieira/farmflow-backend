package com.farmflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String X_IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final long DEFAULT_TTL_MS = 86_400_000L;
    private static final int MAX_CACHE_SIZE = 10000;

    private final Map<String, IdempotencyRecord> storage = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return method == null || !MUTATING_METHODS.contains(method.toUpperCase());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = resolveIdempotencyKey(request);
        if (key == null || key.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (key.length() > 128) {
            sendError(response, request.getRequestURI(), HttpStatus.BAD_REQUEST, "Idempotency-Key header cannot exceed 128 characters.");
            return;
        }

        long now = System.currentTimeMillis();
        evictStaleEntries(now);

        IdempotencyRecord existing = storage.putIfAbsent(key, IdempotencyRecord.inProgress(now));
        if (existing != null) {
            if (existing.inProgress()) {
                sendError(response, request.getRequestURI(), HttpStatus.CONFLICT, "A request with the provided Idempotency-Key is currently being processed.");
                return;
            }
            response.setStatus(existing.status());
            if (existing.contentType() != null) {
                response.setContentType(existing.contentType());
            }
            response.setHeader(IDEMPOTENCY_KEY_HEADER, key);
            response.setHeader(IDEMPOTENCY_REPLAYED_HEADER, "true");
            if (existing.body() != null && existing.body().length > 0) {
                response.getOutputStream().write(existing.body());
            }
            return;
        }

        CachedResponseWrapper wrappedResponse = new CachedResponseWrapper(response);
        boolean completed = false;
        try {
            filterChain.doFilter(request, wrappedResponse);
            byte[] body = wrappedResponse.toByteArray();
            int status = wrappedResponse.getStatus();
            String contentType = wrappedResponse.getContentType();

            storage.put(key, new IdempotencyRecord(status, contentType, body, false, now));
            completed = true;

            response.setHeader(IDEMPOTENCY_KEY_HEADER, key);
            response.setHeader(IDEMPOTENCY_REPLAYED_HEADER, "false");
            if (body.length > 0) {
                response.getOutputStream().write(body);
            }
        } finally {
            if (!completed) {
                storage.remove(key);
            }
        }
    }

    private String resolveIdempotencyKey(HttpServletRequest request) {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (key == null || key.isBlank()) {
            key = request.getHeader(X_IDEMPOTENCY_KEY_HEADER);
        }
        return key != null ? key.trim() : null;
    }

    private void evictStaleEntries(long now) {
        if (storage.size() > MAX_CACHE_SIZE) {
            storage.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > DEFAULT_TTL_MS);
        }
    }

    private void sendError(
            HttpServletResponse response,
            String uri,
            HttpStatus status,
            String detail
    ) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        response.setStatus(status.value());
        response.setContentType("application/problem+json");

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://tools.ietf.org/html/rfc9457");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", uri != null ? uri : "");
        problem.put("traceId", correlationId);
        problem.put("timestamp", Instant.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private record IdempotencyRecord(
            int status,
            String contentType,
            byte[] body,
            boolean inProgress,
            long createdAt
    ) {
        static IdempotencyRecord inProgress(long now) {
            return new IdempotencyRecord(0, null, null, true, now);
        }
    }

    private static class CachedResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private ServletOutputStream servletOutputStream;
        private PrintWriter writer;

        CachedResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (writer != null) {
                throw new IllegalStateException("getWriter() has already been called on this response.");
            }
            if (servletOutputStream == null) {
                servletOutputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                    }

                    @Override
                    public void write(int b) {
                        output.write(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        output.write(b, off, len);
                    }
                };
            }
            return servletOutputStream;
        }

        @Override
        public PrintWriter getWriter() {
            if (servletOutputStream != null) {
                throw new IllegalStateException("getOutputStream() has already been called on this response.");
            }
            if (writer == null) {
                writer = new PrintWriter(output);
            }
            return writer;
        }

        @Override
        public void flushBuffer() {
            if (writer != null) {
                writer.flush();
            }
        }

        byte[] toByteArray() {
            if (writer != null) {
                writer.flush();
            }
            return output.toByteArray();
        }
    }
}
