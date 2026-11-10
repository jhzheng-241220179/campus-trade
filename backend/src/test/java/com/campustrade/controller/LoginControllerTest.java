
package com.campustrade.controller;

import com.campustrade.config.SecurityConfig;
import com.campustrade.service.EmailVerificationFacade;
import com.campustrade.service.LoginService;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({
        LoginController.class,
        AuthController.class
})
@Import(SecurityConfig.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private EmailVerificationFacade emailVerificationFacade;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private final String loginJson = """
            {
              "email": "student@example.com",
              "password": "Password123!"
            }
            """;

    private Authentication authenticatedUser() {
        return UsernamePasswordAuthenticationToken.authenticated(
                "student@example.com",
                null,
                java.util.List.of()
        );
    }

    @Test
    void successfulLoginShouldCreateSession() throws Exception {

        when(loginService.login(
                "student@example.com",
                "Password123!"
        )).thenReturn(authenticatedUser());

        MvcResult result = mockMvc.perform(
                post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data")
                .value("student@example.com"))
        .andReturn();

        assertNotNull(
                result.getRequest().getSession(false)
        );
    }

    @Test
    void sessionShouldAuthenticateLaterRequests() throws Exception {

        when(loginService.login(
                "student@example.com",
                "Password123!"
        )).thenReturn(authenticatedUser());

        MvcResult loginResult = mockMvc.perform(
                post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson)
        )
        .andExpect(status().isOk())
        .andReturn();

        HttpSession session =
                loginResult.getRequest().getSession(false);

        assertNotNull(session);

        mockMvc.perform(
                get("/api/auth/me")
                        .session(
                            (org.springframework.mock.web.MockHttpSession)
                            session
                        )
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data")
                .value("student@example.com"));
    }

    @Test
    void loginWithoutCsrfShouldBeForbidden() throws Exception {

        mockMvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson)
        )
        .andExpect(status().isForbidden());

        verifyNoInteractions(loginService);
    }

    @Test
    void wrongPasswordShouldReturn401() throws Exception {

        when(loginService.login(
                "student@example.com",
                "Password123!"
        )).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        MvcResult result = mockMvc.perform(
                post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson)
        )
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401))
        .andReturn();

        HttpSession session =
                result.getRequest().getSession(false);

        if (session != null) {
            assertNull(session.getAttribute(
                    "SPRING_SECURITY_CONTEXT"
            ));
        }
    }

    @Test
    void anonymousUserCannotAccessMe() throws Exception {

        mockMvc.perform(
                get("/api/auth/me")
        )
        .andExpect(status().isUnauthorized());
    }
}
