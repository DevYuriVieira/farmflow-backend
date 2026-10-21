package com.farmflow.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderSecurityTest {

    @Test
    void shouldFailFastWhenSecretIsBlankOrNull() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("", 86400000));
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(null, 86400000));
    }

    @Test
    void shouldFailFastWhenSecretIsShorterThan256Bits() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("short-secret-under-32-bytes", 86400000));
    }

    @Test
    void shouldInitializeSuccessfullyWithValidSecret() {
        String validSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        assertDoesNotThrow(() -> new JwtTokenProvider(validSecret, 86400000));
    }
}
