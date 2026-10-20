package com.farmflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.JwtTokenProvider;
import com.farmflow.dto.ScoutingDtos.CreatePestRecordRequest;
import com.farmflow.entity.PestScoutingRecord;
import com.farmflow.repository.PestRecordRepository;
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
class ScoutingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PestRecordRepository pestRecordRepository;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        validJwtToken = "Bearer " + jwtTokenProvider.generateToken("user-test-id", "test@farmflow.com", "owner");
    }

    @Test
    void shouldDenyUnauthenticatedAccessToScouting() throws Exception {
        mockMvc.perform(get("/scouting/farm/farm-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldListScoutingRecordsByFarm() throws Exception {
        mockMvc.perform(get("/scouting/farm/farm-123")
                .header("Authorization", validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldCreatePestRecordSuccessfully() throws Exception {
        var request = new CreatePestRecordRequest(
                "farm-123",
                "plantation-456",
                "Ferrugem Asiatica",
                "HIGH",
                0.95,
                "https://storage.farmflow.com/pests/p1.jpg",
                -18.91,
                -48.27,
                "Aplicacao imediata de fungicida"
        );

        mockMvc.perform(post("/scouting")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.pestName").value("Ferrugem Asiatica"));
    }
}
