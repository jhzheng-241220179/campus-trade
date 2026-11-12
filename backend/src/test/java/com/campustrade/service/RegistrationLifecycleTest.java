
package com.campustrade.service;

import com.campustrade.dto.UserRegisterRequest;
import com.campustrade.exception.EmailAlreadyRegisteredException;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationLifecycleTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userMapper, passwordEncoder);
    }

    private UserRegisterRequest request(
            String email,
            String password,
            String nickname) {

        UserRegisterRequest request =
                mock(UserRegisterRequest.class);

        when(request.email()).thenReturn(email);
        when(request.password()).thenReturn(password);
        when(request.nickname()).thenReturn(nickname);

        return request;
    }

    @Test
    void registrationShouldCreatePendingUser() {

        when(userMapper.insert(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(100L);
                    return 1;
                });

        Long userId = userService.register(
                request(
                        " STUDENT@EXAMPLE.COM ",
                        "Password123!",
                        " CampusUser "
                )
        );

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userMapper).insert(captor.capture());

        User saved = captor.getValue();

        assertEquals(100L, userId);
        assertEquals("student@example.com", saved.getEmail());
        assertEquals("CampusUser", saved.getNickname());
        assertEquals("USER", saved.getRole());
        assertEquals("PENDING_VERIFICATION", saved.getStatus());
        assertEquals(Boolean.FALSE, saved.getEmailVerified());

        assertNotEquals(
                "Password123!",
                saved.getPasswordHash()
        );

        assertTrue(
                passwordEncoder.matches(
                        "Password123!",
                        saved.getPasswordHash()
                )
        );

        verify(userMapper).findByEmail(
                "student@example.com"
        );
    }

    @Test
    void duplicateEmailShouldBeRejected() {

        User existing = new User();
        existing.setId(1L);
        existing.setEmail("student@example.com");

        when(userMapper.findByEmail("student@example.com"))
                .thenReturn(existing);

        assertThrows(
                EmailAlreadyRegisteredException.class,
                () -> userService.register(
                        request(
                                "STUDENT@EXAMPLE.COM",
                                "Password123!",
                                "CampusUser"
                        )
                )
        );

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void databaseUniqueConflictShouldBeRejected() {

        when(userMapper.insert(any(User.class)))
                .thenThrow(
                        new DuplicateKeyException(
                                "Duplicate email"
                        )
                );

        assertThrows(
                EmailAlreadyRegisteredException.class,
                () -> userService.register(
                        request(
                                "student@example.com",
                                "Password123!",
                                "CampusUser"
                        )
                )
        );
    }
}
