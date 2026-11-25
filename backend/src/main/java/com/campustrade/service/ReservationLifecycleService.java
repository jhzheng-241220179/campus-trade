
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

    public ReservationLifecycleService(
            ReservationRoundMapper roundMapper,
            ProductReservationMapper reservationMapper,
            JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.roundMapper = roundMapper;
        this.reservationMapper = reservationMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    // 卖家主动下架前检查。
    // 调用方必须已锁定卖家账户。
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

    // 管理员强制下架商品时调用。
    // 调用方必须已锁定卖家账户及商品。
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

    // 封禁卖家时，取消其所有商品的预约。
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

    // 封禁买家时，只取消其个人预约，
    // 不影响其他买家的预约资格。
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

        LocalDateTime now = now();

        for (Long roundId : roundIds) {
            ReservationRound round =
                    roundMapper.findByIdForUpdate(roundId);

            if (round == null) {
                continue;
            }

            if (round.getStatus()
                    != ReservationRoundStatus.OPEN
                    && round.getStatus()
                    != ReservationRoundStatus.FROZEN) {
                continue;
            }

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

            if (rows == 0) {
                continue;
            }

            int remaining =
                    reservationMapper.countActiveByRoundId(
                            roundId
                    );

            if (remaining == 0) {
                int updated = roundMapper.cancel(
                        roundId,
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
        }
    }

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
