
package com.campustrade.service;

import com.campustrade.dto.AdminUserResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserService {

    private final JdbcTemplate jdbcTemplate;

    public AdminUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    email,
                    nickname,
                    role,
                    status,
                    email_verified,
                    created_at
                FROM users
                ORDER BY id DESC
                LIMIT 200
                """,
                (rs, rowNum) -> new AdminUserResponse(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("nickname"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getBoolean("email_verified"),
                        rs.getTimestamp("created_at")
                                .toLocalDateTime()
                )
        );
    }
}
