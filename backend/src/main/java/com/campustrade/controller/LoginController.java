
package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.LoginRequest;
import com.campustrade.service.LoginService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final LoginService loginService;

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public ApiResponse<String> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Authentication authentication = loginService.login(
                request.email(),
                request.password()
        );

        // 已有 Session 时轮换 ID，防止会话固定攻击
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        // 将认证结果放入 SecurityContext
        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);

        // 显式保存到 HttpSession
        securityContextRepository.saveContext(
                context,
                httpRequest,
                httpResponse
        );

        return ApiResponse.success(authentication.getName());
    }
}
