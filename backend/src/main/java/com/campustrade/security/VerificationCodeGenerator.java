package com.campustrade.security;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class VerificationCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        int value = random.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}