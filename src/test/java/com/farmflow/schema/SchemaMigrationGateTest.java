package com.farmflow.schema;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class SchemaMigrationGateTest {

    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER;
    private static final boolean HAS_EXTERNAL_POSTGRES;

    static {
        String envUrl = System.getenv("SPRING_DATASOURCE_URL");
        HAS_EXTERNAL_POSTGRES = envUrl != null && envUrl.startsWith("jdbc:postgresql:");

        if (!HAS_EXTERNAL_POSTGRES && isDockerAvailable()) {
            PostgreSQLContainer<?> container = null;
            try {
                container = new PostgreSQLContainer<>("postgres:17-alpine")
                        .withDatabaseName("farmflow_gate")
                        .withUsername("postgres")
                        .withPassword("postgres");
                container.start();
            } catch (Throwable t) {
                container = null;
            }
            POSTGRES_CONTAINER = container;
        } else {
            POSTGRES_CONTAINER = null;
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES_CONTAINER != null && POSTGRES_CONTAINER.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
            registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.baseline-on-migrate", () -> "true");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        } else if (HAS_EXTERNAL_POSTGRES) {
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.baseline-on-migrate", () -> "true");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        }
    }

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsWithRealMigrations() {
        boolean canRun = (POSTGRES_CONTAINER != null && POSTGRES_CONTAINER.isRunning()) || HAS_EXTERNAL_POSTGRES;
        Assumptions.assumeTrue(canRun,
                "Neither Testcontainers Docker nor external PostgreSQL is available. Skipping real PostgreSQL migration gate validation.");

        assertNotNull(jdbcTemplate, "JdbcTemplate must be present in application context");

        Integer machineryCount = jdbcTemplate.queryForObject("SELECT count(*) FROM machinery", Integer.class);
        assertNotNull(machineryCount);

        Integer inventoryCount = jdbcTemplate.queryForObject("SELECT count(*) FROM inventory_items", Integer.class);
        assertNotNull(inventoryCount);

        Integer pestRecordsCount = jdbcTemplate.queryForObject("SELECT count(*) FROM pest_scouting_records", Integer.class);
        assertNotNull(pestRecordsCount);
    }
}
