# ADR-007: RFC 9457 ProblemDetails and Exception Sanitization

## Status
Accepted

## Context
Legacy custom error formats create inconsistent client experiences and risk leaking sensitive infrastructure details (SQL syntax, table names, connection strings).

## Decision
We standardize all HTTP error responses using IETF RFC 9457 `ProblemDetail` via `GlobalExceptionHandler`. Unhandled exceptions are masked with a generic message and correlated by `traceId`. Database conflicts are mapped to HTTP 409 Conflict with sanitized descriptions.

## Consequences
- **Positive:** Strict compliance with RFC 9457 / RFC 7807 standards.
- **Positive:** Zero leakage of database schema, credentials, or internal stack traces to clients.