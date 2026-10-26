package com.farmflow.service;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.FarmSecurityEvaluator;
import com.farmflow.dto.TelemetryDtos.CreateTelemetryRequest;
import com.farmflow.entity.TelemetryReading;
import com.farmflow.enums.TelemetryType;
import com.farmflow.event.TelemetryAlertEvent;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.event.cloudevents.TelemetryAlertRaisedData;
import com.farmflow.repository.TelemetryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TelemetryServiceTest {

    private TelemetryRepository telemetryRepository;
    private ApplicationEventPublisher eventPublisher;
    private OutboxService outboxService;
    private FarmSecurityEvaluator farmSecurity;
    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        telemetryRepository = mock(TelemetryRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        outboxService = mock(OutboxService.class);
        farmSecurity = mock(FarmSecurityEvaluator.class);

        telemetryService = new TelemetryService(
                telemetryRepository,
                eventPublisher,
                outboxService,
                farmSecurity
        );

        MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID_KEY, "corr-telemetry-test-1");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecordReadingAndEmitCriticalCloudEventForLowSoilMoisture() {
        CreateTelemetryRequest request = new CreateTelemetryRequest(
                "farm-1", "plt-1", "sensor-soil-01", TelemetryType.SOIL_MOISTURE,
                14.5, "%", "98%"
        );

        when(telemetryRepository.save(any(TelemetryReading.class))).thenAnswer(invocation -> {
            TelemetryReading reading = invocation.getArgument(0);
            ReflectionTestUtils.setField(reading, "id", "reading-100");
            reading.setTimestamp(Instant.now());
            return reading;
        });

        TelemetryReading saved = telemetryService.recordReading(request);

        assertNotNull(saved);
        assertEquals("reading-100", saved.getId());
        verify(farmSecurity).validateTenantAccess("farm-1");
        verify(eventPublisher).publishEvent(any(TelemetryAlertEvent.class));
        verify(outboxService).recordEvent(eq("TelemetryAlert"), any(TelemetryAlertEvent.class));

        ArgumentCaptor<CloudEvent<TelemetryAlertRaisedData>> captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(outboxService).recordCloudEvent(captor.capture());

        CloudEvent<TelemetryAlertRaisedData> event = captor.getValue();
        assertEquals("com.farmflow.telemetry.alert.raised.v1", event.type());
        assertEquals("farm-1", event.tenantId());
        assertEquals("corr-telemetry-test-1", event.correlationId());
        assertNotNull(event.data());
        assertEquals("reading-100", event.data().readingId());
        assertEquals("sensor-soil-01", event.data().sensorId());
        assertEquals("SOIL_MOISTURE", event.data().telemetryType());
        assertEquals(14.5, event.data().value());
        assertEquals("%", event.data().unit());
        assertEquals("CRITICAL", event.data().severity());
        assertTrue(event.data().alertReason().contains("Low soil moisture"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecordReadingAndEmitHighCloudEventForHighTemperature() {
        CreateTelemetryRequest request = new CreateTelemetryRequest(
                "farm-2", "plt-2", "sensor-temp-02", TelemetryType.TEMPERATURE,
                42.0, "C", "90%"
        );

        when(telemetryRepository.save(any(TelemetryReading.class))).thenAnswer(invocation -> {
            TelemetryReading reading = invocation.getArgument(0);
            ReflectionTestUtils.setField(reading, "id", "reading-200");
            reading.setTimestamp(Instant.now());
            return reading;
        });

        TelemetryReading saved = telemetryService.recordReading(request);

        assertNotNull(saved);
        ArgumentCaptor<CloudEvent<TelemetryAlertRaisedData>> captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(outboxService).recordCloudEvent(captor.capture());

        CloudEvent<TelemetryAlertRaisedData> event = captor.getValue();
        assertEquals("HIGH", event.data().severity());
        assertTrue(event.data().alertReason().contains("High temperature"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecordReadingAndEmitMediumCloudEventForLowSiloLevel() {
        CreateTelemetryRequest request = new CreateTelemetryRequest(
                "farm-3", null, "sensor-silo-03", TelemetryType.SILO_LEVEL,
                11.0, "%", "85%"
        );

        when(telemetryRepository.save(any(TelemetryReading.class))).thenAnswer(invocation -> {
            TelemetryReading reading = invocation.getArgument(0);
            ReflectionTestUtils.setField(reading, "id", "reading-300");
            reading.setTimestamp(Instant.now());
            return reading;
        });

        TelemetryReading saved = telemetryService.recordReading(request);

        assertNotNull(saved);
        ArgumentCaptor<CloudEvent<TelemetryAlertRaisedData>> captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(outboxService).recordCloudEvent(captor.capture());

        CloudEvent<TelemetryAlertRaisedData> event = captor.getValue();
        assertEquals("MEDIUM", event.data().severity());
        assertTrue(event.data().alertReason().contains("Low silo storage"));
    }

    @Test
    void shouldNotEmitAlertWhenReadingsAreWithinNormalThresholds() {
        CreateTelemetryRequest request = new CreateTelemetryRequest(
                "farm-1", "plt-1", "sensor-soil-01", TelemetryType.SOIL_MOISTURE,
                45.0, "%", "100%"
        );

        when(telemetryRepository.save(any(TelemetryReading.class))).thenAnswer(invocation -> {
            TelemetryReading reading = invocation.getArgument(0);
            ReflectionTestUtils.setField(reading, "id", "reading-400");
            return reading;
        });

        TelemetryReading saved = telemetryService.recordReading(request);

        assertNotNull(saved);
        verify(eventPublisher, never()).publishEvent(any());
        verify(outboxService, never()).recordEvent(anyString(), any());
        verify(outboxService, never()).recordCloudEvent(any());
    }

    @Test
    void shouldGetReadingsByFarmWithAndWithoutType() {
        when(telemetryRepository.findByFarmIdAndTypeOrderByTimestampDesc("farm-1", TelemetryType.SOIL_MOISTURE))
                .thenReturn(List.of(TelemetryReading.builder().farmId("farm-1").type(TelemetryType.SOIL_MOISTURE).build()));
        when(telemetryRepository.findByFarmIdOrderByTimestampDesc("farm-1"))
                .thenReturn(List.of(TelemetryReading.builder().farmId("farm-1").build()));

        List<TelemetryReading> filtered = telemetryService.getReadingsByFarm("farm-1", TelemetryType.SOIL_MOISTURE);
        assertEquals(1, filtered.size());

        List<TelemetryReading> all = telemetryService.getReadingsByFarm("farm-1", null);
        assertEquals(1, all.size());

        verify(farmSecurity, times(2)).validateTenantAccess("farm-1");
    }

    @Test
    void shouldGetReadingsBySensorAndValidateTenantAccess() {
        TelemetryReading reading = TelemetryReading.builder().farmId("farm-99").sensorId("sensor-x").build();
        when(telemetryRepository.findBySensorIdOrderByTimestampDesc("sensor-x"))
                .thenReturn(List.of(reading));

        List<TelemetryReading> result = telemetryService.getReadingsBySensor("sensor-x");

        assertEquals(1, result.size());
        verify(farmSecurity).validateTenantAccess("farm-99");
    }
}
