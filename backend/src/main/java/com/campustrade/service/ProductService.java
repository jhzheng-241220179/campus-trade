
package com.campustrade.service;

import com.campustrade.dto.ProductCreateRequest;
import com.campustrade.dto.ProductUpdateRequest;
import com.campustrade.exception.ProductConflictException;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final JdbcTemplate jdbcTemplate;

    public ProductService(
            ProductMapper productMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.productMapper = productMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Product> listPublishedProducts() {
        return productMapper.findPublished();
    }

    public Product getPublishedProductById(Long id) {
        Product product = productMapper.findById(id);

        if (product == null
                || !"PUBLISHED".equals(product.getStatus())) {
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

    public List<Product> listProducts() {
        return productMapper.findAll();
    }

    public List<Product> listMyProducts(Long sellerId) {
        return productMapper.findBySellerId(sellerId);
    }

    public Product getOwnedDraftById(
            Long id,
            Long sellerId
    ) {
        Product product = productMapper.findById(id);

        requireOwner(product, id, sellerId);

        if (!"DRAFT".equals(product.getStatus())) {
            throw new ProductStatusException(
                    "只有草稿状态的商品允许编辑"
            );
        }

        return product;
    }

    private void lockAndCheckSeller(Long sellerId) {
        List<String> statuses = jdbcTemplate.query(
                """
                SELECT status
                FROM users
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getString("status"),
                sellerId
        );

        if (statuses.size() != 1
                || !"ACTIVE".equals(statuses.get(0))) {
            throw new AccessDeniedException(
                    "当前账户无法进行商品操作"
            );
        }
    }

    @Transactional
    public Long createProduct(
            ProductCreateRequest request,
            Long sellerId
    ) {
        lockAndCheckSeller(sellerId);

        Product product = new Product();
        product.setSellerId(sellerId);
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

    @Transactional
    public void updateProduct(
            Long id,
            ProductUpdateRequest request,
            Long sellerId
    ) {
        lockAndCheckSeller(sellerId);

        Product product = productMapper.findById(id);
        requireOwner(product, id, sellerId);

        if (!"DRAFT".equals(product.getStatus())) {
            throw new ProductStatusException(
                    "只有草稿状态的商品允许编辑"
            );
        }

        if (!product.getVersion().equals(request.version())) {
            throw new ProductConflictException(
                    "商品版本已过期，请刷新后重试"
            );
        }

        product.setTitle(request.title());
        product.setPrice(request.price());
        product.setCategory(request.category());
        product.setDescription(request.description());
        product.setConditionLevel(request.conditionLevel());
        product.setContact(request.contact());

        int rows = productMapper.updateOwnedDraft(
                product,
                sellerId
        );

        if (rows != 1) {
            throw new ProductConflictException(
                    "商品已发生变化，请刷新后重试"
            );
        }
    }

    @Transactional
    public void publishProduct(
            Long id,
            Long sellerId
    ) {
        lockAndCheckSeller(sellerId);

        Product product = productMapper.findById(id);
        requireOwner(product, id, sellerId);

        if (!"DRAFT".equals(product.getStatus())) {
            throw new ProductStatusException(
                    "只有草稿状态的商品允许发布"
            );
        }

        int rows = productMapper.publishOwnedDraft(
                id,
                sellerId,
                product.getVersion()
        );

        if (rows != 1) {
            throw new ProductConflictException(
                    "商品状态已发生变化，请刷新后重试"
            );
        }
    }

    @Transactional
    public void offShelfProduct(
            Long id,
            Long sellerId
    ) {
        lockAndCheckSeller(sellerId);

        Product product = productMapper.findById(id);
        requireOwner(product, id, sellerId);

        if (!"PUBLISHED".equals(product.getStatus())) {
            throw new ProductStatusException(
                    "只有已发布的商品允许下架"
            );
        }

        int rows = productMapper.offShelfOwnedPublished(
                id,
                sellerId,
                product.getVersion()
        );

        if (rows != 1) {
            throw new ProductConflictException(
                    "商品状态已发生变化，请刷新后重试"
            );
        }
    }

    private void requireOwner(
            Product product,
            Long productId,
            Long sellerId
    ) {
        if (product == null) {
            throw new ProductNotFoundException(productId);
        }

        if (!sellerId.equals(product.getSellerId())) {
            throw new AccessDeniedException(
                    "无权操作其他用户的商品"
            );
        }
    }
}
