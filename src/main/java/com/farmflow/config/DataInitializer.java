package com.farmflow.config;

import com.farmflow.domain.*;
import com.farmflow.domain.enums.InventoryCategory;
import com.farmflow.domain.enums.TelemetryType;
import com.farmflow.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final PlantationRepository plantationRepository;
    private final ActivityRepository activityRepository;
    private final TelemetryRepository telemetryRepository;
    private final InventoryRepository inventoryRepository;
    private final MachineryRepository machineryRepository;
    private final PestRecordRepository pestRecordRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        log.info("🌱 Inicializando e semeando o ecossistema FarmFlow...");

        // 1. Usuário Administrador Demo
        User demoUser = User.builder()
            .name("Produtor Yuri")
            .email("admin@farmflow.com")
            .password(passwordEncoder.encode("admin123"))
            .role("owner")
            .build();
        demoUser = userRepository.save(demoUser);

        // 2. Fazenda Principal
        Farm farm = Farm.builder()
            .name("Fazenda Santa Fé")
            .location("Ribeirão Preto, SP")
            .totalAreaHectares(1250.0)
            .primaryCrop("Soja")
            .ownerName("Produtor Yuri")
            .contactPhone("(16) 99876-5432")
            .status("active")
            .build();
        farm = farmRepository.save(farm);

        demoUser.setFarmId(farm.getId());
        userRepository.save(demoUser);

        // 3. Talhões
        Plantation talhao1 = Plantation.builder()
            .farmId(farm.getId())
            .name("Talhão Norte 01 - Soja")
            .crop("Soja")
            .variety("TMG 7062 IPRO")
            .areaHectares(320.0)
            .status("growing")
            .plantingDate(LocalDate.now().minusMonths(2).toString())
            .harvestEstimateDate(LocalDate.now().plusMonths(2).toString())
            .expectedYieldTons(1150.0)
            .notes("Desenvolvimento vegetativo excelente, boa retenção hídrica.")
            .build();
        talhao1 = plantationRepository.save(talhao1);

        Plantation talhao2 = Plantation.builder()
            .farmId(farm.getId())
            .name("Talhão Sul 02 - Milho Safrinha")
            .crop("Milho")
            .variety("DKB 390 PRO3")
            .areaHectares(210.0)
            .status("planted")
            .plantingDate(LocalDate.now().minusDays(15).toString())
            .harvestEstimateDate(LocalDate.now().plusMonths(4).toString())
            .expectedYieldTons(1400.0)
            .notes("Germinação homogênea em 94% da área monitorada.")
            .build();
        talhao2 = plantationRepository.save(talhao2);

        // 4. Atividades
        activityRepository.save(FarmActivity.builder()
            .farmId(farm.getId())
            .plantationId(talhao1.getId())
            .title("Vistoria Fisiológica e Pragas")
            .type("scouting")
            .date(LocalDate.now().plusDays(1).toString())
            .priority("high")
            .notes("Atenção especial a focos iniciais de lagarta da soja.")
            .costEstimate(350.0)
            .completed(false)
            .build());

        activityRepository.save(FarmActivity.builder()
            .farmId(farm.getId())
            .plantationId(talhao2.getId())
            .title("Adubação de Cobertura com Nitrogênio")
            .type("fertilization")
            .date(LocalDate.now().plusDays(5).toString())
            .priority("medium")
            .notes("Aplicação de Ureia protegida conforme laudo de solo.")
            .costEstimate(4800.0)
            .completed(false)
            .build());

        // 5. Telemetria (Silos e Umidade do Solo)
        telemetryRepository.save(TelemetryReading.builder()
            .farmId(farm.getId())
            .sensorId("SILO-GRAOS-01")
            .type(TelemetryType.SILO_LEVEL)
            .value(78.5)
            .unit("%")
            .batteryLevel("98%")
            .build());

        telemetryRepository.save(TelemetryReading.builder()
            .farmId(farm.getId())
            .plantationId(talhao1.getId())
            .sensorId("SOLO-TALHAO-01")
            .type(TelemetryType.SOIL_MOISTURE)
            .value(68.2)
            .unit("%")
            .batteryLevel("85%")
            .build());

        // 6. Inventário de Insumos
        inventoryRepository.save(InventoryItem.builder()
            .farmId(farm.getId())
            .name("Fertilizante NPK 04-14-08")
            .category(InventoryCategory.FERTILIZER)
            .quantity(15000.0)
            .unit("kg")
            .minimumThreshold(3000.0)
            .unitPrice(3.20)
            .location("Galpão Principal - Ala B")
            .build());

        inventoryRepository.save(InventoryItem.builder()
            .farmId(farm.getId())
            .name("Sementes de Soja TMG 7062")
            .category(InventoryCategory.SEED)
            .quantity(420.0)
            .unit("sacas")
            .minimumThreshold(50.0)
            .unitPrice(220.0)
            .location("Armazém Climatizado 01")
            .build());

        // 7. Maquinário & Frota
        machineryRepository.save(Machinery.builder()
            .farmId(farm.getId())
            .name("Trator John Deere 8R 370")
            .model("8R 370 2024")
            .type("Trator Pesado")
            .operatingHours(842.0)
            .status("OPERATIONAL")
            .nextMaintenanceDate(LocalDate.now().plusMonths(1).toString())
            .fuelLevelPercent(82.0)
            .build());

        machineryRepository.save(Machinery.builder()
            .farmId(farm.getId())
            .name("Colheitadeira New Holland CR 8.90")
            .model("CR 8.90 Dual Rotor")
            .type("Colheitadeira")
            .operatingHours(1210.0)
            .status("OPERATIONAL")
            .nextMaintenanceDate(LocalDate.now().plusMonths(2).toString())
            .fuelLevelPercent(65.0)
            .build());

        // 8. Registro de Pragas (Scouting)
        pestRecordRepository.save(PestScoutingRecord.builder()
            .farmId(farm.getId())
            .plantationId(talhao1.getId())
            .pestName("Lagarta-da-soja (Anticarsia gemmatalis)")
            .severity("MEDIUM")
            .confidenceScore(0.92)
            .latitude(-21.1767)
            .longitude(-47.8208)
            .recommendedAction("Monitorar nível de desfolha. Aplicar inseticida biológico caso atinja 15%.")
            .detectedAt(Instant.now().minusSeconds(86400))
            .build());

        log.info("✅ Dados e módulos agrícolas inicializados! E-mail: admin@farmflow.com | Senha: admin123");
    }
}