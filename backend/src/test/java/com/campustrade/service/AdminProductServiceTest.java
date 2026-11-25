
package com.campustrade.service;

import com.campustrade.exception.ProductConflictException;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminProductServiceTest {

    private ProductMapper mapper;
    private JdbcTemplate jdbc;
    private ReservationLifecycleService lifecycle;
    private AdminProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductMapper.class);
        jdbc = mock(JdbcTemplate.class);

        lifecycle = mock(
                ReservationLifecycleService.class
        );

        service = new AdminProductService(
                mapper,
                jdbc,
                lifecycle
        );
    }

    private Product product(
            String status,
            int version
    ) {
        Product product = new Product();

        product.setId(5L);
        product.setSellerId(2L);
        product.setTitle("测试商品");
        product.setStatus(status);
        product.setVersion(version);

        return product;
    }

    private void givenLockedSeller() {
        when(jdbc.query(
                contains("FOR UPDATE"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Long>>any(),
                eq(2L)
        )).thenReturn(List.of(2L));
    }

    private void givenProduct(
            String status,
            int version
    ) {
        Product product = product(status, version);

        when(mapper.findById(5L))
                .thenReturn(product);

        givenLockedSeller();

        when(mapper.findByIdForUpdate(5L))
                .thenReturn(product);
    }

    @Test
    void adminCanListAllProducts() {
        when(mapper.findAll()).thenReturn(
                List.of(
                        product("DRAFT", 0),
                        product("PUBLISHED", 1)
                )
        );

        var result = service.listProducts();

        assertEquals(2, result.size());

        assertEquals(
                "DRAFT",
                result.get(0).status()
        );

        assertEquals(
                "PUBLISHED",
                result.get(1).status()
        );
    }

    @Test
    void shouldForceOffShelfPublishedProduct() {
        givenProduct("PUBLISHED", 3);

        when(jdbc.update(
                contains("UPDATE products"),
                eq(5L),
                eq(2L),
                eq(3)
        )).thenReturn(1);

        assertDoesNotThrow(
                () -> service.forceOffShelf(5L)
        );

        verify(jdbc).update(
                contains("UPDATE products"),
                eq(5L),
                eq(2L),
                eq(3)
        );
    }

    @Test
    void shouldRejectDraft() {
        givenProduct("DRAFT", 0);

        assertThrows(
                ProductStatusException.class,
                () -> service.forceOffShelf(5L)
        );

        verifyNoInteractions(lifecycle);

        verify(jdbc, never()).update(
                contains("UPDATE products"),
                any(),
                any(),
                any()
        );
    }

    @Test
    void shouldRejectAlreadyOffShelf() {
        givenProduct("OFF_SHELF", 2);

        assertThrows(
                ProductStatusException.class,
                () -> service.forceOffShelf(5L)
        );

        verifyNoInteractions(lifecycle);
    }

    @Test
    void shouldRejectMissingProduct() {
        when(mapper.findById(5L))
                .thenReturn(null);

        assertThrows(
                ProductNotFoundException.class,
                () -> service.forceOffShelf(5L)
        );

        verifyNoInteractions(lifecycle);
    }

    @Test
    void shouldRejectConcurrentChange() {
        givenProduct("PUBLISHED", 3);

        assertThrows(
                ProductConflictException.class,
                () -> service.forceOffShelf(5L)
        );
    }

    @Test
    void shouldCancelReservationsBeforeOffShelf() {
        givenProduct("PUBLISHED", 3);

        when(jdbc.update(
                contains("UPDATE products"),
                eq(5L),
                eq(2L),
                eq(3)
        )).thenReturn(1);

        service.forceOffShelf(5L);

        var order = inOrder(
                lifecycle,
                jdbc
        );

        order.verify(lifecycle)
                .cancelProductReservations(
                        5L,
                        "ADMIN_OFF_SHELF"
                );

        order.verify(jdbc).update(
                contains("UPDATE products"),
                eq(5L),
                eq(2L),
                eq(3)
        );
    }

    @Test
    void cancellationFailurePreventsOffShelf() {
        givenProduct("PUBLISHED", 3);

        doThrow(
                new ProductConflictException(
                        "预约状态发生变化"
                )
        ).when(lifecycle)
                .cancelProductReservations(
                        5L,
                        "ADMIN_OFF_SHELF"
                );

        assertThrows(
                ProductConflictException.class,
                () -> service.forceOffShelf(5L)
        );

        verify(jdbc, never()).update(
                contains("UPDATE products"),
                any(),
                any(),
                any()
        );
    }
}
