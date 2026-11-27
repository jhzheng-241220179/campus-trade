
package com.campustrade.service;

import com.campustrade.config.ReservationProperties;
import com.campustrade.dto.ReservationResponse;
import com.campustrade.exception.ProductConflictException;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.mapper.ProductMapper;
import com.campustrade.mapper.ProductReservationMapper;
import com.campustrade.mapper.ReservationRoundMapper;
import com.campustrade.model.Product;
import com.campustrade.model.ProductReservation;
import com.campustrade.model.ReservationRound;
import com.campustrade.model.ReservationRoundStatus;
import com.campustrade.model.ReservationStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReservationService {

    private final ProductMapper productMapper;
    private final ReservationRoundMapper roundMapper;
    private final ProductReservationMapper reservationMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ReservationProperties properties;
    private final Clock clock;
    private final ReservationDrawService drawService;

    public ReservationService(
            ProductMapper productMapper,
            ReservationRoundMapper roundMapper,
            ProductReservationMapper reservationMapper,
            JdbcTemplate jdbcTemplate,
            ReservationProperties properties,
            Clock clock,
            ReservationDrawService drawService
    ) {
        this.productMapper = productMapper;
        this.roundMapper = roundMapper;
        this.reservationMapper = reservationMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
        this.drawService = drawService;
    }

    // =================================================
    // 创建预约
    // =================================================

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse reserve(
            Long productId,
            Long buyerId
    ) {
        Product product = lockTradeParticipants(
                productId, buyerId, true
        );

        if (!"PUBLISHED".equals(product.getStatus())) {
            throw new ProductConflictException(
                    "当前商品不允许预约"
            );
        }

        ReservationRound round =
                roundMapper.findActiveByProductIdForUpdate(productId);

        LocalDateTime now = now();

        if (round != null) {
            ProductReservation existing =
                    reservationMapper.findByRoundAndBuyer(
                            round.getId(), buyerId
                    );

            if (existing != null) {
                if (existing.getStatus() == ReservationStatus.ACTIVE) {
                    return buildResponse(
                            product, round, existing, buyerId, now
                    );
                }

                throw new ProductConflictException(
                        "不能重复加入同一轮预约"
                );
            }

            requireOpenRound(round, now);
        }

        checkBuyerReservationLimit(buyerId);
        checkCancellationCooldown(productId, buyerId, now);

        if (round == null) {
            round = createRound(productId, now);
        } else {
            ensureFirstDraw(round, now);
        }

        int nextOrder = Math.incrementExact(
                reservationMapper.findMaxReservationOrder(
                        round.getId()
                )
        );

        ProductReservation reservation =
                new ProductReservation();

        reservation.setRoundId(round.getId());
        reservation.setBuyerId(buyerId);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setReservationOrder(nextOrder);
        reservation.setVersion(0);
        reservation.setCreatedAt(now);

        int rows = reservationMapper.insert(reservation);

        if (rows != 1 || reservation.getId() == null) {
            throw new IllegalStateException(
                    "创建预约记录失败"
            );
        }

        if (now.isBefore(round.getPriorityEndsAt())) {
            drawService.initializePriorityQueue(round, now);
        } else {
            drawService.redrawAfterNewReservation(
                    round,
                    reservation.getId(),
                    now
            );
        }

        ProductReservation latest =
                reservationMapper.findById(reservation.getId());

        if (latest == null || latest.getQueuePosition() == null) {
            throw new IllegalStateException(
                    "未能获取最新候补排名"
            );
        }

        return buildResponse(
                product, round, latest, buyerId, now
        );
    }

    // =================================================
    // 取消预约
    // =================================================

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse cancel(
            Long productId,
            Long buyerId,
            Long reservationId
    ) {
        if (reservationId == null || reservationId <= 0) {
            throw new IllegalArgumentException(
                    "预约ID必须大于0"
            );
        }

        Product product = lockTradeParticipants(
                productId, buyerId, false
        );

        ReservationRound round =
                roundMapper.findActiveByProductIdForUpdate(productId);

        LocalDateTime now = now();

        if (round == null) {
            throw new ProductConflictException(
                    "当前预约轮次已结束，请刷新页面"
            );
        }

        ProductReservation reservation =
                reservationMapper.findByRoundAndBuyerForUpdate(
                        round.getId(), buyerId
                );

        // 只允许操作当前登录买家自己的指定预约。
        requireMatchingReservation(
                reservation, reservationId
        );

        // 必须先校验预约ID，再判断是否属于幂等请求。
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return buildResponse(
                    product, round, reservation, buyerId, now
            );
        }

        if (round.getStatus() == ReservationRoundStatus.MATCHING) {
            throw new ProductConflictException(
                    "当前轮次已进入交易匹配阶段"
            );
        }

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ProductConflictException(
                    "当前预约状态不允许取消"
            );
        }

        if (!now.isBefore(round.getSellerResponseDeadline())) {
            throw new ProductConflictException(
                    "本轮预约已超过卖家响应期限"
            );
        }

        // 如果首次抽签已经到期但尚未执行，
        // 先补做抽签，再重新读取预约版本。
        if (ensureFirstDraw(round, now)) {

            reservation =
                    reservationMapper.findByRoundAndBuyerForUpdate(
                            round.getId(), buyerId
                    );

            requireMatchingReservation(
                    reservation, reservationId
            );

            if (reservation.getStatus() != ReservationStatus.ACTIVE) {
                throw new ProductConflictException(
                        "预约记录状态已发生变化"
                );
            }
        }

        int rows = reservationMapper.cancelByBuyer(
                reservation.getId(),
                buyerId,
                now,
                reservation.getVersion()
        );

        if (rows != 1) {
            throw new ProductConflictException(
                    "预约状态已变化，请刷新后重试"
            );
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(now);
        reservation.setCancelReason("BUYER_CANCELLED");
        reservation.setQueuePosition(null);

        reservation.setVersion(
                Math.incrementExact(reservation.getVersion())
        );

        int remaining =
                reservationMapper.countActiveByRoundId(round.getId());

        if (remaining == 0) {

            int updated = roundMapper.cancel(
                    round.getId(),
                    "NO_ACTIVE_BUYERS",
                    now,
                    round.getVersion()
            );

            if (updated != 1) {
                throw new ProductConflictException(
                        "预约轮次状态已变化"
                );
            }

            round.setStatus(ReservationRoundStatus.CANCELLED);
            round.setClosedAt(now);
            round.setCloseReason("NO_ACTIVE_BUYERS");

            round.setVersion(
                    Math.incrementExact(round.getVersion())
            );

        } else {
            drawService.compactQueue(round);
        }

        return buildResponse(
                product, round, reservation, buyerId, now
        );
    }

    // 校验当前预约是否与客户端指定的预约一致。
    private void requireMatchingReservation(
            ProductReservation reservation,
            Long reservationId
    ) {
        if (reservation == null
                || !reservationId.equals(reservation.getId())) {

            throw new ProductConflictException(
                    "预约记录已变化，请刷新后重试"
            );
        }
    }

    // =================================================
    // 查询当前预约信息
    // =================================================

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public ReservationResponse getReservation(
            Long productId,
            Long buyerId
    ) {
        if (buyerId == null) {
            throw new AccessDeniedException("请先登录");
        }

        Product product = productMapper.findById(productId);

        if (product == null) {
            throw new ProductNotFoundException(productId);
        }

        if (!"PUBLISHED".equals(product.getStatus())
                && !buyerId.equals(product.getSellerId())) {

            throw new ProductNotFoundException(productId);
        }

        ReservationRound round =
                roundMapper.findActiveByProductId(productId);

        ProductReservation reservation = null;

        if (round != null) {
            reservation =
                    reservationMapper.findByRoundAndBuyer(
                            round.getId(), buyerId
                    );
        }

        return buildResponse(
                product, round, reservation, buyerId, now()
        );
    }

    // =================================================
    // 补偿执行首次抽签
    // =================================================

    private boolean ensureFirstDraw(
            ReservationRound round,
            LocalDateTime now
    ) {
        if (round.getStatus() != ReservationRoundStatus.OPEN) {
            return false;
        }

        if (round.getDrawVersion() != 0) {
            return false;
        }

        if (now.isBefore(round.getPriorityEndsAt())) {
            return false;
        }

        if (!now.isBefore(round.getSellerResponseDeadline())) {
            return false;
        }

        boolean drawn = drawService.drawAfterPriority(round, now);

        if (!drawn) {
            throw new ProductConflictException(
                    "首次随机抽签未能完成"
            );
        }

        return true;
    }

    // =================================================
    // 创建首次预约轮次
    // =================================================

    private ReservationRound createRound(
            Long productId,
            LocalDateTime now
    ) {
        ReservationRound round = new ReservationRound();

        round.setProductId(productId);
        round.setStatus(ReservationRoundStatus.OPEN);
        round.setFirstReservedAt(now);

        round.setPriorityEndsAt(
                now.plus(properties.getPriorityWindow())
        );

        round.setCoolingEndsAt(
                now.plus(properties.getCoolingPeriod())
        );

        round.setSellerResponseDeadline(
                round.getCoolingEndsAt().plus(
                        properties.getSellerResponsePeriod()
                )
        );

        round.setVersion(0);
        round.setDrawVersion(0);

        int rows = roundMapper.insert(round);

        if (rows != 1 || round.getId() == null) {
            throw new IllegalStateException(
                    "创建预约轮次失败"
            );
        }

        return round;
    }

    // =================================================
    // 账户与商品锁定
    // =================================================

    private Product lockTradeParticipants(
            Long productId,
            Long buyerId,
            boolean requireActiveSeller
    ) {
        if (buyerId == null) {
            throw new AccessDeniedException("请先登录");
        }

        Product snapshot = productMapper.findById(productId);

        if (snapshot == null) {
            throw new ProductNotFoundException(productId);
        }

        Long sellerId = snapshot.getSellerId();

        if (buyerId.equals(sellerId)) {
            throw new ProductConflictException(
                    "不能预约自己发布的商品"
            );
        }

        Long first = Math.min(buyerId, sellerId);
        Long second = Math.max(buyerId, sellerId);

        AccountState firstAccount = lockAccount(first);
        AccountState secondAccount = lockAccount(second);

        AccountState buyer =
                buyerId.equals(first)
                        ? firstAccount : secondAccount;

        AccountState seller =
                sellerId.equals(first)
                        ? firstAccount : secondAccount;

        requireActiveAccount(
                buyer,
                "当前账户无法进行预约操作"
        );

        if (requireActiveSeller) {
            requireActiveAccount(
                    seller,
                    "卖家账户当前不可交易"
            );
        }

        Product product =
                productMapper.findByIdForUpdate(productId);

        if (product == null) {
            throw new ProductNotFoundException(productId);
        }

        if (!sellerId.equals(product.getSellerId())) {
            throw new ProductConflictException(
                    "商品所属用户已发生变化"
            );
        }

        return product;
    }

    private AccountState lockAccount(Long userId) {
        List<AccountState> accounts =
                jdbcTemplate.query(
                        """
                        SELECT status, email_verified
                        FROM users
                        WHERE id = ?
                        FOR UPDATE
                        """,
                        (rs, rowNum) -> new AccountState(
                                rs.getString("status"),
                                rs.getBoolean("email_verified")
                        ),
                        userId
                );

        if (accounts.size() != 1) {
            throw new ProductConflictException(
                    "交易相关账户不存在"
            );
        }

        return accounts.get(0);
    }

    private void requireActiveAccount(
            AccountState account,
            String message
    ) {
        if (!"ACTIVE".equals(account.status())
                || !account.emailVerified()) {

            throw new AccessDeniedException(message);
        }
    }

    private record AccountState(
            String status,
            boolean emailVerified
    ) {
    }

    // =================================================
    // 预约业务规则
    // =================================================

    private void requireOpenRound(
            ReservationRound round,
            LocalDateTime now
    ) {
        if (round.getStatus() != ReservationRoundStatus.OPEN) {
            throw new ProductConflictException(
                    "当前预约轮次已停止接受新预约"
            );
        }

        if (!now.isBefore(round.getCoolingEndsAt())) {
            throw new ProductConflictException(
                    "预约冷静期已结束"
            );
        }
    }

    private void checkBuyerReservationLimit(Long buyerId) {
        int count =
                reservationMapper.countActiveByBuyerId(buyerId);

        if (count >= properties.getMaxActiveReservations()) {
            throw new ProductConflictException(
                    "同时参与的预约数量已达到上限"
            );
        }
    }

    private void checkCancellationCooldown(
            Long productId,
            Long buyerId,
            LocalDateTime now
    ) {
        if (isInCancellationCooldown(productId, buyerId, now)) {
            throw new ProductConflictException(
                    "取消预约后暂时不能再次预约该商品"
            );
        }
    }

    private boolean isInCancellationCooldown(
            Long productId,
            Long buyerId,
            LocalDateTime now
    ) {
        LocalDateTime cancelledAt =
                reservationMapper.findLatestBuyerCancellation(
                        productId, buyerId
                );

        return cancelledAt != null
                && now.isBefore(
                        cancelledAt.plus(
                                properties.getBuyerCancelCooldown()
                        )
                );
    }

    // =================================================
    // 构建前端响应
    // =================================================

    private ReservationResponse buildResponse(
            Product product,
            ReservationRound round,
            ProductReservation reservation,
            Long buyerId,
            LocalDateTime now
    ) {
        boolean published =
                "PUBLISHED".equals(product.getStatus());

        boolean isSeller =
                buyerId.equals(product.getSellerId());

        boolean roundOpen =
                round == null
                || (
                    round.getStatus() == ReservationRoundStatus.OPEN
                    && now.isBefore(round.getCoolingEndsAt())
                );

        boolean canReserve =
                published
                && !isSeller
                && roundOpen
                && reservation == null;

        if (canReserve) {
            canReserve =
                    reservationMapper.countActiveByBuyerId(buyerId)
                            < properties.getMaxActiveReservations()

                    && !isInCancellationCooldown(
                            product.getId(), buyerId, now
                    );
        }

        boolean canCancel =
                round != null
                && reservation != null
                && reservation.getStatus() == ReservationStatus.ACTIVE
                && (
                    round.getStatus() == ReservationRoundStatus.OPEN
                    || round.getStatus() == ReservationRoundStatus.FROZEN
                )
                && now.isBefore(round.getSellerResponseDeadline());

        if (round == null) {
            return new ReservationResponse(
                    product.getId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    canReserve,
                    false,
                    now,
                    null
            );
        }

        int activeCount =
                reservationMapper.countActiveByRoundId(round.getId());

        ReservationRoundStatus visibleStatus = round.getStatus();

        if (visibleStatus == ReservationRoundStatus.OPEN
                && !now.isBefore(round.getCoolingEndsAt())) {

            visibleStatus = ReservationRoundStatus.FROZEN;
        }

        return new ReservationResponse(
                product.getId(),
                round.getId(),
                visibleStatus,
                round.getFirstReservedAt(),
                round.getPriorityEndsAt(),
                round.getCoolingEndsAt(),
                round.getSellerResponseDeadline(),
                activeCount,

                reservation == null
                        ? null : reservation.getId(),

                reservation == null
                        ? null : reservation.getStatus(),

                reservation == null
                        ? null : reservation.getReservationOrder(),

                reservation == null
                        ? null : reservation.getQueuePosition(),

                canReserve,
                canCancel,
                now,
                round.getDrawVersion()
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MILLIS);
    }
}
