package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

public final class GoldExperienceLifeformOrderingSmokeTest {
    private GoldExperienceLifeformOrderingSmokeTest() {}

    public static void main(String[] args) {
        verifyPriorityAndTieBreaker();
        verifyKeysAreSnapshottedOnce();
    }

    private static void verifyPriorityAndTieBreaker() {
        Candidate zebra = new Candidate("Zebra", "test:a");
        Candidate ant = new Candidate("Ant", "test:z");
        Candidate beeSecond = new Candidate("Bee", "test:b");
        Candidate beeFirst = new Candidate("Bee", "test:a");

        List<Candidate> sorted = GoldExperienceLifeforms.sortedByStableKeys(
                List.of(zebra, beeSecond, ant, beeFirst).stream(),
                Candidate::displayName,
                Candidate::id);

        check(sorted.equals(List.of(ant, beeFirst, beeSecond, zebra)),
                "display-name priority or canonical-ID tie-breaker changed");
    }

    private static void verifyKeysAreSnapshottedOnce() {
        List<Candidate> candidates = IntStream.range(0, 4_096)
                .mapToObj(index -> new Candidate(
                        String.format(Locale.ROOT, "Entity %03d",
                                index * 37 % 251),
                        String.format(Locale.ROOT, "test:entity_%05d", index)))
                .toList();
        Map<Candidate, Integer> displayNameReads = new IdentityHashMap<>();
        Map<Candidate, Integer> idReads = new IdentityHashMap<>();

        List<Candidate> sorted = GoldExperienceLifeforms.sortedByStableKeys(
                candidates.stream(),
                candidate -> changingKey(
                        candidate.displayName(), candidate, displayNameReads),
                candidate -> changingKey(candidate.id(), candidate, idReads));

        check(sorted.size() == candidates.size(),
                "stable-key sort lost an entity type");
        for (Candidate candidate : candidates) {
            check(displayNameReads.get(candidate) == 1,
                    "display name was read more than once for " + candidate.id());
            check(idReads.get(candidate) == 1,
                    "canonical ID was read more than once for " + candidate.id());
        }
        assertOrdered(sorted, Candidate::displayName, Candidate::id);

        List<String> expectedIds =
                sorted.stream().map(Candidate::id).toList();
        for (int seed = 0; seed < 64; seed++) {
            List<Candidate> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, new Random(seed));
            List<Candidate> rerun =
                    GoldExperienceLifeforms.sortedByStableKeys(
                            shuffled.stream(),
                            Candidate::displayName,
                            Candidate::id);
            check(rerun.stream().map(Candidate::id).toList()
                            .equals(expectedIds),
                    "entity ordering changed with input order for seed " + seed);
        }
    }

    private static <T> String changingKey(
            String stableValue, T value, Map<T, Integer> reads) {
        int read = reads.merge(value, 1, Integer::sum);
        return read == 1 ? stableValue : stableValue + "-changed-" + read;
    }

    private static <T> void assertOrdered(
            List<T> values,
            java.util.function.Function<T, String> priorityKey,
            java.util.function.Function<T, String> tieBreaker) {
        for (int index = 1; index < values.size(); index++) {
            T previous = values.get(index - 1);
            T current = values.get(index);
            int comparison = priorityKey.apply(previous)
                    .compareTo(priorityKey.apply(current));
            if (comparison == 0) {
                comparison = tieBreaker.apply(previous)
                        .compareTo(tieBreaker.apply(current));
            }
            check(comparison <= 0,
                    "entity ordering is not monotonic at index " + index);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Candidate(String displayName, String id) {}
}
