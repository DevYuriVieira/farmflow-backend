package com.farmflow.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class W3CTraceContextUtilsTest {

    @Test
    void shouldGenerateValidW3CTraceParent() {
        String traceParent = W3CTraceContextUtils.generateTraceParent("corr-123");

        assertNotNull(traceParent);
        assertEquals(55, traceParent.length());
        assertTrue(traceParent.startsWith("00-"));
        assertTrue(traceParent.endsWith("-01"));
        assertTrue(W3CTraceContextUtils.isValidW3CTraceParent(traceParent));
    }

    @Test
    void shouldGenerateUniqueTraceParentsSequentially() {
        String tp1 = W3CTraceContextUtils.generateTraceParent("corr-1");
        String tp2 = W3CTraceContextUtils.generateTraceParent("corr-2");

        assertNotEquals(tp1, tp2);
        assertTrue(W3CTraceContextUtils.isValidW3CTraceParent(tp1));
        assertTrue(W3CTraceContextUtils.isValidW3CTraceParent(tp2));
    }

    @Test
    void shouldValidateCorrectly() {
        assertTrue(W3CTraceContextUtils.isValidW3CTraceParent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
        assertFalse(W3CTraceContextUtils.isValidW3CTraceParent(null));
        assertFalse(W3CTraceContextUtils.isValidW3CTraceParent("invalid-uuid"));
        assertFalse(W3CTraceContextUtils.isValidW3CTraceParent("00-short-01"));
    }
}
