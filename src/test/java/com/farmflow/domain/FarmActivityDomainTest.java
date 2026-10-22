package com.farmflow.domain;

import com.farmflow.entity.FarmActivity;
import com.farmflow.enums.ActivityPriority;
import com.farmflow.enums.ActivityType;
import com.farmflow.exceptions.BusinessException;
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
        assertEquals(ActivityPriority.HIGH, activity.getActivityPriority());
        assertEquals("SPRAYING", activity.getType());
        assertEquals(ActivityType.SPRAYING, activity.getActivityType());
        assertEquals(2500.0, activity.getCostEstimate());
    }

    @Test
    void shouldFailWhenFarmIdIsBlank() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                FarmActivity.builder()
                        .farmId(" ")
                        .plantationId("plantation-001")
                        .title("Aplicacao de Fungicida")
                        .build()
        );
        assertEquals("Farm ID cannot be blank.", ex.getMessage());
    }

    @Test
    void shouldFailWhenPlantationIdIsBlank() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                FarmActivity.builder()
                        .farmId("farm-001")
                        .plantationId("")
                        .title("Aplicacao de Fungicida")
                        .build()
        );
        assertEquals("Plantation ID cannot be blank.", ex.getMessage());
    }

    @Test
    void shouldFailWhenTitleIsBlank() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                FarmActivity.builder()
                        .farmId("farm-001")
                        .plantationId("plantation-001")
                        .title(null)
                        .build()
        );
        assertEquals("Title cannot be blank.", ex.getMessage());
    }

    @Test
    void shouldFailWhenCostEstimateIsNegative() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                FarmActivity.builder()
                        .farmId("farm-001")
                        .plantationId("plantation-001")
                        .title("Aplicacao de Fungicida")
                        .costEstimate(-10.0)
                        .build()
        );
        assertEquals("Cost estimate cannot be negative.", ex.getMessage());
    }

    @Test
    void shouldCompleteActivityAndSetTimestamp() {
        FarmActivity activity = FarmActivity.builder()
                .farmId("farm-001")
                .plantationId("plantation-001")
                .title("Adubacao de Cobertura")
                .completed(false)
                .build();

        activity.complete();

        assertTrue(activity.getCompleted());
        assertNotNull(activity.getCompletedAt());

        String firstCompletedAt = activity.getCompletedAt();
        activity.complete();
        assertEquals(firstCompletedAt, activity.getCompletedAt());
    }

    @Test
    void shouldReopenActivityAndClearTimestamp() {
        FarmActivity activity = FarmActivity.builder()
                .farmId("farm-001")
                .plantationId("plantation-001")
                .title("Adubacao de Cobertura")
                .completed(false)
                .build();

        activity.complete();
        assertTrue(activity.getCompleted());

        activity.reopen();
        assertFalse(activity.getCompleted());
        assertNull(activity.getCompletedAt());
    }

    @Test
    void shouldUpdateDetailsWithValidData() {
        FarmActivity activity = FarmActivity.builder()
                .farmId("farm-001")
                .plantationId("plantation-001")
                .title("Original Title")
                .type(ActivityType.SPRAYING)
                .priority(ActivityPriority.LOW)
                .date("2026-11-01")
                .notes("Original Notes")
                .costEstimate(100.0)
                .build();

        activity.updateDetails(
                "Updated Title",
                ActivityType.HARVEST,
                "2026-11-10",
                ActivityPriority.URGENT,
                "Updated Notes",
                250.0
        );

        assertEquals("Updated Title", activity.getTitle());
        assertEquals(ActivityType.HARVEST, activity.getActivityType());
        assertEquals("2026-11-10", activity.getDate());
        assertEquals(ActivityPriority.URGENT, activity.getActivityPriority());
        assertEquals("Updated Notes", activity.getNotes());
        assertEquals(250.0, activity.getCostEstimate());
    }

    @Test
    void shouldUpdateDetailsWithStringOverload() {
        FarmActivity activity = FarmActivity.builder()
                .farmId("farm-001")
                .plantationId("plantation-001")
                .title("Original Title")
                .type(ActivityType.SPRAYING)
                .priority(ActivityPriority.LOW)
                .build();

        activity.updateDetails(
                "Updated Title",
                "fertilization",
                "2026-11-15",
                "high",
                "Fertilization Notes",
                500.0
        );

        assertEquals("Updated Title", activity.getTitle());
        assertEquals(ActivityType.FERTILIZATION, activity.getActivityType());
        assertEquals("2026-11-15", activity.getDate());
        assertEquals(ActivityPriority.HIGH, activity.getActivityPriority());
        assertEquals("Fertilization Notes", activity.getNotes());
        assertEquals(500.0, activity.getCostEstimate());
    }

    @Test
    void shouldFailWhenUpdatingWithNegativeCostEstimate() {
        FarmActivity activity = FarmActivity.builder()
                .farmId("farm-001")
                .plantationId("plantation-001")
                .title("Original Title")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                activity.updateDetails(
                        "Updated Title",
                        "irrigation",
                        "2026-11-20",
                        "medium",
                        "Notes",
                        -50.0
                )
        );
        assertEquals("Cost estimate cannot be negative.", ex.getMessage());
    }

    @Test
    void shouldParseActivityTypeGracefully() {
        assertEquals(ActivityType.HARVEST, ActivityType.fromString("harvesting"));
        assertEquals(ActivityType.HARVEST, ActivityType.fromString("HARVEST"));
        assertEquals(ActivityType.INSPECTION, ActivityType.fromString("inspection"));
        assertEquals(ActivityType.OTHER, ActivityType.fromString("unknown_type"));
        assertEquals(ActivityType.OTHER, ActivityType.fromString(""));
        assertEquals(ActivityType.OTHER, ActivityType.fromString(null));
    }

    @Test
    void shouldParseActivityPriorityGracefully() {
        assertEquals(ActivityPriority.URGENT, ActivityPriority.fromString("urgent"));
        assertEquals(ActivityPriority.HIGH, ActivityPriority.fromString("HIGH"));
        assertEquals(ActivityPriority.MEDIUM, ActivityPriority.fromString("unknown_priority"));
        assertEquals(ActivityPriority.MEDIUM, ActivityPriority.fromString(""));
        assertEquals(ActivityPriority.MEDIUM, ActivityPriority.fromString(null));
    }
}