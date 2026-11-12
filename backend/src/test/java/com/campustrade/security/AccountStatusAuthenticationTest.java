
package com.campustrade.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountStatusAuthenticationTest {

    private UserDetailsService userDetailsService;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;

    private static final String EMAIL =
            "student@example.com";

    private static final String PASSWORD =
            "Password123!";

    @BeforeEach
    void setUp() {

        userDetailsService = mock(UserDetailsService.class);
        passwordEncoder = new BCryptPasswordEncoder();

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        authenticationManager = new ProviderManager(provider);
    }

    private UserDetails account(boolean enabled) {

        return User.withUsername(EMAIL)
                .password(passwordEncoder.encode(PASSWORD))
                .roles("USER")
                .disabled(!enabled)
                .build();
    }

    private Authentication authenticate(String password) {

        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        EMAIL,
                        password
                )
        );
    }

    @Test
    void activeAndVerifiedUserCanLogin() {

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(account(true));

        Authentication result = authenticate(PASSWORD);

        assertTrue(result.isAuthenticated());
        assertEquals(EMAIL, result.getName());
    }

    @Test
    void pendingVerificationUserCannotLogin() {

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(account(false));

        assertThrows(
                DisabledException.class,
                () -> authenticate(PASSWORD)
        );
    }

    @Test
    void disabledUserCannotLogin() {

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(account(false));

        assertThrows(
                DisabledException.class,
                () -> authenticate(PASSWORD)
        );
    }

    @Test
    void activeUserWithWrongPasswordCannotLogin() {

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(account(true));

        assertThrows(
                BadCredentialsException.class,
                () -> authenticate("WrongPassword")
        );
    }
}
