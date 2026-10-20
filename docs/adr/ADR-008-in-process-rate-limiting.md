# ADR-008: In-Process Rate Limiting with Retry-After Header

## Status
Accepted

## Context
Denial-of-Service or runaway IoT sensor bursts can exhaust application thread pools and database connections.

## Decision
We implement a local, thread-safe fixed-window rate limiter (`RateLimitFilter`) allowing 60 requests per minute per IP. Excess requests receive HTTP 429 Too Many Requests, an RFC 9457 `ProblemDetail` payload, and a standard `Retry-After: 60` HTTP header.

## Consequences
- **Positive:** Node-level protection against traffic bursts and resource starvation.
- **Positive:** Conforms to RFC 6585 and RFC 9457 standards.