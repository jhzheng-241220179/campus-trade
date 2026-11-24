
package com.campustrade.service;

import com.campustrade.dto.AdminUserResponse;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {

    @Test
    void shouldReturnUsers() {
        JdbcTemplate jdbcTemplate =
                mock(JdbcTemplate.class);

        AdminUserService service =
                new AdminUserService(jdbcTemplate);

        AdminUserResponse user =
                new AdminUserResponse(
                        1L,
                        "student@example.com",
                        "测试用户",
                        "USER",
                        "ACTIVE",
                        true,
                        null
                );

        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<AdminUserResponse>>any()
        )).thenReturn(List.of(user));

        List<AdminUserResponse> result =
                service.listUsers();

        assertEquals(1, result.size());
        assertEquals("USER", result.get(0).role());
        assertEquals("ACTIVE", result.get(0).status());

        verify(jdbcTemplate).query(
                contains("LIMIT 200"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<AdminUserResponse>>any()
        );
    }
}
