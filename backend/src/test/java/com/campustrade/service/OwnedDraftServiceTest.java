
package com.campustrade.service;

import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnedDraftServiceTest {

    private ProductMapper mapper;
    private ProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductMapper.class);

        service = new ProductService(
                mapper,
                mock(JdbcTemplate.class),
                mock(ReservationLifecycleService.class)
        );
    }

    private Product product(
            Long id,
            Long sellerId,
            String status
    ) {
        Product p = new Product();

        p.setId(id);
        p.setSellerId(sellerId);
        p.setStatus(status);

        return p;
    }

    @Test
    void shouldReturnOwnedDraft() {
        Product draft = product(8L, 1L, "DRAFT");

        when(mapper.findById(8L))
                .thenReturn(draft);

        assertSame(
                draft,
                service.getOwnedDraftById(8L, 1L)
        );
    }

    @Test
    void shouldRejectOtherUsersDraft() {
        when(mapper.findById(8L))
                .thenReturn(product(8L, 2L, "DRAFT"));

        assertThrows(
                AccessDeniedException.class,
                () -> service.getOwnedDraftById(8L, 1L)
        );
    }

    @Test
    void shouldRejectPublishedProduct() {
        when(mapper.findById(8L))
                .thenReturn(product(8L, 1L, "PUBLISHED"));

        assertThrows(
                ProductStatusException.class,
                () -> service.getOwnedDraftById(8L, 1L)
        );
    }

    @Test
    void shouldRejectMissingProduct() {
        assertThrows(
                ProductNotFoundException.class,
                () -> service.getOwnedDraftById(8L, 1L)
        );
    }
}
