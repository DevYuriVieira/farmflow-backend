package com.farmflow.config;

import com.farmflow.config.idempotency.IdempotencyRecord;
import com.farmflow.config.idempotency.IdempotencyStorage;
import com.farmflow.config.idempotency.InMemoryIdempotencyStorage;
import com.farmflow.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String X_IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final long DEFAULT_TTL_MS = 86_400_000L;

    private final IdempotencyStorage storage;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IdempotencyFilter() {
        this(new InMemoryIdempotencyStorage());
    }

    @Autowired
    public IdempotencyFilter(@Autowired(required = false) IdempotencyStorage storage) {
        this.storage = storage != null ? storage : new InMemoryIdempotencyStorage();
    }

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
        String rawKey = resolveIdempotencyKey(request);
        if (rawKey == null || rawKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (rawKey.length() > 128) {
            sendError(response, request.getRequestURI(), HttpStatus.BAD_REQUEST, "Idempotency-Key header cannot exceed 128 characters.");
            return;
        }

        long now = System.currentTimeMillis();
        storage.evictExpired(now, DEFAULT_TTL_MS);

        String principalId = resolvePrincipalId();
        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();
        String storageKey = principalId + ":" + method + ":" + path + ":" + rawKey;

        IdempotencyRecord existing = storage.get(storageKey);
        if (existing != null && (now - existing.createdAt() > DEFAULT_TTL_MS)) {
            storage.remove(storageKey);
            existing = null;
        }

        if (existing != null) {
            if (existing.inProgress()) {
                sendError(response, request.getRequestURI(), HttpStatus.CONFLICT, "A request with the provided Idempotency-Key is currently being processed.");
                return;
            }

            byte[] incomingBody = request.getInputStream().readAllBytes();
            String incomingHash = computeSha256(incomingBody);
            if (existing.requestHash() != null && !existing.requestHash().equals(incomingHash)) {
                sendError(response, request.getRequestURI(), HttpStatus.UNPROCESSABLE_ENTITY, "Request payload does not match the original request for this Idempotency-Key.");
                return;
            }

            response.setStatus(existing.status());
            if (existing.contentType() != null) {
                response.setContentType(existing.contentType());
            }
            response.setHeader(IDEMPOTENCY_KEY_HEADER, rawKey);
            response.setHeader(IDEMPOTENCY_REPLAYED_HEADER, "true");
            if (existing.body() != null && existing.body().length > 0) {
                response.getOutputStream().write(existing.body());
            }
            return;
        }

        boolean claimed = storage.putIfAbsentInProgress(storageKey, now);
        if (!claimed) {
            sendError(response, request.getRequestURI(), HttpStatus.CONFLICT, "A request with the provided Idempotency-Key is currently being processed.");
            return;
        }

        ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(request, 1024 * 1024);
        CachedResponseWrapper wrappedResponse = new CachedResponseWrapper(response);
        boolean completed = false;
        try {
            filterChain.doFilter(cachingRequest, wrappedResponse);
            int status = wrappedResponse.getStatus();
            byte[] responseBody = wrappedResponse.toByteArray();
            String contentType = wrappedResponse.getContentType();

            if (status >= 500) {
                storage.remove(storageKey);
            } else {
                byte[] requestBody = cachingRequest.getContentAsByteArray();
                String requestHash = computeSha256(requestBody);
                storage.save(storageKey, new IdempotencyRecord(status, contentType, responseBody, requestHash, false, now));
                completed = true;
            }

            response.setHeader(IDEMPOTENCY_KEY_HEADER, rawKey);
            response.setHeader(IDEMPOTENCY_REPLAYED_HEADER, "false");
            if (responseBody.length > 0) {
                response.getOutputStream().write(responseBody);
            }
        } finally {
            if (!completed) {
                storage.remove(storageKey);
            }
        }
    }

    private String resolvePrincipalId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return "anonymous";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            return user.getId();
        }
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        if (auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "anonymous";
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data != null ? data : new byte[0]);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String resolveIdempotencyKey(HttpServletRequest request) {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (key == null || key.isBlank()) {
            key = request.getHeader(X_IDEMPOTENCY_KEY_HEADER);
        }
        return key != null ? key.trim() : null;
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
        problem.put("type", "urn:problem-type:farmflow:idempotency-conflict");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", uri != null ? uri : "");
        problem.put("traceId", correlationId);
        problem.put("timestamp", Instant.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(problem));
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
