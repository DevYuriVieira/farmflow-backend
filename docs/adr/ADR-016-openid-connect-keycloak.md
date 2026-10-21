# ADR-016: OpenID Connect Resource Server with Keycloak and Asymmetric RS256 Validation

## Status
Accepted

## Context
Symmetric HMAC-SHA256 tokens require the API to hold signing secrets and handle user password management, violating separation of concerns.

## Decision
We architect FarmFlow identity around two complementary modes:
1. **Production Identity Federation (Keycloak 26.1):** Configured as an OAuth 2.0 / OpenID Connect Resource Server. Tokens are signed asymmetrically with RS256, dynamically validated via JWKS, with deterministic role mapping (`Admin`, `FarmManager`, `Agronomist`, `Operator`).
2. **Standalone & Integration Mode (`JwtTokenProvider`):** Employs HMAC-SHA256 with **strict fail-fast secret validation**. If `JWT_SECRET` is missing, blank, or shorter than 256 bits, the application immediately aborts startup (`IllegalStateException`), guaranteeing zero accidental deployments with hardcoded insecure defaults.

## Consequences
- **Positive:** Decoupled identity management and enterprise SSO readiness via Keycloak.
- **Positive:** Immune to insecure default secret leakage; standalone dev/CI fails closed without explicit credentials.
- **Positive:** High performance local cryptographic validation with short token lifetimes.