
package com.campustrade.service;

import com.campustrade.config.ReservationProperties;
import com.campustrade.dto.ReservationResponse;
import com.campustrade.exception.ProductConflictException;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.mapper.ProductReservationMapper;
import com.campustrade.mapper.ReservationRoundMapper;
import com.campustrade.model.Product;
import com.campustrade.model.ProductReservation;
import com.campustrade.model.ReservationRound;
import com.campustrade.model.ReservationRoundStatus;
import com.campustrade.model.ReservationStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationServiceTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long SELLER_ID = 2L;
    private static final Long BUYER_ID = 5L;
    private static final Long ROUND_ID = 100L;
    private static final Long RESERVATION_ID = 200L;

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 11, 27, 14, 0);

    private ProductMapper productMapper;
    private ReservationRoundMapper roundMapper;
    private ProductReservationMapper reservationMapper;
    private JdbcTemplate jdbcTemplate;
    private ReservationDrawService drawService;

    private ReservationProperties properties;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        roundMapper = mock(ReservationRoundMapper.class);
        reservationMapper = mock(ProductReservationMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        drawService = mock(ReservationDrawService.class);

        properties = new ReservationProperties();

        Clock clock = Clock.fixed(
                Instant.parse("2026-11-27T06:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );

        service = new ReservationService(
                productMapper,
                roundMapper,
                reservationMapper,
                jdbcTemplate,
                properties,
                clock,
                drawService
        );

        givenProduct("PUBLISHED", SELLER_ID);
        givenAccount(SELLER_ID, "ACTIVE", true);
        givenAccount(BUYER_ID, "ACTIVE", true);
    }

    private void givenProduct(
            String status,
            Long sellerId
    ) {
        Product product = new Product();

        product.setId(PRODUCT_ID);
        product.setSellerId(sellerId);
        product.setStatus(status);
        product.setVersion(0);

        when(productMapper.findById(PRODUCT_ID))
                .thenReturn(product);

        when(productMapper.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(product);
    }

    private void givenAccount(
            Long userId,
            String status,
            boolean verified
    ) {
        when(jdbcTemplate.query(
                contains("FOR UPDATE"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Object>>any(),
                eq(userId)
        )).thenAnswer(invocation -> {
            RowMapper<?> mapper =
                    invocation.getArgument(1);

            ResultSet rs = mock(ResultSet.class);

            when(rs.getString("status"))
                    .thenReturn(status);

            when(rs.getBoolean("email_verified"))
                    .thenReturn(verified);

            return List.of(mapper.mapRow(rs, 0));
        });
    }

    private ReservationRound existingRound(
            LocalDateTime firstReservedAt
    ) {
        ReservationRound round = new ReservationRound();

        round.setId(ROUND_ID);
        round.setProductId(PRODUCT_ID);
        round.setStatus(ReservationRoundStatus.OPEN);
        round.setFirstReservedAt(firstReservedAt);

        round.setPriorityEndsAt(
                firstReservedAt.plusMinutes(1)
        );

        round.setCoolingEndsAt(
                firstReservedAt.plusMinutes(30)
        );

        round.setSellerResponseDeadline(
                firstReservedAt.plusMinutes(60)
        );

        round.setVersion(0);
        round.setDrawVersion(0);

        when(roundMapper.findActiveByProductIdForUpdate(
                PRODUCT_ID
        )).thenReturn(round);

        return round;
    }

    private ProductReservation existingReservation(
            ReservationStatus status
    ) {
        ProductReservation reservation =
                new ProductReservation();

        reservation.setId(RESERVATION_ID);
        reservation.setRoundId(ROUND_ID);
        reservation.setBuyerId(BUYER_ID);
        reservation.setStatus(status);
        reservation.setReservationOrder(1);

        reservation.setQueuePosition(
                status == ReservationStatus.ACTIVE
                        ? 1 : null
        );

        reservation.setVersion(0);

        when(reservationMapper.findByRoundAndBuyer(
                ROUND_ID, BUYER_ID
        )).thenReturn(reservation);

        when(reservationMapper.findByRoundAndBuyerForUpdate(
                ROUND_ID, BUYER_ID
        )).thenReturn(reservation);

        return reservation;
    }

    private void allowRoundInsert() {
        when(roundMapper.insert(
                any(ReservationRound.class)
        )).thenAnswer(invocation -> {

            ReservationRound round =
                    invocation.getArgument(0);

            round.setId(ROUND_ID);

            return 1;
        });
    }

    private void allowReservationInsert() {
        when(reservationMapper.insert(
                any(ProductReservation.class)
        )).thenAnswer(invocation -> {

            ProductReservation reservation =
                    invocation.getArgument(0);

            reservation.setId(RESERVATION_ID);

            ProductReservation latest =
                    new ProductReservation();

            latest.setId(RESERVATION_ID);
            latest.setRoundId(ROUND_ID);
            latest.setBuyerId(BUYER_ID);
            latest.setStatus(ReservationStatus.ACTIVE);

            latest.setReservationOrder(
                    reservation.getReservationOrder()
            );

            latest.setQueuePosition(1);
            latest.setVersion(1);

            when(reservationMapper.findById(
                    RESERVATION_ID
            )).thenReturn(latest);

            return 1;
        });
    }

    private void allowFirstDraw(
            ReservationRound round
    ) {
        when(drawService.drawAfterPriority(
                round, NOW
        )).thenAnswer(invocation -> {

            round.setDrawVersion(
                    round.getDrawVersion() + 1
            );

            round.setVersion(
                    round.getVersion() + 1
            );

            return true;
        });
    }

    // =================================================
    // Day 25 原有预约业务测试
    // =================================================

    @Test
    void firstReservationCreatesRoundAndDeadlines() {
        allowRoundInsert();
        allowReservationInsert();

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(1);

        ReservationResponse result =
                service.reserve(PRODUCT_ID, BUYER_ID);

        assertEquals(ROUND_ID, result.roundId());

        assertEquals(
                ReservationRoundStatus.OPEN,
                result.roundStatus()
        );

        assertEquals(NOW, result.firstReservedAt());

        assertEquals(
                NOW.plusMinutes(1),
                result.priorityEndsAt()
        );

        assertEquals(
                NOW.plusMinutes(30),
                result.coolingEndsAt()
        );

        assertEquals(
                NOW.plusMinutes(60),
                result.sellerResponseDeadline()
        );

        assertEquals(
                ReservationStatus.ACTIVE,
                result.reservationStatus()
        );

        assertEquals(1, result.reservationOrder());
        assertEquals(1, result.activeReservationCount());
        assertEquals(0, result.drawVersion());

        assertFalse(result.canReserve());
        assertTrue(result.canCancel());

        verify(roundMapper).insert(
                any(ReservationRound.class)
        );

        verify(reservationMapper).insert(
                any(ProductReservation.class)
        );
    }

    @Test
    void subsequentReservationDoesNotResetDeadlines() {
        LocalDateTime firstTime =
                NOW.minusMinutes(10);

        ReservationRound round =
                existingRound(firstTime);

        round.setDrawVersion(1);

        allowReservationInsert();

        when(reservationMapper.findMaxReservationOrder(
                ROUND_ID
        )).thenReturn(1);

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(2);

        ReservationResponse result =
                service.reserve(PRODUCT_ID, BUYER_ID);

        assertEquals(2, result.reservationOrder());

        assertEquals(
                firstTime,
                result.firstReservedAt()
        );

        assertEquals(
                round.getCoolingEndsAt(),
                result.coolingEndsAt()
        );

        assertEquals(
                firstTime.plusMinutes(60),
                result.sellerResponseDeadline()
        );

        verify(roundMapper, never()).insert(
                any(ReservationRound.class)
        );
    }

    @Test
    void repeatedReservationReturnsExistingRecord() {
        existingRound(NOW.minusMinutes(2));

        existingReservation(
                ReservationStatus.ACTIVE
        );

        ReservationResponse result =
                service.reserve(PRODUCT_ID, BUYER_ID);

        assertEquals(
                RESERVATION_ID,
                result.reservationId()
        );

        assertEquals(
                ReservationStatus.ACTIVE,
                result.reservationStatus()
        );

        verify(reservationMapper, never()).insert(
                any(ProductReservation.class)
        );

        verifyNoInteractions(drawService);
    }

    @Test
    void cannotReserveOwnProduct() {
        givenProduct("PUBLISHED", BUYER_ID);

        assertThrows(
                ProductConflictException.class,
                () -> service.reserve(PRODUCT_ID, BUYER_ID)
        );

        verify(reservationMapper, never()).insert(
                any(ProductReservation.class)
        );
    }

    @Test
    void cannotReserveUnpublishedProduct() {
        givenProduct("DRAFT", SELLER_ID);

        assertThrows(
                ProductConflictException.class,
                () -> service.reserve(PRODUCT_ID, BUYER_ID)
        );

        verifyNoInteractions(reservationMapper);
    }

    @Test
    void bannedBuyerCannotReserve() {
        givenAccount(BUYER_ID, "BANNED", true);

        assertThrows(
                AccessDeniedException.class,
                () -> service.reserve(PRODUCT_ID, BUYER_ID)
        );

        verifyNoInteractions(reservationMapper);
    }

    @Test
    void cannotReserveProductOfBannedSeller() {
        givenAccount(SELLER_ID, "BANNED", true);

        assertThrows(
                AccessDeniedException.class,
                () -> service.reserve(PRODUCT_ID, BUYER_ID)
        );

        verifyNoInteractions(reservationMapper);
    }

    @Test
    void cannotJoinAtCoolingDeadline() {
        existingRound(NOW.minusMinutes(30));

        assertThrows(
                ProductConflictException.class,
                () -> service.reserve(PRODUCT_ID, BUYER_ID)
        );

        verify(reservationMapper, never()).insert(
                any(ProductReservation.class)
        );

        verifyNoInteractions(drawService);
    }

    @Test
    void cannotExceedReservationLimit() {
        when(reservationMapper.countActiveByBuyerId(
                BUYER_ID
        )).thenReturn(5);

        assertThrows(
                ProductConflictException.class,
                () -> service.reserve(PRODUCT_ID, BUYER_ID)
        );

        verify(roundMapper, never()).insert(
                any(ReservationRound.class)
        );
    }

    @Test
    void cancellationCooldownPreventsNewReservation() {
        when(reservationMapper.findLatestBuyerCancellation(
                PRODUCT_ID, BUYER_ID
        )).thenReturn(NOW.minusMinutes(5));

        assertThrows(
                ProductConflictException.class,
                () -> service.reserve(PRODUCT_ID, BUYER_ID)
        );

        verify(reservationMapper, never()).insert(
                any(ProductReservation.class)
        );
    }

    @Test
    void lastBuyerCancellationClosesRound() {
        ReservationRound round =
                existingRound(NOW.minusMinutes(2));

        round.setDrawVersion(1);

        existingReservation(
                ReservationStatus.ACTIVE
        );

        when(reservationMapper.cancelByBuyer(
                RESERVATION_ID,
                BUYER_ID,
                NOW,
                0
        )).thenReturn(1);

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(0);

        when(roundMapper.cancel(
                ROUND_ID,
                "NO_ACTIVE_BUYERS",
                NOW,
                0
        )).thenReturn(1);

        ReservationResponse result = service.cancel(
                PRODUCT_ID,
                BUYER_ID,
                RESERVATION_ID
        );

        assertEquals(
                ReservationRoundStatus.CANCELLED,
                result.roundStatus()
        );

        assertEquals(
                ReservationStatus.CANCELLED,
                result.reservationStatus()
        );

        assertEquals(0, result.activeReservationCount());
        assertFalse(result.canCancel());

        verify(roundMapper).cancel(
                ROUND_ID,
                "NO_ACTIVE_BUYERS",
                NOW,
                0
        );

        verifyNoInteractions(drawService);
    }

    @Test
    void cancelledBuyerCannotRejoinSameRound() {
        existingRound(NOW.minusMinutes(15));

        existingReservation(
                ReservationStatus.CANCELLED
        );

        assertThrows(
                ProductConflictException.class,
                () -> service.reserve(PRODUCT_ID, BUYER_ID)
        );

        verify(reservationMapper, never()).insert(
                any(ProductReservation.class)
        );
    }

    @Test
    void accountsAreLockedInAscendingOrder() {
        allowRoundInsert();
        allowReservationInsert();

        service.reserve(PRODUCT_ID, BUYER_ID);

        var order = inOrder(
                jdbcTemplate,
                productMapper,
                roundMapper
        );

        order.verify(productMapper)
                .findById(PRODUCT_ID);

        order.verify(jdbcTemplate).query(
                contains("FOR UPDATE"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Object>>any(),
                eq(SELLER_ID)
        );

        order.verify(jdbcTemplate).query(
                contains("FOR UPDATE"),
                org.mockito.ArgumentMatchers
                        .<RowMapper<Object>>any(),
                eq(BUYER_ID)
        );

        order.verify(productMapper)
                .findByIdForUpdate(PRODUCT_ID);

        order.verify(roundMapper)
                .findActiveByProductIdForUpdate(PRODUCT_ID);
    }

    @Test
    void queryShowsFrozenAfterCoolingDeadline() {
        ReservationRound round =
                existingRound(NOW.minusMinutes(35));

        when(roundMapper.findActiveByProductId(
                PRODUCT_ID
        )).thenReturn(round);

        ReservationResponse result =
                service.getReservation(PRODUCT_ID, BUYER_ID);

        assertEquals(
                ReservationRoundStatus.FROZEN,
                result.roundStatus()
        );

        assertFalse(result.canReserve());
    }

    // =================================================
    // Day 26 动态抽签联动测试
    // =================================================

    @Test
    void firstReservationInitializesPriorityQueue() {
        allowRoundInsert();
        allowReservationInsert();

        ReservationResponse result =
                service.reserve(PRODUCT_ID, BUYER_ID);

        verify(drawService)
                .initializePriorityQueue(
                        any(ReservationRound.class),
                        eq(NOW)
                );

        verify(drawService, never())
                .redrawAfterNewReservation(
                        any(), anyLong(), any()
                );

        assertEquals(1, result.queuePosition());
        assertEquals(0, result.drawVersion());
    }

    @Test
    void joiningDuringPriorityDoesNotTriggerRandomDraw() {
        ReservationRound round =
                existingRound(NOW.minusSeconds(30));

        allowReservationInsert();

        when(reservationMapper.findMaxReservationOrder(
                ROUND_ID
        )).thenReturn(1);

        service.reserve(PRODUCT_ID, BUYER_ID);

        verify(drawService)
                .initializePriorityQueue(round, NOW);

        verify(drawService, never())
                .drawAfterPriority(any(), any());

        verify(drawService, never())
                .redrawAfterNewReservation(
                        any(), anyLong(), any()
                );
    }

    @Test
    void joiningDuringCoolingTriggersRedraw() {
        ReservationRound round =
                existingRound(NOW.minusMinutes(5));

        round.setDrawVersion(1);

        allowReservationInsert();

        service.reserve(PRODUCT_ID, BUYER_ID);

        verify(drawService)
                .redrawAfterNewReservation(
                        round, RESERVATION_ID, NOW
                );

        verify(drawService, never())
                .initializePriorityQueue(any(), any());

        verify(drawService, never())
                .drawAfterPriority(any(), any());
    }

    @Test
    void missedFirstDrawRunsBeforeNewBuyerInsertion() {
        ReservationRound round =
                existingRound(NOW.minusMinutes(2));

        allowReservationInsert();
        allowFirstDraw(round);

        service.reserve(PRODUCT_ID, BUYER_ID);

        var order = inOrder(
                drawService,
                reservationMapper
        );

        order.verify(drawService)
                .drawAfterPriority(round, NOW);

        order.verify(reservationMapper)
                .insert(any(ProductReservation.class));

        order.verify(drawService)
                .redrawAfterNewReservation(
                        round, RESERVATION_ID, NOW
                );

        order.verify(reservationMapper)
                .findById(RESERVATION_ID);
    }

    @Test
    void cancellationCompactsRemainingQueue() {
        ReservationRound round =
                existingRound(NOW.minusMinutes(5));

        round.setDrawVersion(2);

        existingReservation(
                ReservationStatus.ACTIVE
        );

        when(reservationMapper.cancelByBuyer(
                RESERVATION_ID,
                BUYER_ID,
                NOW,
                0
        )).thenReturn(1);

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(2);

        ReservationResponse result = service.cancel(
                PRODUCT_ID,
                BUYER_ID,
                RESERVATION_ID
        );

        verify(drawService).compactQueue(round);

        verify(drawService, never())
                .redrawAfterNewReservation(
                        any(), anyLong(), any()
                );

        assertEquals(2, round.getDrawVersion());
        assertEquals(2, result.drawVersion());
        assertEquals(2, result.activeReservationCount());

        assertEquals(
                ReservationStatus.CANCELLED,
                result.reservationStatus()
        );

        assertNull(result.queuePosition());
    }

    @Test
    void cancellationReloadsVersionAfterMissedFirstDraw() {
        ReservationRound round =
                existingRound(NOW.minusMinutes(2));

        ProductReservation before =
                existingReservation(
                        ReservationStatus.ACTIVE
                );

        ProductReservation after =
                new ProductReservation();

        after.setId(RESERVATION_ID);
        after.setRoundId(ROUND_ID);
        after.setBuyerId(BUYER_ID);
        after.setStatus(ReservationStatus.ACTIVE);
        after.setReservationOrder(1);
        after.setQueuePosition(2);
        after.setVersion(2);

        when(reservationMapper
                .findByRoundAndBuyerForUpdate(
                        ROUND_ID, BUYER_ID
                )
        ).thenReturn(before, after);

        allowFirstDraw(round);

        when(reservationMapper.cancelByBuyer(
                RESERVATION_ID,
                BUYER_ID,
                NOW,
                2
        )).thenReturn(1);

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(1);

        service.cancel(
                PRODUCT_ID,
                BUYER_ID,
                RESERVATION_ID
        );

        var order = inOrder(
                reservationMapper,
                drawService
        );

        order.verify(reservationMapper)
                .findByRoundAndBuyerForUpdate(
                        ROUND_ID, BUYER_ID
                );

        order.verify(drawService)
                .drawAfterPriority(round, NOW);

        order.verify(reservationMapper)
                .findByRoundAndBuyerForUpdate(
                        ROUND_ID, BUYER_ID
                );

        order.verify(reservationMapper)
                .cancelByBuyer(
                        RESERVATION_ID,
                        BUYER_ID,
                        NOW,
                        2
                );

        order.verify(drawService)
                .compactQueue(round);
    }

    // =================================================
    // Day 26 取消请求安全测试
    // =================================================

    @Test
    void staleCancellationCannotCancelNewReservation() {
        ReservationRound round =
                existingRound(NOW.minusMinutes(2));

        ProductReservation current =
                existingReservation(
                        ReservationStatus.ACTIVE
                );

        Long oldReservationId = 199L;

        assertThrows(
                ProductConflictException.class,
                () -> service.cancel(
                        PRODUCT_ID,
                        BUYER_ID,
                        oldReservationId
                )
        );

        verify(reservationMapper)
                .findByRoundAndBuyerForUpdate(
                        ROUND_ID, BUYER_ID
                );

        verify(reservationMapper, never())
                .cancelByBuyer(
                        anyLong(),
                        anyLong(),
                        any(LocalDateTime.class),
                        anyInt()
                );

        verify(roundMapper, never()).cancel(
                anyLong(),
                anyString(),
                any(LocalDateTime.class),
                anyInt()
        );

        verifyNoInteractions(drawService);

        assertEquals(
                ReservationStatus.ACTIVE,
                current.getStatus()
        );

        assertEquals(0, round.getDrawVersion());
    }

    @Test
    void cannotCancelAnotherBuyersReservation() {
        existingRound(NOW.minusMinutes(5));

        // 当前登录买家拥有预约200，
        // 请求中却传入其他买家的预约201。
        ProductReservation own =
                existingReservation(
                        ReservationStatus.ACTIVE
                );

        Long anotherBuyersReservationId = 201L;

        assertThrows(
                ProductConflictException.class,
                () -> service.cancel(
                        PRODUCT_ID,
                        BUYER_ID,
                        anotherBuyersReservationId
                )
        );

        verify(reservationMapper)
                .findByRoundAndBuyerForUpdate(
                        ROUND_ID, BUYER_ID
                );

        verify(reservationMapper, never())
                .cancelByBuyer(
                        anyLong(),
                        anyLong(),
                        any(LocalDateTime.class),
                        anyInt()
                );

        verify(roundMapper, never()).cancel(
                anyLong(),
                anyString(),
                any(LocalDateTime.class),
                anyInt()
        );

        verifyNoInteractions(drawService);

        assertEquals(
                ReservationStatus.ACTIVE,
                own.getStatus()
        );
    }

    @Test
    void cancellationWithoutActiveRoundReturnsConflict() {
        when(roundMapper.findActiveByProductIdForUpdate(
                PRODUCT_ID
        )).thenReturn(null);

        assertThrows(
                ProductConflictException.class,
                () -> service.cancel(
                        PRODUCT_ID,
                        BUYER_ID,
                        RESERVATION_ID
                )
        );

        verifyNoInteractions(
                reservationMapper,
                drawService
        );
    }

    @Test
    void repeatedCancellationOfSameReservationIsIdempotent() {
        ReservationRound round =
                existingRound(NOW.minusMinutes(5));

        round.setDrawVersion(2);

        existingReservation(
                ReservationStatus.CANCELLED
        );

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(2);

        ReservationResponse result = service.cancel(
                PRODUCT_ID,
                BUYER_ID,
                RESERVATION_ID
        );

        assertEquals(
                ReservationStatus.CANCELLED,
                result.reservationStatus()
        );

        assertEquals(
                RESERVATION_ID,
                result.reservationId()
        );

        assertEquals(2, result.drawVersion());

        assertFalse(result.canCancel());
        assertFalse(result.canReserve());

        verify(reservationMapper, never())
                .cancelByBuyer(
                        anyLong(),
                        anyLong(),
                        any(LocalDateTime.class),
                        anyInt()
                );

        verify(roundMapper, never()).cancel(
                anyLong(),
                anyString(),
                any(LocalDateTime.class),
                anyInt()
        );

        verifyNoInteractions(drawService);
    }

    @Test
    void invalidReservationIdsAreRejectedBeforeLocking() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.cancel(
                        PRODUCT_ID, BUYER_ID, null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.cancel(
                        PRODUCT_ID, BUYER_ID, 0L
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.cancel(
                        PRODUCT_ID, BUYER_ID, -1L
                )
        );

        verifyNoInteractions(
                productMapper,
                roundMapper,
                reservationMapper,
                jdbcTemplate,
                drawService
        );
    }

    @Test
    void queryReturnsCurrentDrawVersionAndQueuePosition() {
        ReservationRound round =
                existingRound(NOW.minusMinutes(5));

        round.setDrawVersion(3);

        ProductReservation reservation =
                existingReservation(
                        ReservationStatus.ACTIVE
                );

        reservation.setQueuePosition(2);

        when(roundMapper.findActiveByProductId(
                PRODUCT_ID
        )).thenReturn(round);

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(4);

        ReservationResponse result =
                service.getReservation(
                        PRODUCT_ID, BUYER_ID
                );

        assertEquals(3, result.drawVersion());
        assertEquals(2, result.queuePosition());
        assertEquals(4, result.activeReservationCount());

        verifyNoInteractions(drawService);
    }

    @Test
    void queryWithoutRoundReturnsNullDrawVersion() {
        when(roundMapper.findActiveByProductId(
                PRODUCT_ID
        )).thenReturn(null);

        ReservationResponse result =
                service.getReservation(
                        PRODUCT_ID, BUYER_ID
                );

        assertNull(result.roundId());
        assertNull(result.roundStatus());
        assertNull(result.drawVersion());
        assertNull(result.reservationId());
        assertNull(result.queuePosition());

        assertEquals(0, result.activeReservationCount());
        assertTrue(result.canReserve());
        assertFalse(result.canCancel());

        verifyNoInteractions(drawService);
    }
}
