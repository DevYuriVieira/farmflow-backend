package com.farmflow.service;

import com.farmflow.config.FarmSecurityEvaluator;
import com.farmflow.dto.ScoutingDtos.CreatePestRecordRequest;
import com.farmflow.entity.PestScoutingRecord;
import com.farmflow.entity.Plantation;
import com.farmflow.event.PestDetectedEvent;
import com.farmflow.exceptions.ResourceNotFoundException;
import com.farmflow.repository.PestRecordRepository;
import com.farmflow.repository.PlantationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        eventPublisher.publishEvent(event);
        return saved;
    }
}