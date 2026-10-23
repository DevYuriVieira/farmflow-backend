package com.farmflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.JwtTokenProvider;
import com.farmflow.entity.Farm;
import com.farmflow.dto.PlantationDtos.CreatePlantationRequest;
import com.farmflow.repository.FarmRepository;
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
class PlantationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private com.farmflow.repository.UserRepository userRepository;

    private String validJwtToken;
    private Farm testFarm;

    @BeforeEach
    void setUp() {
        testFarm = farmRepository.save(Farm.builder()
            .name("Fazenda Talhao Test")
            .location("Campinas, SP")
            .totalAreaHectares(600.0)
            .primaryCrop("Soja")
            .ownerName("Yuri Vieira")
            .build());

        com.farmflow.entity.User testUser = userRepository.findByEmail("test@farmflow.com")
                .orElseGet(() -> userRepository.save(com.farmflow.entity.User.builder()
                        .name("Test User")
                        .email("test@farmflow.com")
                        .password("secret")
                        .role("OWNER")
                        .farmId(testFarm.getId())
                        .build()));
        testUser.setFarmId(testFarm.getId());
        userRepository.save(testUser);
        validJwtToken = "Bearer " + jwtTokenProvider.generateToken(testUser.getId(), testUser.getEmail(), testUser.getRole());
    }

    @Test
    void shouldCreatePlantationSuccessfully() throws Exception {
        var request = new CreatePlantationRequest(
            testFarm.getId(),
            "Talhão 05",
            "Soja",
            "TMG 7062",
            120.0,
            "growing",
            "2026-09-01",
            "2027-01-15",
            450.0,
            "Área bem irrigada"
        );

        mockMvc.perform(post("/plantations")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Talhão 05"))
            .andExpect(jsonPath("$.crop").value("Soja"));
    }

    @Test
    void shouldFilterPlantationsByFarmId() throws Exception {
        mockMvc.perform(get("/plantations?farmId=" + testFarm.getId())
                .header("Authorization", validJwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}

