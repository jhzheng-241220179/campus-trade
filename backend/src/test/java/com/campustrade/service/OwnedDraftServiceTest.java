package com.campustrade.service;

import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnedDraftServiceTest {

    private ProductMapper productMapper;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        productService = new ProductService(productMapper);
    }

    private Product product(
            Long id,
            Long sellerId,
            String status
    ) {
        Product product = new Product();
        product.setId(id);
        product.setSellerId(sellerId);
        product.setStatus(status);
        return product;
    }

    @Test
    void shouldReturnOwnedDraft() {
        Product draft = product(8L, 1L, "DRAFT");
        when(productMapper.findById(8L)).thenReturn(draft);

        Product result =
                productService.getOwnedDraftById(8L, 1L);

        assertSame(draft, result);
    }

    @Test
    void shouldRejectOtherUsersDraft() {
        Product draft = product(8L, 2L, "DRAFT");
        when(productMapper.findById(8L)).thenReturn(draft);

        assertThrows(
                AccessDeniedException.class,
                () -> productService.getOwnedDraftById(8L, 1L)
        );
    }

    @Test
    void shouldRejectPublishedProduct() {
        Product published = product(8L, 1L, "PUBLISHED");
        when(productMapper.findById(8L)).thenReturn(published);

        assertThrows(
                ProductStatusException.class,
                () -> productService.getOwnedDraftById(8L, 1L)
        );
    }

    @Test
    void shouldRejectMissingProduct() {
        when(productMapper.findById(8L)).thenReturn(null);

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.getOwnedDraftById(8L, 1L)
        );
    }
}