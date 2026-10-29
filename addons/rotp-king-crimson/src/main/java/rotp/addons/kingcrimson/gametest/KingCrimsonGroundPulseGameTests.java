package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase4Spec;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonGroundPulseGameTests {
    private KingCrimsonGroundPulseGameTests() {}
    private record Pulse(float strength, double x, double z, DamageSource source, boolean canceled) {}
    private static final Vec3 INITIAL = new Vec3(0.2, 0.1, -0.15);

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_ground_pulses")
    public static void groundPunchUsesDetachedDonorPulses(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonGroundPulseGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        // 1.16 Ground Slam's addKnockback(0.75f) takes the same two detached half pulses as Ground Punch's 1.5.
        int scenes = pulseScenes(helper, checks, "kingcrimson_groundpunch")
                + pulseScenes(helper, checks, "kingcrimson_groundslam");
        checks.finish(helper, "D-KC-GROUND-02", scenes);
    }

    private static int pulseScenes(GameTestHelper helper, KingCrimsonGroundGameTestFixture.Checks checks, String ability) {
        int scenes = 0;
        boolean slam = ability.equals("kingcrimson_groundslam");
        double half = (slam ? KingCrimsonPhase4Spec.GROUND_SLAM_KNOCKBACK : KingCrimsonPhase4Spec.GROUND_PUNCH_KNOCKBACK) * 0.5;
        for (int mode = 0; mode < 5; mode++) {
            boolean refused = mode == 2;
            boolean immune = mode == 4;
            double multiplier = mode == 3 ? 0 : 1;
            double resistance = mode == 1 ? 0.25 : 0;
            boolean grounded = mode != 1;
            String id = (slam ? "G2/slam/living/" : "G2/living/") + mode;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, multiplier)) {
                if (slam) scene.stand.setArmsOnlyMode(true, true);
                var cow = scene.cow(scene.origin.add(0.6, 0, 1.5), 200);
                cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(resistance);
                if (immune) {
                    DamageSource preparation = new DamageSource(DamageUtil.type(scene.level, ModDamageTypes.STAND_ATTACK), scene.stand, scene.user);
                    ((DamageSourceModified) preparation).jojo_ripples$setStandInvulTicks(40);
                    scene.premise(scene.stand.hurtWithStandAttack(cow, preparation, 1), "Native prior-hit immunity preparation");
                    scene.premise(StandEntity.isStandAttackInvulnerable(cow), "Raw immunity active before actual input");
                    scene.hits.clear();
                }
                if (refused) scene.listen(LivingIncomingDamageEvent.class, event -> {
                    if (event.getEntity() == cow) event.setCanceled(true);
                });
                List<Pulse> pulses = new ArrayList<>();
                scene.listen(LivingKnockBackEvent.class, event -> {
                    if (event.getEntity() == cow) pulses.add(new Pulse(event.getStrength(), event.getRatioX(),
                            event.getRatioZ(), DamageSourceModified.currentKnockbackSource(cow), event.isCanceled()));
                });
                cow.setDeltaMovement(INITIAL); cow.setOnGround(grounded);
                float health = cow.getHealth();
                scene.cast(cow, "heavy_punch", ability);
                var hits = scene.hits(cow);
                checks.check(hits.size() == (immune ? 0 : 1), id + " one virtual hurt or raw immune rejection=" + hits.size());
                List<Pulse> detached = pulses.stream().filter(pulse -> pulse.source == null).toList();
                List<Pulse> nativeHurt = pulses.stream().filter(pulse -> pulse.source != null).toList();
                int expected = immune ? 0 : refused ? 1 : 2;
                checks.check(detached.size() == expected, id + " detached count=" + detached.size() + " expected=" + expected);
                checks.check(detached.stream().allMatch(pulse -> KingCrimsonGroundGameTestFixture.near(pulse.strength, half)
                                && !pulse.canceled), id + " detached" + half + " source-free event strengths=" + detached);
                checks.check(nativeHurt.stream().allMatch(pulse -> KingCrimsonGroundGameTestFixture.near(pulse.strength, 0.4)),
                        id + " original native0.4 excludes added metadata=" + nativeHurt);
                Vec3 expectedVelocity = immune ? INITIAL : hits.getFirst().after;
                Vec3 ratio = scene.stand.position().subtract(cow.position()).multiply(1, 0, 1).normalize();
                for (int pulse = 0; pulse < expected; pulse++)
                    expectedVelocity = apply(expectedVelocity, half * (1 - resistance), ratio, grounded);
                checks.check(KingCrimsonGroundGameTestFixture.near(cow.getDeltaMovement(), expectedVelocity),
                        id + " composed vector actual=" + cow.getDeltaMovement() + " expected=" + expectedVelocity);
                if (!immune) checks.check(Boolean.valueOf(!refused).equals(hits.getFirst().result),
                        id + " actual hurt boolean=" + hits.getFirst().result);
                if (multiplier == 0) checks.check(cow.getHealth() == health && hits.getFirst().amount == 0,
                        id + " M0 true hurt without health loss still has success pulse");
            }
            scenes++;
        }
        for (int mode = 0; mode < 3; mode++) {
            String id = (slam ? "G2/slam/nonliving/" : "G2/nonliving/") + mode;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, mode == 1 ? 0 : 1)) {
                if (slam) scene.stand.setArmsOnlyMode(true, true);
                var boat = scene.boat(scene.origin.add(-0.6, 0, 1.5));
                boat.setInvulnerable(mode == 2); boat.setDeltaMovement(INITIAL);
                scene.premise(scene.stand.canAttackEntity(boat), "Nonliving raw permission admitted");
                scene.cast(boat, "heavy_punch", ability);
                var hits = scene.hits(boat);
                checks.check(hits.size() == 1 && Boolean.valueOf(mode != 2).equals(hits.getFirst().result),
                        id + " single actual virtual hurt and native refusal");
                Vec3 expected = boat.position().subtract(scene.stand.position()).multiply(1, 0, 1).normalize().scale(half);
                checks.check(KingCrimsonGroundGameTestFixture.near(boat.getDeltaMovement(), expected),
                        id + " admitted replacement vector actual=" + boat.getDeltaMovement() + " expected=" + expected);
            }
            scenes++;
        }
        return scenes;
    }

    private static Vec3 apply(Vec3 before, double strength, Vec3 ratio, boolean grounded) {
        return new Vec3(before.x / 2 - ratio.x * strength,
                grounded ? Math.min(0.4, before.y / 2 + strength) : before.y,
                before.z / 2 - ratio.z * strength);
    }
}
