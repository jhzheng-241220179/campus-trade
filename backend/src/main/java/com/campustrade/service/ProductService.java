package com.campustrade.service;

import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public List<Product> listProducts() {
        return productMapper.findAll();
    }
}