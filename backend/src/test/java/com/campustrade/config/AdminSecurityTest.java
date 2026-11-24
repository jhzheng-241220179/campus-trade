
package com.campustrade.config;

import com.campustrade.controller.AdminController;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.ResultSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Object>>any(),
                anyString()
        )).thenAnswer(invocation -> {
            String email = invocation.getArgument(2);
            String role = email.equals("admin@example.com")
                    ? "ADMIN" : "USER";

            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);

            when(rs.getString("status"))
                    .thenReturn("ACTIVE");
            when(rs.getString("role"))
                    .thenReturn(role);
            when(rs.getBoolean("email_verified"))
                    .thenReturn(true);

            return List.of(mapper.mapRow(rs, 0));
        });
    }

    @Test
    void anonymousCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void normalUserCannotAccessAdmin() throws Exception {
        mockMvc.perform(
                get("/api/admin/me")
                        .with(user("student@example.com")
                                .roles("USER"))
        ).andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdmin() throws Exception {
        mockMvc.perform(
                get("/api/admin/me")
                        .with(user("admin@example.com")
                                .roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email")
                .value("admin@example.com"))
        .andExpect(jsonPath("$.data.role")
                .value("ADMIN"));
    }

    @Test
    void staleAdminRoleCannotBypassDatabase() throws Exception {
        mockMvc.perform(
                get("/api/admin/me")
                        .with(user("student@example.com")
                                .roles("ADMIN"))
        ).andExpect(status().isUnauthorized());
    }
}
