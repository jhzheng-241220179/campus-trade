
package com.campustrade.service;

import com.campustrade.mapper.ReservationQueueMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConditionalOnProperty(
        prefix = "campustrade.reservation.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class ReservationScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ReservationScheduler.class
            );

    private static final int BATCH_SIZE = 100;

    private final ReservationQueueMapper queueMapper;
    private final ReservationRoundTaskService taskService;
    private final Clock clock;

    public ReservationScheduler(
            ReservationQueueMapper queueMapper,
            ReservationRoundTaskService taskService,
            Clock clock
    ) {
        this.queueMapper = queueMapper;
        this.taskService = taskService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString =
                    "${campustrade.reservation.scheduler.interval-ms:1000}",
            initialDelayString =
                    "${campustrade.reservation.scheduler.initial-delay-ms:5000}"
    )
    public void scan() {

        LocalDateTime now = LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MILLIS);

        Set<Long> roundIds = new LinkedHashSet<>();

        try {
            // 优先期已经结束，但还没有执行首次抽签。
            roundIds.addAll(
                    queueMapper.findPriorityEndedRoundIds(
                            now,
                            BATCH_SIZE
                    )
            );

            // 已达到预约截止时间，但尚未冻结。
            roundIds.addAll(
                    queueMapper.findCoolingEndedRoundIds(
                            now,
                            BATCH_SIZE
                    )
            );

        } catch (RuntimeException ex) {

            log.error(
                    "预约定时任务扫描失败",
                    ex
            );

            return;
        }

        for (Long roundId : roundIds) {

            try {
                taskService.processRound(roundId);

            } catch (RuntimeException ex) {

                // 单轮次失败不影响其他轮次。
                log.error(
                        "预约轮次定时处理失败，roundId={}",
                        roundId,
                        ex
                );
            }
        }
    }
}
