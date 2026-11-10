
package com.campustrade.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordAuthenticationTest {

    private AuthenticationManager authenticationManager;
    private UserDetailsService userDetailsService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {

        userDetailsService = mock(UserDetailsService.class);
        passwordEncoder = new BCryptPasswordEncoder();

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        authenticationManager = new ProviderManager(provider);
    }

    @Test
    void correctPasswordShouldAuthenticate() {

        String hash = passwordEncoder.encode("Password123!");

        when(userDetailsService.loadUserByUsername(
                "student@example.com"
        )).thenReturn(
                User.withUsername("student@example.com")
                        .password(hash)
                        .roles("USER")
                        .build()
        );

        Authentication result = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        "student@example.com",
                        "Password123!"
                )
        );

        assertTrue(result.isAuthenticated());
        assertEquals("student@example.com", result.getName());
    }

    @Test
    void wrongPasswordShouldBeRejected() {

        String hash = passwordEncoder.encode("Password123!");

        when(userDetailsService.loadUserByUsername(
                "student@example.com"
        )).thenReturn(
                User.withUsername("student@example.com")
                        .password(hash)
                        .roles("USER")
                        .build()
        );

        assertThrows(
                BadCredentialsException.class,
                () -> authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                "student@example.com",
                                "WrongPassword"
                        )
                )
        );
    }

    @Test
    void disabledUserShouldBeRejected() {

        String hash = passwordEncoder.encode("Password123!");

        when(userDetailsService.loadUserByUsername(
                "student@example.com"
        )).thenReturn(
                User.withUsername("student@example.com")
                        .password(hash)
                        .roles("USER")
                        .disabled(true)
                        .build()
        );

        assertThrows(
                DisabledException.class,
                () -> authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                "student@example.com",
                                "Password123!"
                        )
                )
        );
    }
}
