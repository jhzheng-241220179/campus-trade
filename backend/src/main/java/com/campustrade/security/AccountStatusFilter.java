
package com.campustrade.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class AccountStatusFilter extends OncePerRequestFilter {

    private final JdbcTemplate jdbcTemplate;

    public AccountStatusFilter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private record AccountState(
            String status,
            String role,
            boolean emailVerified
    ) {
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication
                        instanceof AnonymousAuthenticationToken) {

            chain.doFilter(request, response);
            return;
        }

        List<AccountState> states = jdbcTemplate.query(
                """
                SELECT status, role, email_verified
                FROM users
                WHERE email = ?
                """,
                (rs, rowNum) -> new AccountState(
                        rs.getString("status"),
                        rs.getString("role"),
                        rs.getBoolean("email_verified")
                ),
                authentication.getName()
        );

        boolean valid = states.size() == 1;

        if (valid) {
            AccountState state = states.get(0);

            valid = "ACTIVE".equals(state.status())
                    && state.emailVerified()
                    && ("USER".equals(state.role())
                        || "ADMIN".equals(state.role()))
                    && authentication.getAuthorities()
                            .stream()
                            .anyMatch(authority ->
                                    ("ROLE_" + state.role())
                                            .equals(
                                                authority.getAuthority()
                                            )
                            );
        }

        if (!valid) {
            SecurityContextHolder.clearContext();

            HttpSession session =
                    request.getSession(false);

            if (session != null) {
                session.removeAttribute(
                        HttpSessionSecurityContextRepository
                                .SPRING_SECURITY_CONTEXT_KEY
                );
                session.invalidate();
            }

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "账户已失效或权限发生变化，请重新登录"
            );
            return;
        }

        chain.doFilter(request, response);
    }
}
