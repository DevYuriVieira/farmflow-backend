package com.farmflow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyFilterTest {

    private IdempotencyFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new IdempotencyFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldBypassWhenNoIdempotencyKeyHeaderPresent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/machinery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(response.getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));
    }

    @Test
    void shouldIgnoreNonMutatingHttpMethods() {
        MockHttpServletRequest getReq = new MockHttpServletRequest("GET", "/machinery");
        assertTrue(filter.shouldNotFilter(getReq));

        MockHttpServletRequest deleteReq = new MockHttpServletRequest("DELETE", "/machinery/1");
        assertTrue(filter.shouldNotFilter(deleteReq));

        MockHttpServletRequest postReq = new MockHttpServletRequest("POST", "/machinery");
        assertFalse(filter.shouldNotFilter(postReq));

        MockHttpServletRequest putReq = new MockHttpServletRequest("PUT", "/machinery/1");
        assertFalse(filter.shouldNotFilter(putReq));
    }

    @Test
    void shouldRejectKeyLongerThan128Characters() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/machinery");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "a".repeat(129));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertTrue(response.getContentAsString().contains("128 characters"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void shouldExecuteAndCacheFirstRequest() throws ServletException, IOException {
        String key = "txn-unique-001";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/machinery");
        request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpStatus.CREATED.value());
            res.setContentType("application/json");
            res.getWriter().write("{\"id\":\"m-100\",\"name\":\"Trator\"}");
            return null;
        }).when(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpStatus.CREATED.value(), response.getStatus());
        assertEquals("false", response.getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));
        assertEquals(key, response.getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER));
        assertTrue(response.getContentAsString().contains("Trator"));
        verify(filterChain, times(1)).doFilter(any(), any());
    }

    @Test
    void shouldReplayCachedResponseOnDuplicateRequest() throws ServletException, IOException {
        String key = "txn-unique-002";

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/machinery");
        req1.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        MockHttpServletResponse res1 = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpStatus.CREATED.value());
            res.setContentType("application/json");
            res.getWriter().write("{\"id\":\"m-200\",\"name\":\"Colheitadeira\"}");
            return null;
        }).when(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        filter.doFilter(req1, res1, filterChain);
        assertEquals(HttpStatus.CREATED.value(), res1.getStatus());
        assertEquals("false", res1.getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/machinery");
        req2.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        filter.doFilter(req2, res2, filterChain);

        assertEquals(HttpStatus.CREATED.value(), res2.getStatus());
        assertEquals("true", res2.getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));
        assertEquals(key, res2.getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER));
        assertEquals("application/json", res2.getContentType());
        assertTrue(res2.getContentAsString().contains("Colheitadeira"));
        verify(filterChain, times(1)).doFilter(any(), any());
    }

    @Test
    void shouldRejectConcurrentInFlightRequestWithConflict() throws ServletException, IOException {
        String key = "txn-concurrent-003";

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/machinery");
        req1.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        MockHttpServletResponse res1 = new MockHttpServletResponse();

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/machinery");
        req2.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        doAnswer(invocation -> {
            filter.doFilter(req2, res2, filterChain);
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpStatus.CREATED.value());
            res.getWriter().write("{\"status\":\"ok\"}");
            return null;
        }).when(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        filter.doFilter(req1, res1, filterChain);

        assertEquals(HttpStatus.CONFLICT.value(), res2.getStatus());
        assertEquals("application/problem+json", res2.getContentType());
        assertTrue(res2.getContentAsString().contains("currently being processed"));
    }

    @Test
    void shouldRemoveKeyIfExecutionFailsSoClientCanRetry() throws ServletException, IOException {
        String key = "txn-fail-004";
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/machinery");
        req1.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        MockHttpServletResponse res1 = new MockHttpServletResponse();

        doThrow(new RuntimeException("Database down"))
                .when(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        assertThrows(RuntimeException.class, () -> {
            try {
                filter.doFilter(req1, res1, filterChain);
            } catch (ServletException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/machinery");
        req2.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpStatus.CREATED.value());
            res.getWriter().write("{\"status\":\"success\"}");
            return null;
        }).when(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        assertDoesNotThrow(() -> {
            try {
                filter.doFilter(req2, res2, filterChain);
            } catch (ServletException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(HttpStatus.CREATED.value(), res2.getStatus());
        assertEquals("false", res2.getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));
    }

    @Test
    void shouldIsolateIdempotencyKeysBetweenDifferentPrincipals() throws ServletException, IOException {
        String key = "shared-key-123";

        UserPrincipal user1 = UserPrincipal.builder().id("user-1").email("u1@farmflow.com").role("OWNER").build();
        UserPrincipal user2 = UserPrincipal.builder().id("user-2").email("u2@farmflow.com").role("OWNER").build();

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpStatus.CREATED.value());
            res.getWriter().write("{\"owner\":\"user-1\"}");
            return null;
        }).when(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/machinery");
        req1.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        req1.setContent("{\"data\":\"common\"}".getBytes());
        MockHttpServletResponse res1 = new MockHttpServletResponse();

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user1, null)
        );
        filter.doFilter(req1, res1, filterChain);
        assertEquals(HttpStatus.CREATED.value(), res1.getStatus());
        assertEquals("false", res1.getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpStatus.CREATED.value());
            res.getWriter().write("{\"owner\":\"user-2\"}");
            return null;
        }).when(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/machinery");
        req2.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        req2.setContent("{\"data\":\"common\"}".getBytes());
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user2, null)
        );
        filter.doFilter(req2, res2, filterChain);

        assertEquals(HttpStatus.CREATED.value(), res2.getStatus());
        assertEquals("false", res2.getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));
        assertTrue(res2.getContentAsString().contains("user-2"));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectDuplicateRequestWhenPayloadDiffersWith422() throws ServletException, IOException {
        String key = "txn-payload-mismatch";

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/machinery");
        req1.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        req1.setContent("{\"name\":\"Tractor 1\"}".getBytes());
        MockHttpServletResponse res1 = new MockHttpServletResponse();

        doAnswer(invocation -> {
            ServletRequest req = invocation.getArgument(0);
            req.getInputStream().readAllBytes();
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpStatus.CREATED.value());
            res.getWriter().write("{\"id\":\"1\"}");
            return null;
        }).when(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        filter.doFilter(req1, res1, filterChain);
        assertEquals(HttpStatus.CREATED.value(), res1.getStatus());

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/machinery");
        req2.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        req2.setContent("{\"name\":\"Tractor DIFFERENT\"}".getBytes());
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        filter.doFilter(req2, res2, filterChain);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), res2.getStatus());
        assertEquals("application/problem+json", res2.getContentType());
        assertTrue(res2.getContentAsString().contains("Request payload does not match"));
    }

    @Test
    void shouldNotCache5xxResponsesAndAllowRetry() throws ServletException, IOException {
        String key = "txn-server-error-500";

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/machinery");
        req1.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        MockHttpServletResponse res1 = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            res.getWriter().write("{\"error\":\"Internal Server Error\"}");
            return null;
        }).when(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        filter.doFilter(req1, res1, filterChain);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), res1.getStatus());

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/machinery");
        req2.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpStatus.CREATED.value());
            res.getWriter().write("{\"id\":\"m-retry-ok\"}");
            return null;
        }).when(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));

        filter.doFilter(req2, res2, filterChain);
        assertEquals(HttpStatus.CREATED.value(), res2.getStatus());
        assertEquals("false", res2.getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));
        assertTrue(res2.getContentAsString().contains("m-retry-ok"));
    }
}
