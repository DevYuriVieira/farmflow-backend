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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE_URN = "urn:problem-type:farmflow:";

    private static URI problemType(String slug) {
        return URI.create(PROBLEM_BASE_URN + slug);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(problemType("not-found"));
        problem.setTitle("Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(problemType("business-rule-violation"));
        problem.setTitle("Business Rule Violation");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ProblemDetail handleDateTimeParseException(DateTimeParseException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid date format: '" + ex.getParsedString() + "'. Expected format is YYYY-MM-DD (ISO-8601)."
        );
        problem.setType(problemType("invalid-date-format"));
        problem.setTitle("Invalid Date Format");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String detail = "Malformed JSON request body or unparseable field format.";
        if (ex.getCause() instanceof DateTimeParseException parseEx) {
            detail = "Invalid date format: '" + parseEx.getParsedString() + "'. Expected format is YYYY-MM-DD (ISO-8601).";
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(problemType("malformed-request"));
        problem.setTitle("Malformed Request Body");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(InvalidMachineryStateException.class)
    public ProblemDetail handleInvalidMachineryState(InvalidMachineryStateException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(problemType("invalid-machinery-state"));
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
        problem.setType(problemType("conflict"));
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
        problem.setType(problemType("optimistic-concurrency-conflict"));
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
        problem.setType(problemType("method-not-allowed"));
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
        problem.setType(problemType("request-error"));
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
        problem.setType(problemType("validation-failed"));
        problem.setTitle("Validation Failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("invalidParams", errors);
        problem.setProperty("validationErrors", errors);
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Access denied: insufficient permissions to access this resource."
        );
        problem.setType(problemType("forbidden"));
        problem.setTitle("Forbidden");
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichProblemDetail(problem);
        return problem;
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ProblemDetail handleAuthentication(org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Authentication required or credentials invalid."
        );
        problem.setType(problemType("unauthorized"));
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));
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
        problem.setType(problemType("internal-error"));
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