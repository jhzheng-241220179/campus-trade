
package com.campustrade.dto;

import com.campustrade.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminProductResponse(
        Long id,
        Long sellerId,
        String title,
        BigDecimal price,
        String category,
        String status,
        Integer version,
        LocalDateTime createdAt
) {

    public static AdminProductResponse from(Product product) {
        return new AdminProductResponse(
                product.getId(),
                product.getSellerId(),
                product.getTitle(),
                product.getPrice(),
                product.getCategory(),
                product.getStatus(),
                product.getVersion(),
                product.getCreatedAt()
        );
    }
}
