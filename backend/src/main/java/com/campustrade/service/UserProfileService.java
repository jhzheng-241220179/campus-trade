
package com.campustrade.service;

import com.campustrade.dto.CurrentUserResponse;
import com.campustrade.mapper.UserProfileMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;
    private final CurrentUserService currentUserService;

    public UserProfileService(
            UserProfileMapper userProfileMapper,
            CurrentUserService currentUserService) {

        this.userProfileMapper = userProfileMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public CurrentUserResponse updateNickname(
            String email,
            String nickname) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("用户身份不能为空");
        }

        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("昵称不能为空");
        }

        String normalizedNickname = nickname.trim();

        if (normalizedNickname.codePointCount(
                0, normalizedNickname.length()) > 64) {
            throw new IllegalArgumentException("昵称不能超过64个字符");
        }

        int updated = userProfileMapper.updateNickname(
                email,
                normalizedNickname
        );

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "用户不存在或账户不可用"
            );
        }

        return currentUserService.getCurrentUser(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "更新后无法查询当前用户"
                ));
    }
}
