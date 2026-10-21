# ADR-012: Empirical Performance Baseline via k6

## Status
Accepted

## Context
Production readiness requires automated, reproducible load testing scripts that simulate real-world traffic profiles rather than optimistic guesses.

## Decision
We create a standardized k6 benchmark suite under `load-tests/`:
- `health-benchmark.js`: Diagnostic in-memory throughput testing.
- `telemetry-ingestion-benchmark.js`: Authenticated transactional write throughput testing.
- `run-benchmarks.ps1`: Automated PowerShell execution runner with tabular reporting.

## Consequences
- **Positive:** Verifiable latency degradation knee and throughput saturation boundaries before deploying to production.
- **Positive:** Regression testing across major releases.