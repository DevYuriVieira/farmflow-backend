package com.farmflow.dto;

import java.time.Instant;
import java.util.List;

public class AdminDtos {

    public record DlqReplayRequest(
            Integer maxMessages,
            String targetQueue
    ) {}

    public record DlqReplayResponse(
            int replayedCount,
            List<String> replayedMessageIds,
            Instant executedAt,
            String status
    ) {}
}
