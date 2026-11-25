
package com.campustrade.service;

import com.campustrade.dto.ProductUpdateRequest;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductMapper mapper;
    private JdbcTemplate jdbc;
    private ProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductMapper.class);
        jdbc = mock(JdbcTemplate.class);

        service = new ProductService(
                mapper,
                jdbc,
                mock(ReservationLifecycleService.class)
        );

        when(jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<String>>any(),
                eq(1L)
        )).thenReturn(List.of("ACTIVE"));
    }

    private Product product(
            long id,
            long sellerId,
            String status,
            int version
    ) {
        Product p = new Product();

        p.setId(id);
        p.setSellerId(sellerId);
        p.setTitle("旧标题");
        p.setPrice(new BigDecimal("25.00"));
        p.setStatus(status);
        p.setVersion(version);

        return p;
    }

    private ProductUpdateRequest update(int version) {
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

    @Test
    void updateRejectsMissingProduct() {
        assertThrows(
                ProductNotFoundException.class,
                () -> service.updateProduct(
                        999L, update(0), 1L
                )
        );
    }

    @Test
    void updateRejectsPublishedProduct() {
        when(mapper.findById(2L))
                .thenReturn(product(2, 1, "PUBLISHED", 0));

        assertThrows(
                ProductStatusException.class,
                () -> service.updateProduct(
                        2L, update(0), 1L
                )
        );
    }

    @Test
    void updateRejectsOutdatedVersion() {
        when(mapper.findById(1L))
                .thenReturn(product(1, 1, "DRAFT", 3));

        assertThrows(
                ProductConflictException.class,
                () -> service.updateProduct(
                        1L, update(2), 1L
                )
        );
    }

    @Test
    void updateRejectsOtherOwner() {
        when(mapper.findById(1L))
                .thenReturn(product(1, 2, "DRAFT", 0));

        assertThrows(
                AccessDeniedException.class,
                () -> service.updateProduct(
                        1L, update(0), 1L
                )
        );
    }

    @Test
    void updateSucceeds() {
        when(mapper.findById(1L))
                .thenReturn(product(1, 1, "DRAFT", 2));

        when(mapper.updateOwnedDraft(
                any(Product.class), eq(1L)
        )).thenReturn(1);

        service.updateProduct(1L, update(2), 1L);

        verify(mapper).updateOwnedDraft(
                argThat(p ->
                        "二手计算机网络教材".equals(p.getTitle())
                        && p.getPrice().compareTo(
                                new BigDecimal("18.00")
                        ) == 0
                        && p.getVersion().equals(2)
                ),
                eq(1L)
        );
    }

    @Test
    void updateRejectsConcurrentChange() {
        when(mapper.findById(1L))
                .thenReturn(product(1, 1, "DRAFT", 2));

        assertThrows(
                ProductConflictException.class,
                () -> service.updateProduct(
                        1L, update(2), 1L
                )
        );
    }

    @Test
    void publishSucceeds() {
        when(mapper.findById(1L))
                .thenReturn(product(1, 1, "DRAFT", 2));

        when(mapper.publishOwnedDraft(1L, 1L, 2))
                .thenReturn(1);

        service.publishProduct(1L, 1L);

        verify(mapper).publishOwnedDraft(1L, 1L, 2);
    }

    @Test
    void publishRejectsOtherOwner() {
        when(mapper.findById(1L))
                .thenReturn(product(1, 2, "DRAFT", 0));

        assertThrows(
                AccessDeniedException.class,
                () -> service.publishProduct(1L, 1L)
        );
    }

    @Test
    void publishRejectsAlreadyPublished() {
        when(mapper.findById(1L))
                .thenReturn(product(1, 1, "PUBLISHED", 1));

        assertThrows(
                ProductStatusException.class,
                () -> service.publishProduct(1L, 1L)
        );
    }

    @Test
    void bannedSellerCannotPublish() {
        when(jdbc.query(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<String>>any(),
                eq(1L)
        )).thenReturn(List.of("BANNED"));

        assertThrows(
                AccessDeniedException.class,
                () -> service.publishProduct(1L, 1L)
        );

        verify(mapper, never()).publishOwnedDraft(
                anyLong(), anyLong(), anyInt()
        );
    }
}
