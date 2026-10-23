package com.farmflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ProblemSecurityHandlersTest {

    @Test
    void shouldReturnRfc9457ProblemDetailOnAuthenticationEntryPoint() throws IOException {
        var entryPoint = new ProblemAuthenticationEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/farms");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"type\":\"urn:problem-type:farmflow:unauthorized\""));
        assertTrue(body.contains("\"title\":\"Unauthorized\""));
        assertTrue(body.contains("\"status\":401"));
        assertTrue(body.contains("\"instance\":\"/farms\""));
        assertTrue(body.contains("\"traceId\""));
    }

    @Test
    void shouldReturnRfc9457ProblemDetailOnAccessDeniedHandler() throws IOException {
        var handler = new ProblemAccessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Forbidden access"));

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"type\":\"urn:problem-type:farmflow:forbidden\""));
        assertTrue(body.contains("\"title\":\"Forbidden\""));
        assertTrue(body.contains("\"status\":403"));
        assertTrue(body.contains("\"instance\":\"/admin/settings\""));
        assertTrue(body.contains("\"traceId\""));
    }
}
