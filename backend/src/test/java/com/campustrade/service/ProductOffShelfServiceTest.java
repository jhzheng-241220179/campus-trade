
package com.campustrade.service;

import com.campustrade.exception.ProductConflictException;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductOffShelfServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private ProductMapper mapper;
    private ProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductMapper.class);
        service = new ProductService(mapper);
    }

    private Product createProduct(
            Long id,
            Long sellerId,
            String status,
            int version
    ) {
        Product product = new Product();
        product.setId(id);
        product.setSellerId(sellerId);
        product.setStatus(status);
        product.setVersion(version);
        return product;
    }

    @Test
    void shouldOffShelfPublishedProduct() {
        Product product = createProduct(
                5L, SELLER_ID, "PUBLISHED", 3
        );

        when(mapper.findById(5L)).thenReturn(product);

        when(mapper.offShelfOwnedPublished(
                5L, SELLER_ID, 3
        )).thenReturn(1);

        assertDoesNotThrow(
                () -> service.offShelfProduct(5L, SELLER_ID)
        );

        verify(mapper).offShelfOwnedPublished(
                5L, SELLER_ID, 3
        );
    }

    @Test
    void shouldRejectOtherSeller() {
        Product product = createProduct(
                5L, OTHER_USER_ID, "PUBLISHED", 3
        );

        when(mapper.findById(5L)).thenReturn(product);

        assertThrows(
                AccessDeniedException.class,
                () -> service.offShelfProduct(5L, SELLER_ID)
        );

        verify(mapper, never()).offShelfOwnedPublished(
                anyLong(), anyLong(), anyInt()
        );
    }

    @Test
    void shouldRejectDraftProduct() {
        Product product = createProduct(
                5L, SELLER_ID, "DRAFT", 3
        );

        when(mapper.findById(5L)).thenReturn(product);

        assertThrows(
                ProductStatusException.class,
                () -> service.offShelfProduct(5L, SELLER_ID)
        );
    }

    @Test
    void shouldRejectRepeatedOffShelf() {
        Product product = createProduct(
                5L, SELLER_ID, "OFF_SHELF", 4
        );

        when(mapper.findById(5L)).thenReturn(product);

        assertThrows(
                ProductStatusException.class,
                () -> service.offShelfProduct(5L, SELLER_ID)
        );
    }

    @Test
    void shouldRejectMissingProduct() {
        when(mapper.findById(5L)).thenReturn(null);

        assertThrows(
                ProductNotFoundException.class,
                () -> service.offShelfProduct(5L, SELLER_ID)
        );
    }

    @Test
    void shouldRejectConcurrentChange() {
        Product product = createProduct(
                5L, SELLER_ID, "PUBLISHED", 3
        );

        when(mapper.findById(5L)).thenReturn(product);

        when(mapper.offShelfOwnedPublished(
                5L, SELLER_ID, 3
        )).thenReturn(0);

        assertThrows(
                ProductConflictException.class,
                () -> service.offShelfProduct(5L, SELLER_ID)
        );
    }

    @Test
    void unpublishedProductShouldNotBePublic() {
        Product product = createProduct(
                5L, SELLER_ID, "OFF_SHELF", 4
        );

        when(mapper.findById(5L)).thenReturn(product);

        assertThrows(
                ProductNotFoundException.class,
                () -> service.getPublishedProductById(5L)
        );
    }
}
