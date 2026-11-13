
package com.campustrade.controller;

import com.campustrade.config.SecurityConfig;
import com.campustrade.dto.CurrentUserResponse;
import com.campustrade.service.UserProfileService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserProfileController.class)
@Import(SecurityConfig.class)
class UserProfileControllerTest {

    private static final String EMAIL =
            "student@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CurrentUserResponse profile(String nickname) {
        return new CurrentUserResponse(
                1L,
                nickname,
                EMAIL,
                "USER",
                true
        );
    }

    @Test
    void authenticatedUserCanUpdateNickname() throws Exception {

        when(userProfileService.updateNickname(
                EMAIL, "NewName"))
                .thenReturn(profile("NewName"));

        mockMvc.perform(
                patch("/api/auth/me")
                        .with(user(EMAIL).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "NewName"
                                }
                                """)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.nickname").value("NewName"))
        .andExpect(jsonPath("$.data.email").value(EMAIL))
        .andExpect(jsonPath("$.data.role").value("USER"));

        verify(userProfileService).updateNickname(
                EMAIL, "NewName"
        );
    }

    @Test
    void anonymousUserCannotUpdateNickname() throws Exception {

        mockMvc.perform(
                patch("/api/auth/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "NewName"
                                }
                                """)
        )
        .andExpect(status().isUnauthorized());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void missingCsrfTokenShouldBeForbidden() throws Exception {

        mockMvc.perform(
                patch("/api/auth/me")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "NewName"
                                }
                                """)
        )
        .andExpect(status().isForbidden());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void blankNicknameShouldBeRejected() throws Exception {

        mockMvc.perform(
                patch("/api/auth/me")
                        .with(user(EMAIL).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "   "
                                }
                                """)
        )
        .andExpect(status().isBadRequest());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void oversizedNicknameShouldBeRejected() throws Exception {

        String nickname = "A".repeat(65);

        mockMvc.perform(
                patch("/api/auth/me")
                        .with(user(EMAIL).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"nickname\":\"" + nickname + "\"}"
                        )
        )
        .andExpect(status().isBadRequest());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void unavailableAccountShouldBeRejected() throws Exception {

        when(userProfileService.updateNickname(
                eq(EMAIL), anyString()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "用户不存在或账户不可用"
                ));

        mockMvc.perform(
                patch("/api/auth/me")
                        .with(user(EMAIL).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "NewName"
                                }
                                """)
        )
        .andExpect(status().isNotFound());

        verify(userProfileService).updateNickname(
                EMAIL, "NewName"
        );
    }
}
