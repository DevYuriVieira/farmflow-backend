package com.farmflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.JwtTokenProvider;
import com.farmflow.dto.TelemetryDtos.CreateTelemetryRequest;
import com.farmflow.enums.TelemetryType;
import com.farmflow.repository.TelemetryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TelemetryRepository telemetryRepository;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        validJwtToken = "Bearer " + jwtTokenProvider.generateToken("user-test-id", "test@farmflow.com", "owner");
    }

    @Test
    void shouldDenyUnauthenticatedAccessToTelemetry() throws Exception {
        mockMvc.perform(get("/telemetry/farm/farm-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldListTelemetryReadingsByFarm() throws Exception {
        mockMvc.perform(get("/telemetry/farm/farm-123")
                .header("Authorization", validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldRecordTelemetryReadingSuccessfully() throws Exception {
        var request = new CreateTelemetryRequest(
                "farm-123",
                "plantation-456",
                "sensor-soil-01",
                TelemetryType.SOIL_MOISTURE,
                35.5,
                "%",
                "98%"
        );

        mockMvc.perform(post("/telemetry")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sensorId").value("sensor-soil-01"));
    }
}
