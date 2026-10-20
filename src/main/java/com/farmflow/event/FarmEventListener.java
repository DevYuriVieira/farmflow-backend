package com.farmflow.event;

import com.farmflow.entity.FarmActivity;
import com.farmflow.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class FarmEventListener {

    private final ActivityRepository activityRepository;

    @Async("farmTaskExecutor")
    @EventListener
    public void onPestDetected(PestDetectedEvent event) {
        var record = event.record();
        if ("CRITICAL".equalsIgnoreCase(record.getSeverity()) || "HIGH".equalsIgnoreCase(record.getSeverity())) {
            FarmActivity urgentTask = FarmActivity.builder()
                    .farmId(record.getFarmId())
                    .plantationId(record.getPlantationId())
                    .title("Urgent Treatment: " + record.getPestName())
                    .type("scouting")
                    .priority("high")
                    .date(LocalDate.now().plusDays(1).toString())
                    .notes("Generated from pest detection: " + record.getPestName())
                    .completed(false)
                    .build();
            activityRepository.save(urgentTask);
            log.warn("Created urgent treatment task for pest {} on plantation {}", record.getPestName(), record.getPlantationId());
        }
    }

    @Async("farmTaskExecutor")
    @EventListener
    public void onTelemetryAlert(TelemetryAlertEvent event) {
        log.warn("Telemetry Alert on Farm {} Sensor {}: {} [{}]",
                event.reading().getFarmId(),
                event.reading().getSensorId(),
                event.alertReason(),
                event.severity());
    }

    @Async("farmTaskExecutor")
    @EventListener
    public void onActivityCompleted(ActivityCompletedEvent event) {
        var activity = event.activity();
        log.info("Activity completed: id={}, title={}, plantation={}",
                activity.getId(),
                activity.getTitle(),
                activity.getPlantationId());
    }
}
