package com.campustrade.service;

import com.campustrade.dto.UserRegisterRequest;
import com.campustrade.exception.EmailAlreadyRegisteredException;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = new BCryptPasswordEncoder();

        userService = new UserService(
                userMapper,
                passwordEncoder
        );
    }
    @Test
void register_shouldCreateUnverifiedUser() {

    UserRegisterRequest request = new UserRegisterRequest(
            " Student@Example.com ",
            "CampusTrade123!",
            "测试用户"
    );

    when(userMapper.findByEmail("student@example.com"))
            .thenReturn(null);

    when(userMapper.insert(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(10L);
                return 1;
            });

    Long userId = userService.register(request);

    assertEquals(10L, userId);

    ArgumentCaptor<User> captor =
            ArgumentCaptor.forClass(User.class);

    verify(userMapper).insert(captor.capture());

    User savedUser = captor.getValue();

    assertEquals("student@example.com", savedUser.getEmail());
    assertEquals("USER", savedUser.getRole());
    assertEquals("PENDING_VERIFICATION", savedUser.getStatus());
    assertEquals(Boolean.FALSE, savedUser.getEmailVerified());

    assertNotEquals(
            request.password(),
            savedUser.getPasswordHash()
    );

    assertTrue(passwordEncoder.matches(
            request.password(),
            savedUser.getPasswordHash()
    ));
}
@Test
void register_shouldRejectDuplicateEmail() {

    UserRegisterRequest request = new UserRegisterRequest(
            "student@example.com",
            "CampusTrade123!",
            "测试用户"
    );

    User existingUser = new User();
    existingUser.setId(1L);
    existingUser.setEmail("student@example.com");

    when(userMapper.findByEmail("student@example.com"))
            .thenReturn(existingUser);

    assertThrows(
            EmailAlreadyRegisteredException.class,
            () -> userService.register(request)
    );

    verify(userMapper, never()).insert(any(User.class));
}
@Test
void register_shouldHandleDuplicateKeyConflict() {

    UserRegisterRequest request = new UserRegisterRequest(
            "student@example.com",
            "CampusTrade123!",
            "测试用户"
    );

    when(userMapper.findByEmail("student@example.com"))
            .thenReturn(null);

    when(userMapper.insert(any(User.class)))
            .thenThrow(
                    new DuplicateKeyException("Duplicate email")
            );

    assertThrows(
            EmailAlreadyRegisteredException.class,
            () -> userService.register(request)
    );

    verify(userMapper, times(1)).insert(any(User.class));
}
}