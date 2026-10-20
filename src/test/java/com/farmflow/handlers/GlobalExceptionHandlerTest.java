package com.farmflow.handlers;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.exceptions.BusinessException;
import com.farmflow.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID_KEY, "test-trace-id-123");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldHandleResourceNotFoundException() {
        var ex = new ResourceNotFoundException("Farm not found");
        ProblemDetail problem = handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals("Not Found", problem.getTitle());
        assertEquals("Farm not found", problem.getDetail());
        assertEquals(URI.create("/api/v1/test"), problem.getInstance());
        assertEquals("test-trace-id-123", problem.getProperties().get("traceId"));
        assertNotNull(problem.getProperties().get("timestamp"));
    }

    @Test
    void shouldHandleBusinessException() {
        var ex = new BusinessException("Invalid machinery status transition");
        ProblemDetail problem = handler.handleBusiness(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Business Rule Violation", problem.getTitle());
        assertEquals("Invalid machinery status transition", problem.getDetail());
        assertEquals("test-trace-id-123", problem.getProperties().get("traceId"));
    }

    @Test
    void shouldHandleDataIntegrityViolationExceptionWithSanitization() {
        var ex = new DataIntegrityViolationException("duplicate key value violates unique constraint ix_test_plate");
        ProblemDetail problem = handler.handleDataIntegrity(ex, request);

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Conflict", problem.getTitle());
        assertEquals("A database integrity constraint violation or duplicate record conflict occurred.", problem.getDetail());
        assertFalse(problem.getDetail().contains("ix_test_plate"));
        assertEquals("test-trace-id-123", problem.getProperties().get("traceId"));
    }

    @Test
    void shouldHandleGenericExceptionWithSanitization() {
        var ex = new RuntimeException("Fatal SQL syntax error near table secret_credentials");
        ProblemDetail problem = handler.handleGeneric(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
        assertEquals("Internal Server Error", problem.getTitle());
        assertEquals("An unexpected internal error occurred. Please contact support referencing the traceId.", problem.getDetail());
        assertFalse(problem.getDetail().contains("secret_credentials"));
        assertEquals("test-trace-id-123", problem.getProperties().get("traceId"));
    }
}