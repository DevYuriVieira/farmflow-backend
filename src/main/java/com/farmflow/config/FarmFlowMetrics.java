package com.farmflow.config;

import com.farmflow.service.OutboxService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class FarmFlowMetrics {

    private final Counter outboxPublishedCounter;
    private final Counter outboxFailedCounter;
    private final MeterRegistry meterRegistry;

    public FarmFlowMetrics(MeterRegistry meterRegistry, OutboxService outboxService) {
        this.meterRegistry = meterRegistry;

        this.outboxPublishedCounter = Counter.builder("farmflow.outbox.messages.published")
                .description("Total number of outbox messages published to RabbitMQ")
                .register(meterRegistry);

        this.outboxFailedCounter = Counter.builder("farmflow.outbox.messages.failed")
                .description("Total number of outbox messages that failed publishing")
                .register(meterRegistry);

        Gauge.builder("farmflow.outbox.backlog.count", outboxService, OutboxService::getBacklogCount)
                .description("Current number of pending outbox messages")
                .register(meterRegistry);
    }

    public void incrementOutboxPublished() {
        outboxPublishedCounter.increment();
    }

    public void incrementOutboxFailed() {
        outboxFailedCounter.increment();
    }

    public void incrementConsumerProcessed(String eventType) {
        Counter.builder("farmflow.consumer.messages.processed")
                .tag("event_type", eventType != null ? eventType : "unknown")
                .register(meterRegistry)
                .increment();
    }

    public void incrementConsumerFailed(String eventType) {
        Counter.builder("farmflow.consumer.messages.failed")
                .tag("event_type", eventType != null ? eventType : "unknown")
                .register(meterRegistry)
                .increment();
    }

    public void incrementConsumerDuplicate(String eventType) {
        Counter.builder("farmflow.consumer.messages.duplicate")
                .tag("event_type", eventType != null ? eventType : "unknown")
                .register(meterRegistry)
                .increment();
    }
}