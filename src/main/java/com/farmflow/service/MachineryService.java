package com.farmflow.service;

import com.farmflow.dto.MachineryDtos.*;
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
@Transactional(readOnly = true)
public class MachineryService {

    private final MachineryRepository machineryRepository;
    private final OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.farmflow.config.FarmSecurityEvaluator farmSecurity;

    public List<Machinery> getAllMachinery() {
        if (farmSecurity.isAdmin()) {
            return machineryRepository.findAll();
        }
        String farmId = farmSecurity.getCurrentUserFarmId();
        if (farmId == null) {
            return List.of();
        }
        return machineryRepository.findByFarmId(farmId);
    }

    public List<Machinery> getMachineryByFarm(String farmId, String status) {
        farmSecurity.validateTenantAccess(farmId);
        if (status != null && !status.isBlank()) {
            return machineryRepository.findByFarmIdAndStatus(farmId, status);
        }
        return machineryRepository.findByFarmId(farmId);
    }

    public Machinery getMachineryById(String id) {
        Machinery machinery = machineryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Máquina/equipamento não encontrado"));
        farmSecurity.validateTenantAccess(machinery.getFarmId());
        return machinery;
    }

    @Transactional
    public Machinery createMachinery(CreateMachineryRequest request) {
        farmSecurity.validateTenantAccess(request.farmId());
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

        Machinery saved = machineryRepository.save(machinery);
        dispatchDomainEvents(saved);
        return saved;
    }

    @Transactional
    public void deleteMachinery(String id) {
        Machinery machinery = getMachineryById(id);
        machineryRepository.delete(machinery);
    }

    @Transactional
    public Machinery sendToMaintenance(String id, SendToMaintenanceRequest request) {
        Machinery machinery = getMachineryById(id);
        machinery.sendToMaintenance(
            request != null ? request.reason() : null,
            request != null ? request.expectedDate() : null
        );
        Machinery saved = machineryRepository.save(machinery);
        dispatchDomainEvents(saved);
        return saved;
    }

    @Transactional
    public Machinery returnFromMaintenance(String id, ReturnFromMaintenanceRequest request) {
        Machinery machinery = getMachineryById(id);
        machinery.returnFromMaintenance(request != null ? request.newOperatingHours() : null);
        Machinery saved = machineryRepository.save(machinery);
        dispatchDomainEvents(saved);
        return saved;
    }

    @Transactional
    public Machinery updateOperatingHours(String id, UpdateOperatingHoursRequest request) {
        Machinery machinery = getMachineryById(id);
        if (request != null && request.operatingHours() != null) {
            machinery.updateOperatingHours(request.operatingHours());
            Machinery saved = machineryRepository.save(machinery);
            dispatchDomainEvents(saved);
            return saved;
        }
        return machinery;
    }

    @Transactional
    public Machinery deactivate(String id) {
        Machinery machinery = getMachineryById(id);
        machinery.deactivate();
        Machinery saved = machineryRepository.save(machinery);
        dispatchDomainEvents(saved);
        return saved;
    }

    @Transactional
    public Machinery activate(String id) {
        Machinery machinery = getMachineryById(id);
        machinery.activate();
        Machinery saved = machineryRepository.save(machinery);
        dispatchDomainEvents(saved);
        return saved;
    }

    private void dispatchDomainEvents(Machinery machinery) {
        List<Object> events = machinery.pullDomainEvents();
        for (Object event : events) {
            if (event instanceof MachineryMaintenanceScheduledEvent maintenanceEvent) {
                outboxService.recordEvent("MachineryMaintenanceScheduled", maintenanceEvent);
                recordMaintenanceCloudEvent(maintenanceEvent);
                eventPublisher.publishEvent(maintenanceEvent);
            } else if (event instanceof MachineryOperatingHoursUpdatedEvent hoursEvent) {
                outboxService.recordEvent("MachineryOperatingHoursUpdated", hoursEvent);
                recordHoursUpdatedCloudEvent(hoursEvent);
                eventPublisher.publishEvent(hoursEvent);
            }
        }
    }

    private void recordMaintenanceCloudEvent(MachineryMaintenanceScheduledEvent event) {
        Machinery machinery = event.machinery();
        String correlationId = org.slf4j.MDC.get(com.farmflow.config.CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }

        com.farmflow.event.cloudevents.MachineryMaintenanceScheduledData data =
                new com.farmflow.event.cloudevents.MachineryMaintenanceScheduledData(
                        machinery.getId() != null ? machinery.getId() : java.util.UUID.randomUUID().toString(),
                        machinery.getFarmId(),
                        machinery.getName() != null ? machinery.getName() : "Machinery",
                        machinery.getModel(),
                        event.reason() != null && !event.reason().isBlank() ? event.reason() : "Scheduled Maintenance",
                        event.expectedDate(),
                        machinery.getOperatingHours() != null ? machinery.getOperatingHours() : 0.0,
                        event.occurredOn() != null ? event.occurredOn() : java.time.Instant.now()
                );

        com.farmflow.event.cloudevents.CloudEvent<com.farmflow.event.cloudevents.MachineryMaintenanceScheduledData> cloudEvent =
                com.farmflow.event.cloudevents.CloudEvent.of(
                        "https://farmflow.com/machinery-service",
                        "com.farmflow.machinery.maintenance.scheduled.v1",
                        machinery.getFarmId(),
                        correlationId,
                        data
                );
        outboxService.recordCloudEvent(cloudEvent);
    }

    private void recordHoursUpdatedCloudEvent(MachineryOperatingHoursUpdatedEvent event) {
        Machinery machinery = event.machinery();
        String correlationId = org.slf4j.MDC.get(com.farmflow.config.CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }

        com.farmflow.event.cloudevents.MachineryHoursUpdatedData data =
                new com.farmflow.event.cloudevents.MachineryHoursUpdatedData(
                        machinery.getId() != null ? machinery.getId() : java.util.UUID.randomUUID().toString(),
                        machinery.getFarmId(),
                        event.previousHours() != null ? event.previousHours() : 0.0,
                        event.newHours() != null ? event.newHours() : 0.0,
                        event.occurredOn() != null ? event.occurredOn() : java.time.Instant.now()
                );

        com.farmflow.event.cloudevents.CloudEvent<com.farmflow.event.cloudevents.MachineryHoursUpdatedData> cloudEvent =
                com.farmflow.event.cloudevents.CloudEvent.of(
                        "https://farmflow.com/machinery-service",
                        "com.farmflow.machinery.hours.updated.v1",
                        machinery.getFarmId(),
                        correlationId,
                        data
                );
        outboxService.recordCloudEvent(cloudEvent);
    }
}