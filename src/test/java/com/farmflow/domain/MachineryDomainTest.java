package com.farmflow.domain;

import com.farmflow.entity.Machinery;
import com.farmflow.enums.MachineryStatus;
import com.farmflow.event.MachineryMaintenanceScheduledEvent;
import com.farmflow.event.MachineryOperatingHoursUpdatedEvent;
import com.farmflow.exceptions.BusinessException;
import com.farmflow.exceptions.InvalidMachineryStateException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

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
        assertEquals(MachineryStatus.OPERATIONAL, machinery.getMachineryStatus());
        assertEquals(120.5, machinery.getOperatingHours());
        assertEquals(85.0, machinery.getFuelLevelPercent());
    }

    @Test
    void shouldSendMachineryToMaintenanceSuccessfullyAndRecordReasonAndEmitEvent() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Colheitadeira Case IH")
                .model("Axial-Flow 8250")
                .type("HARVESTER")
                .status("OPERATIONAL")
                .build();

        machinery.sendToMaintenance("Troca de filtro e oleo hidráulico", "2026-11-15");

        assertEquals("MAINTENANCE", machinery.getStatus());
        assertEquals(MachineryStatus.MAINTENANCE, machinery.getMachineryStatus());
        assertEquals("2026-11-15", machinery.getNextMaintenanceDate());
        assertEquals("Troca de filtro e oleo hidráulico", machinery.getMaintenanceReason());

        List<Object> events = machinery.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(MachineryMaintenanceScheduledEvent.class, events.get(0));
        MachineryMaintenanceScheduledEvent event = (MachineryMaintenanceScheduledEvent) events.get(0);
        assertEquals("Troca de filtro e oleo hidráulico", event.reason());
        assertEquals("2026-11-15", event.expectedDate());

        assertTrue(machinery.pullDomainEvents().isEmpty());
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
    void shouldReturnFromMaintenanceSuccessfullyAndClearReason() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator Massey Ferguson")
                .model("MF 8S")
                .type("TRACTOR")
                .operatingHours(350.0)
                .status("MAINTENANCE")
                .maintenanceReason("Ajuste de transmissão")
                .build();

        machinery.returnFromMaintenance(352.5);

        assertEquals("OPERATIONAL", machinery.getStatus());
        assertEquals(MachineryStatus.OPERATIONAL, machinery.getMachineryStatus());
        assertEquals(352.5, machinery.getOperatingHours());
        assertNull(machinery.getMaintenanceReason());
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
    void shouldUpdateOperatingHoursSuccessfullyAndEmitEvent() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator Fendt")
                .model("1050 Vario")
                .type("TRACTOR")
                .operatingHours(500.0)
                .build();

        machinery.updateOperatingHours(545.0);

        assertEquals(545.0, machinery.getOperatingHours());

        List<Object> events = machinery.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(MachineryOperatingHoursUpdatedEvent.class, events.get(0));
        MachineryOperatingHoursUpdatedEvent event = (MachineryOperatingHoursUpdatedEvent) events.get(0);
        assertEquals(500.0, event.previousHours());
        assertEquals(545.0, event.newHours());
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

    @Test
    void shouldThrowExceptionWhenBuildingWithBlankRequiredFields() {
        assertThrows(BusinessException.class, () ->
                Machinery.builder()
                        .farmId("")
                        .name("Trator")
                        .model("M1")
                        .type("T1")
                        .build()
        );

        assertThrows(BusinessException.class, () ->
                Machinery.builder()
                        .farmId("f1")
                        .name("")
                        .model("M1")
                        .type("T1")
                        .build()
        );
    }

    @Test
    void shouldSupportDeactivationAndReactivation() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator Reserva")
                .model("6150M")
                .type("TRACTOR")
                .status("OPERATIONAL")
                .build();

        machinery.deactivate();
        assertEquals("INACTIVE", machinery.getStatus());
        assertEquals(MachineryStatus.INACTIVE, machinery.getMachineryStatus());

        machinery.activate();
        assertEquals("OPERATIONAL", machinery.getStatus());
        assertEquals(MachineryStatus.OPERATIONAL, machinery.getMachineryStatus());
    }

    @Test
    void shouldHandleNextMaintenanceDateAsTypedLocalDateAndStringCompatibility() {
        LocalDate expectedDate = LocalDate.of(2026, 12, 25);
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Colheitadeira de Graos")
                .model("S790")
                .type("HARVESTER")
                .nextMaintenanceDate(expectedDate)
                .build();

        assertEquals(expectedDate, machinery.getNextMaintenanceDateValue());
        assertEquals("2026-12-25", machinery.getNextMaintenanceDate());

        machinery.scheduleNextMaintenance(LocalDate.of(2027, 1, 15));
        assertEquals(LocalDate.of(2027, 1, 15), machinery.getNextMaintenanceDateValue());
        assertEquals("2027-01-15", machinery.getNextMaintenanceDate());

        machinery.scheduleNextMaintenance("2027-03-01");
        assertEquals(LocalDate.of(2027, 3, 1), machinery.getNextMaintenanceDateValue());
        assertEquals("2027-03-01", machinery.getNextMaintenanceDate());

        machinery.scheduleNextMaintenance((String) null);
        assertNull(machinery.getNextMaintenanceDateValue());
        assertNull(machinery.getNextMaintenanceDate());
    }

    @Test
    void shouldSupportSendToMaintenanceWithTypedLocalDate() {
        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator Agrale")
                .model("7215")
                .type("TRACTOR")
                .build();

        LocalDate maintenanceDate = LocalDate.of(2026, 11, 20);
        machinery.sendToMaintenance("Substituição de embreagem", maintenanceDate);

        assertEquals(MachineryStatus.MAINTENANCE, machinery.getMachineryStatus());
        assertEquals(maintenanceDate, machinery.getNextMaintenanceDateValue());
        assertEquals("2026-11-20", machinery.getNextMaintenanceDate());
        assertEquals("Substituição de embreagem", machinery.getMaintenanceReason());
    }

    @Test
    void shouldThrowDateTimeParseExceptionOnMalformedDateString() {
        assertThrows(DateTimeParseException.class, () ->
                Machinery.builder()
                        .farmId("farm-001")
                        .name("Trator")
                        .model("M1")
                        .type("T1")
                        .nextMaintenanceDate("25-12-2026")
                        .build()
        );

        Machinery machinery = Machinery.builder()
                .farmId("farm-001")
                .name("Trator")
                .model("M1")
                .type("T1")
                .build();

        assertThrows(DateTimeParseException.class, () ->
                machinery.sendToMaintenance("Manutenção", "data-invalida")
        );

        assertThrows(DateTimeParseException.class, () ->
                machinery.scheduleNextMaintenance("2026/12/31")
        );
    }
}