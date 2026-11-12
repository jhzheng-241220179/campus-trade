
package com.campustrade;

import com.campustrade.dto.UserRegisterRequest;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;
import com.campustrade.service.UserService;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationLifecycleIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SqlSession sqlSession;

    private static final String PASSWORD = "Password123!";

    private String createPendingUser() {

        String email = "test-" +
                UUID.randomUUID() +
                "@example.com";

        UserRegisterRequest request =
                mock(UserRegisterRequest.class);

        when(request.email()).thenReturn(email);
        when(request.password()).thenReturn(PASSWORD);
        when(request.nickname()).thenReturn("TestUser");

        Long id = userService.register(request);

        assertNotNull(id);

        return email;
    }

    private Authentication authenticate(
            String email, String password) {

        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email,
                        password
                )
        );
    }

    @Test
    void registrationActivationAndLoginShouldWork() {

        String email = createPendingUser();

        User pending = userMapper.findByEmail(email);

        assertNotNull(pending);
        assertEquals("PENDING_VERIFICATION", pending.getStatus());
        assertEquals(Boolean.FALSE, pending.getEmailVerified());

        assertThrows(
                AuthenticationException.class,
                () -> authenticate(email, PASSWORD)
        );

        assertEquals(
                1,
                userMapper.activateEmail(pending.getId())
        );

        User activated = userMapper.findByEmail(email);

        assertEquals("ACTIVE", activated.getStatus());
        assertEquals(Boolean.TRUE, activated.getEmailVerified());

        Authentication result = authenticate(email, PASSWORD);

        assertTrue(result.isAuthenticated());
        assertEquals(email, result.getName());

        assertThrows(
                AuthenticationException.class,
                () -> authenticate(email, "WrongPassword")
        );
    }

    @Test
    void disabledAccountShouldNotLogin() {

        String email = createPendingUser();

        User pending = userMapper.findByEmail(email);

        assertEquals(
                1,
                userMapper.activateEmail(pending.getId())
        );

        Authentication beforeDisable =
                authenticate(email, PASSWORD);

        assertTrue(beforeDisable.isAuthenticated());

        int updated = jdbcTemplate.update(
                "UPDATE users SET status = ? WHERE id = ?",
                "DISABLED",
                pending.getId()
        );

        assertEquals(1, updated);

        String databaseStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE id = ?",
                String.class,
                pending.getId()
        );

        assertEquals("DISABLED", databaseStatus);

        sqlSession.clearCache();

        User disabled = userMapper.findByEmail(email);

        assertEquals("DISABLED", disabled.getStatus());

        assertThrows(
                AuthenticationException.class,
                () -> authenticate(email, PASSWORD)
        );
    }

    @Test
    void unverifiedActiveAccountShouldNotLogin() {

        String email = createPendingUser();

        User pending = userMapper.findByEmail(email);

        int updated = jdbcTemplate.update(
                "UPDATE users SET status = ? WHERE id = ?",
                "ACTIVE",
                pending.getId()
        );

        assertEquals(1, updated);

        String databaseStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE id = ?",
                String.class,
                pending.getId()
        );

        assertEquals("ACTIVE", databaseStatus);

        sqlSession.clearCache();

        User user = userMapper.findByEmail(email);

        assertEquals("ACTIVE", user.getStatus());
        assertEquals(Boolean.FALSE, user.getEmailVerified());

        assertThrows(
                AuthenticationException.class,
                () -> authenticate(email, PASSWORD)
        );
    }
}
