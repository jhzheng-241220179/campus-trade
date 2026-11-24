
package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.AdminUserResponse;
import com.campustrade.service.AdminAccountService;
import com.campustrade.service.AdminUserService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminAccountService adminAccountService;

    public AdminUserController(
            AdminUserService adminUserService,
            AdminAccountService adminAccountService
    ) {
        this.adminUserService = adminUserService;
        this.adminAccountService = adminAccountService;
    }

    @GetMapping
    public ApiResponse<List<AdminUserResponse>> listUsers() {
        return ApiResponse.success(
                adminUserService.listUsers()
        );
    }

    @PostMapping("/{id}/ban")
    public ApiResponse<Void> ban(
            @PathVariable Long id
    ) {
        adminAccountService.ban(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/unban")
    public ApiResponse<Void> unban(
            @PathVariable Long id
    ) {
        adminAccountService.unban(id);
        return ApiResponse.success(null);
    }
}
