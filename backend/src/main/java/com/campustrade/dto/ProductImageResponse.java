
package com.campustrade.dto;

public record ProductImageResponse(
        Long id,
        Long productId,
        String url,
        Integer displayOrder
) {
}
