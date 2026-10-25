package com.farmflow.service;

import com.farmflow.config.FarmSecurityEvaluator;
import com.farmflow.dto.ActivityDtos.CreateActivityRequest;
import com.farmflow.dto.ActivityDtos.UpdateActivityRequest;
import com.farmflow.entity.FarmActivity;
import com.farmflow.event.ActivityCompletedEvent;
import com.farmflow.event.cloudevents.ActivityCompletedData;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.repository.ActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActivityServiceTest {

    private ActivityRepository activityRepository;
    private ApplicationEventPublisher eventPublisher;
    private OutboxService outboxService;
    private FarmSecurityEvaluator farmSecurity;
    private ActivityService activityService;

    @BeforeEach
    void setUp() {
        activityRepository = mock(ActivityRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        outboxService = mock(OutboxService.class);
        farmSecurity = mock(FarmSecurityEvaluator.class);

        activityService = new ActivityService(activityRepository, eventPublisher, outboxService, farmSecurity);
    }

    @Test
    void shouldCreateActivityWithTenantValidation() {
        CreateActivityRequest request = new CreateActivityRequest(
                "plt-1", "farm-1", "Pulverização", "spraying",
                "2026-10-25", "high", "Preventivo", 500.0
        );

        when(activityRepository.save(any(FarmActivity.class))).thenAnswer(invocation -> {
            FarmActivity a = invocation.getArgument(0);
            return FarmActivity.builder()
                    .plantationId(a.getPlantationId())
                    .farmId(a.getFarmId())
                    .title(a.getTitle())
                    .type(a.getType())
                    .date(a.getDate())
                    .priority(a.getPriority())
                    .notes(a.getNotes())
                    .costEstimate(a.getCostEstimate())
                    .completed(false)
                    .build();
        });

        FarmActivity created = activityService.createActivity(request);

        assertNotNull(created);
        assertEquals("farm-1", created.getFarmId());
        assertEquals("Pulverização", created.getTitle());
        assertFalse(created.getCompleted());
        verify(farmSecurity, times(1)).validateTenantAccess("farm-1");
        verify(activityRepository, times(1)).save(any(FarmActivity.class));
    }

    @Test
    void shouldThrowNotFoundWhenActivityDoesNotExist() {
        when(activityRepository.findById("act-unknown")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> activityService.getActivityById("act-unknown"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldEmitCloudEventWhenTogglingStatusToCompleted() {
        FarmActivity activity = FarmActivity.builder()
                .id("act-1")
                .farmId("farm-1")
                .plantationId("plt-1")
                .title("Adubação Foliar")
                .type("fertilizing")
                .completed(false)
                .build();

        when(activityRepository.findById("act-1")).thenReturn(Optional.of(activity));
        when(activityRepository.save(any(FarmActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FarmActivity result = activityService.toggleStatus("act-1", true);

        assertTrue(result.getCompleted());
        assertNotNull(result.getCompletedAt());

        verify(outboxService, times(1)).recordEvent(eq("ActivityCompleted"), any(ActivityCompletedEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(ActivityCompletedEvent.class));

        ArgumentCaptor<CloudEvent<ActivityCompletedData>> captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(outboxService, times(1)).recordCloudEvent(captor.capture());

        CloudEvent<ActivityCompletedData> cloudEvent = captor.getValue();
        assertEquals("com.farmflow.activity.completed.v1", cloudEvent.type());
        assertEquals("farm-1", cloudEvent.tenantId());
        assertEquals("Adubação Foliar", cloudEvent.data().title());
    }

    @Test
    void shouldNotEmitCloudEventWhenTogglingStatusToIncomplete() {
        FarmActivity activity = FarmActivity.builder()
                .id("act-1")
                .farmId("farm-1")
                .plantationId("plt-1")
                .title("Adubação Foliar")
                .type("fertilizing")
                .completed(true)
                .build();

        when(activityRepository.findById("act-1")).thenReturn(Optional.of(activity));
        when(activityRepository.save(any(FarmActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FarmActivity result = activityService.toggleStatus("act-1", false);

        assertFalse(result.getCompleted());
        verify(outboxService, never()).recordCloudEvent(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldEmitCloudEventWhenUpdatingActivityWithCompletedTrue() {
        FarmActivity activity = FarmActivity.builder()
                .id("act-2")
                .farmId("farm-1")
                .plantationId("plt-1")
                .title("Plantio")
                .type("planting")
                .completed(false)
                .build();

        when(activityRepository.findById("act-2")).thenReturn(Optional.of(activity));
        when(activityRepository.save(any(FarmActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateActivityRequest request = new UpdateActivityRequest(
                "Plantio Milho Safrinha", "planting", "2026-10-25",
                "high", "Finalizado com sucesso", 1500.0, true
        );

        FarmActivity updated = activityService.updateActivity("act-2", request);

        assertTrue(updated.getCompleted());
        assertEquals("Plantio Milho Safrinha", updated.getTitle());
        verify(outboxService, times(1)).recordCloudEvent(any(CloudEvent.class));
    }

    @Test
    void shouldDeleteActivitySuccessfully() {
        FarmActivity activity = FarmActivity.builder()
                .id("act-del")
                .farmId("farm-1")
                .plantationId("plt-1")
                .title("To delete")
                .build();
        when(activityRepository.findById("act-del")).thenReturn(Optional.of(activity));

        activityService.deleteActivity("act-del");

        verify(activityRepository, times(1)).delete(activity);
    }
}
