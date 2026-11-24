
package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.AdminProductResponse;
import com.campustrade.service.AdminProductService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(
            AdminProductService adminProductService
    ) {
        this.adminProductService = adminProductService;
    }

    @GetMapping
    public ApiResponse<List<AdminProductResponse>> listProducts() {
        return ApiResponse.success(
                adminProductService.listProducts()
        );
    }

    @PostMapping("/{id}/off-shelf")
    public ApiResponse<Void> forceOffShelf(
            @PathVariable Long id
    ) {
        adminProductService.forceOffShelf(id);
        return ApiResponse.success(null);
    }
}
