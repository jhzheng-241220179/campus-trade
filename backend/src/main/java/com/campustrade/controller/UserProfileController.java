
package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.CurrentUserResponse;
import com.campustrade.dto.NicknameUpdateRequest;
import com.campustrade.service.UserProfileService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(
            UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @PatchMapping("/me")
    public ApiResponse<CurrentUserResponse> updateNickname(
            @Valid @RequestBody NicknameUpdateRequest request,
            Authentication authentication) {

        CurrentUserResponse result =
                userProfileService.updateNickname(
                        authentication.getName(),
                        request.nickname()
                );

        return ApiResponse.success(result);
    }
}
