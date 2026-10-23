package com.farmflow.domain;

import com.farmflow.entity.Farm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FarmDomainTest {

    @Test
    void shouldCreateFarmSuccessfully() {
        Farm farm = Farm.builder()
                .name("Fazenda Santa Tereza")
                .location("Ribeirao Preto - SP")
                .totalAreaHectares(1500.0)
                .primaryCrop("SOYBEAN")
                .ownerName("Yuri Vieira")
                .build();

        assertEquals("Fazenda Santa Tereza", farm.getName());
        assertEquals("Ribeirao Preto - SP", farm.getLocation());
        assertEquals(1500.0, farm.getTotalAreaHectares());
        assertEquals("Yuri Vieira", farm.getOwnerName());
    }

    @Test
    void shouldUpdateDetailsSuccessfully() {
        Farm farm = Farm.builder()
                .name("Fazenda Santa Tereza")
                .location("Ribeirao Preto - SP")
                .totalAreaHectares(1500.0)
                .primaryCrop("SOYBEAN")
                .ownerName("Yuri Vieira")
                .status("active")
                .build();

        farm.updateDetails("Fazenda Nova", "Franca - SP", 2000.0, "CORN", "Carlos", "16999999999", "inactive");

        assertEquals("Fazenda Nova", farm.getName());
        assertEquals("Franca - SP", farm.getLocation());
        assertEquals(2000.0, farm.getTotalAreaHectares());
        assertEquals("CORN", farm.getPrimaryCrop());
        assertEquals("Carlos", farm.getOwnerName());
        assertEquals("16999999999", farm.getContactPhone());
        assertEquals("inactive", farm.getStatus());
    }

    @Test
    void shouldThrowExceptionWhenTotalAreaIsZeroOrNegative() {
        Farm farm = Farm.builder()
                .name("Fazenda Santa Tereza")
                .location("Ribeirao Preto - SP")
                .totalAreaHectares(1500.0)
                .build();

        assertThrows(com.farmflow.exceptions.BusinessException.class, () ->
                farm.updateDetails(null, null, 0.0, null, null, null, null));

        assertThrows(com.farmflow.exceptions.BusinessException.class, () ->
                farm.updateDetails(null, null, -10.0, null, null, null, null));
    }
}