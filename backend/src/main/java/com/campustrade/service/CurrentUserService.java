
package com.campustrade.service;

import com.campustrade.dto.CurrentUserResponse;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    private final UserMapper userMapper;

    public CurrentUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Optional<CurrentUserResponse> getCurrentUser(
            String email) {

        User user = userMapper.findPublicProfileByEmail(email);

        if (user == null) {
            return Optional.empty();
        }

        CurrentUserResponse response =
                new CurrentUserResponse(
                        user.getId(),
                        user.getNickname(),
                        user.getEmail(),
                        user.getRole(),
                        Boolean.TRUE.equals(user.getEmailVerified())
                );

        return Optional.of(response);
    }
}
