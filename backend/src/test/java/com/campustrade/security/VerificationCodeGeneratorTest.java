package com.campustrade.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerificationCodeGeneratorTest {

    private final VerificationCodeGenerator generator =
            new VerificationCodeGenerator();

    @Test
    void shouldGenerateSixDigitCode() {

        for (int i = 0; i < 100; i++) {

            String code = generator.generate();

            assertNotNull(code);
            assertTrue(code.matches("[0-9]{6}"));
        }
    }
}