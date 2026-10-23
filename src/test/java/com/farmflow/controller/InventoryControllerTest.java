package com.farmflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.JwtTokenProvider;
import com.farmflow.dto.InventoryDtos.CreateInventoryRequest;
import com.farmflow.entity.InventoryItem;
import com.farmflow.enums.InventoryCategory;
import com.farmflow.repository.InventoryRepository;
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
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private InventoryRepository inventoryRepository;

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
    void shouldDenyUnauthenticatedAccessToInventory() throws Exception {
        mockMvc.perform(get("/inventory"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldListInventoryWithValidToken() throws Exception {
        mockMvc.perform(get("/inventory")
                .header("Authorization", validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldCreateInventoryItemSuccessfully() throws Exception {
        var request = new CreateInventoryRequest(
                "farm-123",
                "Adubo NPK 20-05-20",
                InventoryCategory.FERTILIZER,
                5000.0,
                "kg",
                1000.0,
                18.50,
                "Galpao 1"
        );

        mockMvc.perform(post("/inventory")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Adubo NPK 20-05-20"));
    }

    @Test
    void shouldGetInventoryItemById() throws Exception {
        var item = inventoryRepository.save(InventoryItem.builder()
                .farmId("farm-123")
                .name("Semente Soja Monsoy")
                .category(InventoryCategory.SEED)
                .quantity(200.0)
                .unit("sc")
                .minimumThreshold(50.0)
                .build());

        mockMvc.perform(get("/inventory/" + item.getId())
                .header("Authorization", validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Semente Soja Monsoy"));
    }
}
