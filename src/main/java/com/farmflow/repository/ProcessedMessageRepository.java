package com.farmflow.repository;

import com.farmflow.entity.ProcessedMessage;
import com.farmflow.entity.ProcessedMessageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, ProcessedMessageId> {

    @Modifying
    @Query(value = """
        INSERT INTO processed_messages (consumer_name, message_id, event_type, processed_at)
        VALUES (:consumerName, :messageId, :eventType, :processedAt)
        ON CONFLICT (consumer_name, message_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfNew(
            @Param("consumerName") String consumerName,
            @Param("messageId") String messageId,
            @Param("eventType") String eventType,
            @Param("processedAt") Instant processedAt
    );

    @Modifying
    @Query("DELETE FROM ProcessedMessage p WHERE p.processedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}