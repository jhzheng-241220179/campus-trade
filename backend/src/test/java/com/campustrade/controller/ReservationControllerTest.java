
package com.campustrade.controller;

import com.campustrade.config.SecurityConfig;
import com.campustrade.dto.ReservationResponse;
import com.campustrade.exception.ProductConflictException;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.model.ReservationRoundStatus;
import com.campustrade.model.ReservationStatus;
import com.campustrade.security.CurrentUserIdResolver;
import com.campustrade.service.ReservationService;

import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long BUYER_ID = 5L;

    private static final String EMAIL =
            "buyer@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private CurrentUserIdResolver currentUserIdResolver;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 模拟 AccountStatusFilter 查询有效账户。
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
        )).thenReturn(BUYER_ID);
    }

    private ReservationResponse response(
            ReservationStatus status
    ) {
        LocalDateTime now = LocalDateTime.of(
                2026, 11, 25, 14, 0
        );

        boolean active =
                status == ReservationStatus.ACTIVE;

        return new ReservationResponse(
                PRODUCT_ID,
                1L,
                ReservationRoundStatus.OPEN,
                now,
                now.plusMinutes(1),
                now.plusMinutes(30),
                now.plusMinutes(60),
                active ? 1 : 0,
                100L,
                status,
                1,
                null,
                false,
                active,
                now
        );
    }

    @Test
    void anonymousCannotQueryReservation() throws Exception {
        mockMvc.perform(
                get("/api/products/10/reservations/mine")
        ).andExpect(
                status().isUnauthorized()
        );

        verifyNoInteractions(reservationService);
    }

    @Test
    void anonymousCannotCreateReservation() throws Exception {
        mockMvc.perform(
                post("/api/products/10/reservations")
                        .with(csrf())
        ).andExpect(
                status().isUnauthorized()
        );

        verifyNoInteractions(reservationService);
    }

    @Test
    void reservationRequiresCsrf() throws Exception {
        mockMvc.perform(
                post("/api/products/10/reservations")
                        .with(user(EMAIL).roles("USER"))
        ).andExpect(
                status().isForbidden()
        );

        verifyNoInteractions(reservationService);
    }

    @Test
    void authenticatedUserCanCreateReservation()
            throws Exception {

        when(reservationService.reserve(
                PRODUCT_ID,
                BUYER_ID
        )).thenReturn(
                response(ReservationStatus.ACTIVE)
        );

        mockMvc.perform(
                post("/api/products/10/reservations")
                        .with(user(EMAIL).roles("USER"))
                        .with(csrf())
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.productId").value(10))
        .andExpect(jsonPath("$.data.reservationId").value(100))
        .andExpect(
                jsonPath("$.data.reservationStatus")
                        .value("ACTIVE")
        );

        verify(reservationService).reserve(
                PRODUCT_ID,
                BUYER_ID
        );
    }

    @Test
    void authenticatedUserCanQueryReservation()
            throws Exception {

        when(reservationService.getReservation(
                PRODUCT_ID,
                BUYER_ID
        )).thenReturn(
                response(ReservationStatus.ACTIVE)
        );

        mockMvc.perform(
                get("/api/products/10/reservations/mine")
                        .with(user(EMAIL).roles("USER"))
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$.data.canCancel").value(true)
        );

        verify(reservationService).getReservation(
                PRODUCT_ID,
                BUYER_ID
        );
    }

    @Test
    void authenticatedUserCanCancelReservation()
            throws Exception {

        when(reservationService.cancel(
                PRODUCT_ID,
                BUYER_ID
        )).thenReturn(
                response(ReservationStatus.CANCELLED)
        );

        mockMvc.perform(
                delete("/api/products/10/reservations/mine")
                        .with(user(EMAIL).roles("USER"))
                        .with(csrf())
        )
        .andExpect(status().isOk())
        .andExpect(
                jsonPath("$.data.reservationStatus")
                        .value("CANCELLED")
        );

        verify(reservationService).cancel(
                PRODUCT_ID,
                BUYER_ID
        );
    }

    @Test
    void cancellationRequiresCsrf() throws Exception {
        mockMvc.perform(
                delete("/api/products/10/reservations/mine")
                        .with(user(EMAIL).roles("USER"))
        ).andExpect(
                status().isForbidden()
        );

        verifyNoInteractions(reservationService);
    }

    @Test
    void businessConflictReturns409() throws Exception {
        when(reservationService.reserve(
                PRODUCT_ID,
                BUYER_ID
        )).thenThrow(
                new ProductConflictException(
                        "不能预约自己发布的商品"
                )
        );

        mockMvc.perform(
                post("/api/products/10/reservations")
                        .with(user(EMAIL).roles("USER"))
                        .with(csrf())
        )
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void missingProductReturns404() throws Exception {
        when(reservationService.getReservation(
                PRODUCT_ID,
                BUYER_ID
        )).thenThrow(
                new ProductNotFoundException(PRODUCT_ID)
        );

        mockMvc.perform(
                get("/api/products/10/reservations/mine")
                        .with(user(EMAIL).roles("USER"))
        )
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void businessAccessDeniedReturns403() throws Exception {
        when(reservationService.reserve(
                PRODUCT_ID,
                BUYER_ID
        )).thenThrow(
                new AccessDeniedException(
                        "当前账户无法进行预约操作"
                )
        );

        mockMvc.perform(
                post("/api/products/10/reservations")
                        .with(user(EMAIL).roles("USER"))
                        .with(csrf())
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403));
    }
}
