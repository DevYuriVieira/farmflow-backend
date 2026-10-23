package com.farmflow.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncConfigTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldPropagateMdcContextToAsyncThreadViaTaskDecorator() throws InterruptedException {
        AsyncConfig config = new AsyncConfig();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.farmTaskExecutor();

        MDC.put("correlationId", "trace-async-1234");
        MDC.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        AtomicReference<String> asyncCorrelationId = new AtomicReference<>();
        AtomicReference<String> asyncTraceParent = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            asyncCorrelationId.set(MDC.get("correlationId"));
            asyncTraceParent.set(MDC.get("traceparent"));
            latch.countDown();
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals("trace-async-1234", asyncCorrelationId.get());
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", asyncTraceParent.get());

        executor.destroy();
    }
}
