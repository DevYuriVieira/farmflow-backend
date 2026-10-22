package com.farmflow.handlers;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.exceptions.BusinessException;
import com.farmflow.exceptions.InvalidMachineryStateException;
import com.farmflow.exceptions.ResourceNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI DEFAULT_TYPE = URI.create("https://tools.ietf.org/html/rfc9457");

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(DEFAULT_TYPE);
        problem.setTitle("Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(DEFAULT_TYPE);
        problem.setTitle("Business Rule Violation");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(InvalidMachineryStateException.class)
    public ProblemDetail handleInvalidMachineryState(InvalidMachineryStateException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(DEFAULT_TYPE);
        problem.setTitle("Invalid Machinery State Transition");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String traceId = getTraceId();
        log.warn("Database integrity violation: traceId={}, uri={}", traceId, request.getRequestURI());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A database integrity constraint violation or duplicate record conflict occurred."
        );
        problem.setType(DEFAULT_TYPE);
        problem.setTitle("Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", traceId);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ProblemDetail handleOptimisticLocking(Exception ex, HttpServletRequest request) {
        String traceId = getTraceId();
        log.warn("Optimistic lock conflict: traceId={}, uri={}", traceId, request.getRequestURI());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The requested resource was modified by another concurrent transaction. Please reload and retry."
        );
        problem.setType(DEFAULT_TYPE);
        problem.setTitle("Optimistic Concurrency Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", traceId);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                ex.getMessage() != null ? ex.getMessage() : "Request method not supported"
        );
        problem.setType(DEFAULT_TYPE);
        problem.setTitle("Method Not Allowed");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                ex.getStatusCode(),
                ex.getReason() != null ? ex.getReason() : "Request processing error"
        );
        problem.setType(DEFAULT_TYPE);
        problem.setTitle(ex.getStatusCode().toString());
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Validation failed for one or more fields."
        );
        problem.setType(DEFAULT_TYPE);
        problem.setTitle("Validation Failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("invalidParams", errors);
        problem.setProperty("validationErrors", errors);
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        String traceId = getTraceId();
        log.error("Unhandled internal server error: traceId={}, uri={}", traceId, request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred. Please contact support referencing the traceId."
        );
        problem.setType(DEFAULT_TYPE);
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", traceId);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private void enrichProblemDetail(ProblemDetail problem) {
        problem.setProperty("traceId", getTraceId());
        problem.setProperty("timestamp", Instant.now());
    }

    private String getTraceId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }
}