package com.campustrade.service;

import com.campustrade.dto.UserRegisterRequest;
import com.campustrade.exception.EmailAlreadyRegisteredException;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder) {

        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Long register(UserRegisterRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userMapper.findByEmail(email) != null) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User();

        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setEmailVerified(false);
        user.setNickname(request.nickname().trim());
        user.setRole("USER");
        user.setStatus("PENDING_VERIFICATION");

        try {
            int rows = userMapper.insert(user);

            if (rows != 1 || user.getId() == null) {
                throw new IllegalStateException("用户创建失败");
            }

        } catch (DuplicateKeyException ex) {
            throw new EmailAlreadyRegisteredException();
        }

        return user.getId();
    }
}