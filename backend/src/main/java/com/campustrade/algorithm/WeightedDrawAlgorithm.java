
package com.campustrade.algorithm;

import com.campustrade.config.ReservationProperties;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

@Component
public class WeightedDrawAlgorithm {

    private final ReservationProperties properties;

    public WeightedDrawAlgorithm(
            ReservationProperties properties
    ) {
        this.properties = properties;
    }

    // 抽签参与者的输入信息。
    public record Candidate(
            Long reservationId,
            int reservationOrder
    ) {
    }

    // 抽签结束后生成的单个候选结果。
    public record DrawEntry(
            Long reservationId,
            int reservationOrder,
            int drawWeight,
            int queuePosition
    ) {
    }

    // 内部使用的带权重候选对象。
    private record WeightedCandidate(
            Candidate candidate,
            int weight
    ) {
    }

    /**
     * 执行加权随机不放回抽样。
     *
     * candidates：当前所有有效预约者。
     * random：随机数生成器。
     */
    public List<DrawEntry> draw(
            List<Candidate> candidates,
            RandomGenerator random
    ) {
        Objects.requireNonNull(
                candidates,
                "抽签参与者列表不能为空"
        );

        Objects.requireNonNull(
                random,
                "随机数生成器不能为空"
        );

        int maxWeight =
                properties.getMaxDrawWeight();

        if (maxWeight <= 0) {
            throw new IllegalArgumentException(
                    "抽签权重上限必须大于0"
            );
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        // 校验输入，防止重复预约ID或重复预约序号。
        validateCandidates(candidates);

        // 按历史预约顺序排列。
        List<Candidate> ordered =
                new ArrayList<>(candidates);

        ordered.sort(
                Comparator.comparingInt(
                        Candidate::reservationOrder
                )
        );

        int size = ordered.size();

        List<WeightedCandidate> pool =
                new ArrayList<>(size);

        long totalWeight = 0;

        // 根据当前有效预约者的相对顺序计算权重。
        for (int i = 0; i < size; i++) {
            int weight = Math.min(
                    maxWeight,
                    size - i
            );

            WeightedCandidate weighted =
                    new WeightedCandidate(
                            ordered.get(i),
                            weight
                    );

            pool.add(weighted);

            totalWeight += weight;
        }

        List<DrawEntry> results =
                new ArrayList<>(size);

        // 不断抽出候选人，直到待抽签集合为空。
        while (!pool.isEmpty()) {

            long ticket =
                    random.nextLong(totalWeight);

            long cumulativeWeight = 0;

            int selectedIndex = -1;

            for (int i = 0; i < pool.size(); i++) {

                cumulativeWeight +=
                        pool.get(i).weight();

                if (ticket < cumulativeWeight) {
                    selectedIndex = i;
                    break;
                }
            }

            if (selectedIndex < 0) {
                throw new IllegalStateException(
                        "未能选出有效抽签对象"
                );
            }

            // 抽中后移除，实现不放回抽样。
            WeightedCandidate selected =
                    pool.remove(selectedIndex);

            Candidate candidate =
                    selected.candidate();

            int queuePosition =
                    results.size() + 1;

            results.add(
                    new DrawEntry(
                            candidate.reservationId(),
                            candidate.reservationOrder(),
                            selected.weight(),
                            queuePosition
                    )
            );

            // 下一轮仅使用剩余参与者的总权重。
            totalWeight -= selected.weight();
        }

        // 返回不可修改的结果列表。
        return List.copyOf(results);
    }

    private void validateCandidates(
            List<Candidate> candidates
    ) {
        Set<Long> reservationIds =
                new HashSet<>();

        Set<Integer> reservationOrders =
                new HashSet<>();

        for (Candidate candidate : candidates) {

            if (candidate == null) {
                throw new IllegalArgumentException(
                        "抽签参与者不能为空"
                );
            }

            if (candidate.reservationId() == null
                    || candidate.reservationId() <= 0) {
                throw new IllegalArgumentException(
                        "预约ID必须大于0"
                );
            }

            if (candidate.reservationOrder() <= 0) {
                throw new IllegalArgumentException(
                        "预约序号必须大于0"
                );
            }

            if (!reservationIds.add(
                    candidate.reservationId()
            )) {
                throw new IllegalArgumentException(
                        "存在重复的预约ID"
                );
            }

            if (!reservationOrders.add(
                    candidate.reservationOrder()
            )) {
                throw new IllegalArgumentException(
                        "存在重复的预约序号"
                );
            }
        }
    }
}
