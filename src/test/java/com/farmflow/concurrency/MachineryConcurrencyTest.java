package com.farmflow.concurrency;

import com.farmflow.handlers.GlobalExceptionHandler;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.*;

class MachineryConcurrencyTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void shouldMapObjectOptimisticLockingFailureToRfc9457ConflictProblemDetail() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/v1/machinery/machinery-100");
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException("Machinery", "machinery-100");

        ProblemDetail problem = exceptionHandler.handleOptimisticLocking(ex, request);

        assertNotNull(problem);
        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Optimistic Concurrency Conflict", problem.getTitle());
        assertTrue(problem.getDetail().contains("modified by another concurrent transaction"));
        assertEquals("/v1/machinery/machinery-100", problem.getInstance().toString());
        assertNotNull(problem.getProperties().get("traceId"));
        assertNotNull(problem.getProperties().get("timestamp"));
    }

    @Test
    void shouldMapJakartaOptimisticLockExceptionToRfc9457ConflictProblemDetail() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/v1/machinery/machinery-200/status");
        OptimisticLockException ex = new OptimisticLockException("Row was updated or deleted by another transaction");

        ProblemDetail problem = exceptionHandler.handleOptimisticLocking(ex, request);

        assertNotNull(problem);
        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Optimistic Concurrency Conflict", problem.getTitle());
        assertEquals("/v1/machinery/machinery-200/status", problem.getInstance().toString());
    }
}