
package com.campustrade.algorithm;

import com.campustrade.config.ReservationProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class WeightedDrawAlgorithmTest {

    private ReservationProperties properties;

    private WeightedDrawAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        properties = new ReservationProperties();

        algorithm = new WeightedDrawAlgorithm(
                properties
        );
    }

    private WeightedDrawAlgorithm.Candidate candidate(
            long id,
            int order
    ) {
        return new WeightedDrawAlgorithm.Candidate(
                id,
                order
        );
    }

    private List<WeightedDrawAlgorithm.Candidate>
    fourCandidates() {
        return List.of(
                candidate(101L, 1),
                candidate(102L, 2),
                candidate(103L, 3),
                candidate(104L, 4)
        );
    }

    @Test
    void emptyCandidatesReturnEmptyResult() {
        var results = algorithm.draw(
                List.of(),
                new Random(2026L)
        );

        assertTrue(results.isEmpty());
    }

    @Test
    void singleCandidateAlwaysRanksFirst() {
        var results = algorithm.draw(
                List.of(candidate(101L, 1)),
                new Random(2026L)
        );

        assertEquals(1, results.size());

        var entry = results.get(0);

        assertEquals(101L, entry.reservationId());
        assertEquals(1, entry.reservationOrder());
        assertEquals(1, entry.drawWeight());
        assertEquals(1, entry.queuePosition());
    }

    @Test
    void allCandidatesAppearExactlyOnce() {
        var results = algorithm.draw(
                fourCandidates(),
                new Random(2026L)
        );

        assertEquals(4, results.size());

        var ids = results.stream()
                .map(
                    WeightedDrawAlgorithm.DrawEntry
                            ::reservationId
                )
                .collect(Collectors.toSet());

        assertEquals(4, ids.size());

        assertTrue(ids.containsAll(
                List.of(101L, 102L, 103L, 104L)
        ));
    }

    @Test
    void queuePositionsAreContinuous() {
        var results = algorithm.draw(
                fourCandidates(),
                new Random(2026L)
        );

        for (int i = 0; i < results.size(); i++) {
            assertEquals(
                    i + 1,
                    results.get(i).queuePosition()
            );
        }
    }

    @Test
    void earlierCandidatesReceiveHigherWeights() {
        var results = algorithm.draw(
                fourCandidates(),
                new Random(2026L)
        );

        Map<Long, Integer> weights =
                results.stream()
                        .collect(
                                Collectors.toMap(
                                    WeightedDrawAlgorithm
                                        .DrawEntry
                                        ::reservationId,

                                    WeightedDrawAlgorithm
                                        .DrawEntry
                                        ::drawWeight
                                )
                        );

        assertEquals(4, weights.get(101L));
        assertEquals(3, weights.get(102L));
        assertEquals(2, weights.get(103L));
        assertEquals(1, weights.get(104L));
    }

    @Test
    void gapsInReservationOrderDoNotAffectWeights() {
        var candidates = List.of(
                candidate(101L, 1),
                candidate(103L, 3),
                candidate(105L, 5)
        );

        var results = algorithm.draw(
                candidates,
                new Random(2026L)
        );

        Map<Long, Integer> weights =
                results.stream()
                        .collect(
                                Collectors.toMap(
                                    WeightedDrawAlgorithm
                                        .DrawEntry
                                        ::reservationId,

                                    WeightedDrawAlgorithm
                                        .DrawEntry
                                        ::drawWeight
                                )
                        );

        assertEquals(3, weights.get(101L));
        assertEquals(2, weights.get(103L));
        assertEquals(1, weights.get(105L));
    }

    @Test
    void weightsNeverExceedConfiguredLimit() {
        List<WeightedDrawAlgorithm.Candidate>
                candidates = new ArrayList<>();

        for (int i = 1; i <= 15; i++) {
            candidates.add(candidate(i, i));
        }

        var results = algorithm.draw(
                candidates,
                new Random(2026L)
        );

        Map<Long, Integer> weights =
                results.stream()
                        .collect(
                                Collectors.toMap(
                                    WeightedDrawAlgorithm
                                        .DrawEntry
                                        ::reservationId,

                                    WeightedDrawAlgorithm
                                        .DrawEntry
                                        ::drawWeight
                                )
                        );

        for (long i = 1; i <= 6; i++) {
            assertEquals(10, weights.get(i));
        }

        assertEquals(9, weights.get(7L));
        assertEquals(1, weights.get(15L));

        assertTrue(
                results.stream().allMatch(
                        entry ->
                                entry.drawWeight() <= 10
                )
        );
    }

    @Test
    void algorithmRespectsCustomWeightLimit() {
        properties.setMaxDrawWeight(2);

        var results = algorithm.draw(
                fourCandidates(),
                new Random(2026L)
        );

        Map<Long, Integer> weights =
                results.stream()
                        .collect(
                                Collectors.toMap(
                                    WeightedDrawAlgorithm
                                        .DrawEntry
                                        ::reservationId,

                                    WeightedDrawAlgorithm
                                        .DrawEntry
                                        ::drawWeight
                                )
                        );

        assertEquals(2, weights.get(101L));
        assertEquals(2, weights.get(102L));
        assertEquals(2, weights.get(103L));
        assertEquals(1, weights.get(104L));
    }

    @Test
    void unorderedInputProducesSameResult() {
        var ordered = fourCandidates();

        var unordered = List.of(
                candidate(104L, 4),
                candidate(102L, 2),
                candidate(101L, 1),
                candidate(103L, 3)
        );

        var first = algorithm.draw(
                ordered,
                new Random(2026L)
        );

        var second = algorithm.draw(
                unordered,
                new Random(2026L)
        );

        assertEquals(first, second);
    }

    @Test
    void algorithmDoesNotModifyOriginalInput() {
        var candidates = new ArrayList<>(
                fourCandidates()
        );

        var original = List.copyOf(candidates);

        algorithm.draw(
                candidates,
                new Random(2026L)
        );

        assertEquals(original, candidates);
    }

    @Test
    void duplicateReservationIdsAreRejected() {
        var candidates = List.of(
                candidate(101L, 1),
                candidate(101L, 2)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> algorithm.draw(
                        candidates,
                        new Random(2026L)
                )
        );
    }

    @Test
    void duplicateReservationOrdersAreRejected() {
        var candidates = List.of(
                candidate(101L, 1),
                candidate(102L, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> algorithm.draw(
                        candidates,
                        new Random(2026L)
                )
        );
    }

    @Test
    void invalidReservationIdIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> algorithm.draw(
                        List.of(candidate(0L, 1)),
                        new Random(2026L)
                )
        );
    }

    @Test
    void invalidReservationOrderIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> algorithm.draw(
                        List.of(candidate(101L, 0)),
                        new Random(2026L)
                )
        );
    }

    @Test
    void invalidWeightLimitIsRejected() {
        properties.setMaxDrawWeight(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> algorithm.draw(
                        fourCandidates(),
                        new Random(2026L)
                )
        );
    }

    @Test
    void firstPositionProbabilityMatchesWeights() {
        int runs = 100_000;

        Random random = new Random(2026L);

        var candidates = fourCandidates();

        Map<Long, Integer> counts =
                new HashMap<>();

        for (int i = 0; i < runs; i++) {
            var results = algorithm.draw(
                    candidates,
                    random
            );

            Long firstId =
                    results.get(0).reservationId();

            counts.merge(
                    firstId,
                    1,
                    Integer::sum
            );
        }

        double probabilityA =
                counts.getOrDefault(101L, 0)
                        / (double) runs;

        double probabilityB =
                counts.getOrDefault(102L, 0)
                        / (double) runs;

        double probabilityC =
                counts.getOrDefault(103L, 0)
                        / (double) runs;

        double probabilityD =
                counts.getOrDefault(104L, 0)
                        / (double) runs;

        // 允许实际概率与理论概率相差1个百分点。
        double tolerance = 0.01;

        assertEquals(
                0.4,
                probabilityA,
                tolerance
        );

        assertEquals(
                0.3,
                probabilityB,
                tolerance
        );

        assertEquals(
                0.2,
                probabilityC,
                tolerance
        );

        assertEquals(
                0.1,
                probabilityD,
                tolerance
        );

        System.out.printf(
                """
                Weighted draw probability test:
                A: %.4f
                B: %.4f
                C: %.4f
                D: %.4f
                %n
                """,
                probabilityA,
                probabilityB,
                probabilityC,
                probabilityD
        );
    }
}
