package com.farmflow.service;

import com.farmflow.entity.PestScoutingRecord;
import com.farmflow.dto.scouting.ScoutingDtos.CreatePestRecordRequest;
import com.farmflow.repository.PestRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScoutingService {

    private final PestRecordRepository pestRecordRepository;

    public List<PestScoutingRecord> getRecordsByFarm(String farmId) {
        return pestRecordRepository.findByFarmIdOrderByDetectedAtDesc(farmId);
    }

    public List<PestScoutingRecord> getRecordsByPlantation(String plantationId) {
        return pestRecordRepository.findByPlantationIdOrderByDetectedAtDesc(plantationId);
    }

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

        return pestRecordRepository.save(record);
    }
}


