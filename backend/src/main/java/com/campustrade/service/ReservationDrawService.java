
package com.campustrade.service;

import com.campustrade.algorithm.WeightedDrawAlgorithm;
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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.random.RandomGenerator;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ReservationDrawService {

    private final WeightedDrawAlgorithm algorithm;
    private final ReservationQueueMapper queueMapper;
    private final ReservationDrawMapper drawMapper;
    private final ReservationDrawEntryMapper entryMapper;
    private final RandomGenerator random;

    public ReservationDrawService(
            WeightedDrawAlgorithm algorithm,
            ReservationQueueMapper queueMapper,
            ReservationDrawMapper drawMapper,
            ReservationDrawEntryMapper entryMapper,
            @Qualifier("reservationRandomGenerator")
            RandomGenerator random
    ) {
        this.algorithm = algorithm;
        this.queueMapper = queueMapper;
        this.drawMapper = drawMapper;
        this.entryMapper = entryMapper;
        this.random = random;
    }

    // =================================================
    // 优先保护期：按加入顺序建立队列
    // =================================================

    public void initializePriorityQueue(
            ReservationRound round,
            LocalDateTime now
    ) {
        requireOpenRound(round);

        if (!now.isBefore(round.getPriorityEndsAt())) {
            throw new ProductConflictException(
                    "首位买家优先期已结束"
            );
        }

        if (round.getDrawVersion() != 0) {
            throw new ProductConflictException(
                    "当前轮次已经执行过随机抽签"
            );
        }

        List<ProductReservation> candidates =
                queueMapper.findActiveQueueForUpdate(
                        round.getId()
                );

        if (candidates.isEmpty()) {
            return;
        }

        List<ProductReservation> ordered =
                candidates.stream()
                        .sorted(
                            java.util.Comparator.comparingInt(
                                ProductReservation
                                    ::getReservationOrder
                            )
                        )
                        .toList();

        persistQueuePositions(round, ordered);
    }

    // =================================================
    // 优先期结束：首次随机抽签
    // =================================================

    public boolean drawAfterPriority(
            ReservationRound round,
            LocalDateTime now
    ) {
        if (round.getStatus()
                != ReservationRoundStatus.OPEN) {
            return false;
        }

        // 防止重复执行首次抽签。
        if (round.getDrawVersion() != 0) {
            return false;
        }

        if (now.isBefore(round.getPriorityEndsAt())) {
            return false;
        }

        if (!now.isBefore(
                round.getSellerResponseDeadline()
        )) {
            return false;
        }

        List<ProductReservation> candidates =
                queueMapper.findActiveQueueForUpdate(
                        round.getId()
                );

        if (candidates.isEmpty()) {
            return false;
        }

        performDraw(
                round,
                candidates,
                ReservationDrawTrigger.PRIORITY_ENDED,
                null,
                now
        );

        return true;
    }

    // =================================================
    // 动态抽签期间新买家加入
    // =================================================

    public void redrawAfterNewReservation(
            ReservationRound round,
            Long reservationId,
            LocalDateTime now
    ) {
        requireOpenRound(round);

        if (now.isBefore(round.getPriorityEndsAt())) {
            throw new ProductConflictException(
                    "当前仍处于首位买家优先期"
            );
        }

        if (!now.isBefore(round.getCoolingEndsAt())) {
            throw new ProductConflictException(
                    "预约期限已结束"
            );
        }

        if (round.getDrawVersion() == 0) {
            throw new ProductConflictException(
                    "必须先完成优先期结束后的首次抽签"
            );
        }

        List<ProductReservation> candidates =
                queueMapper.findActiveQueueForUpdate(
                        round.getId()
                );

        boolean containsNewReservation =
                candidates.stream()
                        .anyMatch(
                            candidate ->
                                candidate.getId()
                                    .equals(reservationId)
                        );

        if (!containsNewReservation) {
            throw new ProductConflictException(
                    "触发抽签的预约记录不存在或已经失效"
            );
        }

        performDraw(
                round,
                candidates,
                ReservationDrawTrigger.NEW_RESERVATION,
                reservationId,
                now
        );
    }

    // =================================================
    // 买家退出：保留原有相对顺序并压缩排名
    // =================================================

    public void compactQueue(
            ReservationRound round
    ) {
        if (round.getStatus()
                != ReservationRoundStatus.OPEN
                && round.getStatus()
                != ReservationRoundStatus.FROZEN) {

            throw new ProductConflictException(
                    "当前轮次不允许调整候补队列"
            );
        }

        List<ProductReservation> candidates =
                queueMapper.findActiveQueueForUpdate(
                        round.getId()
                );

        if (candidates.isEmpty()) {
            return;
        }

        // Mapper 已按照当前候补排名排序。
        // 此处不进行任何随机抽签。
        persistQueuePositions(
                round,
                candidates
        );
    }

    // =================================================
    // 执行随机抽签并保存当前结果及历史快照
    // =================================================

    private void performDraw(
            ReservationRound round,
            List<ProductReservation> reservations,
            ReservationDrawTrigger trigger,
            Long triggerReservationId,
            LocalDateTime now
    ) {
        List<WeightedDrawAlgorithm.Candidate> candidates =
                reservations.stream()
                        .map(
                            reservation ->
                                new WeightedDrawAlgorithm.Candidate(
                                    reservation.getId(),
                                    reservation.getReservationOrder()
                                )
                        )
                        .toList();

        List<WeightedDrawAlgorithm.DrawEntry> results =
                algorithm.draw(
                        candidates,
                        random
                );

        if (results.isEmpty()) {
            throw new IllegalStateException(
                    "抽签参与者不能为空"
            );
        }

        int nextDrawVersion = Math.incrementExact(
                round.getDrawVersion()
        );

        long totalWeight =
                results.stream()
                        .mapToLong(
                            WeightedDrawAlgorithm.DrawEntry
                                ::drawWeight
                        )
                        .sum();

        // 第一步：清空当前排名。
        queueMapper.clearActiveQueuePositions(
                round.getId()
        );

        // 第二步：保存新的权重和候补排名。
        for (var result : results) {
            int rows = queueMapper.updateDrawResult(
                    round.getId(),
                    result.reservationId(),
                    result.drawWeight(),
                    result.queuePosition()
            );

            requireOneRow(
                    rows,
                    "保存当前抽签结果失败"
            );
        }

        // 第三步：创建抽签历史主记录。
        ReservationDraw draw =
                new ReservationDraw();

        draw.setRoundId(round.getId());
        draw.setDrawVersion(nextDrawVersion);
        draw.setTriggerType(trigger);
        draw.setTriggerReservationId(
                triggerReservationId
        );
        draw.setParticipantCount(results.size());
        draw.setTotalWeight(totalWeight);
        draw.setCreatedAt(now);

        int inserted = drawMapper.insert(draw);

        requireOneRow(
                inserted,
                "保存抽签历史主记录失败"
        );

        if (draw.getId() == null) {
            throw new IllegalStateException(
                    "未能获取抽签历史记录ID"
            );
        }

        // 第四步：保存所有参与者的历史快照。
        List<ReservationDrawEntry> entries =
                results.stream()
                        .map(
                            result ->
                                new ReservationDrawEntry(
                                    draw.getId(),
                                    round.getId(),
                                    result.reservationId(),
                                    result.reservationOrder(),
                                    result.drawWeight(),
                                    result.queuePosition()
                                )
                        )
                        .toList();

        int entryRows =
                entryMapper.insertBatch(entries);

        if (entryRows != entries.size()) {
            throw new ProductConflictException(
                    "抽签历史明细保存不完整"
            );
        }

        // 第五步：更新抽签版本及乐观锁版本。
        int updated =
                queueMapper.advanceDrawVersion(
                        round.getId(),
                        round.getDrawVersion(),
                        round.getVersion()
                );

        requireOneRow(
                updated,
                "抽签版本已发生变化"
        );

        // 同步内存中的轮次对象。
        round.setDrawVersion(nextDrawVersion);

        round.setVersion(
                Math.incrementExact(round.getVersion())
        );
    }

    // =================================================
    // 非随机操作：保存候补队列
    // =================================================

    private void persistQueuePositions(
            ReservationRound round,
            List<ProductReservation> ordered
    ) {
        queueMapper.clearActiveQueuePositions(
                round.getId()
        );

        for (int i = 0; i < ordered.size(); i++) {
            ProductReservation reservation =
                    ordered.get(i);

            int rows = queueMapper.updateQueuePosition(
                    round.getId(),
                    reservation.getId(),
                    i + 1
            );

            requireOneRow(
                    rows,
                    "更新候补排名失败"
            );
        }

        int updated =
                queueMapper.advanceQueueVersion(
                        round.getId(),
                        round.getVersion()
                );

        requireOneRow(
                updated,
                "候补队列版本已发生变化"
        );

        round.setVersion(
                Math.incrementExact(round.getVersion())
        );
    }

    private void requireOpenRound(
            ReservationRound round
    ) {
        if (round.getStatus()
                != ReservationRoundStatus.OPEN) {
            throw new ProductConflictException(
                    "当前预约轮次已停止动态抽签"
            );
        }
    }

    private void requireOneRow(
            int rows,
            String message
    ) {
        if (rows != 1) {
            throw new ProductConflictException(
                    message
            );
        }
    }
}
