package com.farmflow.concurrency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class VirtualThreadsSanityTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Should verify spring.threads.virtual.enabled is set to true")
    void shouldVerifyVirtualThreadsEnabledInConfig() {
        String virtualEnabled = environment.getProperty("spring.threads.virtual.enabled");
        assertThat(virtualEnabled).isEqualTo("true");
    }

    @Test
    @DisplayName("Should confirm Java 21 runtime can spawn and execute on virtual threads")
    void shouldExecuteTaskOnVirtualThread() throws ExecutionException, InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> future = executor.submit(() -> Thread.currentThread().isVirtual());
            Boolean isVirtual = future.get();
            assertThat(isVirtual).isTrue();
        }
    }
}
