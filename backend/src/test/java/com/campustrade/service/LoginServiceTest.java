
package com.campustrade.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private LoginService loginService;

    @Test
    void correctCredentialsShouldReturnAuthentication() {

        Authentication authenticated =
                UsernamePasswordAuthenticationToken.authenticated(
                        "student@example.com",
                        null,
                        java.util.List.of()
                );

        when(authenticationManager.authenticate(any()))
                .thenReturn(authenticated);

        Authentication result = loginService.login(
                "student@example.com",
                "Password123!"
        );

        assertTrue(result.isAuthenticated());
        assertEquals("student@example.com", result.getName());
    }

    @Test
    void emailShouldBeNormalized() {

        when(authenticationManager.authenticate(any()))
                .thenAnswer(invocation -> {

                    Authentication input = invocation.getArgument(0);

                    assertEquals(
                            "student@example.com",
                            input.getPrincipal()
                    );

                    assertEquals(
                            "Password123!",
                            input.getCredentials()
                    );

                    assertFalse(input.isAuthenticated());

                    return UsernamePasswordAuthenticationToken.authenticated(
                            input.getPrincipal(),
                            null,
                            java.util.List.of()
                    );
                });

        loginService.login(
                " STUDENT@EXAMPLE.COM ",
                "Password123!"
        );

        verify(authenticationManager).authenticate(any());
    }

    @Test
    void wrongPasswordShouldPropagateException() {

        when(authenticationManager.authenticate(any()))
                .thenThrow(
                        new BadCredentialsException(
                                "Bad credentials"
                        )
                );

        assertThrows(
                BadCredentialsException.class,
                () -> loginService.login(
                        "student@example.com",
                        "WrongPassword"
                )
        );
    }

    @Test
    void blankPasswordShouldBeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> loginService.login(
                        "student@example.com",
                        ""
                )
        );

        verifyNoInteractions(authenticationManager);
    }
}
