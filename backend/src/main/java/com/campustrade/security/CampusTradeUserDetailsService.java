
package com.campustrade.security;

import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CampusTradeUserDetailsService
        implements UserDetailsService {

    private final UserMapper userMapper;

    public CampusTradeUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException(
                    "用户不存在"
            );
        }

        String email = username.trim()
                .toLowerCase(Locale.ROOT);

        User user = userMapper.findByEmail(email);

        if (user == null
                || user.getPasswordHash() == null
                || user.getPasswordHash().isBlank()) {
            throw new UsernameNotFoundException(
                    "用户不存在"
            );
        }

        boolean enabled =
                "ACTIVE".equals(user.getStatus())
                && Boolean.TRUE.equals(user.getEmailVerified());

        String role = user.getRole();

        if (role == null || role.isBlank()) {
            role = "USER";
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + role)
                .disabled(!enabled)
                .build();
    }
}
