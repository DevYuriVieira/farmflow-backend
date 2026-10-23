package com.farmflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.JwtTokenProvider;
import com.farmflow.dto.MachineryDtos.*;
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

import com.farmflow.support.AbstractIntegrationTest;

@AutoConfigureMockMvc
class MachineryControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MachineryRepository machineryRepository;

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
    void shouldDenyUnauthenticatedAccessToMachinery() throws Exception {
        mockMvc.perform(get("/machinery"))
                .andExpect(status().isUnauthorized());
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

    @Test
    void shouldSendMachineryToMaintenanceSuccessfully() throws Exception {
        var machinery = machineryRepository.save(Machinery.builder()
                .farmId("farm-123")
                .name("Trator 7M")
                .model("7M 230")
                .type("Trator")
                .build());

        var request = new SendToMaintenanceRequest("Troca de óleo preventiva", "2026-11-15");

        mockMvc.perform(post("/machinery/" + machinery.getId() + "/maintenance")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.maintenanceReason").value("Troca de óleo preventiva"));
    }

    @Test
    void shouldReturnConflictWhenSendingAlreadyInMaintenanceMachineryToMaintenance() throws Exception {
        var machinery = machineryRepository.save(Machinery.builder()
                .farmId("farm-123")
                .name("Pulverizador M4040")
                .model("M4040")
                .type("Pulverizador")
                .build());
        machinery.sendToMaintenance("Initial repair", "2026-11-20");
        machineryRepository.save(machinery);

        var request = new SendToMaintenanceRequest("Reparo redundante", "2026-11-25");

        mockMvc.perform(post("/machinery/" + machinery.getId() + "/maintenance")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid Machinery State Transition"))
                .andExpect(jsonPath("$.detail").value("Machinery is already under maintenance."));
    }

    @Test
    void shouldReturnMachineryFromMaintenanceSuccessfully() throws Exception {
        var machinery = machineryRepository.save(Machinery.builder()
                .farmId("farm-123")
                .name("Colheitadeira X9")
                .model("X9 1000")
                .type("Colheitadeira")
                .operatingHours(50.0)
                .build());
        machinery.sendToMaintenance("Ajuste de esteira", "2026-11-10");
        machineryRepository.save(machinery);

        var request = new ReturnFromMaintenanceRequest(55.0);

        mockMvc.perform(post("/machinery/" + machinery.getId() + "/return-from-maintenance")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPERATIONAL"))
                .andExpect(jsonPath("$.operatingHours").value(55.0));
    }

    @Test
    void shouldUpdateOperatingHoursSuccessfully() throws Exception {
        var machinery = machineryRepository.save(Machinery.builder()
                .farmId("farm-123")
                .name("Trator 6M")
                .model("6135M")
                .type("Trator")
                .operatingHours(100.0)
                .build());

        var request = new UpdateOperatingHoursRequest(125.0);

        mockMvc.perform(post("/machinery/" + machinery.getId() + "/operating-hours")
                .header("Authorization", validJwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatingHours").value(125.0));
    }
}
