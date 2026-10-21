# ADR-015: Zero-Comment Code Discipline and Clean Code Policy

## Status
Accepted

## Context
Outdated comments are worse than no comments; they drift from code reality and create false assumptions during incident investigations.

## Decision
We enforce strict **zero-comment discipline** across all production and test Java code (`//` and `/* */` prohibited). Code expressiveness must be achieved through self-documenting method names, strong domain typing, immutable records, and external architectural documentation (ADRs and Runbooks).

## Consequences
- **Positive:** Eliminates obsolete, misleading, or drifting comments.
- **Positive:** Forces developers to write clean, intention-revealing identifiers.