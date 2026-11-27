
package com.campustrade.service;

import com.campustrade.mapper.ReservationQueueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationSchedulerTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 11, 27, 14, 35);

    private ReservationQueueMapper queueMapper;
    private ReservationRoundTaskService taskService;
    private ReservationScheduler scheduler;

    @BeforeEach
    void setUp() {
        queueMapper = mock(ReservationQueueMapper.class);
        taskService = mock(ReservationRoundTaskService.class);

        ZoneId zone = ZoneId.of("Asia/Shanghai");

        Clock clock = Clock.fixed(
                NOW.atZone(zone).toInstant(),
                zone
        );

        scheduler = new ReservationScheduler(
                queueMapper,
                taskService,
                clock
        );
    }

    private void givenRoundIds(
            List<Long> priorityEnded,
            List<Long> coolingEnded
    ) {
        when(queueMapper.findPriorityEndedRoundIds(
                NOW, 100
        )).thenReturn(priorityEnded);

        when(queueMapper.findCoolingEndedRoundIds(
                NOW, 100
        )).thenReturn(coolingEnded);
    }

    @Test
    void emptyScanDoesNotProcessAnyRound() {
        givenRoundIds(
                List.of(),
                List.of()
        );

        scheduler.scan();

        verifyNoInteractions(taskService);
    }

    @Test
    void schedulerProcessesBothKindsOfRounds() {
        givenRoundIds(
                List.of(100L, 101L),
                List.of(102L, 103L)
        );

        scheduler.scan();

        var order = inOrder(taskService);

        order.verify(taskService).processRound(100L);
        order.verify(taskService).processRound(101L);
        order.verify(taskService).processRound(102L);
        order.verify(taskService).processRound(103L);
    }

    @Test
    void duplicateRoundIdsAreProcessedOnlyOnce() {
        givenRoundIds(
                List.of(100L, 101L, 102L),
                List.of(102L, 103L, 100L)
        );

        scheduler.scan();

        verify(taskService, times(1)).processRound(100L);
        verify(taskService, times(1)).processRound(101L);
        verify(taskService, times(1)).processRound(102L);
        verify(taskService, times(1)).processRound(103L);

        verifyNoMoreInteractions(taskService);
    }

    @Test
    void oneFailedRoundDoesNotBlockOtherRounds() {
        givenRoundIds(
                List.of(100L, 101L, 102L),
                List.of()
        );

        doThrow(
                new IllegalStateException("模拟轮次处理失败")
        ).when(taskService).processRound(101L);

        assertDoesNotThrow(() -> scheduler.scan());

        verify(taskService).processRound(100L);
        verify(taskService).processRound(101L);
        verify(taskService).processRound(102L);
    }

    @Test
    void databaseScanFailureIsHandled() {
        when(queueMapper.findPriorityEndedRoundIds(
                NOW, 100
        )).thenThrow(
                new IllegalStateException("模拟数据库查询失败")
        );

        assertDoesNotThrow(() -> scheduler.scan());

        verifyNoInteractions(taskService);
    }

    @Test
    void schedulerUsesCurrentTimeAndBatchLimit() {
        givenRoundIds(
                List.of(),
                List.of()
        );

        scheduler.scan();

        verify(queueMapper)
                .findPriorityEndedRoundIds(NOW, 100);

        verify(queueMapper)
                .findCoolingEndedRoundIds(NOW, 100);
    }
}
