package com.farmflow.domain;

import com.farmflow.entity.Plantation;
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
    }
}