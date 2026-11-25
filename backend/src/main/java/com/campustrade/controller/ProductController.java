
package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.ProductCreateRequest;
import com.campustrade.dto.ProductUpdateRequest;
import com.campustrade.dto.PublicProductResponse;
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

    // 公开商品列表，不返回卖家联系方式。
    @GetMapping
    public ApiResponse<List<PublicProductResponse>> listProducts() {
        List<PublicProductResponse> products =
                productService.listPublishedProducts()
                        .stream()
                        .map(PublicProductResponse::from)
                        .toList();

        return ApiResponse.success(products);
    }

    // 卖家管理自己的商品，保留完整商品信息。
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

    // 卖家编辑自己的草稿，保留联系方式。
    @GetMapping("/mine/{id}")
    public ApiResponse<Product> getMyDraft(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long sellerId = currentUserIdResolver
                .requireCurrentUserId(authentication);

        return ApiResponse.success(
                productService.getOwnedDraftById(
                        id,
                        sellerId
                )
        );
    }

    // 公开商品详情，不返回卖家联系方式。
    @GetMapping("/{id}")
    public ApiResponse<PublicProductResponse> getProduct(
            @PathVariable Long id
    ) {
        Product product =
                productService.getPublishedProductById(id);

        return ApiResponse.success(
                PublicProductResponse.from(product)
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
