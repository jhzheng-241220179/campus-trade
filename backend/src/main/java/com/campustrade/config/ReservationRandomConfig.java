
package com.campustrade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@Configuration
public class ReservationRandomConfig {

    @Bean("reservationRandomGenerator")
    public RandomGenerator reservationRandomGenerator() {
        return new SecureRandom();
    }
}
