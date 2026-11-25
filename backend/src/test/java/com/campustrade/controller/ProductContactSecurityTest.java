
package com.campustrade.controller;

import com.campustrade.config.SecurityConfig;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.model.Product;
import com.campustrade.security.CurrentUserIdResolver;
import com.campustrade.service.ProductService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductContactSecurityTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long SELLER_ID = 2L;

    private static final String EMAIL =
            "seller@example.com";

    private static final String CONTACT =
            "private-test-contact";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CurrentUserIdResolver currentUserIdResolver;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Object>>any(),
                eq(EMAIL)
        )).thenAnswer(invocation -> {
            RowMapper<?> mapper =
                    invocation.getArgument(1);

            ResultSet rs = mock(ResultSet.class);

            when(rs.getString("status"))
                    .thenReturn("ACTIVE");

            when(rs.getString("role"))
                    .thenReturn("USER");

            when(rs.getBoolean("email_verified"))
                    .thenReturn(true);

            return List.of(mapper.mapRow(rs, 0));
        });

        when(currentUserIdResolver.requireCurrentUserId(
                any(Authentication.class)
        )).thenReturn(SELLER_ID);
    }

    private Product product(String status) {
        Product product = new Product();

        product.setId(PRODUCT_ID);
        product.setSellerId(SELLER_ID);
        product.setTitle("测试教材");
        product.setPrice(new BigDecimal("20.00"));
        product.setCategory("教材");
        product.setDescription("保存完好");
        product.setConditionLevel("九成新");
        product.setContact(CONTACT);
        product.setStatus(status);
        product.setVersion(0);

        return product;
    }

    @Test
    void publicListDoesNotExposeContact()
            throws Exception {

        when(productService.listPublishedProducts())
                .thenReturn(
                        List.of(product("PUBLISHED"))
                );

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data[0].id")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.data[0].title")
                                .value("测试教材")
                )
                .andExpect(
                        jsonPath("$.data[0].contact")
                                .doesNotHaveJsonPath()
                );
    }

    @Test
    void publicDetailDoesNotExposeContact()
            throws Exception {

        when(productService.getPublishedProductById(
                PRODUCT_ID
        )).thenReturn(product("PUBLISHED"));

        mockMvc.perform(get("/api/products/10"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.id")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.data.sellerId")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.data.contact")
                                .doesNotHaveJsonPath()
                );
    }

    @Test
    void authenticatedPublicDetailStillHidesContact()
            throws Exception {

        when(productService.getPublishedProductById(
                PRODUCT_ID
        )).thenReturn(product("PUBLISHED"));

        mockMvc.perform(
                get("/api/products/10")
                        .with(user(EMAIL).roles("USER"))
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$.data.contact")
                        .doesNotHaveJsonPath()
        );
    }

    @Test
    void sellerCanReadContactInOwnProductList()
            throws Exception {

        when(productService.listMyProducts(
                SELLER_ID
        )).thenReturn(
                List.of(product("PUBLISHED"))
        );

        mockMvc.perform(
                get("/api/products/mine")
                        .with(user(EMAIL).roles("USER"))
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$.data[0].contact")
                        .value(CONTACT)
        );

        verify(productService)
                .listMyProducts(SELLER_ID);
    }

    @Test
    void sellerCanReadContactInOwnDraft()
            throws Exception {

        when(productService.getOwnedDraftById(
                PRODUCT_ID,
                SELLER_ID
        )).thenReturn(product("DRAFT"));

        mockMvc.perform(
                get("/api/products/mine/10")
                        .with(user(EMAIL).roles("USER"))
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$.data.contact")
                        .value(CONTACT)
        );

        verify(productService)
                .getOwnedDraftById(
                        PRODUCT_ID,
                        SELLER_ID
                );
    }

    @Test
    void anonymousCannotReadPrivateDraft()
            throws Exception {

        mockMvc.perform(
                get("/api/products/mine/10")
        ).andExpect(
                status().isUnauthorized()
        );

        verifyNoInteractions(productService);
    }

    @Test
    void unpublishedProductRemainsUnavailable()
            throws Exception {

        when(productService.getPublishedProductById(
                PRODUCT_ID
        )).thenThrow(
                new ProductNotFoundException(
                        PRODUCT_ID
                )
        );

        mockMvc.perform(
                get("/api/products/10")
        ).andExpect(
                status().isNotFound()
        );
    }
}
