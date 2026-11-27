
package com.campustrade.service;

import com.campustrade.exception.ProductConflictException;
import com.campustrade.mapper.ProductReservationMapper;
import com.campustrade.mapper.ReservationRoundMapper;
import com.campustrade.model.ReservationRound;
import com.campustrade.model.ReservationRoundStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class ReservationRoundTaskService {

    private final ReservationRoundMapper roundMapper;
    private final ProductReservationMapper reservationMapper;
    private final ReservationDrawService drawService;
    private final Clock clock;

    public ReservationRoundTaskService(
            ReservationRoundMapper roundMapper,
            ProductReservationMapper reservationMapper,
            ReservationDrawService drawService,
            Clock clock
    ) {
        this.roundMapper = roundMapper;
        this.reservationMapper = reservationMapper;
        this.drawService = drawService;
        this.clock = clock;
    }

    /**
     * 每个预约轮次独立使用一个事务。
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED
    )
    public void processRound(Long roundId) {

        // 加锁并重新读取当前轮次。
        ReservationRound round =
                roundMapper.findByIdForUpdate(roundId);

        if (round == null) {
            return;
        }

        if (round.getStatus()
                != ReservationRoundStatus.OPEN) {
            return;
        }

        // 获取锁之后再读取当前时间。
        LocalDateTime now = LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MILLIS);

        // 卖家响应超时将在 Day 27 统一处理。
        if (!now.isBefore(
                round.getSellerResponseDeadline()
        )) {
            return;
        }

        // 尚未结束优先保护期。
        if (now.isBefore(round.getPriorityEndsAt())) {
            return;
        }

        // 防御性检查：没有有效预约时关闭轮次。
        int activeCount =
                reservationMapper.countActiveByRoundId(
                        roundId
                );

        if (activeCount == 0) {
            closeEmptyRound(round, now);
            return;
        }

        // 优先期结束后，补做首次随机抽签。
        if (round.getDrawVersion() == 0) {
            boolean drawn = drawService.drawAfterPriority(
                    round,
                    now
            );

            if (!drawn) {
                throw new ProductConflictException(
                        "首次随机抽签未能完成"
                );
            }
        }

        // 达到预约截止时间后冻结队列。
        if (!now.isBefore(round.getCoolingEndsAt())) {
            freezeRound(round, now);
        }
    }

    private void freezeRound(
            ReservationRound round,
            LocalDateTime now
    ) {
        int rows = roundMapper.freeze(
                round.getId(),
                now,
                round.getVersion()
        );

        if (rows != 1) {
            throw new ProductConflictException(
                    "预约轮次冻结失败，状态可能已发生变化"
            );
        }

        round.setStatus(
                ReservationRoundStatus.FROZEN
        );

        round.setFrozenAt(now);

        round.setVersion(
                Math.incrementExact(round.getVersion())
        );
    }

    private void closeEmptyRound(
            ReservationRound round,
            LocalDateTime now
    ) {
        int rows = roundMapper.cancel(
                round.getId(),
                "NO_ACTIVE_BUYERS",
                now,
                round.getVersion()
        );

        if (rows != 1) {
            throw new ProductConflictException(
                    "空预约轮次关闭失败"
            );
        }

        round.setStatus(
                ReservationRoundStatus.CANCELLED
        );

        round.setCloseReason("NO_ACTIVE_BUYERS");
        round.setClosedAt(now);

        round.setVersion(
                Math.incrementExact(round.getVersion())
        );
    }
}
