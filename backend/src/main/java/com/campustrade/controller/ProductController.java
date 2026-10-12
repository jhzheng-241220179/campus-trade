package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.model.Product;
import com.campustrade.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return ApiResponse.success(productService.listProducts());
    }
}