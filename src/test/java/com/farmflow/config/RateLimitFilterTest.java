package com.farmflow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldAllowRequestsUnderLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        request.setRequestURI("/api/v1/machinery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals("60", response.getHeader("X-RateLimit-Limit"));
        assertEquals("59", response.getHeader("X-RateLimit-Remaining"));
        assertNotNull(response.getHeader("X-RateLimit-Reset"));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldBlockAndReturnRfc9457WhenExceedingLimit() throws ServletException, IOException {
        String clientIp = "198.51.100.20";

        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr(clientIp);
            req.setRequestURI("/api/v1/telemetry");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, filterChain);
            assertEquals(HttpStatus.OK.value(), res.getStatus());
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest();
        blockedReq.setRemoteAddr(clientIp);
        blockedReq.setRequestURI("/api/v1/telemetry");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        filter.doFilterInternal(blockedReq, blockedRes, filterChain);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedRes.getStatus());
        assertNotNull(blockedRes.getHeader("Retry-After"));
        assertEquals("60", blockedRes.getHeader("X-RateLimit-Limit"));
        assertEquals("0", blockedRes.getHeader("X-RateLimit-Remaining"));
        assertEquals("application/problem+json", blockedRes.getContentType());

        String body = blockedRes.getContentAsString();
        assertTrue(body.contains("\"type\":\"https://tools.ietf.org/html/rfc9457\""));
        assertTrue(body.contains("\"title\":\"Too Many Requests\""));
        assertTrue(body.contains("\"status\":429"));
        assertTrue(body.contains("\"traceId\""));
    }

    @Test
    void shouldEnforceStricterLimitOnAuthEndpoints() throws ServletException, IOException {
        String clientIp = "198.51.100.30";

        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr(clientIp);
            req.setRequestURI("/auth/login");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, filterChain);
            assertEquals(HttpStatus.OK.value(), res.getStatus());
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest();
        blockedReq.setRemoteAddr(clientIp);
        blockedReq.setRequestURI("/auth/login");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        filter.doFilterInternal(blockedReq, blockedRes, filterChain);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedRes.getStatus());
        assertEquals("15", blockedRes.getHeader("X-RateLimit-Limit"));
        assertEquals("0", blockedRes.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    void shouldBypassRateLimitOnHealthCheck() {
        MockHttpServletRequest healthReq = new MockHttpServletRequest();
        healthReq.setRequestURI("/health");
        assertTrue(filter.shouldNotFilter(healthReq));

        MockHttpServletRequest actuatorReq = new MockHttpServletRequest();
        actuatorReq.setRequestURI("/actuator/health/readiness");
        assertTrue(filter.shouldNotFilter(actuatorReq));

        MockHttpServletRequest apiReq = new MockHttpServletRequest();
        apiReq.setRequestURI("/api/v1/machinery");
        assertFalse(filter.shouldNotFilter(apiReq));
    }

    @Test
    void shouldBypassRateLimitOnHealthCheckWithContextPath() {
        MockHttpServletRequest healthReq = new MockHttpServletRequest();
        healthReq.setContextPath("/v1");
        healthReq.setRequestURI("/v1/health");
        assertTrue(filter.shouldNotFilter(healthReq));

        MockHttpServletRequest actuatorReq = new MockHttpServletRequest();
        actuatorReq.setContextPath("/v1");
        actuatorReq.setRequestURI("/v1/actuator/health/readiness");
        assertTrue(filter.shouldNotFilter(actuatorReq));
    }

    @Test
    void shouldEnforceAuthLimitWithContextPath() throws ServletException, IOException {
        String clientIp = "198.51.100.35";

        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setContextPath("/v1");
            req.setRemoteAddr(clientIp);
            req.setRequestURI("/v1/auth/login");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, filterChain);
            assertEquals(HttpStatus.OK.value(), res.getStatus());
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest();
        blockedReq.setContextPath("/v1");
        blockedReq.setRemoteAddr(clientIp);
        blockedReq.setRequestURI("/v1/auth/login");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        filter.doFilterInternal(blockedReq, blockedRes, filterChain);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedRes.getStatus());
        assertEquals("15", blockedRes.getHeader("X-RateLimit-Limit"));
        assertEquals("0", blockedRes.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    void shouldNotTreatPrefixCollisionsAsAuthRoute() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("198.51.100.40");
        req.setRequestURI("/authorities");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertEquals(HttpStatus.OK.value(), res.getStatus());
        assertEquals("60", res.getHeader("X-RateLimit-Limit"));
    }

    @Test
    void shouldIgnoreXForwardedForWhenDirectClientIsUntrusted() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.50");
        req.addHeader("X-Forwarded-For", "198.51.100.99, 10.0.0.1");

        String resolvedIp = filter.resolveClientIp(req);

        assertEquals("203.0.113.50", resolvedIp);
    }

    @Test
    void shouldExtractRightmostUntrustedClientIpWhenBehindTrustedProxy() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.1, 198.51.100.77, 10.0.0.2");

        String resolvedIp = filter.resolveClientIp(req);

        assertEquals("198.51.100.77", resolvedIp);
    }

    @Test
    void shouldCorrectlyIdentifyTrustedProxies() {
        assertTrue(filter.isTrustedProxy("127.0.0.1"));
        assertTrue(filter.isTrustedProxy("::1"));
        assertTrue(filter.isTrustedProxy("10.244.0.15"));
        assertTrue(filter.isTrustedProxy("192.168.1.1"));
        assertTrue(filter.isTrustedProxy("172.18.0.2"));

        assertFalse(filter.isTrustedProxy("172.15.0.1"));
        assertFalse(filter.isTrustedProxy("172.32.0.1"));
        assertFalse(filter.isTrustedProxy("8.8.8.8"));
        assertFalse(filter.isTrustedProxy("203.0.113.195"));
        assertFalse(filter.isTrustedProxy(null));
        assertFalse(filter.isTrustedProxy(""));
    }
}