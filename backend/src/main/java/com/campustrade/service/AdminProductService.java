
package com.campustrade.service;

import com.campustrade.dto.AdminProductResponse;
import com.campustrade.exception.ProductConflictException;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminProductService {

    private final ProductMapper productMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ReservationLifecycleService reservationLifecycleService;

    public AdminProductService(
            ProductMapper productMapper,
            JdbcTemplate jdbcTemplate,
            ReservationLifecycleService reservationLifecycleService
    ) {
        this.productMapper = productMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.reservationLifecycleService =
                reservationLifecycleService;
    }

    @Transactional(readOnly = true)
    public List<AdminProductResponse> listProducts() {
        return productMapper.findAll()
                .stream()
                .map(AdminProductResponse::from)
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void forceOffShelf(Long productId) {
        if (productId == null || productId <= 0) {
            throw new ProductConflictException(
                    "商品编号无效"
            );
        }

        Product snapshot =
                productMapper.findById(productId);

        if (snapshot == null) {
            throw new ProductNotFoundException(productId);
        }

        Long sellerId = snapshot.getSellerId();

        List<Long> lockedUsers = jdbcTemplate.query(
                """
                SELECT id
                FROM users
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getLong("id"),
                sellerId
        );

        if (lockedUsers.size() != 1) {
            throw new ProductConflictException(
                    "商品所属用户不存在"
            );
        }

        Product product =
                productMapper.findByIdForUpdate(productId);

        if (product == null) {
            throw new ProductNotFoundException(productId);
        }

        if (!sellerId.equals(product.getSellerId())) {
            throw new ProductConflictException(
                    "商品所属用户已发生变化"
            );
        }

        if (!"PUBLISHED".equals(product.getStatus())) {
            throw new ProductStatusException(
                    "只有已发布商品可以被强制下架"
            );
        }

        reservationLifecycleService
                .cancelProductReservations(
                        productId,
                        "ADMIN_OFF_SHELF"
                );

        int rows = jdbcTemplate.update(
                """
                UPDATE products
                SET status = 'OFF_SHELF',
                    version = version + 1
                WHERE id = ?
                  AND seller_id = ?
                  AND status = 'PUBLISHED'
                  AND version = ?
                """,
                productId,
                sellerId,
                product.getVersion()
        );

        if (rows != 1) {
            throw new ProductConflictException(
                    "商品状态已发生变化，请刷新后重试"
            );
        }
    }
}
