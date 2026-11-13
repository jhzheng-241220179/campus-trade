
package com.campustrade;

import com.campustrade.dto.CurrentUserResponse;
import com.campustrade.dto.UserRegisterRequest;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;
import com.campustrade.service.UserProfileService;
import com.campustrade.service.UserService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserProfileIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String createPendingUser() {

        String email = "profile-" +
                UUID.randomUUID() +
                "@example.com";

        UserRegisterRequest request =
                mock(UserRegisterRequest.class);

        when(request.email()).thenReturn(email);
        when(request.password()).thenReturn("Password123!");
        when(request.nickname()).thenReturn("TestUser");

        Long userId = userService.register(request);

        assertNotNull(userId);

        return email;
    }

    private User activateUser(String email) {

        User user = userMapper.findByEmail(email);

        assertNotNull(user);

        assertEquals(
                1,
                userMapper.activateEmail(user.getId())
        );

        return user;
    }

    @Test
    void activeUserShouldUpdateNicknameSuccessfully() {

        String email = createPendingUser();

        activateUser(email);

        CurrentUserResponse result =
                userProfileService.updateNickname(
                        email,
                        "NewNickname"
                );

        assertNotNull(result);
        assertEquals("NewNickname", result.nickname());
        assertEquals(email, result.email());

        String databaseNickname = jdbcTemplate.queryForObject(
                "SELECT nickname FROM users WHERE email = ?",
                String.class,
                email
        );

        assertEquals("NewNickname", databaseNickname);
    }

    @Test
    void pendingUserShouldNotUpdateNickname() {

        String email = createPendingUser();

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userProfileService.updateNickname(
                                email,
                                "NewNickname"
                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        String databaseNickname = jdbcTemplate.queryForObject(
                "SELECT nickname FROM users WHERE email = ?",
                String.class,
                email
        );

        assertEquals("TestUser", databaseNickname);
    }

    @Test
    void disabledUserShouldNotUpdateNickname() {

        String email = createPendingUser();

        User user = activateUser(email);

        int updated = jdbcTemplate.update(
                "UPDATE users SET status = ? WHERE id = ?",
                "DISABLED",
                user.getId()
        );

        assertEquals(1, updated);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userProfileService.updateNickname(
                                email,
                                "NewNickname"
                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        String databaseNickname = jdbcTemplate.queryForObject(
                "SELECT nickname FROM users WHERE email = ?",
                String.class,
                email
        );

        assertEquals("TestUser", databaseNickname);
    }
}
