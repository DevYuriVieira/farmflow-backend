package com.farmflow.service;

import com.farmflow.dto.ScoutingDtos.CreatePestRecordRequest;
import com.farmflow.entity.PestScoutingRecord;
import com.farmflow.event.PestDetectedEvent;
import com.farmflow.repository.PestRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScoutingService {

    private final PestRecordRepository pestRecordRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxService outboxService;

    public List<PestScoutingRecord> getRecordsByFarm(String farmId) {
        return pestRecordRepository.findByFarmIdOrderByDetectedAtDesc(farmId);
    }

    public List<PestScoutingRecord> getRecordsByPlantation(String plantationId) {
        return pestRecordRepository.findByPlantationIdOrderByDetectedAtDesc(plantationId);
    }

    @Transactional
    public PestScoutingRecord recordPest(CreatePestRecordRequest request) {
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