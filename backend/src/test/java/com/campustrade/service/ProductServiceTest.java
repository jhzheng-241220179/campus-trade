package com.campustrade.service;

import com.campustrade.dto.ProductUpdateRequest;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.mapper.UserMapper;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.exception.ProductConflictException;
import com.campustrade.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductMapper productMapper;
    private UserMapper userMapper;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        userMapper = mock(UserMapper.class);

        productService = new ProductService(
                productMapper,
                userMapper
        );
    }

    @Test
    void updateProduct_shouldThrowWhenNotFound() {

        Long id = 999L;

        when(productMapper.findById(id)).thenReturn(null);

        ProductUpdateRequest request = new ProductUpdateRequest(
                "二手教材",
                new BigDecimal("20.00"),
                "教材",
                "保存完好",
                "九成新",
                "test-contact",
                0
        );

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.updateProduct(id, request)
        );

        verify(productMapper, never()).update(any());
    }
    @Test
void updateProduct_shouldThrowWhenStatusIsPublished() {

    Long id = 2L;

    Product product = new Product();
    product.setId(id);
    product.setStatus("PUBLISHED");
    product.setVersion(0);

    when(productMapper.findById(id)).thenReturn(product);

    ProductUpdateRequest request = new ProductUpdateRequest(
            "二手数据库教材",
            new BigDecimal("30.00"),
            "教材",
            "保存完好",
            "九成新",
            "test-contact",
            0
    );

    assertThrows(
                ProductStatusException.class,
                () -> productService.updateProduct(id, request)
        );

        verify(productMapper, never()).update(any());
    }
    @Test
void updateProduct_shouldThrowWhenVersionIsOutdated() {

    Long id = 1L;

    Product product = new Product();
    product.setId(id);
    product.setStatus("DRAFT");
    product.setVersion(3);

    when(productMapper.findById(id)).thenReturn(product);

    ProductUpdateRequest request = new ProductUpdateRequest(
            "二手计算机网络教材",
            new BigDecimal("18.00"),
            "教材",
            "保存完好",
            "九成新",
            "test-contact",
            2
    );

    assertThrows(
                ProductConflictException.class,
                () -> productService.updateProduct(id, request)
        );

        verify(productMapper, never()).update(any());
    }
    @Test
void updateProduct_shouldSucceedWhenValid() {

    Long id = 1L;

    Product product = new Product();
    product.setId(id);
    product.setTitle("旧标题");
    product.setPrice(new BigDecimal("25.00"));
    product.setStatus("DRAFT");
    product.setVersion(2);

    when(productMapper.findById(id)).thenReturn(product);
    when(productMapper.update(any(Product.class))).thenReturn(1);

    ProductUpdateRequest request = new ProductUpdateRequest(
            "二手计算机网络教材",
            new BigDecimal("18.00"),
            "教材",
            "保存完好",
            "九成新",
            "test-contact",
            2
    );

    assertDoesNotThrow(
                () -> productService.updateProduct(id, request)
        );

        verify(productMapper, times(1)).update(argThat(p ->
                p.getId().equals(1L)
                        && p.getTitle().equals("二手计算机网络教材")
                        && p.getPrice().compareTo(new BigDecimal("18.00")) == 0
                        && p.getVersion().equals(2)
        ));
    }
}