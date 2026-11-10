
package com.campustrade.security;

import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampusTradeUserDetailsServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private CampusTradeUserDetailsService service;

    private User createUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");
        user.setPasswordHash("stored-bcrypt-hash");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        return user;
    }

    @Test
    void activeUserShouldBeEnabled() {

        User user = createUser();

        when(userMapper.findByEmail("student@example.com"))
                .thenReturn(user);

        UserDetails details = service.loadUserByUsername(
                " STUDENT@EXAMPLE.COM "
        );

        assertEquals(
                "student@example.com",
                details.getUsername()
        );

        assertEquals(
                "stored-bcrypt-hash",
                details.getPassword()
        );

        assertTrue(details.isEnabled());

        assertTrue(
                details.getAuthorities().stream()
                        .anyMatch(a ->
                                a.getAuthority().equals("ROLE_USER"))
        );
    }

    @Test
    void unverifiedUserShouldBeDisabled() {

        User user = createUser();
        user.setEmailVerified(false);
        user.setStatus("PENDING_VERIFICATION");

        when(userMapper.findByEmail("student@example.com"))
                .thenReturn(user);

        UserDetails details = service.loadUserByUsername(
                "student@example.com"
        );

        assertFalse(details.isEnabled());
    }

    @Test
    void unknownUserShouldThrowException() {

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername(
                        "unknown@example.com"
                )
        );
    }

    @Test
    void userWithoutPasswordShouldBeRejected() {

        User user = createUser();
        user.setPasswordHash(null);

        when(userMapper.findByEmail("student@example.com"))
                .thenReturn(user);

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername(
                        "student@example.com"
                )
        );
    }
}
