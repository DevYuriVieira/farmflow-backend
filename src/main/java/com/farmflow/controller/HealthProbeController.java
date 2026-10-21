package com.farmflow.controller;

import com.farmflow.service.OutboxService;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/health")
public class HealthProbeController {

    private final DataSource dataSource;
    private final ConnectionFactory rabbitConnectionFactory;
    private final OutboxService outboxService;

    public HealthProbeController(DataSource dataSource, ConnectionFactory rabbitConnectionFactory) {
        this(dataSource, rabbitConnectionFactory, null);
    }

    @Autowired
    public HealthProbeController(DataSource dataSource,
                                 ConnectionFactory rabbitConnectionFactory,
                                 @Autowired(required = false) OutboxService outboxService) {
        this.dataSource = dataSource;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
        this.outboxService = outboxService;
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        body.put("service", "farmflow-backend");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        boolean dbHealthy = checkDatabaseConnectivity();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbHealthy ? "UP" : "DOWN");
        body.put("database", dbHealthy ? "CONNECTED" : "DISCONNECTED");
        body.put("timestamp", Instant.now().toString());

        if (!dbHealthy) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/dependencies")
    public ResponseEntity<Map<String, Object>> dependencies() {
        boolean dbHealthy = checkDatabaseConnectivity();
        boolean rabbitHealthy = checkRabbitMqConnectivity();

        Map<String, Object> dependencies = new LinkedHashMap<>();
        dependencies.put("database", dbHealthy ? "HEALTHY" : "UNHEALTHY");
        dependencies.put("rabbitmq", rabbitHealthy ? "HEALTHY" : "UNHEALTHY");

        Double outboxLag = null;
        Long deadCount = null;
        if (outboxService != null) {
            outboxLag = outboxService.getOldestMessageLagSeconds();
            deadCount = outboxService.getDeadLetterCount();
            dependencies.put("outboxLagSeconds", outboxLag);
            dependencies.put("outboxDeadCount", deadCount);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("dependencies", dependencies);

        if (!dbHealthy) {
            body.put("status", "UNHEALTHY");
            body.put("detail", "Primary relational persistence is offline. Cannot accept transactions.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        if (!rabbitHealthy) {
            body.put("status", "DEGRADED");
            body.put("detail", "Message broker is offline. Synchronous write commands persist safely via Transactional Outbox.");
            return ResponseEntity.ok(body);
        }

        if (outboxLag != null && (outboxLag > 120.0 || (deadCount != null && deadCount > 0))) {
            body.put("status", "DEGRADED");
            body.put("detail", "Outbox message backlog lag is elevated or dead letters require operator inspection.");
            return ResponseEntity.ok(body);
        }

        body.put("status", "HEALTHY");
        body.put("detail", "All operational dependencies are responsive.");
        return ResponseEntity.ok(body);
    }

    private boolean checkDatabaseConnectivity() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRabbitMqConnectivity() {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    var conn = rabbitConnectionFactory.createConnection();
                    boolean open = conn.isOpen();
                    conn.close();
                    return open;
                } catch (Exception e) {
                    return false;
                }
            }).get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }
}