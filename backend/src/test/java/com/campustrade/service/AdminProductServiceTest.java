
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
    private AdminProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductMapper.class);
        jdbc = mock(JdbcTemplate.class);

        service = new AdminProductService(mapper, jdbc);
    }

    private Product product(String status, int version) {
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
        assertEquals("DRAFT", result.get(0).status());
        assertEquals("PUBLISHED", result.get(1).status());
    }

    @Test
    void shouldForceOffShelfPublishedProduct() {
        Product published = product("PUBLISHED", 3);

        when(mapper.findById(5L)).thenReturn(published);
        givenLockedSeller();
        when(mapper.findByIdForUpdate(5L))
                .thenReturn(published);

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
        when(mapper.findById(5L))
                .thenReturn(product("DRAFT", 0));

        givenLockedSeller();

        when(mapper.findByIdForUpdate(5L))
                .thenReturn(product("DRAFT", 0));

        assertThrows(
                ProductStatusException.class,
                () -> service.forceOffShelf(5L)
        );

        verify(jdbc, never()).update(
                contains("UPDATE products"),
                any(),
                any(),
                any()
        );
    }

    @Test
    void shouldRejectAlreadyOffShelf() {
        when(mapper.findById(5L))
                .thenReturn(product("OFF_SHELF", 2));

        givenLockedSeller();

        when(mapper.findByIdForUpdate(5L))
                .thenReturn(product("OFF_SHELF", 2));

        assertThrows(
                ProductStatusException.class,
                () -> service.forceOffShelf(5L)
        );
    }

    @Test
    void shouldRejectMissingProduct() {
        when(mapper.findById(5L)).thenReturn(null);

        assertThrows(
                ProductNotFoundException.class,
                () -> service.forceOffShelf(5L)
        );
    }

    @Test
    void shouldRejectConcurrentChange() {
        when(mapper.findById(5L))
                .thenReturn(product("PUBLISHED", 3));

        givenLockedSeller();

        when(mapper.findByIdForUpdate(5L))
                .thenReturn(product("PUBLISHED", 3));

        // UPDATE 默认影响 0 行，模拟版本冲突。
        assertThrows(
                ProductConflictException.class,
                () -> service.forceOffShelf(5L)
        );
    }
}
