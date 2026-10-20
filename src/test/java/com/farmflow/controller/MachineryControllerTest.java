package com.farmflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.JwtTokenProvider;
import com.farmflow.dto.MachineryDtos.CreateMachineryRequest;
import com.farmflow.entity.Machinery;
import com.farmflow.repository.MachineryRepository;
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
class MachineryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MachineryRepository machineryRepository;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        validJwtToken = "Bearer " + jwtTokenProvider.generateToken("user-test-id", "test@farmflow.com", "owner");
    }

    @Test
    void shouldDenyUnauthenticatedAccessToMachinery() throws Exception {
        mockMvc.perform(get("/machinery"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldListMachineryWithValidToken() throws Exception {
        mockMvc.perform(get("/machinery")
                .header("Authorization", validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldCreateNewMachinerySuccessfully() throws Exception {
        var request = new CreateMachineryRequest(
                "farm-123",
                "Trator John Deere 8R",
                "8R 370",
                "Trator",
                120.5,
                "operational",
                "2026-12-01",
                85.0
        );

        mockMvc.perform(post("/machinery")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Trator John Deere 8R"));
    }

    @Test
    void shouldGetMachineryById() throws Exception {
        var machinery = machineryRepository.save(Machinery.builder()
                .farmId("farm-123")
                .name("Colheitadeira S700")
                .model("S790")
                .type("Colheitadeira")
                .build());

        mockMvc.perform(get("/machinery/" + machinery.getId())
                .header("Authorization", validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Colheitadeira S700"));
    }
}
