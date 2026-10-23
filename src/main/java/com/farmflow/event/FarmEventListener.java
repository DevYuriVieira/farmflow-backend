package com.farmflow.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class FarmEventListener {

    @Async("farmTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPestDetected(PestDetectedEvent event) {
        var record = event.record();
        log.warn("Pest detected with severity {}: pest {} on plantation {}", record.getSeverity(), record.getPestName(), record.getPlantationId());
    }

    @Async("farmTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTelemetryAlert(TelemetryAlertEvent event) {
        log.warn("Telemetry Alert on Farm {} Sensor {}: {} [{}]",
                event.reading().getFarmId(),
                event.reading().getSensorId(),
                event.alertReason(),
                event.severity());
    }

    @Async("farmTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityCompleted(ActivityCompletedEvent event) {
        var activity = event.activity();
        log.info("Activity completed: id={}, title={}, plantation={}",
                activity.getId(),
                activity.getTitle(),
                activity.getPlantationId());
    }
}
