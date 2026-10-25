package com.farmflow.domain;

import com.farmflow.domain.vo.Coordinates;
import com.farmflow.entity.PestScoutingRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PestScoutingRecordDomainTest {

    @Test
    void shouldCreatePestScoutingRecordAndReturnTypedCoordinates() {
        PestScoutingRecord record = PestScoutingRecord.builder()
                .farmId("farm-1")
                .plantationId("plt-1")
                .pestName("Spodoptera frugiperda")
                .severity("HIGH")
                .latitude(-19.7472)
                .longitude(-47.9392)
                .build();

        assertEquals("farm-1", record.getFarmId());
        assertEquals("plt-1", record.getPlantationId());
        assertEquals("Spodoptera frugiperda", record.getPestName());
        assertEquals("HIGH", record.getSeverity());
        assertNotNull(record.getCoordinates());
        assertEquals(-19.7472, record.getCoordinates().latitude());
        assertEquals(-47.9392, record.getCoordinates().longitude());
    }

    @Test
    void shouldUpdateCoordinatesThroughValueObject() {
        PestScoutingRecord record = PestScoutingRecord.builder()
                .farmId("farm-1")
                .plantationId("plt-1")
                .pestName("Lagarta")
                .severity("MEDIUM")
                .build();

        assertNull(record.getCoordinates());

        Coordinates newCoords = Coordinates.of(-19.7500, -47.9400);
        record.setCoordinates(newCoords);

        assertNotNull(record.getCoordinates());
        assertEquals(-19.7500, record.getLatitude());
        assertEquals(-47.9400, record.getLongitude());

        record.setCoordinates(null);
        assertNull(record.getCoordinates());
        assertNull(record.getLatitude());
        assertNull(record.getLongitude());
    }
}
