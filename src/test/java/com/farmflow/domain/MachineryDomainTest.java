package com.farmflow.domain;

import com.farmflow.entity.Machinery;
import com.farmflow.exceptions.BusinessException;
import com.farmflow.exceptions.InvalidMachineryStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachineryDomainTest {

    @Test
    void shouldCreateMachineryWithDefaultOperationalStatus() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator John Deere 8R")
                .model("8R 370")
                .type("TRACTOR")
                .operatingHours(120.5)
                .fuelLevelPercent(85.0)
                .build();

        assertEquals("OPERATIONAL", machinery.getStatus());
        assertEquals(120.5, machinery.getOperatingHours());
        assertEquals(85.0, machinery.getFuelLevelPercent());
    }

    @Test
    void shouldSendMachineryToMaintenanceSuccessfully() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Colheitadeira Case IH")
                .model("Axial-Flow 8250")
                .type("HARVESTER")
                .status("OPERATIONAL")
                .build();

        machinery.sendToMaintenance("Troca de filtro e oleo hidráulico", "2026-11-15");

        assertEquals("MAINTENANCE", machinery.getStatus());
        assertEquals("2026-11-15", machinery.getNextMaintenanceDate());
    }

    @Test
    void shouldThrowExceptionWhenSendingToMaintenanceWhileAlreadyInMaintenance() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Pulverizador Uniport")
                .model("3030")
                .type("SPRAYER")
                .status("MAINTENANCE")
                .build();

        InvalidMachineryStateException ex = assertThrows(
                InvalidMachineryStateException.class,
                () -> machinery.sendToMaintenance("Revisao preventiva", "2026-12-01")
        );

        assertTrue(ex.getMessage().contains("already under maintenance"));
    }

    @Test
    void shouldReturnFromMaintenanceSuccessfully() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator Massey Ferguson")
                .model("MF 8S")
                .type("TRACTOR")
                .operatingHours(350.0)
                .status("MAINTENANCE")
                .build();

        machinery.returnFromMaintenance(352.5);

        assertEquals("OPERATIONAL", machinery.getStatus());
        assertEquals(352.5, machinery.getOperatingHours());
    }

    @Test
    void shouldThrowExceptionWhenReturningFromMaintenanceWhileOperational() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Plantadeira Momentum")
                .model("40 Linhas")
                .type("PLANTER")
                .status("OPERATIONAL")
                .build();

        InvalidMachineryStateException ex = assertThrows(
                InvalidMachineryStateException.class,
                () -> machinery.returnFromMaintenance(null)
        );

        assertTrue(ex.getMessage().contains("not under maintenance"));
    }

    @Test
    void shouldUpdateOperatingHoursSuccessfully() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator Fendt")
                .model("1050 Vario")
                .type("TRACTOR")
                .operatingHours(500.0)
                .build();

        machinery.updateOperatingHours(545.0);

        assertEquals(545.0, machinery.getOperatingHours());
    }

    @Test
    void shouldThrowExceptionWhenDecreasingOperatingHours() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator Fendt")
                .model("1050 Vario")
                .type("TRACTOR")
                .operatingHours(500.0)
                .build();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> machinery.updateOperatingHours(480.0)
        );

        assertTrue(ex.getMessage().contains("cannot decrease"));
    }

    @Test
    void shouldThrowExceptionWhenOperatingHoursIsNegative() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator Fendt")
                .model("1050 Vario")
                .type("TRACTOR")
                .operatingHours(0.0)
                .build();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> machinery.updateOperatingHours(-10.0)
        );

        assertTrue(ex.getMessage().contains("cannot be negative"));
    }

    @Test
    void shouldUpdateFuelLevelSuccessfully() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Caminhao Munck")
                .model("VW Constellation")
                .type("TRUCK")
                .fuelLevelPercent(45.0)
                .build();

        machinery.updateFuelLevel(95.5);

        assertEquals(95.5, machinery.getFuelLevelPercent());
    }

    @Test
    void shouldThrowExceptionWhenFuelLevelExceedsOneHundredPercent() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Caminhao Munck")
                .model("VW Constellation")
                .type("TRUCK")
                .build();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> machinery.updateFuelLevel(105.0)
        );

        assertTrue(ex.getMessage().contains("between 0.0 and 100.0"));
    }

    @Test
    void shouldThrowExceptionWhenFuelLevelIsNegative() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Caminhao Munck")
                .model("VW Constellation")
                .type("TRUCK")
                .build();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> machinery.updateFuelLevel(-5.0)
        );

        assertTrue(ex.getMessage().contains("between 0.0 and 100.0"));
    }

    @Test
    void shouldAssignFarmSuccessfully() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator John Deere")
                .model("6150M")
                .type("TRACTOR")
                .status("OPERATIONAL")
                .build();

        machinery.assignFarm("farm-002");

        assertEquals("farm-002", machinery.getFarmId());
    }

    @Test
    void shouldThrowExceptionWhenAssigningFarmWhileUnderMaintenance() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator John Deere")
                .model("6150M")
                .type("TRACTOR")
                .status("MAINTENANCE")
                .build();

        InvalidMachineryStateException ex = assertThrows(
                InvalidMachineryStateException.class,
                () -> machinery.assignFarm("farm-002")
        );

        assertTrue(ex.getMessage().contains("under maintenance"));
    }
}