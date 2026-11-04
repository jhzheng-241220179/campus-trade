
package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.SendCodeRequest;
import com.campustrade.dto.VerifyEmailRequest;
import com.campustrade.service.EmailVerificationFacade;
import com.campustrade.service.VerificationCodeService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final EmailVerificationFacade facade;

    public AuthController(EmailVerificationFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/me")
    public ApiResponse<String> me(Authentication authentication) {
        return ApiResponse.success(authentication.getName());
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
                facade.verifyEmail(request.email(), request.code());

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
