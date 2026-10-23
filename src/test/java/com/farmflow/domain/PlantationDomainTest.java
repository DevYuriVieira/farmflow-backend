package com.farmflow.domain;

import com.farmflow.entity.Plantation;
import com.farmflow.enums.PlantationStatus;
import com.farmflow.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlantationDomainTest {

    @Test
    void shouldCreatePlantationSuccessfully() {
        Plantation plantation = Plantation.builder()
                .farmId("farm-001")
                .name("Talhao 12 - Soja")
                .crop("SOYBEAN")
                .areaHectares(350.0)
                .plantingDate("2026-10-15")
                .harvestEstimateDate("2027-02-28")
                .status("planted")
                .build();

        assertEquals("farm-001", plantation.getFarmId());
        assertEquals("Talhao 12 - Soja", plantation.getName());
        assertEquals("SOYBEAN", plantation.getCrop());
        assertEquals(350.0, plantation.getAreaHectares());
        assertEquals("planted", plantation.getStatus());
        assertEquals(PlantationStatus.PLANTED, plantation.getPlantationStatus());
    }

    @Test
    void shouldCreatePlantationWithTypedStatusEnum() {
        Plantation plantation = Plantation.builder()
                .farmId("farm-001")
                .name("Talhao 15 - Milho")
                .crop("CORN")
                .areaHectares(120.0)
                .status(PlantationStatus.GROWING)
                .build();

        assertEquals("growing", plantation.getStatus());
        assertEquals(PlantationStatus.GROWING, plantation.getPlantationStatus());
    }

    @Test
    void shouldThrowExceptionWhenMandatoryFieldsAreBlank() {
        assertThrows(BusinessException.class, () ->
                Plantation.builder()
                        .farmId("")
                        .name("Talhao")
                        .crop("SOY")
                        .areaHectares(10.0)
                        .build()
        );

        assertThrows(BusinessException.class, () ->
                Plantation.builder()
                        .farmId("farm-1")
                        .name("")
                        .crop("SOY")
                        .areaHectares(10.0)
                        .build()
        );

        assertThrows(BusinessException.class, () ->
                Plantation.builder()
                        .farmId("farm-1")
                        .name("Talhao")
                        .crop("")
                        .areaHectares(10.0)
                        .build()
        );

        assertThrows(BusinessException.class, () ->
                Plantation.builder()
                        .farmId("farm-1")
                        .name("Talhao")
                        .crop("SOY")
                        .areaHectares(0.0)
                        .build()
        );

        assertThrows(BusinessException.class, () ->
                Plantation.builder()
                        .farmId("farm-1")
                        .name("Talhao")
                        .crop("SOY")
                        .areaHectares(-5.0)
                        .build()
        );

        assertThrows(BusinessException.class, () ->
                Plantation.builder()
                        .farmId("farm-1")
                        .name("Talhao")
                        .crop("SOY")
                        .areaHectares(10.0)
                        .expectedYieldTons(-1.0)
                        .build()
        );
    }

    @Test
    void shouldTransitionThroughLifecycleStatesSuccessfully() {
        Plantation plantation = Plantation.builder()
                .farmId("farm-001")
                .name("Talhao Ciclo")
                .crop("SOYBEAN")
                .areaHectares(200.0)
                .status(PlantationStatus.PLANNING)
                .build();

        assertEquals(PlantationStatus.PLANNING, plantation.getPlantationStatus());

        plantation.transitionStatus(PlantationStatus.PLANTED);
        assertEquals(PlantationStatus.PLANTED, plantation.getPlantationStatus());

        plantation.transitionStatus("growing");
        assertEquals(PlantationStatus.GROWING, plantation.getPlantationStatus());

        plantation.transitionStatus(PlantationStatus.HARVESTING);
        assertEquals(PlantationStatus.HARVESTING, plantation.getPlantationStatus());

        plantation.transitionStatus(PlantationStatus.COMPLETED);
        assertEquals(PlantationStatus.COMPLETED, plantation.getPlantationStatus());
    }

    @Test
    void shouldAllowTransitionToFailedFromActiveState() {
        Plantation plantation = Plantation.builder()
                .farmId("farm-001")
                .name("Talhao Seca")
                .crop("SOYBEAN")
                .areaHectares(80.0)
                .status(PlantationStatus.GROWING)
                .build();

        plantation.transitionStatus(PlantationStatus.FAILED);
        assertEquals(PlantationStatus.FAILED, plantation.getPlantationStatus());
    }

    @Test
    void shouldThrowExceptionOnIllegalStateTransition() {
        Plantation plantation = Plantation.builder()
                .farmId("farm-001")
                .name("Talhao Invalido")
                .crop("SOYBEAN")
                .areaHectares(50.0)
                .status(PlantationStatus.PLANNING)
                .build();

        assertThrows(BusinessException.class, () -> plantation.transitionStatus(PlantationStatus.HARVESTING));

        Plantation completed = Plantation.builder()
                .farmId("farm-001")
                .name("Talhao Concluido")
                .crop("SOYBEAN")
                .areaHectares(50.0)
                .status(PlantationStatus.COMPLETED)
                .build();

        assertThrows(BusinessException.class, () -> completed.transitionStatus(PlantationStatus.GROWING));
    }

    @Test
    void shouldUpdateDetailsViaAggregateMethod() {
        Plantation plantation = Plantation.builder()
                .farmId("farm-001")
                .name("Talhao Original")
                .crop("SOYBEAN")
                .areaHectares(100.0)
                .variety("V1")
                .notes("Inicial")
                .build();

        plantation.updateDetails(
                "Talhao Atualizado",
                "CORN",
                "V2",
                150.0,
                "2026-11-01",
                "2027-03-15",
                450.0,
                "Atualizado"
        );

        assertEquals("Talhao Atualizado", plantation.getName());
        assertEquals("CORN", plantation.getCrop());
        assertEquals("V2", plantation.getVariety());
        assertEquals(150.0, plantation.getAreaHectares());
        assertEquals("2026-11-01", plantation.getPlantingDate());
        assertEquals("2027-03-15", plantation.getHarvestEstimateDate());
        assertEquals(450.0, plantation.getExpectedYieldTons());
        assertEquals("Atualizado", plantation.getNotes());

        assertThrows(BusinessException.class, () ->
                plantation.updateDetails(null, null, null, -10.0, null, null, null, null)
        );

        assertThrows(BusinessException.class, () ->
                plantation.updateDetails(null, null, null, null, null, null, -5.0, null)
        );
    }

    @Test
    void shouldThrowExceptionWhenParsingInvalidStatusString() {
        assertThrows(BusinessException.class, () -> PlantationStatus.fromString("invalid_status_xyz"));
        assertThrows(BusinessException.class, () -> PlantationStatus.fromString(null));
        assertThrows(BusinessException.class, () -> PlantationStatus.fromString("   "));
    }

    @Test
    void shouldThrowExceptionWhenTransitioningToNullOrBlankStatus() {
        Plantation plantation = Plantation.builder()
                .farmId("farm-001")
                .name("Talhao Test")
                .crop("SOYBEAN")
                .areaHectares(100.0)
                .build();

        assertThrows(BusinessException.class, () -> plantation.transitionStatus((String) null));
        assertThrows(BusinessException.class, () -> plantation.transitionStatus(""));
        assertThrows(BusinessException.class, () -> plantation.transitionStatus((PlantationStatus) null));
    }
}