package com.farmflow.service;

import com.farmflow.config.FarmSecurityEvaluator;
import com.farmflow.dto.ScoutingDtos.CreatePestRecordRequest;
import com.farmflow.entity.PestScoutingRecord;
import com.farmflow.entity.Plantation;
import com.farmflow.event.PestDetectedEvent;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.event.cloudevents.PestAlertRaisedData;
import com.farmflow.exceptions.ResourceNotFoundException;
import com.farmflow.repository.PestRecordRepository;
import com.farmflow.repository.PlantationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScoutingServiceTest {

    private PestRecordRepository pestRecordRepository;
    private PlantationRepository plantationRepository;
    private ApplicationEventPublisher eventPublisher;
    private OutboxService outboxService;
    private FarmSecurityEvaluator farmSecurity;
    private ScoutingService scoutingService;

    @BeforeEach
    void setUp() {
        pestRecordRepository = mock(PestRecordRepository.class);
        plantationRepository = mock(PlantationRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        outboxService = mock(OutboxService.class);
        farmSecurity = mock(FarmSecurityEvaluator.class);

        scoutingService = new ScoutingService(
                pestRecordRepository,
                plantationRepository,
                eventPublisher,
                outboxService,
                farmSecurity
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecordPestAndEmitCanonicalCloudEvent() {
        CreatePestRecordRequest request = new CreatePestRecordRequest(
                "farm-1", "plt-10", "Spodoptera frugiperda", "high",
                0.95, "https://storage.farmflow.com/photo.jpg",
                -18.91, -48.27, "Aplicar baculovírus"
        );

        when(pestRecordRepository.save(any(PestScoutingRecord.class))).thenAnswer(invocation -> {
            PestScoutingRecord r = invocation.getArgument(0);
            PestScoutingRecord savedRecord = PestScoutingRecord.builder()
                    .farmId(r.getFarmId())
                    .plantationId(r.getPlantationId())
                    .pestName(r.getPestName())
                    .severity(r.getSeverity())
                    .confidenceScore(r.getConfidenceScore())
                    .photoUrl(r.getPhotoUrl())
                    .latitude(r.getLatitude())
                    .longitude(r.getLongitude())
                    .recommendedAction(r.getRecommendedAction())
                    .detectedAt(Instant.now())
                    .build();
            org.springframework.test.util.ReflectionTestUtils.setField(savedRecord, "id", "scout-999");
            return savedRecord;
        });

        PestScoutingRecord saved = scoutingService.recordPest(request);

        assertNotNull(saved);
        assertEquals("scout-999", saved.getId());
        verify(farmSecurity, times(1)).validateTenantAccess("farm-1");
        verify(outboxService, times(1)).recordEvent(eq("PestDetected"), any(PestDetectedEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(PestDetectedEvent.class));

        ArgumentCaptor<CloudEvent<PestAlertRaisedData>> captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(outboxService, times(1)).recordCloudEvent(captor.capture());

        CloudEvent<PestAlertRaisedData> cloudEvent = captor.getValue();
        assertEquals("com.farmflow.pest.alert.raised.v1", cloudEvent.type());
        assertEquals("farm-1", cloudEvent.tenantId());
        assertEquals("scout-999", cloudEvent.data().scoutingId());
        assertEquals("Spodoptera frugiperda", cloudEvent.data().pestType());
        assertEquals("HIGH", cloudEvent.data().severity());
        assertEquals("Aplicar baculovírus", cloudEvent.data().recommendedAction());
    }

    @Test
    void shouldGetRecordsByFarmWithTenantValidation() {
        PestScoutingRecord record = PestScoutingRecord.builder()
                .farmId("farm-1")
                .plantationId("plt-1")
                .pestName("Broca")
                .severity("LOW")
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(record, "id", "scout-1");
        when(pestRecordRepository.findByFarmIdOrderByDetectedAtDesc("farm-1")).thenReturn(List.of(record));

        List<PestScoutingRecord> records = scoutingService.getRecordsByFarm("farm-1");

        assertEquals(1, records.size());
        assertEquals("Broca", records.get(0).getPestName());
        verify(farmSecurity, times(1)).validateTenantAccess("farm-1");
    }

    @Test
    void shouldGetRecordsByPlantationWithTenantValidation() {
        Plantation plantation = Plantation.builder()
                .id("plt-1")
                .farmId("farm-1")
                .name("Talhão Norte")
                .crop("Soja")
                .areaHectares(50.0)
                .build();
        when(plantationRepository.findById("plt-1")).thenReturn(Optional.of(plantation));
        when(pestRecordRepository.findByPlantationIdOrderByDetectedAtDesc("plt-1")).thenReturn(List.of());

        List<PestScoutingRecord> records = scoutingService.getRecordsByPlantation("plt-1");

        assertNotNull(records);
        verify(farmSecurity, times(1)).validateTenantAccess("farm-1");
    }

    @Test
    void shouldThrowNotFoundWhenPlantationDoesNotExist() {
        when(plantationRepository.findById("plt-missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scoutingService.getRecordsByPlantation("plt-missing"));
    }
}
