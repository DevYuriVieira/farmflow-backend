package com.farmflow.config;

import com.farmflow.service.OutboxService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class FarmFlowMetrics {

    private final Counter outboxPublishedCounter;
    private final Counter outboxFailedCounter;
    private final Timer outboxPublishTimer;
    private final MeterRegistry meterRegistry;

    public FarmFlowMetrics(
            MeterRegistry meterRegistry,
            OutboxService outboxService,
            org.springframework.beans.factory.ObjectProvider<com.farmflow.service.AdminDlqService> adminDlqServiceProvider
    ) {
        this.meterRegistry = meterRegistry;

        this.outboxPublishedCounter = Counter.builder("farmflow.outbox.messages.published")
                .description("Total number of outbox messages published to RabbitMQ")
                .register(meterRegistry);

        this.outboxFailedCounter = Counter.builder("farmflow.outbox.messages.failed")
                .description("Total number of outbox messages that failed publishing")
                .register(meterRegistry);

        this.outboxPublishTimer = Timer.builder("farmflow.outbox.publish.duration")
                .description("Latency distribution of publishing messages from outbox to RabbitMQ")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        Gauge.builder("farmflow.outbox.backlog.count", outboxService, OutboxService::getBacklogCount)
                .description("Current number of pending outbox messages")
                .register(meterRegistry);

        Gauge.builder("farmflow.outbox.oldest.message.age.seconds", outboxService, OutboxService::getOldestMessageLagSeconds)
                .description("Age in seconds of the oldest unprocessed outbox message (lag)")
                .register(meterRegistry);

        Gauge.builder("farmflow.outbox.dead.count", outboxService, OutboxService::getDeadLetterCount)
                .description("Number of dead outbox messages that exceeded max attempts")
                .register(meterRegistry);

        adminDlqServiceProvider.ifAvailable(adminDlqService ->
                Gauge.builder("farmflow.rabbitmq.dlq.message.count", adminDlqService, com.farmflow.service.AdminDlqService::getDlqMessageCount)
                        .description("Total number of dead letter messages currently waiting in RabbitMQ DLQ")
                        .register(meterRegistry)
        );
    }

    public void incrementOutboxPublished() {
        outboxPublishedCounter.increment();
    }

    public void incrementOutboxFailed() {
        outboxFailedCounter.increment();
    }

    public void recordOutboxPublishDuration(Duration duration) {
        if (duration != null) {
            outboxPublishTimer.record(duration);
        }
    }

    public void recordOutboxPublishDurationNanos(long nanos) {
        outboxPublishTimer.record(nanos, TimeUnit.NANOSECONDS);
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

    public void recordConsumerDuration(String eventType, Duration duration) {
        if (duration != null) {
            Timer.builder("farmflow.consumer.processing.duration")
                    .tag("event_type", eventType != null ? eventType : "unknown")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry)
                    .record(duration);
        }
    }

    public void recordConsumerDurationNanos(String eventType, long nanos) {
        Timer.builder("farmflow.consumer.processing.duration")
                .tag("event_type", eventType != null ? eventType : "unknown")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    public void incrementSagaTimeout(String sagaName) {
        Counter.builder("farmflow.saga.timeout.total")
                .tag("saga_name", sagaName != null ? sagaName : "unknown")
                .description("Total number of distributed saga timeout events triggering automatic compensation")
                .register(meterRegistry)
                .increment();
    }
}