
package com.campustrade.service;

import com.campustrade.exception.ProductConflictException;
import com.campustrade.mapper.ProductReservationMapper;
import com.campustrade.mapper.ReservationRoundMapper;
import com.campustrade.model.ReservationRound;
import com.campustrade.model.ReservationRoundStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReservationLifecycleService {

    private final ReservationRoundMapper roundMapper;
    private final ProductReservationMapper reservationMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ReservationDrawService drawService;

    public ReservationLifecycleService(
            ReservationRoundMapper roundMapper,
            ProductReservationMapper reservationMapper,
            JdbcTemplate jdbcTemplate,
            Clock clock,
            ReservationDrawService drawService
    ) {
        this.roundMapper = roundMapper;
        this.reservationMapper = reservationMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.drawService = drawService;
    }

    // =================================================
    // 卖家主动下架前检查
    // =================================================

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireNoActiveRound(Long productId) {
        ReservationRound round =
                roundMapper.findActiveByProductIdForUpdate(
                        productId
                );

        if (round != null) {
            throw new ProductConflictException(
                    "当前商品存在有效预约，暂时不能主动下架"
            );
        }
    }

    // =================================================
    // 管理员强制下架商品
    // =================================================

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelProductReservations(
            Long productId,
            String reason
    ) {
        ReservationRound round =
                roundMapper.findActiveByProductIdForUpdate(
                        productId
                );

        if (round == null) {
            return;
        }

        cancelRound(round, reason, now());
    }

    // =================================================
    // 封禁卖家：取消其全部商品预约
    // =================================================

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelSellerReservations(Long sellerId) {
        List<Long> productIds = jdbcTemplate.query(
                """
                SELECT id
                FROM products
                WHERE seller_id = ?
                ORDER BY id
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getLong("id"),
                sellerId
        );

        for (Long productId : productIds) {
            cancelProductReservations(
                    productId,
                    "SELLER_BANNED"
            );
        }
    }

    // =================================================
    // 封禁买家：取消个人预约并调整候补队列
    // =================================================

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelBuyerReservations(Long buyerId) {
        List<Long> roundIds = jdbcTemplate.query(
                """
                SELECT rr.id
                FROM reservation_rounds rr
                JOIN product_reservations pr
                  ON rr.id = pr.round_id
                WHERE pr.buyer_id = ?
                  AND pr.status = 'ACTIVE'
                  AND rr.status IN ('OPEN', 'FROZEN')
                ORDER BY rr.id
                """,
                (rs, rowNum) -> rs.getLong("id"),
                buyerId
        );

        for (Long roundId : roundIds) {

            // 锁定轮次后重新检查状态。
            ReservationRound round =
                    roundMapper.findByIdForUpdate(
                            roundId
                    );

            if (round == null) {
                continue;
            }

            if (round.getStatus()
                    != ReservationRoundStatus.OPEN
                    && round.getStatus()
                    != ReservationRoundStatus.FROZEN) {
                continue;
            }

            // 获得轮次锁之后再获取时间。
            LocalDateTime now = now();

            // 确认目标买家仍具有有效预约。
            var reservation =
                    reservationMapper
                            .findByRoundAndBuyerForUpdate(
                                    roundId,
                                    buyerId
                            );

            if (reservation == null
                    || reservation.getStatus()
                        != com.campustrade.model
                            .ReservationStatus.ACTIVE) {
                continue;
            }

            // 若首次抽签已经到期但尚未执行，
            // 先补做首次抽签。
            ensureFirstDraw(round, now);

            // 取消目标买家的预约资格。
            int rows = jdbcTemplate.update(
                    """
                    UPDATE product_reservations
                    SET status = 'CANCELLED',
                        cancelled_at = ?,
                        cancel_reason = 'BUYER_BANNED',
                        queue_position = NULL,
                        version = version + 1
                    WHERE round_id = ?
                      AND buyer_id = ?
                      AND status = 'ACTIVE'
                    """,
                    now,
                    roundId,
                    buyerId
            );

            if (rows != 1) {
                throw new ProductConflictException(
                        "买家预约状态已发生变化"
                );
            }

            int remaining =
                    reservationMapper.countActiveByRoundId(
                            roundId
                    );

            if (remaining == 0) {

                // 最后一位有效买家退出，关闭轮次。
                closeEmptyRound(round, now);

            } else {

                // 保持其他买家的相对排名，
                // 不触发新的随机抽签。
                drawService.compactQueue(round);
            }
        }
    }

    // =================================================
    // 补偿首次随机抽签
    // =================================================

    private void ensureFirstDraw(
            ReservationRound round,
            LocalDateTime now
    ) {
        if (round.getStatus()
                != ReservationRoundStatus.OPEN) {
            return;
        }

        if (round.getDrawVersion() != 0) {
            return;
        }

        if (now.isBefore(round.getPriorityEndsAt())) {
            return;
        }

        if (!now.isBefore(
                round.getSellerResponseDeadline()
        )) {
            return;
        }

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

    // =================================================
    // 关闭没有有效买家的预约轮次
    // =================================================

    private void closeEmptyRound(
            ReservationRound round,
            LocalDateTime now
    ) {
        int updated = roundMapper.cancel(
                round.getId(),
                "NO_ACTIVE_BUYERS",
                now,
                round.getVersion()
        );

        if (updated != 1) {
            throw new ProductConflictException(
                    "预约轮次状态发生变化"
            );
        }
    }

    // =================================================
    // 取消整个预约轮次
    // =================================================

    private void cancelRound(
            ReservationRound round,
            String reason,
            LocalDateTime now
    ) {
        if (round.getStatus()
                == ReservationRoundStatus.MATCHING) {
            throw new ProductConflictException(
                    "当前轮次已进入交易匹配阶段"
            );
        }

        reservationMapper.cancelAllActiveByRoundId(
                round.getId(),
                reason,
                now
        );

        int rows = roundMapper.cancel(
                round.getId(),
                reason,
                now,
                round.getVersion()
        );

        if (rows != 1) {
            throw new ProductConflictException(
                    "预约轮次状态发生变化"
            );
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MILLIS);
    }
}
