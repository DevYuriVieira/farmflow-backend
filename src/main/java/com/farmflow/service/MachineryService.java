package com.farmflow.service;

import com.farmflow.exceptions.ResourceNotFoundException;
import com.farmflow.entity.Machinery;
import com.farmflow.dto.machinery.MachineryDtos.*;
import com.farmflow.repository.MachineryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MachineryService {

    private final MachineryRepository machineryRepository;

    public List<Machinery> getMachineryByFarm(String farmId, String status) {
        if (status != null && !status.isBlank()) {
            return machineryRepository.findByFarmIdAndStatus(farmId, status);
        }
        return machineryRepository.findByFarmId(farmId);
    }

    public Machinery getMachineryById(String id) {
        return machineryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Máquina/equipamento não encontrado"));
    }

    public Machinery createMachinery(CreateMachineryRequest request) {
        Machinery machinery = Machinery.builder()
            .farmId(request.farmId())
            .name(request.name())
            .model(request.model())
            .type(request.type())
            .operatingHours(request.operatingHours() != null ? request.operatingHours() : 0.0)
            .status(request.status() != null ? request.status() : "OPERATIONAL")
            .nextMaintenanceDate(request.nextMaintenanceDate())
            .fuelLevelPercent(request.fuelLevelPercent())
            .build();

        return machineryRepository.save(machinery);
    }

    public Machinery updateMachinery(String id, UpdateMachineryRequest request) {
        Machinery machinery = getMachineryById(id);

        if (request.name() != null) machinery.setName(request.name());
        if (request.model() != null) machinery.setModel(request.model());
        if (request.type() != null) machinery.setType(request.type());
        if (request.operatingHours() != null) machinery.setOperatingHours(request.operatingHours());
        if (request.status() != null) machinery.setStatus(request.status());
        if (request.nextMaintenanceDate() != null) machinery.setNextMaintenanceDate(request.nextMaintenanceDate());
        if (request.fuelLevelPercent() != null) machinery.setFuelLevelPercent(request.fuelLevelPercent());
        machinery.setUpdatedAt(Instant.now());

        return machineryRepository.save(machinery);
    }

    public void deleteMachinery(String id) {
        Machinery machinery = getMachineryById(id);
        machineryRepository.delete(machinery);
    }
}


