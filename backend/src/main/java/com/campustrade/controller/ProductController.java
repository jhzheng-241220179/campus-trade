package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.ProductCreateRequest;
import com.campustrade.model.Product;
import com.campustrade.service.ProductService;
import com.campustrade.dto.ProductUpdateRequest;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

@GetMapping
public ApiResponse<List<Product>> listProducts() {
    return ApiResponse.success(
            productService.listPublishedProducts()
    );
}
@GetMapping("/{id}")
public ApiResponse<Product> getProduct(@PathVariable Long id) {
    return ApiResponse.success(
            productService.getPublishedProductById(id)
    );
}
    @PostMapping
    public ApiResponse<Long> createProduct(
            @Valid @RequestBody ProductCreateRequest request) {

        Long productId = productService.createProduct(request);

        return ApiResponse.success(productId);
    }
    @PutMapping("/{id}")
public ApiResponse<Void> updateProduct(
        @PathVariable Long id,
        @Valid @RequestBody ProductUpdateRequest request) {

    productService.updateProduct(id, request);

    return ApiResponse.success(null);
}
}
