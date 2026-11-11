
package com.campustrade.controller;

import com.campustrade.config.SecurityConfig;
import com.campustrade.dto.CurrentUserResponse;
import com.campustrade.service.CurrentUserService;
import com.campustrade.service.EmailVerificationFacade;
import com.campustrade.service.LoginService;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
    private CurrentUserService currentUserService;

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
                List.of()
        );
    }

    // 1. 登录成功后应该创建 Session

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

    // 2. 登录后，后续请求应该能够恢复用户身份并查询个人信息

    @Test
    void sessionShouldAuthenticateLaterRequests() throws Exception {

        when(loginService.login(
                "student@example.com",
                "Password123!"
        )).thenReturn(authenticatedUser());

        when(currentUserService.getCurrentUser(
                "student@example.com"
        )).thenReturn(
                Optional.of(
                        new CurrentUserResponse(
                                1L,
                                "CampusUser",
                                "student@example.com",
                                "USER",
                                true
                        )
                )
        );

        MvcResult loginResult = mockMvc.perform(
                post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson)
        )
        .andExpect(status().isOk())
        .andReturn();

        MockHttpSession session =
                (MockHttpSession)
                        loginResult.getRequest().getSession(false);

        assertNotNull(session);

        mockMvc.perform(
                get("/api/auth/me")
                        .session(session)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.nickname")
                .value("CampusUser"))
        .andExpect(jsonPath("$.data.email")
                .value("student@example.com"))
        .andExpect(jsonPath("$.data.role")
                .value("USER"))
        .andExpect(jsonPath("$.data.emailVerified")
                .value(true))
        .andExpect(jsonPath("$.data.passwordHash")
                .doesNotExist());
    }

    // 3. 登录请求没有 CSRF Token 时应该被拒绝

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

    // 4. 密码错误时应该返回 401

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

        MockHttpSession session =
                (MockHttpSession)
                        result.getRequest().getSession(false);

        if (session != null) {
            assertNull(
                    session.getAttribute("SPRING_SECURITY_CONTEXT")
            );
        }
    }

    // 5. 匿名用户不能访问当前用户信息

    @Test
    void anonymousUserCannotAccessMe() throws Exception {

        mockMvc.perform(
                get("/api/auth/me")
        )
        .andExpect(status().isUnauthorized());
    }

    // 6. 用户不存在时，当前用户信息接口应该返回 404

    @Test
    void meShouldReturn404WhenUserDoesNotExist() throws Exception {

        when(currentUserService.getCurrentUser(
                "student@example.com"
        )).thenReturn(Optional.empty());

        mockMvc.perform(
                get("/api/auth/me")
                        .with(user("student@example.com"))
        )
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));
    }

    // 7. 正常退出登录后应该使 Session 失效

    @Test
    void logoutShouldInvalidateSession() throws Exception {

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

        MockHttpSession session =
                (MockHttpSession)
                        loginResult.getRequest().getSession(false);

        assertNotNull(session);
        assertFalse(session.isInvalid());

        mockMvc.perform(
                post("/api/auth/logout")
                        .session(session)
                        .with(csrf())
        )
        .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());

        mockMvc.perform(
                get("/api/auth/me")
        )
        .andExpect(status().isUnauthorized());
    }

    // 8. 退出登录请求没有 CSRF Token 时应该被拒绝

    @Test
    void logoutWithoutCsrfShouldBeForbidden() throws Exception {

        mockMvc.perform(
                post("/api/auth/logout")
        )
        .andExpect(status().isForbidden());
    }

@Test
void successfulLoginShouldRotateExistingSessionId() throws Exception {

    when(loginService.login(
            "student@example.com",
            "Password123!"
    )).thenReturn(authenticatedUser());

    MockHttpSession existingSession = new MockHttpSession();

    String originalSessionId = existingSession.getId();

    MvcResult result = mockMvc.perform(
            post("/api/auth/login")
                    .session(existingSession)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson)
    )
    .andExpect(status().isOk())
    .andReturn();

    MockHttpSession resultingSession =
            (MockHttpSession)
                    result.getRequest().getSession(false);

    assertNotNull(resultingSession);

    assertNotEquals(
            originalSessionId,
            resultingSession.getId(),
            "登录成功后应该更换 Session ID"
    );
}

}
