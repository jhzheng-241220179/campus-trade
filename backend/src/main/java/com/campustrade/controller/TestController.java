package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.ProductCreateRequest;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final ProductMapper productMapper;

    public TestController(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @GetMapping("/hello")
    public ApiResponse<String> hello() {
        return ApiResponse.success("CampusTrade API is ready!");
    }

    @PostMapping("/product")
    public ApiResponse<ProductCreateRequest> createProduct(
            @Valid @RequestBody ProductCreateRequest request) {

        return ApiResponse.success(request);
    }

    @GetMapping("/error")
    public ApiResponse<Void> error() {
        throw new IllegalArgumentException("这是一个测试异常");
    }

    @GetMapping("/products")
    public ApiResponse<List<Product>> getProducts() {
        return ApiResponse.success(productMapper.findAll());
    }
}