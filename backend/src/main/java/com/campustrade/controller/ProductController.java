
package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.ProductCreateRequest;
import com.campustrade.dto.ProductUpdateRequest;
import com.campustrade.model.Product;
import com.campustrade.security.CurrentUserIdResolver;
import com.campustrade.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final CurrentUserIdResolver currentUserIdResolver;

    public ProductController(
            ProductService productService,
            CurrentUserIdResolver currentUserIdResolver
    ) {
        this.productService = productService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @GetMapping
    public ApiResponse<List<Product>> listProducts() {
        return ApiResponse.success(
                productService.listPublishedProducts()
        );
    }

    @GetMapping("/mine")
    public ApiResponse<List<Product>> listMyProducts(
            Authentication authentication
    ) {
        Long sellerId = currentUserIdResolver
                .requireCurrentUserId(authentication);

        return ApiResponse.success(
                productService.listMyProducts(sellerId)
        );
    }

    @GetMapping("/mine/{id}")
    public ApiResponse<Product> getMyDraft(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long sellerId = currentUserIdResolver
                .requireCurrentUserId(authentication);

        return ApiResponse.success(
                productService.getOwnedDraftById(id, sellerId)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> getProduct(
            @PathVariable Long id
    ) {
        return ApiResponse.success(
                productService.getPublishedProductById(id)
        );
    }

    @PostMapping
    public ApiResponse<Long> createProduct(
            @Valid @RequestBody ProductCreateRequest request,
            Authentication authentication
    ) {
        Long sellerId = currentUserIdResolver
                .requireCurrentUserId(authentication);

        Long productId = productService.createProduct(
                request,
                sellerId
        );

        return ApiResponse.success(productId);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request,
            Authentication authentication
    ) {
        Long sellerId = currentUserIdResolver
                .requireCurrentUserId(authentication);

        productService.updateProduct(
                id,
                request,
                sellerId
        );

        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publishProduct(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long sellerId = currentUserIdResolver
                .requireCurrentUserId(authentication);

        productService.publishProduct(id, sellerId);

        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/off-shelf")
    public ApiResponse<Void> offShelfProduct(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long sellerId = currentUserIdResolver
                .requireCurrentUserId(authentication);

        productService.offShelfProduct(id, sellerId);

        return ApiResponse.success(null);
    }
}
