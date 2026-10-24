package com.farmflow.config;

import com.farmflow.config.idempotency.IdempotencyStorage;
import com.farmflow.config.idempotency.JdbcIdempotencyStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class IdempotencyConfig {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public IdempotencyStorage idempotencyStorage(JdbcTemplate jdbcTemplate) {
        return new JdbcIdempotencyStorage(jdbcTemplate);
    }
}
