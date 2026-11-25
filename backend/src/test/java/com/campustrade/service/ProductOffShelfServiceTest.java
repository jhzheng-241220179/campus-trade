
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
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductOffShelfServiceTest {

    private ProductMapper mapper;
    private JdbcTemplate jdbc;
    private ReservationLifecycleService lifecycle;
    private ProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductMapper.class);
        jdbc = mock(JdbcTemplate.class);
        lifecycle = mock(ReservationLifecycleService.class);

        service = new ProductService(
                mapper,
                jdbc,
                lifecycle
        );

        when(jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<String>>any(),
                eq(1L)
        )).thenReturn(List.of("ACTIVE"));
    }

    private Product product(
            Long sellerId,
            String status
    ) {
        Product p = new Product();

        p.setId(5L);
        p.setSellerId(sellerId);
        p.setStatus(status);
        p.setVersion(3);

        return p;
    }

    @Test
    void shouldOffShelfPublishedProduct() {
        when(mapper.findById(5L))
                .thenReturn(product(1L, "PUBLISHED"));

        when(mapper.offShelfOwnedPublished(
                5L, 1L, 3
        )).thenReturn(1);

        service.offShelfProduct(5L, 1L);

        verify(mapper).offShelfOwnedPublished(
                5L, 1L, 3
        );
    }

    @Test
    void shouldRejectOtherSeller() {
        when(mapper.findById(5L))
                .thenReturn(product(2L, "PUBLISHED"));

        assertThrows(
                AccessDeniedException.class,
                () -> service.offShelfProduct(5L, 1L)
        );

        verifyNoInteractions(lifecycle);
    }

    @Test
    void shouldRejectDraftProduct() {
        when(mapper.findById(5L))
                .thenReturn(product(1L, "DRAFT"));

        assertThrows(
                ProductStatusException.class,
                () -> service.offShelfProduct(5L, 1L)
        );

        verifyNoInteractions(lifecycle);
    }

    @Test
    void shouldRejectRepeatedOffShelf() {
        when(mapper.findById(5L))
                .thenReturn(product(1L, "OFF_SHELF"));

        assertThrows(
                ProductStatusException.class,
                () -> service.offShelfProduct(5L, 1L)
        );

        verifyNoInteractions(lifecycle);
    }

    @Test
    void shouldRejectMissingProduct() {
        assertThrows(
                ProductNotFoundException.class,
                () -> service.offShelfProduct(5L, 1L)
        );

        verifyNoInteractions(lifecycle);
    }

    @Test
    void shouldRejectConcurrentChange() {
        when(mapper.findById(5L))
                .thenReturn(product(1L, "PUBLISHED"));

        assertThrows(
                ProductConflictException.class,
                () -> service.offShelfProduct(5L, 1L)
        );
    }

    @Test
    void unpublishedProductShouldNotBePublic() {
        when(mapper.findById(5L))
                .thenReturn(product(1L, "OFF_SHELF"));

        assertThrows(
                ProductNotFoundException.class,
                () -> service.getPublishedProductById(5L)
        );
    }

    @Test
    void cannotOffShelfProductWithActiveReservations() {
        when(mapper.findById(5L))
                .thenReturn(product(1L, "PUBLISHED"));

        doThrow(
                new ProductConflictException(
                        "当前商品存在有效预约"
                )
        ).when(lifecycle)
                .requireNoActiveRound(5L);

        assertThrows(
                ProductConflictException.class,
                () -> service.offShelfProduct(5L, 1L)
        );

        verify(mapper, never())
                .offShelfOwnedPublished(
                        anyLong(),
                        anyLong(),
                        anyInt()
                );
    }

    @Test
    void checksReservationsBeforeOffShelf() {
        when(mapper.findById(5L))
                .thenReturn(product(1L, "PUBLISHED"));

        when(mapper.offShelfOwnedPublished(
                5L, 1L, 3
        )).thenReturn(1);

        service.offShelfProduct(5L, 1L);

        var order = inOrder(lifecycle, mapper);

        order.verify(lifecycle)
                .requireNoActiveRound(5L);

        order.verify(mapper)
                .offShelfOwnedPublished(
                        5L, 1L, 3
                );
    }
}
