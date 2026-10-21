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
}