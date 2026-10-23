package com.farmflow.service;

import com.farmflow.dto.PageResponse;
import com.farmflow.dto.PlantationDtos.*;
import com.farmflow.entity.Plantation;
import com.farmflow.enums.PlantationStatus;
import com.farmflow.repository.PlantationRepository;
import com.farmflow.specification.PlantationSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlantationService {

    private final PlantationRepository plantationRepository;
    private final com.farmflow.config.FarmSecurityEvaluator farmSecurity;

    public List<Plantation> getPlantations(String farmId, String status) {
        String effectiveFarmId = farmId;
        if (!farmSecurity.isAdmin() && effectiveFarmId == null) {
            effectiveFarmId = farmSecurity.getCurrentUserFarmId();
        }
        if (effectiveFarmId != null && status != null && !status.isBlank()) {
            return plantationRepository.findByFarmIdAndStatus(effectiveFarmId, PlantationStatus.fromString(status));
        } else if (effectiveFarmId != null) {
            return plantationRepository.findByFarmId(effectiveFarmId);
        }
        if (farmSecurity.isAdmin()) {
            return plantationRepository.findAll();
        }
        return List.of();
    }

    public Plantation getPlantationById(String id) {
        Plantation plantation = plantationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talhão não encontrado"));
        farmSecurity.validateTenantAccess(plantation.getFarmId());
        return plantation;
    }

    public Plantation createPlantation(CreatePlantationRequest request) {
        farmSecurity.validateTenantAccess(request.farmId());
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

        plantation.updateDetails(
            request.name(),
            request.crop(),
            request.variety(),
            request.areaHectares(),
            request.plantingDate(),
            request.harvestEstimateDate(),
            request.expectedYieldTons(),
            request.notes()
        );

        if (request.status() != null && !request.status().isBlank()) {
            plantation.transitionStatus(request.status());
        }

        return plantationRepository.save(plantation);
    }

    public PageResponse<Plantation> getPlantationsPaged(String farmId, String status, String crop, Pageable pageable) {
        Specification<Plantation> spec = Specification.allOf(
            PlantationSpecification.hasFarmId(farmId),
            PlantationSpecification.hasStatus(status),
            PlantationSpecification.hasCrop(crop)
        );
        return PageResponse.from(plantationRepository.findAll(spec, pageable));
    }

    public void deletePlantation(String id) {
        Plantation plantation = getPlantationById(id);
        plantationRepository.delete(plantation);
    }
}


