
package com.campustrade.service;

import com.campustrade.exception.ProductConflictException;
import com.campustrade.mapper.ProductReservationMapper;
import com.campustrade.mapper.ReservationRoundMapper;
import com.campustrade.model.ReservationRound;
import com.campustrade.model.ReservationRoundStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationRoundTaskServiceTest {

    private static final Long ROUND_ID = 100L;

    private static final LocalDateTime FIRST_TIME =
            LocalDateTime.of(2026, 11, 27, 14, 0);

    private static final ZoneId ZONE =
            ZoneId.of("Asia/Shanghai");

    private ReservationRoundMapper roundMapper;
    private ProductReservationMapper reservationMapper;
    private ReservationDrawService drawService;

    @BeforeEach
    void setUp() {
        roundMapper = mock(
                ReservationRoundMapper.class
        );

        reservationMapper = mock(
                ProductReservationMapper.class
        );

        drawService = mock(
                ReservationDrawService.class
        );
    }

    private ReservationRoundTaskService serviceAt(
            LocalDateTime now
    ) {
        Clock clock = Clock.fixed(
                now.atZone(ZONE).toInstant(),
                ZONE
        );

        return new ReservationRoundTaskService(
                roundMapper,
                reservationMapper,
                drawService,
                clock
        );
    }

    private ReservationRound givenRound(
            ReservationRoundStatus status,
            int drawVersion,
            int version
    ) {
        ReservationRound round =
                new ReservationRound();

        round.setId(ROUND_ID);
        round.setProductId(10L);
        round.setStatus(status);
        round.setFirstReservedAt(FIRST_TIME);

        round.setPriorityEndsAt(
                FIRST_TIME.plusMinutes(1)
        );

        round.setCoolingEndsAt(
                FIRST_TIME.plusMinutes(30)
        );

        round.setSellerResponseDeadline(
                FIRST_TIME.plusMinutes(60)
        );

        round.setDrawVersion(drawVersion);
        round.setVersion(version);

        when(roundMapper.findByIdForUpdate(
                ROUND_ID
        )).thenReturn(round);

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(2);

        return round;
    }

    private void allowFirstDraw(
            ReservationRound round,
            LocalDateTime now
    ) {
        when(drawService.drawAfterPriority(
                round,
                now
        )).thenAnswer(invocation -> {

            // 模拟抽签 Service 更新轮次版本。
            round.setDrawVersion(
                    round.getDrawVersion() + 1
            );

            round.setVersion(
                    round.getVersion() + 1
            );

            return true;
        });
    }

    @Test
    void missingRoundIsIgnored() {
        serviceAt(
                FIRST_TIME.plusMinutes(2)
        ).processRound(ROUND_ID);

        verifyNoInteractions(
                drawService,
                reservationMapper
        );
    }

    @Test
    void priorityWindowDoesNotTriggerDraw() {
        givenRound(
                ReservationRoundStatus.OPEN,
                0,
                1
        );

        serviceAt(
                FIRST_TIME.plusSeconds(59)
        ).processRound(ROUND_ID);

        verifyNoInteractions(
                drawService,
                reservationMapper
        );
    }

    @Test
    void priorityDeadlineTriggersFirstDraw() {
        ReservationRound round = givenRound(
                ReservationRoundStatus.OPEN,
                0,
                1
        );

        LocalDateTime now =
                FIRST_TIME.plusMinutes(1);

        allowFirstDraw(round, now);

        serviceAt(now).processRound(ROUND_ID);

        verify(drawService).drawAfterPriority(
                round,
                now
        );

        verify(roundMapper, never()).freeze(
                anyLong(),
                any(LocalDateTime.class),
                anyInt()
        );

        assertEquals(1, round.getDrawVersion());
        assertEquals(2, round.getVersion());
    }

    @Test
    void existingDrawIsNotRepeated() {
        givenRound(
                ReservationRoundStatus.OPEN,
                2,
                5
        );

        serviceAt(
                FIRST_TIME.plusMinutes(10)
        ).processRound(ROUND_ID);

        verifyNoInteractions(drawService);

        verify(roundMapper, never()).freeze(
                anyLong(),
                any(LocalDateTime.class),
                anyInt()
        );
    }

    @Test
    void coolingDeadlineFreezesExistingQueue() {
        ReservationRound round = givenRound(
                ReservationRoundStatus.OPEN,
                2,
                5
        );

        LocalDateTime now =
                FIRST_TIME.plusMinutes(30);

        when(roundMapper.freeze(
                ROUND_ID,
                now,
                5
        )).thenReturn(1);

        serviceAt(now).processRound(ROUND_ID);

        verifyNoInteractions(drawService);

        verify(roundMapper).freeze(
                ROUND_ID,
                now,
                5
        );

        assertEquals(
                ReservationRoundStatus.FROZEN,
                round.getStatus()
        );

        assertEquals(2, round.getDrawVersion());
        assertEquals(6, round.getVersion());
        assertEquals(now, round.getFrozenAt());
    }

    @Test
    void missedFirstDrawIsCompletedBeforeFreeze() {
        ReservationRound round = givenRound(
                ReservationRoundStatus.OPEN,
                0,
                3
        );

        LocalDateTime now =
                FIRST_TIME.plusMinutes(35);

        allowFirstDraw(round, now);

        when(roundMapper.freeze(
                ROUND_ID,
                now,
                4
        )).thenReturn(1);

        serviceAt(now).processRound(ROUND_ID);

        var order = inOrder(
                drawService,
                roundMapper
        );

        order.verify(drawService)
                .drawAfterPriority(round, now);

        order.verify(roundMapper)
                .freeze(ROUND_ID, now, 4);

        assertEquals(1, round.getDrawVersion());
        assertEquals(5, round.getVersion());

        assertEquals(
                ReservationRoundStatus.FROZEN,
                round.getStatus()
        );
    }

    @Test
    void frozenRoundIsIgnored() {
        givenRound(
                ReservationRoundStatus.FROZEN,
                1,
                5
        );

        serviceAt(
                FIRST_TIME.plusMinutes(40)
        ).processRound(ROUND_ID);

        verifyNoInteractions(
                drawService,
                reservationMapper
        );

        verify(roundMapper, never()).freeze(
                anyLong(),
                any(LocalDateTime.class),
                anyInt()
        );
    }

    @Test
    void expiredSellerDeadlineIsIgnoredForNow() {
        givenRound(
                ReservationRoundStatus.OPEN,
                0,
                2
        );

        serviceAt(
                FIRST_TIME.plusMinutes(60)
        ).processRound(ROUND_ID);

        verifyNoInteractions(
                drawService,
                reservationMapper
        );

        verify(roundMapper, never()).freeze(
                anyLong(),
                any(LocalDateTime.class),
                anyInt()
        );
    }

    @Test
    void emptyRoundIsCancelledWithoutDraw() {
        ReservationRound round = givenRound(
                ReservationRoundStatus.OPEN,
                0,
                2
        );

        LocalDateTime now =
                FIRST_TIME.plusMinutes(2);

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(0);

        when(roundMapper.cancel(
                ROUND_ID,
                "NO_ACTIVE_BUYERS",
                now,
                2
        )).thenReturn(1);

        serviceAt(now).processRound(ROUND_ID);

        verifyNoInteractions(drawService);

        assertEquals(
                ReservationRoundStatus.CANCELLED,
                round.getStatus()
        );

        assertEquals(
                "NO_ACTIVE_BUYERS",
                round.getCloseReason()
        );

        assertEquals(now, round.getClosedAt());
        assertEquals(3, round.getVersion());
    }

    @Test
    void failedFirstDrawPreventsFreeze() {
        ReservationRound round = givenRound(
                ReservationRoundStatus.OPEN,
                0,
                2
        );

        LocalDateTime now =
                FIRST_TIME.plusMinutes(35);

        when(drawService.drawAfterPriority(
                round,
                now
        )).thenReturn(false);

        assertThrows(
                ProductConflictException.class,
                () -> serviceAt(now)
                        .processRound(ROUND_ID)
        );

        verify(roundMapper, never()).freeze(
                anyLong(),
                any(LocalDateTime.class),
                anyInt()
        );
    }

    @Test
    void freezeVersionConflictThrowsException() {
        ReservationRound round = givenRound(
                ReservationRoundStatus.OPEN,
                1,
                4
        );

        LocalDateTime now =
                FIRST_TIME.plusMinutes(30);

        when(roundMapper.freeze(
                ROUND_ID,
                now,
                4
        )).thenReturn(0);

        assertThrows(
                ProductConflictException.class,
                () -> serviceAt(now)
                        .processRound(ROUND_ID)
        );

        assertEquals(
                ReservationRoundStatus.OPEN,
                round.getStatus()
        );

        assertEquals(4, round.getVersion());
    }

    @Test
    void emptyRoundVersionConflictThrowsException() {
        givenRound(
                ReservationRoundStatus.OPEN,
                0,
                3
        );

        LocalDateTime now =
                FIRST_TIME.plusMinutes(5);

        when(reservationMapper.countActiveByRoundId(
                ROUND_ID
        )).thenReturn(0);

        when(roundMapper.cancel(
                ROUND_ID,
                "NO_ACTIVE_BUYERS",
                now,
                3
        )).thenReturn(0);

        assertThrows(
                ProductConflictException.class,
                () -> serviceAt(now)
                        .processRound(ROUND_ID)
        );

        verifyNoInteractions(drawService);
    }
}
