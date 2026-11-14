
package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.CsrfTokenResponse;
import com.campustrade.dto.CurrentUserResponse;
import com.campustrade.dto.SendCodeRequest;
import com.campustrade.dto.UserRegisterRequest;
import com.campustrade.dto.VerifyEmailRequest;
import com.campustrade.service.CurrentUserService;
import com.campustrade.service.EmailVerificationFacade;
import com.campustrade.service.UserService;
import com.campustrade.service.VerificationCodeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final EmailVerificationFacade facade;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    public AuthController(
            EmailVerificationFacade facade,
            CurrentUserService currentUserService,
            UserService userService) {

        this.facade = facade;
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(CsrfToken csrfToken) {

        CsrfTokenResponse result = new CsrfTokenResponse(
                csrfToken.getToken(),
                csrfToken.getHeaderName()
        );

        return ApiResponse.success(result);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(
            Authentication authentication) {

        var result = currentUserService.getCurrentUser(
                authentication.getName()
        );

        if (result.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(
                            404,
                            "当前用户不存在",
                            null
                    ));
        }

        return ResponseEntity.ok(
                ApiResponse.success(result.get())
        );
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Long>> register(
            @Valid @RequestBody UserRegisterRequest request) {

        Long userId = userService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(userId));
    }

    @PostMapping("/email/send-code")
    public ApiResponse<Void> sendCode(
            @Valid @RequestBody SendCodeRequest request) {

        facade.sendCode(request.email());

        return ApiResponse.success(null);
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        VerificationCodeService.VerifyResult result =
                facade.verifyEmail(
                        request.email(),
                        request.code()
                );

        if (result == VerificationCodeService.VerifyResult.SUCCESS) {
            return ResponseEntity.ok(
                    ApiResponse.success(null)
            );
        }

        return ResponseEntity.badRequest().body(
                ApiResponse.error(400, "邮箱验证失败")
        );
    }
}
