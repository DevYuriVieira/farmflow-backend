package com.farmflow.service;

import com.farmflow.dto.MachineryDtos.CreateMachineryRequest;
import com.farmflow.dto.MachineryDtos.UpdateMachineryRequest;
import com.farmflow.entity.Machinery;
import com.farmflow.event.MachineryMaintenanceScheduledEvent;
import com.farmflow.event.MachineryOperatingHoursUpdatedEvent;
import com.farmflow.exceptions.ResourceNotFoundException;
import com.farmflow.repository.MachineryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MachineryService {

    private final MachineryRepository machineryRepository;
    private final OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;

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

    @Transactional
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

        Machinery saved = machineryRepository.save(machinery);
        if ("MAINTENANCE".equalsIgnoreCase(saved.getStatus())) {
            MachineryMaintenanceScheduledEvent event = new MachineryMaintenanceScheduledEvent(
                    saved,
                    "Initial maintenance registration",
                    saved.getNextMaintenanceDate()
            );
            outboxService.recordEvent("MachineryMaintenanceScheduled", event);
            eventPublisher.publishEvent(event);
        }
        return saved;
    }

    @Transactional
    public Machinery updateMachinery(String id, UpdateMachineryRequest request) {
        Machinery machinery = getMachineryById(id);

        if (request.name() != null) {
            machinery.setName(request.name());
        }
        if (request.model() != null) {
            machinery.setModel(request.model());
        }
        if (request.type() != null) {
            machinery.setType(request.type());
        }
        if (request.operatingHours() != null) {
            Double previousHours = machinery.getOperatingHours();
            machinery.updateOperatingHours(request.operatingHours());
            MachineryOperatingHoursUpdatedEvent hoursEvent = new MachineryOperatingHoursUpdatedEvent(
                    machinery,
                    previousHours,
                    request.operatingHours()
            );
            outboxService.recordEvent("MachineryOperatingHoursUpdated", hoursEvent);
            eventPublisher.publishEvent(hoursEvent);
        }
        if (request.fuelLevelPercent() != null) {
            machinery.updateFuelLevel(request.fuelLevelPercent());
        }
        if (request.status() != null) {
            String oldStatus = machinery.getStatus();
            if ("MAINTENANCE".equalsIgnoreCase(request.status()) && !"MAINTENANCE".equalsIgnoreCase(oldStatus)) {
                machinery.sendToMaintenance("Scheduled maintenance update", request.nextMaintenanceDate());
                MachineryMaintenanceScheduledEvent maintenanceEvent = new MachineryMaintenanceScheduledEvent(
                        machinery,
                        "Scheduled maintenance update",
                        machinery.getNextMaintenanceDate()
                );
                outboxService.recordEvent("MachineryMaintenanceScheduled", maintenanceEvent);
                eventPublisher.publishEvent(maintenanceEvent);
            } else if ("OPERATIONAL".equalsIgnoreCase(request.status()) && "MAINTENANCE".equalsIgnoreCase(oldStatus)) {
                machinery.returnFromMaintenance(request.operatingHours());
            } else {
                machinery.setStatus(request.status());
            }
        }
        if (request.nextMaintenanceDate() != null) {
            machinery.setNextMaintenanceDate(request.nextMaintenanceDate());
        }
        machinery.setUpdatedAt(Instant.now());

        return machineryRepository.save(machinery);
    }

    @Transactional
    public void deleteMachinery(String id) {
        Machinery machinery = getMachineryById(id);
        machineryRepository.delete(machinery);
    }
}