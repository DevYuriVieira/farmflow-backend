package com.farmflow.repository;

import com.farmflow.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, String> {

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
}