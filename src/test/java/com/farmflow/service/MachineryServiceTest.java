package com.farmflow.service;

import com.farmflow.dto.MachineryDtos.CreateMachineryRequest;
import com.farmflow.dto.MachineryDtos.UpdateMachineryRequest;
import com.farmflow.entity.Machinery;
import com.farmflow.event.MachineryMaintenanceScheduledEvent;
import com.farmflow.event.MachineryOperatingHoursUpdatedEvent;
import com.farmflow.exceptions.ResourceNotFoundException;
import com.farmflow.repository.MachineryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MachineryServiceTest {

    @Mock
    private MachineryRepository machineryRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.farmflow.config.FarmSecurityEvaluator farmSecurity;

    @InjectMocks
    private MachineryService machineryService;

    private Machinery sampleMachinery;

    @BeforeEach
    void setUp() {
        sampleMachinery = Machinery.builder()
                .farmId("farm-123")
                .name("Colheitadeira New Holland")
                .model("CR 9.90")
                .type("HARVESTER")
                .operatingHours(400.0)
                .status("OPERATIONAL")
                .fuelLevelPercent(80.0)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(sampleMachinery, "id", "machinery-123");
    }

    @Test
    void shouldCreateMachineryAndPublishMaintenanceEventWhenStatusIsMaintenance() {
        CreateMachineryRequest request = new CreateMachineryRequest(
                "farm-123",
                "Trator John Deere",
                "8R 370",
                "TRACTOR",
                100.0,
                "MAINTENANCE",
                "2026-11-20",
                90.0
        );

        when(machineryRepository.save(any(Machinery.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Machinery created = machineryService.createMachinery(request);

        assertNotNull(created);
        assertEquals("MAINTENANCE", created.getStatus());
        verify(outboxService, times(1)).recordEvent(eq("MachineryMaintenanceScheduled"), any(MachineryMaintenanceScheduledEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(MachineryMaintenanceScheduledEvent.class));

        org.mockito.ArgumentCaptor<com.farmflow.event.cloudevents.CloudEvent> ceCaptor =
                org.mockito.ArgumentCaptor.forClass(com.farmflow.event.cloudevents.CloudEvent.class);
        verify(outboxService, times(1)).recordCloudEvent(ceCaptor.capture());
        assertEquals("com.farmflow.machinery.maintenance.scheduled.v1", ceCaptor.getValue().type());
        assertEquals("farm-123", ceCaptor.getValue().tenantId());
    }

    @Test
    void shouldUpdateOperatingHoursAndPublishHoursUpdatedEvent() {
        when(machineryRepository.findById("machinery-123")).thenReturn(Optional.of(sampleMachinery));
        when(machineryRepository.save(any(Machinery.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMachineryRequest request = new UpdateMachineryRequest(
                null,
                null,
                null,
                450.0,
                null,
                null,
                null
        );

        Machinery updated = machineryService.updateMachinery("machinery-123", request);

        assertEquals(450.0, updated.getOperatingHours());
        verify(outboxService, times(1)).recordEvent(eq("MachineryOperatingHoursUpdated"), any(MachineryOperatingHoursUpdatedEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(MachineryOperatingHoursUpdatedEvent.class));

        org.mockito.ArgumentCaptor<com.farmflow.event.cloudevents.CloudEvent> ceCaptor =
                org.mockito.ArgumentCaptor.forClass(com.farmflow.event.cloudevents.CloudEvent.class);
        verify(outboxService, times(1)).recordCloudEvent(ceCaptor.capture());
        assertEquals("com.farmflow.machinery.hours.updated.v1", ceCaptor.getValue().type());
        assertEquals("farm-123", ceCaptor.getValue().tenantId());
    }

    @Test
    void shouldSendToMaintenanceAndPublishEventWhenTransitioningStatus() {
        when(machineryRepository.findById("machinery-123")).thenReturn(Optional.of(sampleMachinery));
        when(machineryRepository.save(any(Machinery.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMachineryRequest request = new UpdateMachineryRequest(
                null,
                null,
                null,
                null,
                "MAINTENANCE",
                "2026-12-10",
                null
        );

        Machinery updated = machineryService.updateMachinery("machinery-123", request);

        assertEquals("MAINTENANCE", updated.getStatus());
        assertEquals("2026-12-10", updated.getNextMaintenanceDate());
        verify(outboxService, times(1)).recordEvent(eq("MachineryMaintenanceScheduled"), any(MachineryMaintenanceScheduledEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(MachineryMaintenanceScheduledEvent.class));
        verify(outboxService, times(1)).recordCloudEvent(any());
    }

    @Test
    void shouldReturnFromMaintenanceWhenTransitioningToOperational() {
        sampleMachinery.sendToMaintenance("Setup maintenance", "2026-11-20");
        sampleMachinery.pullDomainEvents();

        when(machineryRepository.findById("machinery-123")).thenReturn(Optional.of(sampleMachinery));
        when(machineryRepository.save(any(Machinery.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMachineryRequest request = new UpdateMachineryRequest(
                null,
                null,
                null,
                410.0,
                "OPERATIONAL",
                null,
                null
        );

        Machinery updated = machineryService.updateMachinery("machinery-123", request);

        assertEquals("OPERATIONAL", updated.getStatus());
        assertEquals(410.0, updated.getOperatingHours());
        assertNull(updated.getMaintenanceReason());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentMachinery() {
        when(machineryRepository.findById("non-existent")).thenReturn(Optional.empty());

        UpdateMachineryRequest request = new UpdateMachineryRequest(
                "Novo Nome",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThrows(ResourceNotFoundException.class, () -> machineryService.updateMachinery("non-existent", request));
        verify(machineryRepository, never()).save(any());
    }
}