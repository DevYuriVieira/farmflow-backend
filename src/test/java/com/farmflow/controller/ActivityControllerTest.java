package com.farmflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.JwtTokenProvider;
import com.farmflow.entity.FarmActivity;
import com.farmflow.dto.ActivityDtos.CreateActivityRequest;
import com.farmflow.dto.ActivityDtos.ToggleStatusRequest;
import com.farmflow.repository.ActivityRepository;
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
class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private com.farmflow.repository.UserRepository userRepository;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        com.farmflow.entity.User testUser = userRepository.findByEmail("test@farmflow.com")
                .orElseGet(() -> userRepository.save(com.farmflow.entity.User.builder()
                        .name("Test User")
                        .email("test@farmflow.com")
                        .password("secret")
                        .role("OWNER")
                        .farmId("farm-123")
                        .build()));
        testUser.setFarmId("farm-123");
        userRepository.save(testUser);
        validJwtToken = "Bearer " + jwtTokenProvider.generateToken(testUser.getId(), testUser.getEmail(), testUser.getRole());
    }

    @Test
    void shouldCreateActivitySuccessfully() throws Exception {
        var request = new CreateActivityRequest(
            "plantation-123",
            "farm-123",
            "Aplicação de Fungicida",
            "spraying",
            "2026-10-25",
            "high",
            "Preventivo para ferrugem asiática",
            1200.0
        );

        mockMvc.perform(post("/activities")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.title").value("Aplicação de Fungicida"))
            .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void shouldToggleActivityStatus() throws Exception {
        var activity = activityRepository.save(FarmActivity.builder()
            .plantationId("plantation-test")
            .farmId("farm-123")
            .title("Vistoria de rotina")
            .type("scouting")
            .date("2026-10-20")
            .completed(false)
            .build());

        var request = new ToggleStatusRequest(true);

        mockMvc.perform(patch("/activities/" + activity.getId() + "/status")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }
}

