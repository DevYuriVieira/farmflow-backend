package com.farmflow.service;

import com.farmflow.domain.Plantation;
import com.farmflow.dto.PlantationDtos.*;
import com.farmflow.repository.PlantationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlantationService {

    private final PlantationRepository plantationRepository;

    public List<Plantation> getPlantations(String farmId, String status) {
        if (farmId != null && status != null) {
            return plantationRepository.findByFarmIdAndStatus(farmId, status);
        } else if (farmId != null) {
            return plantationRepository.findByFarmId(farmId);
        }
        return plantationRepository.findAll();
    }

    public Plantation getPlantationById(String id) {
        return plantationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talhão não encontrado"));
    }

    public Plantation createPlantation(CreatePlantationRequest request) {
        Plantation plantation = Plantation.builder()
            .farmId(request.farmId())
            .name(request.name())
            .crop(request.crop())
            .variety(request.variety())
            .areaHectares(request.areaHectares())
            .status(request.status() != null ? request.status() : "planted")
            .plantingDate(request.plantingDate())
            .harvestEstimateDate(request.harvestEstimateDate())
            .expectedYieldTons(request.expectedYieldTons())
            .notes(request.notes())
            .build();

        return plantationRepository.save(plantation);
    }

    public Plantation updatePlantation(String id, UpdatePlantationRequest request) {
        Plantation plantation = getPlantationById(id);

        if (request.name() != null) plantation.setName(request.name());
        if (request.crop() != null) plantation.setCrop(request.crop());
        if (request.variety() != null) plantation.setVariety(request.variety());
        if (request.areaHectares() != null) plantation.setAreaHectares(request.areaHectares());
        if (request.status() != null) plantation.setStatus(request.status());
        if (request.plantingDate() != null) plantation.setPlantingDate(request.plantingDate());
        if (request.harvestEstimateDate() != null) plantation.setHarvestEstimateDate(request.harvestEstimateDate());
        if (request.expectedYieldTons() != null) plantation.setExpectedYieldTons(request.expectedYieldTons());
        if (request.notes() != null) plantation.setNotes(request.notes());

        return plantationRepository.save(plantation);
    }

    public void deletePlantation(String id) {
        Plantation plantation = getPlantationById(id);
        plantationRepository.delete(plantation);
    }
}
