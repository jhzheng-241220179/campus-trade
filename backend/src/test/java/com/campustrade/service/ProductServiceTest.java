
package com.campustrade.service;

import com.campustrade.dto.ProductUpdateRequest;
import com.campustrade.exception.ProductConflictException;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductMapper productMapper;
    private ProductService productService;

    private static final Long SELLER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        productService = new ProductService(productMapper);
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
        product.setTitle("旧标题");
        product.setPrice(new BigDecimal("25.00"));
        product.setStatus(status);
        product.setVersion(version);
        return product;
    }

    private ProductUpdateRequest createUpdateRequest(int version) {
        return new ProductUpdateRequest(
                "二手计算机网络教材",
                new BigDecimal("18.00"),
                "教材",
                "保存完好",
                "九成新",
                "test-contact",
                version
        );
    }

    // ---------- 商品编辑测试 ----------

    @Test
    void updateProduct_shouldThrowWhenNotFound() {
        Long id = 999L;

        when(productMapper.findById(id)).thenReturn(null);

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.updateProduct(
                        id,
                        createUpdateRequest(0),
                        SELLER_ID
                )
        );

        verify(productMapper, never())
                .updateOwnedDraft(any(), anyLong());
    }

    @Test
    void updateProduct_shouldThrowWhenStatusIsPublished() {
        Long id = 2L;

        Product product = createProduct(
                id, SELLER_ID, "PUBLISHED", 0
        );

        when(productMapper.findById(id)).thenReturn(product);

        assertThrows(
                ProductStatusException.class,
                () -> productService.updateProduct(
                        id,
                        createUpdateRequest(0),
                        SELLER_ID
                )
        );

        verify(productMapper, never())
                .updateOwnedDraft(any(), anyLong());
    }

    @Test
    void updateProduct_shouldThrowWhenVersionIsOutdated() {
        Long id = 1L;

        Product product = createProduct(
                id, SELLER_ID, "DRAFT", 3
        );

        when(productMapper.findById(id)).thenReturn(product);

        assertThrows(
                ProductConflictException.class,
                () -> productService.updateProduct(
                        id,
                        createUpdateRequest(2),
                        SELLER_ID
                )
        );

        verify(productMapper, never())
                .updateOwnedDraft(any(), anyLong());
    }

    @Test
    void updateProduct_shouldThrowWhenNotOwner() {
        Long id = 1L;

        Product product = createProduct(
                id, OTHER_USER_ID, "DRAFT", 0
        );

        when(productMapper.findById(id)).thenReturn(product);

        assertThrows(
                AccessDeniedException.class,
                () -> productService.updateProduct(
                        id,
                        createUpdateRequest(0),
                        SELLER_ID
                )
        );

        verify(productMapper, never())
                .updateOwnedDraft(any(), anyLong());
    }

    @Test
    void updateProduct_shouldSucceedWhenValid() {
        Long id = 1L;

        Product product = createProduct(
                id, SELLER_ID, "DRAFT", 2
        );

        when(productMapper.findById(id)).thenReturn(product);

        when(productMapper.updateOwnedDraft(
                any(Product.class),
                eq(SELLER_ID)
        )).thenReturn(1);

        assertDoesNotThrow(
                () -> productService.updateProduct(
                        id,
                        createUpdateRequest(2),
                        SELLER_ID
                )
        );

        verify(productMapper, times(1))
                .updateOwnedDraft(
                        argThat(p ->
                                p.getId().equals(id)
                                && p.getTitle().equals(
                                        "二手计算机网络教材"
                                )
                                && p.getPrice().compareTo(
                                        new BigDecimal("18.00")
                                ) == 0
                                && p.getVersion().equals(2)
                        ),
                        eq(SELLER_ID)
                );
    }

    @Test
    void updateProduct_shouldThrowWhenConcurrentUpdate() {
        Long id = 1L;

        Product product = createProduct(
                id, SELLER_ID, "DRAFT", 2
        );

        when(productMapper.findById(id)).thenReturn(product);

        when(productMapper.updateOwnedDraft(
                any(Product.class),
                eq(SELLER_ID)
        )).thenReturn(0);

        assertThrows(
                ProductConflictException.class,
                () -> productService.updateProduct(
                        id,
                        createUpdateRequest(2),
                        SELLER_ID
                )
        );
    }

    // ---------- 商品发布测试 ----------

    @Test
    void publishProduct_shouldSucceedWhenValid() {
        Long id = 1L;

        Product product = createProduct(
                id, SELLER_ID, "DRAFT", 2
        );

        when(productMapper.findById(id)).thenReturn(product);

        when(productMapper.publishOwnedDraft(
                id, SELLER_ID, 2
        )).thenReturn(1);

        assertDoesNotThrow(
                () -> productService.publishProduct(
                        id, SELLER_ID
                )
        );

        verify(productMapper).publishOwnedDraft(
                id, SELLER_ID, 2
        );
    }

    @Test
    void publishProduct_shouldThrowWhenNotOwner() {
        Long id = 1L;

        Product product = createProduct(
                id, OTHER_USER_ID, "DRAFT", 0
        );

        when(productMapper.findById(id)).thenReturn(product);

        assertThrows(
                AccessDeniedException.class,
                () -> productService.publishProduct(
                        id, SELLER_ID
                )
        );

        verify(productMapper, never())
                .publishOwnedDraft(
                        anyLong(), anyLong(), anyInt()
                );
    }

    @Test
    void publishProduct_shouldThrowWhenAlreadyPublished() {
        Long id = 1L;

        Product product = createProduct(
                id, SELLER_ID, "PUBLISHED", 1
        );

        when(productMapper.findById(id)).thenReturn(product);

        assertThrows(
                ProductStatusException.class,
                () -> productService.publishProduct(
                        id, SELLER_ID
                )
        );

        verify(productMapper, never())
                .publishOwnedDraft(
                        anyLong(), anyLong(), anyInt()
                );
    }
}
