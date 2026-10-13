package com.campustrade.service;

import com.campustrade.dto.ProductCreateRequest;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.Product;
import com.campustrade.exception.ProductNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    public List<Product> listPublishedProducts() {
            return productMapper.findPublished();
        }
        public Product getPublishedProductById(Long id) {

        Product product = productMapper.findById(id);

        if (product == null || !"PUBLISHED".equals(product.getStatus())) {
            throw new ProductNotFoundException(id);
        }

        return product;
    }
    public Product getProductById(Long id) {

        Product product = productMapper.findById(id);

        if (product == null) {
            throw new ProductNotFoundException(id);
        }

        return product;
    }
    public ProductService(
            ProductMapper productMapper,
            UserMapper userMapper) {

        this.productMapper = productMapper;
        this.userMapper = userMapper;
    }

    public List<Product> listProducts() {
        return productMapper.findAll();
    }

    @Transactional
    public Long createProduct(ProductCreateRequest request) {

        if (userMapper.countById(request.sellerId()) == 0) {
            throw new IllegalArgumentException("卖家不存在");
        }

        Product product = new Product();

        product.setSellerId(request.sellerId());
        product.setTitle(request.title());
        product.setPrice(request.price());
        product.setCategory(request.category());
        product.setDescription(request.description());
        product.setConditionLevel(request.conditionLevel());
        product.setContact(request.contact());

        int rows = productMapper.insert(product);

        if (rows != 1 || product.getId() == null) {
            throw new IllegalStateException("商品创建失败");
        }

        return product.getId();
    }
}