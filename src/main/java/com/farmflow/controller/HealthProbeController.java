package com.farmflow.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
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

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthProbeController {

    private final DataSource dataSource;
    private final ConnectionFactory rabbitConnectionFactory;

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
            var conn = rabbitConnectionFactory.createConnection();
            boolean open = conn.isOpen();
            conn.close();
            return open;
        } catch (Exception e) {
            return false;
        }
    }
}