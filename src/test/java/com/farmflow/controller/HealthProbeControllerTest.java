package com.farmflow.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthProbeControllerTest {

    private DataSource dataSource;
    private ConnectionFactory rabbitConnectionFactory;
    private HealthProbeController controller;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        rabbitConnectionFactory = mock(ConnectionFactory.class);
        controller = new HealthProbeController(dataSource, rabbitConnectionFactory);
    }

    @Test
    void shouldReturnLivenessUp() {
        ResponseEntity<Map<String, Object>> response = controller.liveness();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("farmflow-backend", response.getBody().get("service"));
    }

    @Test
    void shouldReturnReadinessUpWhenDatabaseIsHealthy() throws SQLException {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        when(conn.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(conn);

        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("CONNECTED", response.getBody().get("database"));
    }

    @Test
    void shouldReturnReadinessDownWhenDatabaseFails() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        ResponseEntity<Map<String, Object>> response = controller.readiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("status"));
        assertEquals("DISCONNECTED", response.getBody().get("database"));
    }

    @Test
    void shouldReportHealthyWhenAllDependenciesAreResponsive() throws SQLException {
        java.sql.Connection sqlConn = mock(java.sql.Connection.class);
        when(sqlConn.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(sqlConn);

        Connection rabbitConn = mock(Connection.class);
        when(rabbitConn.isOpen()).thenReturn(true);
        when(rabbitConnectionFactory.createConnection()).thenReturn(rabbitConn);

        ResponseEntity<Map<String, Object>> response = controller.dependencies();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("HEALTHY", response.getBody().get("status"));
    }

    @Test
    void shouldReportDegradedWhenRabbitMqIsOfflineAndDatabaseIsHealthy() throws SQLException {
        java.sql.Connection sqlConn = mock(java.sql.Connection.class);
        when(sqlConn.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(sqlConn);

        when(rabbitConnectionFactory.createConnection()).thenThrow(new RuntimeException("RabbitMQ connection refused"));

        ResponseEntity<Map<String, Object>> response = controller.dependencies();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("DEGRADED", response.getBody().get("status"));
        assertTrue(response.getBody().get("detail").toString().contains("Transactional Outbox"));
    }

    @Test
    void shouldReportUnhealthyWhenDatabaseIsOffline() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Database connection timeout"));

        ResponseEntity<Map<String, Object>> response = controller.dependencies();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("UNHEALTHY", response.getBody().get("status"));
    }
}