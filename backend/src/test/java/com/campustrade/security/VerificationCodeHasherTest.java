package com.campustrade.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerificationCodeHasherTest {

    private final VerificationCodeHasher hasher =
            new VerificationCodeHasher(
                    "TestSecretKeyForCampusTrade1234567890"
            );

    @Test
    void shouldHashAndVerifyCode() {

        String hash = hasher.hash(1L, "123456");

        assertEquals(64, hash.length());
        assertNotEquals("123456", hash);

        assertTrue(hasher.matches(
                1L, "123456", hash
        ));

        assertFalse(hasher.matches(
                1L, "654321", hash
        ));

        assertFalse(hasher.matches(
                2L, "123456", hash
        ));
    }
}