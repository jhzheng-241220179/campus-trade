
package com.campustrade.service;

import com.campustrade.dto.CurrentUserResponse;
import com.campustrade.mapper.UserProfileMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {

    private UserProfileMapper userProfileMapper;
    private CurrentUserService currentUserService;
    private UserProfileService userProfileService;

    private static final String EMAIL =
            "student@example.com";

    @BeforeEach
    void setUp() {

        userProfileMapper = mock(UserProfileMapper.class);
        currentUserService = mock(CurrentUserService.class);

        userProfileService = new UserProfileService(
                userProfileMapper,
                currentUserService
        );
    }

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
    void shouldUpdateNicknameSuccessfully() {

        when(userProfileMapper.updateNickname(
                EMAIL, "NewName"))
                .thenReturn(1);

        when(currentUserService.getCurrentUser(EMAIL))
                .thenReturn(Optional.of(profile("NewName")));

        CurrentUserResponse result =
                userProfileService.updateNickname(
                        EMAIL,
                        "NewName"
                );

        assertEquals(1L, result.id());
        assertEquals("NewName", result.nickname());
        assertEquals(EMAIL, result.email());
        assertEquals("USER", result.role());
        assertTrue(result.emailVerified());

        verify(userProfileMapper).updateNickname(
                EMAIL,
                "NewName"
        );
    }

    @Test
    void shouldTrimNicknameBeforeUpdating() {

        when(userProfileMapper.updateNickname(
                EMAIL, "CampusUser"))
                .thenReturn(1);

        when(currentUserService.getCurrentUser(EMAIL))
                .thenReturn(Optional.of(profile("CampusUser")));

        CurrentUserResponse result =
                userProfileService.updateNickname(
                        EMAIL,
                        "  CampusUser  "
                );

        assertEquals("CampusUser", result.nickname());

        verify(userProfileMapper).updateNickname(
                EMAIL,
                "CampusUser"
        );
    }

    @Test
    void shouldRejectBlankNickname() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> userProfileService.updateNickname(
                                EMAIL,
                                "   "
                        )
                );

        assertEquals("昵称不能为空", exception.getMessage());

        verifyNoInteractions(userProfileMapper);
        verifyNoInteractions(currentUserService);
    }

    @Test
    void shouldRejectNicknameLongerThan64Characters() {

        String nickname = "A".repeat(65);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> userProfileService.updateNickname(
                                EMAIL,
                                nickname
                        )
                );

        assertEquals(
                "昵称不能超过64个字符",
                exception.getMessage()
        );

        verifyNoInteractions(userProfileMapper);
        verifyNoInteractions(currentUserService);
    }

    @Test
    void shouldRejectWhenAccountIsNotAvailable() {

        when(userProfileMapper.updateNickname(
                EMAIL, "NewName"))
                .thenReturn(0);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userProfileService.updateNickname(
                                EMAIL,
                                "NewName"
                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(currentUserService, never())
                .getCurrentUser(anyString());
    }

    @Test
    void shouldRejectWhenUpdatedUserCannotBeFound() {

        when(userProfileMapper.updateNickname(
                EMAIL, "NewName"))
                .thenReturn(1);

        when(currentUserService.getCurrentUser(EMAIL))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userProfileService.updateNickname(
                                EMAIL,
                                "NewName"
                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }
}
