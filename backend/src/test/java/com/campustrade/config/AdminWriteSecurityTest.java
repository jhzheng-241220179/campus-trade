
package com.campustrade.config;

import com.campustrade.controller.AdminUserController;
import com.campustrade.service.AdminAccountService;
import com.campustrade.service.AdminUserService;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminWriteSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private AdminAccountService adminAccountService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 模拟 AccountStatusFilter 读取数据库账户状态。
        when(jdbcTemplate.query(
                contains("SELECT status, role, email_verified"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Object>>any(),
                anyString()
        )).thenAnswer(invocation -> {
            String email = invocation.getArgument(2);
            String role = "admin@example.com".equals(email)
                    ? "ADMIN"
                    : "USER";

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
    void adminWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(
                post("/api/admin/users/5/ban")
                        .with(user("admin@example.com")
                                .roles("ADMIN"))
        ).andExpect(status().isForbidden());

        verifyNoInteractions(adminAccountService);
    }

    @Test
    void normalUserWithCsrfIsForbidden() throws Exception {
        mockMvc.perform(
                post("/api/admin/users/5/ban")
                        .with(user("student@example.com")
                                .roles("USER"))
                        .with(csrf())
        ).andExpect(status().isForbidden());

        verifyNoInteractions(adminAccountService);
    }

    @Test
    void anonymousWithCsrfIsUnauthorized() throws Exception {
        mockMvc.perform(
                post("/api/admin/users/5/ban")
                        .with(csrf())
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(adminAccountService);
    }

    @Test
    void adminWithCsrfCanBan() throws Exception {
        mockMvc.perform(
                post("/api/admin/users/5/ban")
                        .with(user("admin@example.com")
                                .roles("ADMIN"))
                        .with(csrf())
        ).andExpect(status().isOk());

        verify(adminAccountService).ban(5L);
    }

    @Test
    void adminWithCsrfCanUnban() throws Exception {
        mockMvc.perform(
                post("/api/admin/users/5/unban")
                        .with(user("admin@example.com")
                                .roles("ADMIN"))
                        .with(csrf())
        ).andExpect(status().isOk());

        verify(adminAccountService).unban(5L);
    }
}
