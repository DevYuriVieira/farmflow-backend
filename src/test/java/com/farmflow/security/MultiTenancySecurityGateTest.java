package com.farmflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.JwtTokenProvider;
import com.farmflow.entity.Farm;
import com.farmflow.entity.InventoryItem;
import com.farmflow.entity.Machinery;
import com.farmflow.entity.User;
import com.farmflow.enums.InventoryCategory;
import com.farmflow.repository.FarmRepository;
import com.farmflow.repository.InventoryRepository;
import com.farmflow.repository.MachineryRepository;
import com.farmflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MultiTenancySecurityGateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private MachineryRepository machineryRepository;

    private Farm farmA;
    private Farm farmB;
    private User userTenantA;
    private User adminUser;
    private String tokenTenantA;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        farmA = farmRepository.save(Farm.builder()
                .name("Farm Alpha")
                .ownerName("Owner Alpha")
                .primaryCrop("Soy")
                .location("Region A")
                .totalAreaHectares(100.0)
                .build());

        farmB = farmRepository.save(Farm.builder()
                .name("Farm Beta")
                .ownerName("Owner Beta")
                .primaryCrop("Corn")
                .location("Region B")
                .totalAreaHectares(200.0)
                .build());

        userTenantA = userRepository.findByEmail("tenantA@farmflow.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("Tenant A Owner")
                        .email("tenantA@farmflow.com")
                        .password("secret")
                        .role("OWNER")
                        .farmId(farmA.getId())
                        .build()));
        userTenantA.setFarmId(farmA.getId());
        userRepository.save(userTenantA);

        adminUser = userRepository.findByEmail("superadmin@farmflow.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("Super Admin")
                        .email("superadmin@farmflow.com")
                        .password("secret")
                        .role("ADMIN")
                        .build()));

        tokenTenantA = "Bearer " + jwtTokenProvider.generateToken(userTenantA.getId(), userTenantA.getEmail(), userTenantA.getRole());
        tokenAdmin = "Bearer " + jwtTokenProvider.generateToken(adminUser.getId(), adminUser.getEmail(), adminUser.getRole());
    }

    @Test
    void shouldDenyTenantAAccessToFarmBDetails() throws Exception {
        mockMvc.perform(get("/farms/" + farmB.getId())
                .header("Authorization", tokenTenantA))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowTenantAAccessToFarmADetails() throws Exception {
        mockMvc.perform(get("/farms/" + farmA.getId())
                .header("Authorization", tokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(farmA.getId()));
    }

    @Test
    void shouldAllowAdminAccessToBothFarms() throws Exception {
        mockMvc.perform(get("/farms/" + farmA.getId())
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/farms/" + farmB.getId())
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn404WhenTenantAAccessesInventoryItemBelongingToFarmB() throws Exception {
        InventoryItem itemFarmB = inventoryRepository.save(InventoryItem.builder()
                .farmId(farmB.getId())
                .name("Beta Fertilizer")
                .category(InventoryCategory.FERTILIZER)
                .quantity(100.0)
                .unit("kg")
                .minimumThreshold(10.0)
                .build());

        mockMvc.perform(get("/inventory/" + itemFarmB.getId())
                .header("Authorization", tokenTenantA))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowAdminToAccessInventoryItemBelongingToFarmB() throws Exception {
        InventoryItem itemFarmB = inventoryRepository.save(InventoryItem.builder()
                .farmId(farmB.getId())
                .name("Beta Seeds")
                .category(InventoryCategory.SEED)
                .quantity(50.0)
                .unit("sc")
                .minimumThreshold(5.0)
                .build());

        mockMvc.perform(get("/inventory/" + itemFarmB.getId())
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemFarmB.getId()));
    }

    @Test
    void shouldReturn404WhenTenantAAccessesMachineryBelongingToFarmB() throws Exception {
        Machinery machineFarmB = machineryRepository.save(Machinery.builder()
                .farmId(farmB.getId())
                .name("Beta Harvester")
                .model("B-100")
                .type("HARVESTER")
                .build());

        mockMvc.perform(get("/machinery/" + machineFarmB.getId())
                .header("Authorization", tokenTenantA))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowTenantAAccessToOwnMachinery() throws Exception {
        Machinery machineFarmA = machineryRepository.save(Machinery.builder()
                .farmId(farmA.getId())
                .name("Alpha Tractor")
                .model("A-100")
                .type("TRACTOR")
                .build());

        mockMvc.perform(get("/machinery/" + machineFarmA.getId())
                .header("Authorization", tokenTenantA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(machineFarmA.getId()));
    }
}
