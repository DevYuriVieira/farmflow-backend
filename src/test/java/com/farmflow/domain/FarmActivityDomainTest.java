package com.farmflow.domain;

import com.farmflow.entity.FarmActivity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FarmActivityDomainTest {

    @Test
    void shouldCreateFarmActivityWithDefaultPendingStatus() {
        FarmActivity activity = FarmActivity.builder()
                .farmId("farm-001")
                .plantationId("plantation-001")
                .title("Aplicacao de Fungicida")
                .type("spraying")
                .priority("HIGH")
                .date("2026-11-05")
                .costEstimate(2500.0)
                .build();

        assertFalse(activity.getCompleted());
        assertEquals("HIGH", activity.getPriority());
        assertEquals(2500.0, activity.getCostEstimate());
    }

    @Test
    void shouldToggleActivityCompletionStatus() {
        FarmActivity activity = FarmActivity.builder()
                .farmId("farm-001")
                .title("Adubacao de Cobertura")
                .completed(false)
                .build();

        activity.setCompleted(true);
        activity.setCompletedAt("2026-11-06T14:30:00Z");

        assertTrue(activity.getCompleted());
        assertNotNull(activity.getCompletedAt());
    }
}