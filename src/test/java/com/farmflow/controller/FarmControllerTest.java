package com.farmflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.JwtTokenProvider;
import com.farmflow.entity.Farm;
import com.farmflow.dto.FarmDtos.CreateFarmRequest;
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
class FarmControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private FarmRepository farmRepository;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        validJwtToken = "Bearer " + jwtTokenProvider.generateToken("user-test-id", "test@farmflow.com", "owner");
    }

    @Test
    void shouldDenyUnauthenticatedAccessToFarms() throws Exception {
        mockMvc.perform(get("/farms"))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldListFarmsWithValidToken() throws Exception {
        mockMvc.perform(get("/farms")
                .header("Authorization", validJwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldCreateNewFarmSuccessfully() throws Exception {
        var request = new CreateFarmRequest(
            "Fazenda Esperança",
            "Uberlândia, MG",
            850.0,
            "Café",
            "Yuri Vieira",
            "(34) 99123-4567"
        );

        mockMvc.perform(post("/farms")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Fazenda Esperança"))
            .andExpect(jsonPath("$.totalAreaHectares").value(850.0));
    }

    @Test
    void shouldGetFarmById() throws Exception {
        var farm = farmRepository.save(Farm.builder()
            .name("Fazenda Busca")
            .location("Goiânia, GO")
            .totalAreaHectares(500.0)
            .primaryCrop("Milho")
            .ownerName("Yuri Vieira")
            .build());

        mockMvc.perform(get("/farms/" + farm.getId())
                .header("Authorization", validJwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Fazenda Busca"));
    }
}

