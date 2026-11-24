
package com.campustrade.service;

import com.campustrade.dto.ProductImageResponse;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.mapper.ProductImageMapper;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.model.Product;
import com.campustrade.model.ProductImage;
import com.campustrade.security.CurrentUserIdResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductImageServiceTest {

    private ProductMapper productMapper;
    private ProductImageMapper imageMapper;
    private CurrentUserIdResolver userIdResolver;
    private JdbcTemplate jdbcTemplate;

    private ProductImageService service;
    private Authentication authentication;

    private static final Long PRODUCT_ID = 10L;
    private static final Long SELLER_ID = 1L;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        imageMapper = mock(ProductImageMapper.class);
        userIdResolver = mock(CurrentUserIdResolver.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        authentication = mock(Authentication.class);

        service = new ProductImageService(
                productMapper,
                imageMapper,
                userIdResolver,
                jdbcTemplate,
                "./target/test-image-uploads"
        );

        when(userIdResolver.requireCurrentUserId(authentication))
                .thenReturn(SELLER_ID);

        givenSellerStatus("ACTIVE");
    }

    private void givenSellerStatus(String status) {
        when(jdbcTemplate.query(
                contains("FOR UPDATE"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<String>>any(),
                eq(SELLER_ID)
        )).thenReturn(List.of(status));
    }

    private Product product(
            Long sellerId,
            String status
    ) {
        Product value = new Product();
        value.setId(PRODUCT_ID);
        value.setSellerId(sellerId);
        value.setStatus(status);
        return value;
    }

    private MockMultipartFile validImage() {
        byte[] pngHeader = {
                (byte) 137, 80, 78, 71, 13, 10, 26, 10
        };

        return new MockMultipartFile(
                "file",
                "sample.png",
                "image/png",
                pngHeader
        );
    }

    @Test
    void upload_shouldRejectOtherSeller() {
        when(productMapper.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(product(2L, "DRAFT"));

        assertThrows(
                AccessDeniedException.class,
                () -> service.upload(
                        PRODUCT_ID,
                        validImage(),
                        authentication
                )
        );

        verifyNoInteractions(imageMapper);
    }

    @Test
    void upload_shouldRejectPublishedProduct() {
        when(productMapper.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(product(SELLER_ID, "PUBLISHED"));

        assertThrows(
                ProductStatusException.class,
                () -> service.upload(
                        PRODUCT_ID,
                        validImage(),
                        authentication
                )
        );

        verifyNoInteractions(imageMapper);
    }

    @Test
    void upload_shouldRejectWhenImageLimitReached() {
        when(productMapper.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(product(SELLER_ID, "DRAFT"));

        List<ProductImage> images = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            ProductImage image = new ProductImage();
            image.setId((long) i + 1);
            image.setProductId(PRODUCT_ID);
            image.setDisplayOrder(i);
            images.add(image);
        }

        when(imageMapper.findByProductId(PRODUCT_ID))
                .thenReturn(images);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(
                        PRODUCT_ID,
                        validImage(),
                        authentication
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(imageMapper, never())
                .insert(any(ProductImage.class));
    }

    @Test
    void listImages_shouldAllowPublishedProduct() {
        when(productMapper.findById(PRODUCT_ID))
                .thenReturn(product(SELLER_ID, "PUBLISHED"));

        ProductImage image = new ProductImage();
        image.setId(5L);
        image.setProductId(PRODUCT_ID);
        image.setDisplayOrder(0);

        when(imageMapper.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(image));

        List<ProductImageResponse> result =
                service.listImages(PRODUCT_ID, null);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).id());
        assertEquals(
                "/api/products/10/images/5",
                result.get(0).url()
        );
    }

    @Test
    void listImages_shouldRejectOtherUserDraft() {
        when(productMapper.findById(PRODUCT_ID))
                .thenReturn(product(2L, "DRAFT"));

        assertThrows(
                ProductNotFoundException.class,
                () -> service.listImages(
                        PRODUCT_ID,
                        authentication
                )
        );

        verify(imageMapper, never())
                .findByProductId(PRODUCT_ID);
    }

    @Test
    void bannedSellerCannotUpload() {
        givenSellerStatus("BANNED");

        assertThrows(
                AccessDeniedException.class,
                () -> service.upload(
                        PRODUCT_ID,
                        validImage(),
                        authentication
                )
        );

        verify(productMapper, never())
                .findByIdForUpdate(anyLong());

        verifyNoInteractions(imageMapper);
    }

    @Test
    void bannedSellerCannotDeleteImage() {
        givenSellerStatus("BANNED");

        assertThrows(
                AccessDeniedException.class,
                () -> service.deleteImage(
                        PRODUCT_ID,
                        5L,
                        authentication
                )
        );

        verify(productMapper, never())
                .findByIdForUpdate(anyLong());

        verifyNoInteractions(imageMapper);
    }

    @Test
    void uploadLocksSellerBeforeProduct() {
        when(productMapper.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(product(SELLER_ID, "PUBLISHED"));

        assertThrows(
                ProductStatusException.class,
                () -> service.upload(
                        PRODUCT_ID,
                        validImage(),
                        authentication
                )
        );

        var order = inOrder(jdbcTemplate, productMapper);

        order.verify(jdbcTemplate).query(
                contains("FOR UPDATE"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<String>>any(),
                eq(SELLER_ID)
        );

        order.verify(productMapper)
                .findByIdForUpdate(PRODUCT_ID);
    }
}
