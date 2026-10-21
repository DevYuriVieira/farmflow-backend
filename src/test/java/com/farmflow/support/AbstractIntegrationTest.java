package com.farmflow.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER;
    private static final RabbitMQContainer RABBITMQ_CONTAINER;

    static {
        if (isDockerAvailable()) {
            POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("farmflow")
                    .withUsername("postgres")
                    .withPassword("postgres");
            POSTGRES_CONTAINER.start();

            RABBITMQ_CONTAINER = new RabbitMQContainer("rabbitmq:3-management-alpine");
            RABBITMQ_CONTAINER.start();
        } else {
            POSTGRES_CONTAINER = null;
            RABBITMQ_CONTAINER = null;
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
        }
        if (RABBITMQ_CONTAINER != null && RABBITMQ_CONTAINER.isRunning()) {
            registry.add("spring.rabbitmq.host", RABBITMQ_CONTAINER::getHost);
            registry.add("spring.rabbitmq.port", RABBITMQ_CONTAINER::getAmqpPort);
            registry.add("spring.rabbitmq.username", RABBITMQ_CONTAINER::getAdminUsername);
            registry.add("spring.rabbitmq.password", RABBITMQ_CONTAINER::getAdminPassword);
        }
    }
}
