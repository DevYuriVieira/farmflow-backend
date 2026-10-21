# ADR-016: OpenID Connect Resource Server with Keycloak and Asymmetric RS256 Validation

## Status
Accepted

## Context
Symmetric HMAC-SHA256 tokens require the API to hold signing secrets and handle user password management, violating separation of concerns.

## Decision
We architect FarmFlow as an OAuth 2.0 / OpenID Connect Resource Server federated with Keycloak 26.1:
- Tokens are signed asymmetrically with RS256.
- Dynamic JWKS discovery allows seamless key rotation without API downtime.
- Role-based access control (Admin, FarmManager, Agronomist, Operator) is mapped deterministically from client claims.
- The `keycloak/realm-export.json` provides synthetic demonstration accounts for automated testing and CI.

## Consequences
- **Positive:** Decoupled identity management and enterprise SSO readiness.
- **Positive:** Zero secret leakage; API only validates public keys via JWKS.