package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase2Spec;
import rotp.addons.kingcrimson.KingCrimsonPhase4Spec;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDamageTypes;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonGroundPrimaryGameTests {
    private KingCrimsonGroundPrimaryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_ground_primary")
    public static void groundPunchUsesNativeNonlivingPrimary(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonGroundPrimaryGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        for (double multiplier : new double[] {1, 0, 2}) {
            String id = "G1/boat/M" + multiplier;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, multiplier)) {
                var boat = scene.boat(scene.origin.add(0, 0, 1.5));
                float raw = (float) scene.stand.getAttackDamage();
                scene.cast(boat, "heavy_punch", "kingcrimson_groundpunch");
                var hits = scene.hits(boat);
                checks.check(hits.size() == 1, id + " exactly one native primary hurt calls=" + hits.size());
                checks.check(KingCrimsonGroundGameTestFixture.near(boat.getDamage(), raw * multiplier * 10),
                        id + " vanilla boat damage=" + boat.getDamage() + " raw=" + raw);
                if (hits.size() == 1) {
                    var hit = hits.getFirst();
                    checks.check(KingCrimsonGroundGameTestFixture.near(hit.amount, raw * multiplier)
                                    && Boolean.TRUE.equals(hit.result), id + " actual native amount/result, including M0");
                    checks.check(hit.source.is(ModDamageTypes.STAND_ATTACK) && hit.source.getDirectEntity() == scene.stand
                                    && hit.source instanceof DamageSourceModified modified && modified.jojo_ripples$standPower() == scene.power,
                            id + " primary source/owner retained");
                }
                checks.check(KingCrimsonGroundGameTestFixture.near(scene.stand.getFinisherMeter(), 0.24),
                        id + " unchanged finisher consumption");
            }
        }
        try (var scene = new KingCrimsonGroundGameTestFixture(helper, "G1/living-control", 1)) {
            var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
            float raw = (float) scene.stand.getAttackDamage();
            scene.cast(cow, "heavy_punch", "kingcrimson_groundpunch");
            checks.check(scene.hits(cow).size() == 1 && KingCrimsonGroundGameTestFixture.near(200 - cow.getHealth(), raw),
                    "G1 living primary remains one native hit");
        }
        // 1.16 StandEntityPunch.doAttack hurt any entity, so Chop, Impale, Eye Jab and Ground Slam punch a boat too.
        for (String ability : new String[] {"kingcrimson_chop", "kingcrimson_impale", "kingcrimson_desperate_eyejab", "kingcrimson_groundslam"}) {
            String id = "G1/sibling/" + ability;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                boolean slam = ability.equals("kingcrimson_groundslam");
                boolean eyejab = ability.equals("kingcrimson_desperate_eyejab");
                boolean impale = ability.equals("kingcrimson_impale");
                if (slam) scene.stand.setArmsOnlyMode(true, true);
                if (eyejab) scene.user.setHealth(60);
                var boat = scene.boat(scene.origin.add(0, 0, 1.5));
                float raw = (float) scene.stand.getAttackDamage() * (slam ? KingCrimsonPhase4Spec.GROUND_SLAM_DAMAGE_MULTIPLIER
                        : eyejab ? KingCrimsonPhase4Spec.EYEJAB_DAMAGE_MULTIPLIER
                        : impale ? KingCrimsonPhase2Spec.IMPALE_DAMAGE_MULTIPLIER : KingCrimsonPhase2Spec.CHOP_DAMAGE_MULTIPLIER);
                scene.cast(boat, ability.equals("kingcrimson_chop") || impale ? ability : "heavy_punch", ability);
                var hits = scene.hits(boat);
                checks.check(hits.size() == 1, id + " exactly one native primary hurt calls=" + hits.size());
                if (hits.size() == 1) {
                    var hit = hits.getFirst();
                    checks.check(KingCrimsonGroundGameTestFixture.near(hit.amount, raw) && Boolean.TRUE.equals(hit.result)
                                    && hit.source.is(ModDamageTypes.STAND_ATTACK) && hit.source.getDirectEntity() == scene.stand
                                    && hit.source instanceof DamageSourceModified modified && modified.jojo_ripples$standPower() == scene.power,
                            id + " native amount/result/source amount=" + hit.amount + " raw=" + raw);
                }
                checks.check(KingCrimsonGroundGameTestFixture.near(boat.getDamage(), raw * 10),
                        id + " vanilla boat damage=" + boat.getDamage() + " raw=" + raw);
                Vec3 expected = slam ? boat.position().subtract(scene.stand.position()).multiply(1, 0, 1).normalize()
                        .scale(KingCrimsonPhase4Spec.GROUND_SLAM_KNOCKBACK * 0.5) : Vec3.ZERO;
                checks.check(KingCrimsonGroundGameTestFixture.near(boat.getDeltaMovement(), expected),
                        id + " tail push actual=" + boat.getDeltaMovement() + " expected=" + expected);
            }
        }
        checks.finish(helper, "D-KC-GROUND-01", 8);
    }
}
