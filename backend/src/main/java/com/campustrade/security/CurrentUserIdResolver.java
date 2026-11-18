
package com.campustrade.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserIdResolver {

    private final JdbcTemplate jdbcTemplate;

    public CurrentUserIdResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long requireCurrentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("请先登录");
        }

        String email = authentication.getName();

        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("无法识别当前用户");
        }

        var ids = jdbcTemplate.query(
                "SELECT id FROM users WHERE email = ?",
                (rs, rowNum) -> rs.getLong("id"),
                email
        );

        if (ids.size() != 1) {
            throw new AccessDeniedException("当前登录用户不存在");
        }

        return ids.get(0);
    }
}
