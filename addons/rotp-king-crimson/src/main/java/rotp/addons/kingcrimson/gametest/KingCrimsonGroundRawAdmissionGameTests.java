package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase4Spec;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.EntityActionInstance;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonGroundRawAdmissionGameTests {
    private static Suite active;
    private KingCrimsonGroundRawAdmissionGameTests() {}
    @GameTest(template = "empty", timeoutTicks = 250, batch = "kc_ground_raw_admission")
    public static void groundPunchRejectsZeroRawNonlivingPrimary(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonGroundRawAdmissionGameTests.class);
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonGroundAdmissionFixture.class);
        helper.assertTrue(active == null, "No overlapping RAW admission suite");
        Suite suite = new Suite(helper); active = suite;
        try { helper.testInfo.addListener(suite); helper.onEachTick(suite::tick); }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }
    @AfterBatch(batch = "kc_ground_raw_admission")
    public static void cleanupRawAdmission(ServerLevel level) { if (active != null) active.close(); }
    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final KingCrimsonGroundGameTestFixture.Checks checks = new KingCrimsonGroundGameTestFixture.Checks();
        KingCrimsonGroundAdmissionFixture scene;
        int index, ticks;
        boolean done, closed;
        RuntimeException eventFailure;
        Suite(GameTestHelper helper) { this.helper = helper; }
        void tick() {
            if (closed) return;
            try {
                if (eventFailure != null) throw eventFailure;
                if (done) { scene.close(); scene = null; done = false; }
                if (scene == null) {
                    if (index == 3) { close(); checks.finish(helper, "D-KC-GROUND-01-R1", 3); return; }
                    prepare(index++); ticks = 0;
                }
                helper.assertTrue(++ticks < 70, "Natural RAW admission scene deadline");
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }
        void prepare(int mode) {
            boolean rawZero = mode < 2;
            // Ground Punch keeps the 1.16 heavy damage floor of 1, so the zero-raw scenes use Ground Slam,
            // the other strike with a non-living primary and an area pass.
            String strike = rawZero ? "kingcrimson_groundslam" : "kingcrimson_groundpunch";
            double vertical = rawZero ? KingCrimsonPhase4Spec.GROUND_SLAM_VERTICAL_KNOCKBACK
                    : KingCrimsonPhase4Spec.GROUND_PUNCH_VERTICAL_KNOCKBACK;
            scene = new KingCrimsonGroundAdmissionFixture(helper, "G1-R1/natural/" + mode, mode == 0 ? 1 : 0);
            var caster = scene.actor(scene.origin, scene.origin, 0, rawZero ? 0 : 1, 28);
            var boat = scene.boat(scene.origin.add(0, 0, 1.5));
            var secondary = scene.cow(boat.position().add(2, 0, 0));
            boat.setDamage(3); boat.setHurtDir(1); boat.setHurtTime(3);
            if (rawZero) caster.stand().setArmsOnlyMode(true, true);
            caster.stand().setFinisherMeter(0.75F);
            scene.premise(caster.stand().canAttackMelee() && caster.stand().canAttackEntity(boat), "Positive speed/ranges and actual permission");
            EntityActionInstance action = scene.press(caster, boat, "heavy_punch", strike, InputMethod.CLICK,
                    KingCrimsonGroundAdmissionFixture.ATTACK);
            scene.premise(action != null, "Real registered Ground action");
            int startAge = caster.stand().tickCount;
            int[] before = new int[2]; float[] damageBefore = new float[1]; Vec3[] velocityBefore = {Vec3.ZERO};
            Vec3[] areaPush = {Vec3.ZERO};
            scene.listen(EntityTickEvent.Pre.class, event -> {
                if (event.getEntity() == caster.stand() && !done) {
                    before[0] = boat.getHurtDir(); before[1] = boat.getHurtTime();
                    damageBefore[0] = boat.getDamage(); velocityBefore[0] = boat.getDeltaMovement();
                    areaPush[0] = secondary.position().subtract(boat.position()).multiply(0.4, 0, 0.4).add(0, vertical, 0);
                }
            });
            scene.listen(EntityTickEvent.Post.class, event -> {
                if (event.getEntity() != caster.stand() || done || eventFailure != null || action.punchedTarget == null) return;
                try {
                    scene.premise(action.punchedTarget.getMainEntity() == boat && caster.stand().tickCount > startAge,
                            "Actual native level ticks reached the exact boat");
                    scene.premise((caster.stand().getAttackDamage() == 0) == rawZero, "RAW remains correctly bound at native impact");
                    scene.premise(!secondary.isPassenger() && secondary.position().distanceTo(boat.position()) > 1.9,
                            "Independent secondary remains outside native boat pickup range");
                    var hits = scene.hits(boat);
                    if (rawZero) {
                        checks.check(hits.isEmpty(), scene.id + " no virtual primary hurt calls=" + hits.size());
                        checks.check(boat.getHurtDir() == before[0] && boat.getHurtTime() == before[1],
                                scene.id + " no direction/time disturbance pre=" + before[0] + "/" + before[1]
                                        + " post=" + boat.getHurtDir() + "/" + boat.getHurtTime());
                        checks.check(boat.getDamage() == damageBefore[0]
                                        && KingCrimsonGroundGameTestFixture.near(boat.getDeltaMovement(), velocityBefore[0]),
                                scene.id + " stored damage/vector preserved within caster native tick");
                        var area = scene.hits(secondary);
                        checks.check(area.size() == 1 && area.getFirst().source.is(DamageTypes.MOB_ATTACK)
                                        && area.getFirst().amount == 0.25F && secondary.getHealth() == 199.75F,
                                scene.id + " independent raw0 secondary minimum0.25");
                        checks.check(secondary.pushes.stream().filter(push -> KingCrimsonGroundGameTestFixture.near(push, areaPush[0])).count() == 1,
                                scene.id + " exactly one native independent area push expected=" + areaPush[0] + " actual=" + secondary.pushes);
                    }
                    else {
                        checks.check(hits.size() == 1 && hits.getFirst().amount == 0 && hits.getFirst().result,
                                scene.id + " positiveRAW/M0 still reaches one successful virtual hurt");
                        checks.check(boat.getDamage() == damageBefore[0] && boat.getHurtDir() == -before[0] && boat.getHurtTime() == 10,
                                scene.id + " positiveRAW/M0 native direction/time retained");
                        checks.check(KingCrimsonGroundGameTestFixture.near(boat.getDeltaMovement(), new Vec3(0, 0, 0.75)),
                                scene.id + " positiveRAW/M0 detached tail retained");
                    }
                    KingCrimsonGroundAdmissionFixture.log("NATIVE-IMPACT " + scene.id + " casterTicks="
                            + (caster.stand().tickCount - startAge) + " targetAge=" + boat.tickCount);
                    done = true;
                }
                catch (RuntimeException error) { eventFailure = error; }
            });
        }
        void close() {
            if (closed) return;
            closed = true;
            try { if (scene != null) scene.close(); }
            finally { if (active == this) active = null; }
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
