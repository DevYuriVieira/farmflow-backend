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
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(HttpStatus.OK.value(), response.getStatus());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldBlockAndReturnRfc9457WhenExceedingLimit() throws ServletException, IOException {
        String clientIp = "192.168.1.200";

        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr(clientIp);
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
        assertEquals("60", blockedRes.getHeader("Retry-After"));
        assertEquals("application/problem+json", blockedRes.getContentType());
        String body = blockedRes.getContentAsString();
        assertTrue(body.contains("\"type\":\"https://tools.ietf.org/html/rfc9457\""));
        assertTrue(body.contains("\"title\":\"Too Many Requests\""));
        assertTrue(body.contains("\"status\":429"));
        assertTrue(body.contains("\"retryAfter\":60"));
        assertTrue(body.contains("\"traceId\""));
    }
}