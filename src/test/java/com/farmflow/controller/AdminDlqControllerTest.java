package com.farmflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.JwtTokenProvider;
import com.farmflow.dto.AdminDtos.DlqReplayRequest;
import com.farmflow.dto.AdminDtos.DlqReplayResponse;
import com.farmflow.entity.User;
import com.farmflow.repository.UserRepository;
import com.farmflow.service.AdminDlqService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDlqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AdminDlqService adminDlqService;

    private String adminToken;
    private String operatorToken;

    @BeforeEach
    void setUp() {
        User admin = userRepository.findByEmail("admin-dlq@farmflow.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("Admin User")
                        .email("admin-dlq@farmflow.com")
                        .password("secret")
                        .role("ADMIN")
                        .build()));

        User operator = userRepository.findByEmail("operator-dlq@farmflow.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("Operator User")
                        .email("operator-dlq@farmflow.com")
                        .password("secret")
                        .role("OPERATOR")
                        .build()));

        adminToken = "Bearer " + jwtTokenProvider.generateToken(admin.getId(), admin.getEmail(), admin.getRole());
        operatorToken = "Bearer " + jwtTokenProvider.generateToken(operator.getId(), operator.getEmail(), operator.getRole());
    }

    @Test
    @DisplayName("POST /admin/dlq/replay: Rejeita chamadas anônimas com 401")
    void shouldDenyUnauthenticatedReplay() throws Exception {
        mockMvc.perform(post("/admin/dlq/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /admin/dlq/replay: Rejeita papéis sem ADMIN com 403")
    void shouldDenyNonAdminRole() throws Exception {
        mockMvc.perform(post("/admin/dlq/replay")
                        .header("Authorization", operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/dlq/replay: Permite execução para usuário ADMIN")
    void shouldAllowAdminReplay() throws Exception {
        when(adminDlqService.replayMessages(any())).thenReturn(
                new DlqReplayResponse(2, List.of("msg-1", "msg-2"), Instant.now(), "COMPLETED")
        );

        mockMvc.perform(post("/admin/dlq/replay")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DlqReplayRequest(10, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayedCount").value(2))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
