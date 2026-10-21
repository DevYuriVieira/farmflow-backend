package com.farmflow.repository;

import com.farmflow.entity.OutboxMessage;
import com.farmflow.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, String> {

    @Query(value = """
        SELECT * FROM outbox_messages
        WHERE ((status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
            OR (status = 'PROCESSING' AND next_attempt_at <= :now))
          AND attempts < :maxAttempts
        ORDER BY occurred_on
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxMessage> findPendingForClaim(
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
        SELECT * FROM outbox_messages
        WHERE processed_on IS NULL AND attempts < :maxAttempts
        ORDER BY occurred_on
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxMessage> findUnprocessedForUpdateSkipLocked(
            @Param("maxAttempts") int maxAttempts,
            @Param("batchSize") int batchSize
    );

    long countByProcessedOnIsNull();

    long countByStatus(OutboxStatus status);

    @Query("SELECT MIN(m.occurredOn) FROM OutboxMessage m WHERE m.processedOn IS NULL AND m.status != com.farmflow.enums.OutboxStatus.DEAD")
    Instant findOldestUnprocessedOccurredOn();
}