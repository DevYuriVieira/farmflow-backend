package com.farmflow.service;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.FarmSecurityEvaluator;
import com.farmflow.dto.ScoutingDtos.CreatePestRecordRequest;
import com.farmflow.entity.PestScoutingRecord;
import com.farmflow.entity.Plantation;
import com.farmflow.event.PestDetectedEvent;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.event.cloudevents.PestAlertRaisedData;
import com.farmflow.exceptions.ResourceNotFoundException;
import com.farmflow.repository.PestRecordRepository;
import com.farmflow.repository.PlantationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScoutingService {

    private final PestRecordRepository pestRecordRepository;
    private final PlantationRepository plantationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxService outboxService;
    private final FarmSecurityEvaluator farmSecurity;

    public List<PestScoutingRecord> getRecordsByFarm(String farmId) {
        farmSecurity.validateTenantAccess(farmId);
        return pestRecordRepository.findByFarmIdOrderByDetectedAtDesc(farmId);
    }

    public List<PestScoutingRecord> getRecordsByPlantation(String plantationId) {
        Plantation plantation = plantationRepository.findById(plantationId)
                .orElseThrow(() -> new ResourceNotFoundException("Plantation not found with id: " + plantationId));
        farmSecurity.validateTenantAccess(plantation.getFarmId());
        return pestRecordRepository.findByPlantationIdOrderByDetectedAtDesc(plantationId);
    }

    @Transactional
    public PestScoutingRecord recordPest(CreatePestRecordRequest request) {
        farmSecurity.validateTenantAccess(request.farmId());
        PestScoutingRecord record = PestScoutingRecord.builder()
                .farmId(request.farmId())
                .plantationId(request.plantationId())
                .pestName(request.pestName())
                .severity(request.severity())
                .confidenceScore(request.confidenceScore())
                .photoUrl(request.photoUrl())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .recommendedAction(request.recommendedAction())
                .build();

        PestScoutingRecord saved = pestRecordRepository.save(record);
        PestDetectedEvent event = new PestDetectedEvent(saved);
        outboxService.recordEvent("PestDetected", event);
        recordPestAlertCloudEvent(saved);
        eventPublisher.publishEvent(event);
        return saved;
    }

    private void recordPestAlertCloudEvent(PestScoutingRecord record) {
        String severityUpper = record.getSeverity() != null ? record.getSeverity().trim().toUpperCase() : "MEDIUM";
        if (!Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(severityUpper)) {
            severityUpper = "MEDIUM";
        }

        PestAlertRaisedData data = new PestAlertRaisedData(
                record.getId() != null ? record.getId() : UUID.randomUUID().toString(),
                record.getFarmId(),
                record.getPlantationId(),
                record.getPestName(),
                severityUpper,
                record.getRecommendedAction() != null && !record.getRecommendedAction().isBlank()
                        ? record.getRecommendedAction()
                        : "Field inspection recommended",
                record.getDetectedAt() != null ? record.getDetectedAt() : Instant.now()
        );

        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        CloudEvent<PestAlertRaisedData> cloudEvent = CloudEvent.of(
                "https://farmflow.com/scouting-service",
                "com.farmflow.pest.alert.raised.v1",
                record.getFarmId(),
                correlationId,
                data
        );
        outboxService.recordCloudEvent(cloudEvent);
    }
}