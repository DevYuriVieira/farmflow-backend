package com.farmflow.config;

import com.farmflow.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FarmTelemetryHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(FarmTelemetryHealthIndicator.class);
    private static final DateTimeFormatter PARTITION_SUFFIX_FORMAT = DateTimeFormatter.ofPattern("uuuu_MM");

    private final TelemetryRepository telemetryRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Health health() {
        try {
            long count = telemetryRepository.count();
            Health.Builder builder = Health.up()
                    .withDetail("totalTelemetryReadings", count)
                    .withDetail("telemetryStatus", "ACTIVE");

            boolean isPg = isPostgreSql();
            if (isPg) {
                YearMonth current = YearMonth.now();
                String currentMonthPartition = "telemetry_readings_" + current.format(PARTITION_SUFFIX_FORMAT);
                String nextMonthPartition = "telemetry_readings_" + current.plusMonths(1).format(PARTITION_SUFFIX_FORMAT);
                String monthAfterNextPartition = "telemetry_readings_" + current.plusMonths(2).format(PARTITION_SUFFIX_FORMAT);

                List<String> targetPartitions = List.of(currentMonthPartition, nextMonthPartition, monthAfterNextPartition);
                Integer foundCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN (?, ?, ?)",
                        Integer.class,
                        currentMonthPartition, nextMonthPartition, monthAfterNextPartition
                );

                builder.withDetail("verifiedPartitions", targetPartitions);
                if (foundCount != null && foundCount >= 2) {
                    builder.withDetail("partitionStatus", "OPTIMAL")
                           .withDetail("activePartitionsCount", foundCount);
                } else {
                    return Health.status("DEGRADED")
                            .withDetail("error", "Missing upcoming monthly telemetry partitions in PostgreSQL")
                            .withDetail("targetPartitions", targetPartitions)
                            .withDetail("foundCount", foundCount)
                            .build();
                }
            } else {
                builder.withDetail("partitionVerification", "SKIPPED_ON_H2");
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Telemetry healthcheck failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private boolean isPostgreSql() {
        try {
            String dbProduct = jdbcTemplate.execute((java.sql.Connection conn) ->
                    conn.getMetaData().getDatabaseProductName()
            );
            return dbProduct != null && dbProduct.toLowerCase().contains("postgres");
        } catch (Exception e) {
            return false;
        }
    }
}

