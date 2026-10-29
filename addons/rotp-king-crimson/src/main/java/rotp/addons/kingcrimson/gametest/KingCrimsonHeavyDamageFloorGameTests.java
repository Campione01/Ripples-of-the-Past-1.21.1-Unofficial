package rotp.addons.kingcrimson.gametest;

import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.CHOP;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.EYEJAB;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.GROUND_PUNCH;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.base;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.prepare;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;

/**
 * 1.16 KingCrimsonGroundPunch keeps StandEntityHeavyAttack.punchEntity's damage, getHeavyAttackDamage(strength) =
 * max(strength, 1). Desperate Eye Jab replaces it with 0.9 * strength and Chop is a light attack, so neither has a floor.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonHeavyDamageFloorGameTests {
    private KingCrimsonHeavyDamageFloorGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_heavy_damage_floor")
    public static void groundPunchKeepsTheHeavyDamageFloor(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonHeavyDamageFloorGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        record Case(String ability, double strength, boolean boat, float expected) {}
        List<Case> cases = List.of(
                new Case(GROUND_PUNCH, 0, false, 1),
                new Case(GROUND_PUNCH, 0.5, false, 1),
                new Case(GROUND_PUNCH, 3, false, 3),
                new Case(GROUND_PUNCH, 0, true, 1),
                new Case(EYEJAB, 0, false, -1),
                new Case(EYEJAB, 0.5, false, 0.45F),
                new Case(CHOP, 0, false, -1));
        for (Case value : cases) {
            String id = "F1/" + value.ability() + "/" + (value.boat() ? "boat/" : "cow/") + value.strength();
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                prepare(scene, value.ability());
                scene.stand.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(value.strength());
                scene.premise(scene.stand.getAttackDamage() == value.strength(), "Stand strength " + value.strength());
                Entity target = value.boat() ? scene.boat(scene.origin.add(0, 0, 1.5)) : scene.cow(scene.origin.add(0, 0, 1.5), 200);
                scene.cast(target, base(value.ability()), value.ability());
                var hits = scene.hits(target);
                if (value.expected() < 0) {
                    checks.check(hits.isEmpty(), id + " zero raw damage is not admitted: hits=" + hits.size());
                }
                else {
                    checks.check(hits.size() == 1 && KingCrimsonGroundGameTestFixture.near(hits.getFirst().amount, value.expected()),
                            id + " primary damage=" + hits.stream().map(hit -> hit.amount).toList() + " expected=" + value.expected());
                }
            }
        }
        checks.finish(helper, "KC-HEAVY-DAMAGE-FLOOR", cases.size());
    }
}
