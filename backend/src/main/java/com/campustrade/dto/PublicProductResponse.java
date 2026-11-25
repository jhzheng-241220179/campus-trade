
package com.campustrade.dto;

import com.campustrade.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PublicProductResponse(

        Long id,
        Long sellerId,
        String title,
        BigDecimal price,
        String category,
        String description,
        String conditionLevel,
        String status,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {

    public static PublicProductResponse from(
            Product product
    ) {
        return new PublicProductResponse(
                product.getId(),
                product.getSellerId(),
                product.getTitle(),
                product.getPrice(),
                product.getCategory(),
                product.getDescription(),
                product.getConditionLevel(),
                product.getStatus(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
