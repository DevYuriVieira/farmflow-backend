package com.farmflow.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class TelemetryPartitionMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPartitionMaintenanceService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${farmflow.partition.retention-months:36}")
    private int retentionMonths = 36;

    @Value("${farmflow.partition.purge-months:48}")
    private int purgeMonths = 48;

    @Scheduled(cron = "${farmflow.partition.maintenance.cron:0 0 2 1 * *}")
    @Transactional
    public void runFullPartitionLifecycleMaintenance() {
        ensureFuturePartitions();
        detachExpiredPartitions(retentionMonths);
        purgeHistoricalPartitions(purgeMonths);
        inspectDefaultPartitionAnomalies();
    }

    public void ensureFuturePartitions() {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        for (int i = 0; i <= 3; i++) {
            YearMonth target = current.plusMonths(i);
            createPartitionIfNotExists(target);
        }
    }

    public void createPartitionIfNotExists(YearMonth target) {
        String tableName = "telemetry_readings_" + target.format(DateTimeFormatter.ofPattern("yyyy_MM"));
        String startTimestamp = target.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC).toString();
        String endTimestamp = target.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC).toString();

        String ddl = "SELECT create_telemetry_partition(:name, CAST(:from AS timestamptz), CAST(:to AS timestamptz))";

        try {
            entityManager.createNativeQuery(ddl)
                    .setParameter("name", tableName)
                    .setParameter("from", startTimestamp)
                    .setParameter("to", endTimestamp)
                    .getSingleResult();
            log.info("Ensured telemetry range partition via DDL: {} [{} to {}]", tableName, startTimestamp, endTimestamp);
        } catch (Exception e) {
            log.trace("Partition creation skipped or table already exists/unsupported dialect: {}", e.getMessage());
        }
    }

    public boolean detachPartition(YearMonth target) {
        String tableName = "telemetry_readings_" + target.format(DateTimeFormatter.ofPattern("yyyy_MM"));
        String ddl = "SELECT detach_telemetry_partition(:name)";

        try {
            Boolean result = (Boolean) entityManager.createNativeQuery(ddl)
                    .setParameter("name", tableName)
                    .getSingleResult();
            if (Boolean.TRUE.equals(result)) {
                log.info("Successfully detached expired telemetry partition: {}", tableName);
                return true;
            }
        } catch (Exception e) {
            log.trace("Partition detach skipped or unsupported dialect: {}", e.getMessage());
        }
        return false;
    }

    public void detachExpiredPartitions(int months) {
        YearMonth oldestAllowed = YearMonth.now(ZoneOffset.UTC).minusMonths(months);
        for (int i = 1; i <= 12; i++) {
            YearMonth candidate = oldestAllowed.minusMonths(i);
            detachPartition(candidate);
        }
    }

    public boolean archiveAndPurgePartition(YearMonth target) {
        String tableName = "telemetry_readings_" + target.format(DateTimeFormatter.ofPattern("yyyy_MM"));
        String ddl = "SELECT archive_and_drop_telemetry_partition(:name)";

        try {
            Boolean result = (Boolean) entityManager.createNativeQuery(ddl)
                    .setParameter("name", tableName)
                    .getSingleResult();
            if (Boolean.TRUE.equals(result)) {
                log.info("Successfully archived and purged historical telemetry partition: {}", tableName);
                return true;
            }
        } catch (Exception e) {
            log.trace("Partition archival/purging skipped or unsupported dialect: {}", e.getMessage());
        }
        return false;
    }

    public void purgeHistoricalPartitions(int months) {
        YearMonth oldestPurgeAllowed = YearMonth.now(ZoneOffset.UTC).minusMonths(months);
        for (int i = 1; i <= 12; i++) {
            YearMonth candidate = oldestPurgeAllowed.minusMonths(i);
            archiveAndPurgePartition(candidate);
        }
    }

    public long inspectDefaultPartitionAnomalies() {
        try {
            Number count = (Number) entityManager.createNativeQuery(
                    "SELECT get_telemetry_default_partition_row_count()")
                    .getSingleResult();
            long rowCount = count != null ? count.longValue() : 0L;
            if (rowCount > 0) {
                log.warn("CRITICAL ANOMALY: telemetry_readings_default contains {} rows! Sensor readings with out-of-range timestamps detected.", rowCount);
            }
            return rowCount;
        } catch (Exception e) {
            log.trace("Default partition anomaly inspection skipped or unsupported dialect: {}", e.getMessage());
            return 0L;
        }
    }
}
