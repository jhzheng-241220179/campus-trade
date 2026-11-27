
package com.campustrade.service;

import com.campustrade.algorithm.WeightedDrawAlgorithm;
import com.campustrade.config.ReservationProperties;
import com.campustrade.exception.ProductConflictException;
import com.campustrade.mapper.ReservationDrawEntryMapper;
import com.campustrade.mapper.ReservationDrawMapper;
import com.campustrade.mapper.ReservationQueueMapper;
import com.campustrade.model.ProductReservation;
import com.campustrade.model.ReservationDraw;
import com.campustrade.model.ReservationDrawEntry;
import com.campustrade.model.ReservationDrawTrigger;
import com.campustrade.model.ReservationRound;
import com.campustrade.model.ReservationRoundStatus;
import com.campustrade.model.ReservationStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationDrawServiceTest {

    private static final Long ROUND_ID = 100L;

    private static final LocalDateTime FIRST_TIME =
            LocalDateTime.of(2026, 11, 27, 14, 0);

    private ReservationQueueMapper queueMapper;
    private ReservationDrawMapper drawMapper;
    private ReservationDrawEntryMapper entryMapper;

    private ReservationDrawService service;

    @BeforeEach
    void setUp() {
        queueMapper = mock(
                ReservationQueueMapper.class
        );

        drawMapper = mock(
                ReservationDrawMapper.class
        );

        entryMapper = mock(
                ReservationDrawEntryMapper.class
        );

        WeightedDrawAlgorithm algorithm =
                new WeightedDrawAlgorithm(
                        new ReservationProperties()
                );

        service = new ReservationDrawService(
                algorithm,
                queueMapper,
                drawMapper,
                entryMapper,
                new Random(2026L)
        );
    }

    private ReservationRound round(
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

        return round;
    }

    private ProductReservation reservation(
            long id,
            int order,
            Integer position
    ) {
        ProductReservation reservation =
                new ProductReservation();

        reservation.setId(id);
        reservation.setRoundId(ROUND_ID);
        reservation.setBuyerId(id + 1000);
        reservation.setReservationOrder(order);
        reservation.setQueuePosition(position);
        reservation.setStatus(
                ReservationStatus.ACTIVE
        );

        reservation.setVersion(0);

        return reservation;
    }

    private void givenCandidates(
            ProductReservation... candidates
    ) {
        when(queueMapper.findActiveQueueForUpdate(
                ROUND_ID
        )).thenReturn(List.of(candidates));
    }

    private void allowQueueUpdates() {
        when(queueMapper.updateQueuePosition(
                eq(ROUND_ID),
                anyLong(),
                anyInt()
        )).thenReturn(1);

        when(queueMapper.advanceQueueVersion(
                eq(ROUND_ID),
                anyInt()
        )).thenReturn(1);
    }

    private void allowDrawPersistence() {
        when(queueMapper.updateDrawResult(
                eq(ROUND_ID),
                anyLong(),
                anyInt(),
                anyInt()
        )).thenReturn(1);

        when(drawMapper.insert(
                any(ReservationDraw.class)
        )).thenAnswer(invocation -> {
            ReservationDraw draw =
                    invocation.getArgument(0);

            draw.setId(500L);

            return 1;
        });

        when(entryMapper.insertBatch(
                anyList()
        )).thenAnswer(invocation -> {
            List<ReservationDrawEntry> entries =
                    invocation.getArgument(0);

            return entries.size();
        });

        when(queueMapper.advanceDrawVersion(
                eq(ROUND_ID),
                anyInt(),
                anyInt()
        )).thenReturn(1);
    }

    @Test
    void firstBuyerGetsFirstQueuePosition() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                0
        );

        givenCandidates(
                reservation(101L, 1, null)
        );

        allowQueueUpdates();

        service.initializePriorityQueue(
                round,
                FIRST_TIME
        );

        verify(queueMapper).updateQueuePosition(
                ROUND_ID,
                101L,
                1
        );

        assertEquals(0, round.getDrawVersion());
        assertEquals(1, round.getVersion());

        verifyNoInteractions(
                drawMapper,
                entryMapper
        );
    }

    @Test
    void priorityQueuePreservesArrivalOrder() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                2
        );

        givenCandidates(
                reservation(103L, 3, null),
                reservation(101L, 1, 1),
                reservation(102L, 2, 2)
        );

        allowQueueUpdates();

        service.initializePriorityQueue(
                round,
                FIRST_TIME.plusSeconds(30)
        );

        var order = inOrder(queueMapper);

        order.verify(queueMapper)
                .clearActiveQueuePositions(
                        ROUND_ID
                );

        order.verify(queueMapper)
                .updateQueuePosition(
                        ROUND_ID, 101L, 1
                );

        order.verify(queueMapper)
                .updateQueuePosition(
                        ROUND_ID, 102L, 2
                );

        order.verify(queueMapper)
                .updateQueuePosition(
                        ROUND_ID, 103L, 3
                );

        assertEquals(0, round.getDrawVersion());
        assertEquals(3, round.getVersion());
    }

    @Test
    void cannotInitializeQueueAfterPriorityEnds() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                1
        );

        assertThrows(
                ProductConflictException.class,
                () -> service.initializePriorityQueue(
                        round,
                        FIRST_TIME.plusMinutes(1)
                )
        );

        verifyNoInteractions(queueMapper);
    }

    @Test
    void firstDrawCannotRunBeforePriorityDeadline() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                1
        );

        boolean result = service.drawAfterPriority(
                round,
                FIRST_TIME.plusSeconds(59)
        );

        assertFalse(result);

        verifyNoInteractions(
                queueMapper,
                drawMapper,
                entryMapper
        );
    }

    @Test
    void firstDrawPersistsHistoryAndUpdatesVersions() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                3
        );

        givenCandidates(
                reservation(101L, 1, 1),
                reservation(102L, 2, 2),
                reservation(103L, 3, 3)
        );

        allowDrawPersistence();

        LocalDateTime now =
                FIRST_TIME.plusMinutes(1);

        assertTrue(
                service.drawAfterPriority(
                        round,
                        now
                )
        );

        ArgumentCaptor<ReservationDraw> captor =
                ArgumentCaptor.forClass(
                        ReservationDraw.class
                );

        verify(drawMapper).insert(
                captor.capture()
        );

        ReservationDraw draw =
                captor.getValue();

        assertEquals(ROUND_ID, draw.getRoundId());
        assertEquals(1, draw.getDrawVersion());

        assertEquals(
                ReservationDrawTrigger.PRIORITY_ENDED,
                draw.getTriggerType()
        );

        assertNull(
                draw.getTriggerReservationId()
        );

        assertEquals(
                3,
                draw.getParticipantCount()
        );

        assertEquals(6L, draw.getTotalWeight());
        assertEquals(now, draw.getCreatedAt());

        verify(entryMapper).insertBatch(
                argThat(entries ->
                    entries.size() == 3
                    && entries.stream().allMatch(
                        entry ->
                            entry.drawId().equals(500L)
                            && entry.roundId()
                                .equals(ROUND_ID)
                    )
                )
        );

        verify(queueMapper).advanceDrawVersion(
                ROUND_ID,
                0,
                3
        );

        assertEquals(1, round.getDrawVersion());
        assertEquals(4, round.getVersion());
    }

    @Test
    void repeatedFirstDrawDoesNotChangeQueue() {
        var round = round(
                ReservationRoundStatus.OPEN,
                1,
                4
        );

        assertFalse(
                service.drawAfterPriority(
                        round,
                        FIRST_TIME.plusMinutes(2)
                )
        );

        verifyNoInteractions(
                queueMapper,
                drawMapper,
                entryMapper
        );
    }

    @Test
    void firstDrawCanCatchUpBeforeSellerDeadline() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                2
        );

        givenCandidates(
                reservation(101L, 1, 1),
                reservation(102L, 2, 2)
        );

        allowDrawPersistence();

        assertTrue(
                service.drawAfterPriority(
                        round,
                        FIRST_TIME.plusMinutes(35)
                )
        );

        assertEquals(1, round.getDrawVersion());
    }

    @Test
    void firstDrawDoesNotRunAfterSellerDeadline() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                2
        );

        assertFalse(
                service.drawAfterPriority(
                        round,
                        FIRST_TIME.plusMinutes(60)
                )
        );

        verifyNoInteractions(queueMapper);
    }

    @Test
    void newReservationCreatesNewDrawVersion() {
        var round = round(
                ReservationRoundStatus.OPEN,
                1,
                4
        );

        givenCandidates(
                reservation(101L, 1, 2),
                reservation(102L, 2, 1),
                reservation(103L, 3, null)
        );

        allowDrawPersistence();

        service.redrawAfterNewReservation(
                round,
                103L,
                FIRST_TIME.plusMinutes(5)
        );

        verify(drawMapper).insert(
                argThat(draw ->
                    draw.getDrawVersion() == 2
                    && draw.getTriggerType()
                        == ReservationDrawTrigger
                            .NEW_RESERVATION
                    && Long.valueOf(103L).equals(
                        draw.getTriggerReservationId()
                    )
                    && draw.getParticipantCount() == 3
                )
        );

        assertEquals(2, round.getDrawVersion());
        assertEquals(5, round.getVersion());
    }

    @Test
    void newReservationCannotSkipFirstDraw() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                2
        );

        assertThrows(
                ProductConflictException.class,
                () -> service.redrawAfterNewReservation(
                        round,
                        103L,
                        FIRST_TIME.plusMinutes(5)
                )
        );

        verifyNoInteractions(queueMapper);
    }

    @Test
    void newReservationCannotTriggerDrawAfterCooling() {
        var round = round(
                ReservationRoundStatus.OPEN,
                1,
                3
        );

        assertThrows(
                ProductConflictException.class,
                () -> service.redrawAfterNewReservation(
                        round,
                        103L,
                        FIRST_TIME.plusMinutes(30)
                )
        );

        verifyNoInteractions(queueMapper);
    }

    @Test
    void frozenRoundCannotBeRedrawn() {
        var round = round(
                ReservationRoundStatus.FROZEN,
                1,
                5
        );

        assertThrows(
                ProductConflictException.class,
                () -> service.redrawAfterNewReservation(
                        round,
                        103L,
                        FIRST_TIME.plusMinutes(35)
                )
        );

        verifyNoInteractions(queueMapper);
    }

    @Test
    void cancellationCompactsQueueWithoutRandomDraw() {
        var round = round(
                ReservationRoundStatus.OPEN,
                2,
                5
        );

        givenCandidates(
                reservation(103L, 3, 2),
                reservation(101L, 1, 3),
                reservation(104L, 4, 4)
        );

        allowQueueUpdates();

        service.compactQueue(round);

        var order = inOrder(queueMapper);

        order.verify(queueMapper)
                .clearActiveQueuePositions(
                        ROUND_ID
                );

        order.verify(queueMapper)
                .updateQueuePosition(
                        ROUND_ID, 103L, 1
                );

        order.verify(queueMapper)
                .updateQueuePosition(
                        ROUND_ID, 101L, 2
                );

        order.verify(queueMapper)
                .updateQueuePosition(
                        ROUND_ID, 104L, 3
                );

        assertEquals(2, round.getDrawVersion());
        assertEquals(6, round.getVersion());

        verifyNoInteractions(
                drawMapper,
                entryMapper
        );

        verify(queueMapper, never())
                .updateDrawResult(
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        anyInt()
                );
    }

    @Test
    void frozenQueueCanStillBeCompacted() {
        var round = round(
                ReservationRoundStatus.FROZEN,
                2,
                6
        );

        givenCandidates(
                reservation(102L, 2, 2)
        );

        allowQueueUpdates();

        service.compactQueue(round);

        verify(queueMapper).updateQueuePosition(
                ROUND_ID, 102L, 1
        );

        assertEquals(2, round.getDrawVersion());
        assertEquals(7, round.getVersion());
    }

    @Test
    void emptyQueueDoesNotCreateDrawHistory() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                1
        );

        givenCandidates();

        assertFalse(
                service.drawAfterPriority(
                        round,
                        FIRST_TIME.plusMinutes(1)
                )
        );

        verifyNoInteractions(
                drawMapper,
                entryMapper
        );
    }

    @Test
    void incompleteHistoryThrowsException() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                2
        );

        givenCandidates(
                reservation(101L, 1, 1),
                reservation(102L, 2, 2)
        );

        allowDrawPersistence();

        when(entryMapper.insertBatch(
                anyList()
        )).thenReturn(1);

        assertThrows(
                ProductConflictException.class,
                () -> service.drawAfterPriority(
                        round,
                        FIRST_TIME.plusMinutes(1)
                )
        );

        verify(queueMapper, never())
                .advanceDrawVersion(
                        anyLong(),
                        anyInt(),
                        anyInt()
                );

        assertEquals(0, round.getDrawVersion());
        assertEquals(2, round.getVersion());
    }

    @Test
    void versionConflictThrowsException() {
        var round = round(
                ReservationRoundStatus.OPEN,
                0,
                2
        );

        givenCandidates(
                reservation(101L, 1, 1)
        );

        allowDrawPersistence();

        when(queueMapper.advanceDrawVersion(
                ROUND_ID,
                0,
                2
        )).thenReturn(0);

        assertThrows(
                ProductConflictException.class,
                () -> service.drawAfterPriority(
                        round,
                        FIRST_TIME.plusMinutes(1)
                )
        );

        assertEquals(0, round.getDrawVersion());
        assertEquals(2, round.getVersion());
    }
}
