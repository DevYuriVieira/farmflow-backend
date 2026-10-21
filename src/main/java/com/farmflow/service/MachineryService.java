package com.farmflow.service;

import com.farmflow.dto.MachineryDtos.CreateMachineryRequest;
import com.farmflow.dto.MachineryDtos.UpdateMachineryRequest;
import com.farmflow.entity.Machinery;
import com.farmflow.enums.MachineryStatus;
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
            .status(MachineryStatus.OPERATIONAL)
            .nextMaintenanceDate(request.nextMaintenanceDate())
            .fuelLevelPercent(request.fuelLevelPercent())
            .build();

        if ("MAINTENANCE".equalsIgnoreCase(request.status())) {
            machinery.sendToMaintenance("Initial maintenance registration", request.nextMaintenanceDate());
        }

        Machinery saved = machineryRepository.save(machinery);
        dispatchDomainEvents(saved);
        return saved;
    }

    @Transactional
    public Machinery updateMachinery(String id, UpdateMachineryRequest request) {
        Machinery machinery = getMachineryById(id);

        if (request.name() != null || request.model() != null || request.type() != null) {
            machinery.updateDetails(request.name(), request.model(), request.type());
        }
        if (request.operatingHours() != null) {
            machinery.updateOperatingHours(request.operatingHours());
        }
        if (request.fuelLevelPercent() != null) {
            machinery.updateFuelLevel(request.fuelLevelPercent());
        }
        if (request.status() != null) {
            String oldStatus = machinery.getStatus();
            if ("MAINTENANCE".equalsIgnoreCase(request.status()) && !"MAINTENANCE".equalsIgnoreCase(oldStatus)) {
                machinery.sendToMaintenance("Scheduled maintenance update", request.nextMaintenanceDate());
            } else if ("OPERATIONAL".equalsIgnoreCase(request.status()) && "MAINTENANCE".equalsIgnoreCase(oldStatus)) {
                machinery.returnFromMaintenance(request.operatingHours());
            } else if ("INACTIVE".equalsIgnoreCase(request.status()) && !"INACTIVE".equalsIgnoreCase(oldStatus)) {
                machinery.deactivate();
            } else if ("OPERATIONAL".equalsIgnoreCase(request.status()) && "INACTIVE".equalsIgnoreCase(oldStatus)) {
                machinery.activate();
            }
        }
        if (request.nextMaintenanceDate() != null) {
            machinery.scheduleNextMaintenance(request.nextMaintenanceDate());
        }
        machinery.setUpdatedAt(Instant.now());

        Machinery saved = machineryRepository.save(machinery);
        dispatchDomainEvents(saved);
        return saved;
    }

    @Transactional
    public void deleteMachinery(String id) {
        Machinery machinery = getMachineryById(id);
        machineryRepository.delete(machinery);
    }

    private void dispatchDomainEvents(Machinery machinery) {
        List<Object> events = machinery.pullDomainEvents();
        for (Object event : events) {
            if (event instanceof MachineryMaintenanceScheduledEvent maintenanceEvent) {
                outboxService.recordEvent("MachineryMaintenanceScheduled", maintenanceEvent);
                eventPublisher.publishEvent(maintenanceEvent);
            } else if (event instanceof MachineryOperatingHoursUpdatedEvent hoursEvent) {
                outboxService.recordEvent("MachineryOperatingHoursUpdated", hoursEvent);
                eventPublisher.publishEvent(hoursEvent);
            }
        }
    }
}