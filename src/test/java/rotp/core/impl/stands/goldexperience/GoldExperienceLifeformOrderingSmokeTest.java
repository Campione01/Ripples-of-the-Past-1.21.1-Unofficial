package rotp.core.impl.stands.goldexperience;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import rotp.core.client.ui.screen_jojomenu.GoldExperienceLifeformLayout;

public final class GoldExperienceLifeformOrderingSmokeTest {
    private GoldExperienceLifeformOrderingSmokeTest() {}

    public static void main(String[] args) {
        verifyPriorityAndTieBreaker();
        verifyKeysAreSnapshottedOnce();
        verifyPickerLayout();
        verifyEntityIconResources();
    }

    // The 1.16 core shipped these head icons for EntityTypeIcon (lifeform marker,
    // new-lifeform toast). The squid icon sits where the 1.21.1 squid texture
    // path maps to (entity/squid/squid.png) instead of 1.16's entity/squid.png.
    private static final List<String> ENTITY_ICONS = List.of(
            "bat", "bear/polarbear", "bee/bee", "blaze", "cat/black", "cat/ocelot",
            "chicken", "cow/brown_mooshroom", "cow/cow", "cow/red_mooshroom",
            "creeper/creeper", "dolphin", "enderdragon/dragon", "enderman/enderman",
            "endermite", "fish/cod", "fish/pufferfish", "fish/salmon", "fish/tropical_a",
            "fox/fox", "ghast/ghast", "guardian", "guardian_elder", "hoglin/hoglin",
            "hoglin/zoglin", "horse/donkey", "horse/horse_white", "horse/mule",
            "iron_golem/iron_golem", "llama/creamy", "panda/panda",
            "parrot/parrot_red_blue", "phantom", "pig/pig", "piglin/piglin",
            "piglin/piglin_brute", "piglin/zombified_piglin", "rabbit/brown",
            "sheep/sheep", "shulker/shulker", "silverfish", "skeleton/skeleton",
            "skeleton/stray", "skeleton/wither_skeleton", "slime/magmacube",
            "slime/slime", "spider/cave_spider", "spider/spider", "squid/squid",
            "strider/strider", "turtle/big_sea_turtle", "turtle/big_sea_turtle.stand",
            "wither/wither", "wolf/wolf", "zombie/drowned", "zombie/husk",
            "zombie/zombie", "unknown");

    private static void verifyEntityIconResources() {
        ClassLoader loader = GoldExperienceLifeformOrderingSmokeTest.class.getClassLoader();
        check(ENTITY_ICONS.size() == 58, "entity icon list drifted from the 57 icons plus unknown");
        for (String icon : ENTITY_ICONS) {
            check(loader.getResource("assets/minecraft/textures/entity_icon/" + icon + ".png") != null,
                    "missing GE entity icon: " + icon);
        }
        check(loader.getResource("assets/minecraft/textures/entity_icon/squid.png") == null,
                "squid icon must follow the 1.21.1 squid texture path");
    }

    private static void verifyPickerLayout() {
        for (int width : new int[] {320, 427, 640, 960}) {
            for (int height : new int[] {240, 270, 300, 360, 480}) {
                for (boolean grid : new boolean[] {false, true}) {
                    GoldExperienceLifeformLayout layout = GoldExperienceLifeformLayout.forScreen(width, height, grid);
                    check(layout.titleY() >= 0, "picker title outside screen");
                    check(layout.searchY() + 40 <= layout.choiceTopY(), "filters overlap choices");
                    check(layout.doneY() + 20 <= height - 6, "picker buttons outside screen");
                    check(layout.selectedY() + 9 < layout.toolsY(), "selection label overlaps controls");
                    check(layout.toolsY() + 20 < layout.doneY(), "footer rows overlap");
                    check(layout.headerLeft() >= 8, "header outside left edge");
                    check(layout.headerLeft() + 3 * (layout.filterWidth() + 4) + 62 <= width - 8,
                            "view button outside right edge");
                    check(layout.headerLeft() + 286 <= width - 8, "creative search controls outside screen");
                    for (int slot = 0; slot < layout.pageSize(); slot++) {
                        check(layout.choiceY(slot) >= layout.choiceTopY(), "choice above list");
                        check(layout.choiceY(slot) + 20 < layout.selectedY(), "choice overlaps footer");
                    }
                    List<Integer> visited = new ArrayList<>();
                    for (int page = 0; page * layout.pageSize() < 101; page++) {
                        for (int slot = 0; slot < layout.pageSize(); slot++) {
                            int index = page * layout.pageSize() + slot;
                            if (index < 101) visited.add(index);
                        }
                    }
                    check(visited.equals(IntStream.range(0, 101).boxed().toList()), "responsive pagination lost choices");
                }
            }
        }
        GoldExperienceLifeformLayout large = GoldExperienceLifeformLayout.forScreen(640, 360, false);
        check(large.titleY() == 84 && large.searchY() == 104 && large.choiceTopY() == 158
                && large.selectedY() == 270 && large.toolsY() == 286 && large.doneY() == 310
                && large.rowsPerColumn() == 5, "normal-size list layout changed");
        check(GoldExperienceLifeformLayout.forScreen(427, 240, false).pageSize() == 8,
                "small list should paginate four rows per column");
        check(GoldExperienceLifeformLayout.forScreen(427, 240, true).pageSize() == 16,
                "small grid should retain four rows");
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
