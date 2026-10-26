package com.campustrade.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void shouldEncodeAndVerifyPassword() {

        String password = "CampusTrade123!";

        String hash1 = encoder.encode(password);
        String hash2 = encoder.encode(password);

        assertNotEquals(password, hash1);
        assertNotEquals(hash1, hash2);

        assertTrue(encoder.matches(password, hash1));
        assertFalse(encoder.matches("WrongPassword123!", hash1));
    }
}