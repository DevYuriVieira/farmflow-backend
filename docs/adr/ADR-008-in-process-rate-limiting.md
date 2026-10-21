# ADR-008: In-Process Sliding Window Rate Limiting with Anti-IP-Spoofing and RFC 9457

## Status
Accepted

## Context
Denial-of-Service or runaway IoT sensor bursts can exhaust application thread pools and database connections. Fixed-window limiters are vulnerable to 2x boundary bursts, and naive `X-Forwarded-For` inspection allows trivial IP spoofing.

## Decision
We implement a high-performance in-memory sliding-window rate limiter (`RateLimitFilter`):
1. **Sliding-Window Granularity:** Tracks requests across sliding 60-second windows with sub-window buckets, eliminating the 2x burst vulnerability at minute boundaries.
2. **Anti-IP-Spoofing Resolution:** Inspects `X-Forwarded-For` with trusted proxy validation, falling back safely to `request.getRemoteAddr()` to prevent client header forgery.
3. **HTTP 429 RFC 9457 Compliance:** Excess requests receive HTTP 429 with RFC 9457 `application/problem+json` payload.
4. **Standard Quota Headers:** Includes `Retry-After: <seconds>`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset`.

## Consequences
- **Positive:** Immune to boundary burst gaming; rate limit is strictly smoothed over time.
- **Positive:** Protected against IP spoofing over untrusted headers.
- **Positive:** Conforms to RFC 6585 and RFC 9457 specifications.