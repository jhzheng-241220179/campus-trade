
package com.campustrade.service;

import com.campustrade.exception.ProductConflictException;
import com.campustrade.mapper.ProductReservationMapper;
import com.campustrade.mapper.ReservationRoundMapper;
import com.campustrade.model.ReservationRound;
import com.campustrade.model.ReservationRoundStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationLifecycleServiceTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long ROUND_ID = 100L;
    private static final Long SELLER_ID = 2L;
    private static final Long BUYER_ID = 5L;

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 11, 25, 14, 0);

    private ReservationRoundMapper roundMapper;
    private ProductReservationMapper reservationMapper;
    private JdbcTemplate jdbcTemplate;

    private ReservationLifecycleService service;

    @BeforeEach
    void setUp() {
        roundMapper = mock(
                ReservationRoundMapper.class
        );

        reservationMapper = mock(
                ProductReservationMapper.class
        );

        jdbcTemplate = mock(JdbcTemplate.class);

        Clock clock = Clock.fixed(
                Instant.parse("2026-11-25T06:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );

        service = new ReservationLifecycleService(
                roundMapper,
                reservationMapper,
                jdbcTemplate,
                clock
        );
    }

    private ReservationRound round(
            Long id,
            Long productId,
            ReservationRoundStatus status
    ) {
        ReservationRound round =
                new ReservationRound();

        round.setId(id);
        round.setProductId(productId);
        round.setStatus(status);
        round.setVersion(0);
        round.setDrawVersion(0);

        return round;
    }

    private void givenBuyerRoundIds(
            List<Long> roundIds
    ) {
        when(jdbcTemplate.query(
                contains("SELECT rr.id"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Long>>any(),
                eq(BUYER_ID)
        )).thenReturn(roundIds);
    }

    private void givenBuyerCancellation(
            Long roundId,
            int affectedRows
    ) {
        when(jdbcTemplate.update(
                contains("UPDATE product_reservations"),
                eq(NOW),
                eq(roundId),
                eq(BUYER_ID)
        )).thenReturn(affectedRows);
    }

    // =============================================
    // 卖家主动下架
    // =============================================

    @Test
    void productWithoutActiveRoundCanOffShelf() {
        when(roundMapper.findActiveByProductIdForUpdate(
                PRODUCT_ID
        )).thenReturn(null);

        assertDoesNotThrow(
                () -> service.requireNoActiveRound(
                        PRODUCT_ID
                )
        );

        verifyNoInteractions(reservationMapper);
    }

    @Test
    void productWithOpenRoundCannotOffShelf() {
        when(roundMapper.findActiveByProductIdForUpdate(
                PRODUCT_ID
        )).thenReturn(
                round(
                        ROUND_ID,
                        PRODUCT_ID,
                        ReservationRoundStatus.OPEN
                )
        );

        assertThrows(
                ProductConflictException.class,
                () -> service.requireNoActiveRound(
                        PRODUCT_ID
                )
        );
    }

    @Test
    void productWithFrozenRoundCannotOffShelf() {
        when(roundMapper.findActiveByProductIdForUpdate(
                PRODUCT_ID
        )).thenReturn(
                round(
                        ROUND_ID,
                        PRODUCT_ID,
                        ReservationRoundStatus.FROZEN
                )
        );

        assertThrows(
                ProductConflictException.class,
                () -> service.requireNoActiveRound(
                        PRODUCT_ID
                )
        );
    }

    // =============================================
    // 管理员强制下架
    // =============================================

    @Test
    void cancellingProductWithoutRoundDoesNothing() {
        when(roundMapper.findActiveByProductIdForUpdate(
                PRODUCT_ID
        )).thenReturn(null);

        service.cancelProductReservations(
                PRODUCT_ID,
                "ADMIN_OFF_SHELF"
        );

        verifyNoInteractions(reservationMapper);

        verify(roundMapper, never()).cancel(
                anyLong(),
                anyString(),
                any(LocalDateTime.class),
                anyInt()
        );
    }

    @Test
    void adminOffShelfCancelsReservationsAndRound() {
        when(roundMapper.findActiveByProductIdForUpdate(
                PRODUCT_ID
        )).thenReturn(
                round(
                        ROUND_ID,
                        PRODUCT_ID,
                        ReservationRoundStatus.OPEN
                )
        );

        when(roundMapper.cancel(
                ROUND_ID,
                "ADMIN_OFF_SHELF",
                NOW,
                0
        )).thenReturn(1);

        service.cancelProductReservations(
                PRODUCT_ID,
                "ADMIN_OFF_SHELF"
        );

        var order = inOrder(
                reservationMapper,
                roundMapper
        );

        order.verify(reservationMapper)
                .cancelAllActiveByRoundId(
                        ROUND_ID,
                        "ADMIN_OFF_SHELF",
                        NOW
                );

        order.verify(roundMapper).cancel(
                ROUND_ID,
                "ADMIN_OFF_SHELF",
                NOW,
                0
        );
    }

    @Test
    void frozenRoundCanBeCancelledByAdmin() {
        when(roundMapper.findActiveByProductIdForUpdate(
                PRODUCT_ID
        )).thenReturn(
                round(
                        ROUND_ID,
                        PRODUCT_ID,
                        ReservationRoundStatus.FROZEN
                )
        );

        when(roundMapper.cancel(
                ROUND_ID,
                "ADMIN_OFF_SHELF",
                NOW,
                0
        )).thenReturn(1);

        assertDoesNotThrow(
                () -> service.cancelProductReservations(
                        PRODUCT_ID,
                        "ADMIN_OFF_SHELF"
                )
        );

        verify(reservationMapper)
                .cancelAllActiveByRoundId(
                        ROUND_ID,
                        "ADMIN_OFF_SHELF",
                        NOW
                );
    }

    @Test
    void roundVersionConflictThrowsException() {
        when(roundMapper.findActiveByProductIdForUpdate(
                PRODUCT_ID
        )).thenReturn(
                round(
                        ROUND_ID,
                        PRODUCT_ID,
                        ReservationRoundStatus.OPEN
                )
        );

        when(roundMapper.cancel(
                ROUND_ID,
                "ADMIN_OFF_SHELF",
                NOW,
                0
        )).thenReturn(0);

        assertThrows(
                ProductConflictException.class,
                () -> service.cancelProductReservations(
                        PRODUCT_ID,
                        "ADMIN_OFF_SHELF"
                )
        );
    }

    // =============================================
    // 封禁卖家
    // =============================================

    @Test
    void sellerBanCancelsAllProductRounds() {
        when(jdbcTemplate.query(
                contains("FROM products"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Long>>any(),
                eq(SELLER_ID)
        )).thenReturn(List.of(10L, 11L));

        when(roundMapper.findActiveByProductIdForUpdate(
                10L
        )).thenReturn(
                round(
                        100L,
                        10L,
                        ReservationRoundStatus.OPEN
                )
        );

        when(roundMapper.findActiveByProductIdForUpdate(
                11L
        )).thenReturn(
                round(
                        101L,
                        11L,
                        ReservationRoundStatus.FROZEN
                )
        );

        when(roundMapper.cancel(
                eq(100L),
                eq("SELLER_BANNED"),
                eq(NOW),
                eq(0)
        )).thenReturn(1);

        when(roundMapper.cancel(
                eq(101L),
                eq("SELLER_BANNED"),
                eq(NOW),
                eq(0)
        )).thenReturn(1);

        service.cancelSellerReservations(SELLER_ID);

        verify(reservationMapper)
                .cancelAllActiveByRoundId(
                        100L,
                        "SELLER_BANNED",
                        NOW
                );

        verify(reservationMapper)
                .cancelAllActiveByRoundId(
                        101L,
                        "SELLER_BANNED",
                        NOW
                );

        verify(roundMapper).cancel(
                100L,
                "SELLER_BANNED",
                NOW,
                0
        );

        verify(roundMapper).cancel(
                101L,
                "SELLER_BANNED",
                NOW,
                0
        );
    }

    @Test
    void sellerWithoutProductsNeedsNoCancellation() {
        when(jdbcTemplate.query(
                contains("FROM products"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Long>>any(),
                eq(SELLER_ID)
        )).thenReturn(List.of());

        service.cancelSellerReservations(SELLER_ID);

        verifyNoInteractions(roundMapper);
        verifyNoInteractions(reservationMapper);
    }

    // =============================================
    // 封禁买家
    // =============================================

    @Test
    void buyerBanKeepsRoundWhenOtherBuyersRemain() {
        givenBuyerRoundIds(
                List.of(ROUND_ID)
        );

        when(roundMapper.findByIdForUpdate(
                ROUND_ID
        )).thenReturn(
                round(
                        ROUND_ID,
                        PRODUCT_ID,
                        ReservationRoundStatus.OPEN
                )
        );

        givenBuyerCancellation(
                ROUND_ID,
                1
        );

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(2);

        service.cancelBuyerReservations(BUYER_ID);

        verify(jdbcTemplate).update(
                contains("UPDATE product_reservations"),
                eq(NOW),
                eq(ROUND_ID),
                eq(BUYER_ID)
        );

        verify(roundMapper, never()).cancel(
                anyLong(),
                anyString(),
                any(LocalDateTime.class),
                anyInt()
        );

        verify(reservationMapper, never())
                .cancelAllActiveByRoundId(
                        anyLong(),
                        anyString(),
                        any(LocalDateTime.class)
                );
    }

    @Test
    void banningLastBuyerClosesRound() {
        givenBuyerRoundIds(
                List.of(ROUND_ID)
        );

        when(roundMapper.findByIdForUpdate(
                ROUND_ID
        )).thenReturn(
                round(
                        ROUND_ID,
                        PRODUCT_ID,
                        ReservationRoundStatus.FROZEN
                )
        );

        givenBuyerCancellation(
                ROUND_ID,
                1
        );

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(0);

        when(roundMapper.cancel(
                ROUND_ID,
                "NO_ACTIVE_BUYERS",
                NOW,
                0
        )).thenReturn(1);

        service.cancelBuyerReservations(BUYER_ID);

        verify(roundMapper).cancel(
                ROUND_ID,
                "NO_ACTIVE_BUYERS",
                NOW,
                0
        );
    }

    @Test
    void alreadyCancelledReservationNeedsNoUpdate() {
        givenBuyerRoundIds(
                List.of(ROUND_ID)
        );

        when(roundMapper.findByIdForUpdate(
                ROUND_ID
        )).thenReturn(
                round(
                        ROUND_ID,
                        PRODUCT_ID,
                        ReservationRoundStatus.OPEN
                )
        );

        givenBuyerCancellation(
                ROUND_ID,
                0
        );

        service.cancelBuyerReservations(BUYER_ID);

        verifyNoInteractions(reservationMapper);

        verify(roundMapper, never()).cancel(
                anyLong(),
                anyString(),
                any(LocalDateTime.class),
                anyInt()
        );
    }

    @Test
    void buyerWithoutReservationsNeedsNoCancellation() {
        givenBuyerRoundIds(List.of());

        service.cancelBuyerReservations(BUYER_ID);

        verifyNoInteractions(roundMapper);
        verifyNoInteractions(reservationMapper);
    }

    @Test
    void matchingRoundRequiresFutureLifecycleHandling() {
        when(roundMapper.findActiveByProductIdForUpdate(
                PRODUCT_ID
        )).thenReturn(
                round(
                        ROUND_ID,
                        PRODUCT_ID,
                        ReservationRoundStatus.MATCHING
                )
        );

        assertThrows(
                ProductConflictException.class,
                () -> service.cancelProductReservations(
                        PRODUCT_ID,
                        "ADMIN_OFF_SHELF"
                )
        );

        verifyNoInteractions(reservationMapper);
    }
}
