# ADR-018: Range Partitioning and BRIN Indexing for High-Throughput Telemetry Ingestion

## Status
Accepted

## Context
High-frequency agro-industrial IoT sensors (soil moisture, temperature, silo fill levels, machinery vibration) generate millions of readings per season. Storing time-series readings in a monolithic B-Tree indexed table results in exponential index bloat, buffer pool thrashing, degraded insertion throughput, and prohibitive operational costs when archiving historical records via `DELETE` statements (causing vacuum degradation and lock contention).

## Decision
We implement declarative PostgreSQL range partitioning by `recorded_at` combined with Block Range Indexes (BRIN):
1. **Range Partitioning by Time Window:** The primary table `telemetry_readings` is partitioned by range on `(recorded_at)` with a composite primary key `(id, recorded_at)`.
2. **Monthly Granularity:** Automated monthly partitions cover active operational cycles, supplemented by a `telemetry_readings_default` catch-all partition to ensure fail-safe insertion of unexpected out-of-band timestamps.
3. **Partition Maintenance Automation:** `TelemetryPartitionMaintenanceService` executes a scheduled job ensuring partitions for `D+3` months are pre-created.
4. **BRIN Indexing:** Replaces bulky B-Tree indexes on `recorded_at` with a BRIN index (`idx_telemetry_recorded_at_brin`). Because time-series telemetry is naturally append-mostly and physically ordered on disk, BRIN consumes less than 1% of the RAM of a traditional B-Tree index while offering equivalent range query acceleration.
5. **Observability Gate:** `FarmTelemetryHealthIndicator` proactively validates the existence of upcoming monthly partitions in PostgreSQL, reporting health degradation if partition creation fails before month rollover.

## Consequences
- **Positive:** Partition pruning allows query plans (`EXPLAIN (ANALYZE, COSTS OFF)`) to scan only the active month's partition during operational dashboard queries.
- **Positive:** Data retention and lifecycle archiving can be executed via instantaneous, zero-lock `ALTER TABLE DETACH PARTITION` operations instead of costly `DELETE` sweeps.
- **Positive:** Massive memory savings: BRIN index pages occupy minimal buffer cache, leaving RAM available for active transactional working sets.
- **Negative:** Primary key and foreign key constraints on partitioned tables must include the partition key `(recorded_at)`.
