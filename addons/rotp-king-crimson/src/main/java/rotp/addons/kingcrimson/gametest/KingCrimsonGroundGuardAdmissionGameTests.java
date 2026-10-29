package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.MathUtil;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonGroundGuardAdmissionGameTests {
    private static Suite active;
    private enum Kind { IMMUNITY, RAW0, ADMITTED, ZERO_M, REFUSED, CHOP_RAW0, EYEJAB_RAW0, SLAM_RAW0, CHOP_PROXIMITY }
    private record Pulse(float strength, boolean nativeHurt) {}
    // Body gap 0.5: the old bounding-box/effective-range ratio is zero at effective range 0.5, the 1.16 ratio is not.
    private static final double PROXIMITY_BODY_Z = 1.15D;
    private static final double PROXIMITY_EFFECTIVE_RANGE = 0.5D;
    private KingCrimsonGroundGuardAdmissionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 900, batch = "kc_ground_guard_admission")
    public static void groundGuardBreakRequiresRawAdmission(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonGroundGuardAdmissionGameTests.class);
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonGroundAdmissionFixture.class);
        helper.assertTrue(active == null, "No overlapping guard admission suite");
        Suite suite = new Suite(helper); active = suite;
        try {
            helper.testInfo.addListener(suite);
            cardinalityControls(helper, suite.checks);
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }
    @AfterBatch(batch = "kc_ground_guard_admission")
    public static void cleanupGuardAdmission(ServerLevel level) { if (active != null) active.close(); }

    // These existing-behavior controls are not correction-sensitive RED assertions.
    private static void cardinalityControls(GameTestHelper helper, KingCrimsonGroundGameTestFixture.Checks checks) {
        for (int mode = 0; mode < 4; mode++) {
            String id = "G2-R1/transport-cardinality/" + mode;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, mode == 2 ? 0 : 1)) {
                var cow = scene.cow(scene.origin.add(0.6, 0, 1.5), 200);
                if (mode == 3) {
                    DamageSource prime = new DamageSource(DamageUtil.type(scene.level, ModDamageTypes.STAND_ATTACK), scene.stand, scene.user);
                    ((DamageSourceModified) prime).jojo_ripples$setStandInvulTicks(40);
                    scene.premise(scene.stand.hurtWithStandAttack(cow, prime, 1) && StandEntity.isStandAttackInvulnerable(cow),
                            "Existing native transport-immunity preparation");
                    scene.hits.clear();
                }
                if (mode == 1) scene.listen(LivingIncomingDamageEvent.class, event -> {
                    if (event.getEntity() == cow) event.setCanceled(true);
                });
                List<Pulse> pulses = new ArrayList<>();
                scene.listen(LivingKnockBackEvent.class, event -> {
                    if (event.getEntity() == cow) pulses.add(new Pulse(event.getStrength(), DamageSourceModified.currentKnockbackSource(cow) != null));
                });
                scene.cast(cow, "heavy_punch", "kingcrimson_groundpunch");
                var nativePulses = pulses.stream().filter(Pulse::nativeHurt).toList();
                var detached = pulses.stream().filter(pulse -> !pulse.nativeHurt).toList();
                int nativeCount = mode == 1 || mode == 3 ? 0 : 1;
                int detachedCount = mode == 3 ? 0 : mode == 1 ? 1 : 2;
                checks.check(nativePulses.size() == nativeCount && nativePulses.stream()
                                .allMatch(pulse -> KingCrimsonGroundGameTestFixture.near(pulse.strength, 0.4)),
                        id + " exact native0.4 count=" + nativePulses.size() + " expected=" + nativeCount);
                checks.check(detached.size() == detachedCount && detached.stream()
                                .allMatch(pulse -> KingCrimsonGroundGameTestFixture.near(pulse.strength, 0.75)),
                        id + " exact detached0.75 count=" + detached.size() + " expected=" + detachedCount);
                var hits = scene.hits(cow);
                checks.check(mode == 3 ? hits.isEmpty() : hits.size() == 1 && Boolean.valueOf(mode != 1).equals(hits.getFirst().result),
                        id + " native cardinality paired with actual virtual hurt boolean");
                if (mode == 2) checks.check(cow.getHealth() == 200 && hits.getFirst().amount == 0,
                        id + " actual true M0 hurt, not inferred from HP loss");
            }
        }
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final KingCrimsonGroundGameTestFixture.Checks checks = new KingCrimsonGroundGameTestFixture.Checks();
        Scene scene;
        int index, ticks;
        RuntimeException eventFailure;
        boolean closed;
        Suite(GameTestHelper helper) { this.helper = helper; }
        void tick() {
            if (closed) return;
            try {
                if (eventFailure != null) throw eventFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == Kind.values().length) { close(); checks.finish(helper, "D-KC-GROUND-02-R1", 4 + Kind.values().length); return; }
                    scene = new Scene(this, Kind.values()[index++]); scene.prepare(); ticks = 0;
                }
                helper.assertTrue(++ticks < 80, "Natural guard admission scene deadline");
            }
            catch (RuntimeException | Error error) { close(); throw error; }
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

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Kind kind;
        final KingCrimsonGroundAdmissionFixture fixture;
        KingCrimsonGroundAdmissionFixture.Actor caster, defender, primer;
        EntityActionInstance action, primeAction;
        int incoming, primeIncoming, knockbacks, startAge;
        boolean guardAtIncoming, immunityBefore, guardBefore, done;
        ActionPhase phaseBefore;
        float healthBefore, incomingAmount;
        long seed = -1;
        Scene(Suite suite, Kind kind) {
            this.suite = suite; this.kind = kind;
            fixture = new KingCrimsonGroundAdmissionFixture(suite.helper, "G2-R1/natural/" + kind, kind == Kind.ZERO_M ? 0 : 1);
        }
        boolean sibling() { return kind == Kind.CHOP_RAW0 || kind == Kind.EYEJAB_RAW0 || kind == Kind.SLAM_RAW0; }
        boolean rawZero() { return kind == Kind.RAW0 || sibling(); }
        boolean seeded() { return sibling() || kind == Kind.CHOP_PROXIMITY; }
        void prepare() {
            caster = fixture.actor(fixture.origin, fixture.origin, 0, rawZero() ? 0 : 1, 28);
            double bodyZ = kind == Kind.CHOP_PROXIMITY ? PROXIMITY_BODY_Z : 1.5;
            defender = fixture.actor(fixture.origin.add(0, 0, 6), fixture.origin.add(0, 0, bodyZ), 180, 1, 16);
            if (kind == Kind.CHOP_PROXIMITY) {
                caster.user().getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(PROXIMITY_EFFECTIVE_RANGE);
                StandEntity.updateStandStatAttributes(caster.stand(), caster.user()); caster.stand().updateStrengthMultipliers();
            }
            // Native actor attributes keep its real owner outside the independent Ground AoE.
            defender.user().getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            defender.user().getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
            StandEntity.updateStandStatAttributes(defender.stand(), defender.user()); defender.stand().updateStrengthMultipliers();
            fixture.press(defender, null, "manual_control", "manual_control", InputMethod.CLICK, KingCrimsonGroundAdmissionFixture.CONTROL);
            fixture.press(defender, null, "guard", "guard", InputMethod.HOLD, KingCrimsonGroundAdmissionFixture.GUARD);
            fixture.premise(defender.stand().isManuallyControlled() && defender.stand().isStandBlocking()
                    && defender.stand().canBlockFromAngle(caster.stand().position()), "Actual held front guard, not passive auto-guard");
            fixture.listen(LivingIncomingDamageEvent.class, this::incoming);
            fixture.listen(LivingKnockBackEvent.class, event -> {
                if (action != null && event.getEntity() == defender.stand()) knockbacks++;
            });
            fixture.listen(EntityTickEvent.Pre.class, event -> {
                if (action != null && event.getEntity() == caster.stand() && !done) {
                    immunityBefore = StandEntity.isStandAttackInvulnerable(defender.stand());
                    guardBefore = defender.stand().isStandBlocking(); phaseBefore = action.getPhase();
                    if (seeded()) caster.stand().getRandom().setSeed(seed);
                }
            });
            fixture.listen(EntityTickEvent.Post.class, event -> {
                if (done || suite.eventFailure != null) return;
                try { post(event.getEntity()); }
                catch (RuntimeException error) { suite.eventFailure = error; }
            });
            KingCrimsonGroundAdmissionFixture.log("WARMUP " + fixture.id + " waiting for native ServerPlayer spawn protection to expire");
        }
        void afterWarmup() {
            fixture.premise(defender.user().tickCount >= 61, "Real ServerPlayer spawn-protection warmup elapsed");
            if (kind == Kind.IMMUNITY) {
                primer = fixture.actor(fixture.origin.add(-1, 0, 0), fixture.origin.add(-1, 0, 0), 0, 1, 32);
                primer.stand().setFinisherMeter(0);
                primeAction = fixture.press(primer, defender.stand(), "heavy_punch", "heavy_punch", InputMethod.CLICK,
                        KingCrimsonGroundAdmissionFixture.ATTACK);
                fixture.premise(primeAction != null, "Actual registered ordinary-heavy prime");
            }
            else beginAttack();
        }
        void beginAttack() {
            if (kind == Kind.SLAM_RAW0) caster.stand().setArmsOnlyMode(true, true);
            caster.stand().setFinisherMeter(0.75F);
            if (kind == Kind.EYEJAB_RAW0) caster.user().setHealth(40);
            // Ground Punch keeps the 1.16 heavy damage floor of 1; the zero-raw rejection is shown with Chop.
            boolean chop = kind == Kind.RAW0 || kind == Kind.CHOP_RAW0 || kind == Kind.CHOP_PROXIMITY;
            String base = chop ? "kingcrimson_chop" : "heavy_punch";
            String expected = chop ? "kingcrimson_chop"
                    : kind == Kind.EYEJAB_RAW0 ? "kingcrimson_desperate_eyejab"
                    : kind == Kind.SLAM_RAW0 ? "kingcrimson_groundslam" : "kingcrimson_groundpunch";
            healthBefore = defender.user().getHealth(); startAge = caster.stand().tickCount;
            fixture.premise(defender.stand().isStandBlocking() && defender.stand().canStartBlocking(), "Guard raised without existing lock");
            if (kind == Kind.IMMUNITY) fixture.premise(primeIncoming > 0 && StandEntity.isStandAttackInvulnerable(defender.stand()),
                    "Actual prior native heavy established live immunity");
            if (seeded()) {
                float chance = kind == Kind.SLAM_RAW0 ? 0.75F : proximityChance(caster.stand(), defender.stand());
                float bound = chance;
                if (kind == Kind.CHOP_PROXIMITY) {
                    float boxChance = Mth.clamp((float) (1 - MathUtil.getAABBDistance(caster.stand().getBoundingBox(),
                            defender.stand().getBoundingBox()) / Math.max(caster.stand().getEffectiveRange(), 0.0001) - 0.25), 0, 1);
                    fixture.premise(boxChance == 0 && chance > 0.25F,
                            "Bounding-box ratio is zero where the 1.16 eye-ray ratio is " + chance);
                }
                else {
                    // Below any chance an older ratio would give, so a roll before admission would break the guard.
                    fixture.premise(chance > 0.25F, "Sibling guard-break chance " + chance);
                    bound = 0.25F;
                }
                seed = seedWithFirstRollsBelow(bound);
                fixture.premise(seed >= 0, "Deterministic guard-break roll below " + bound);
            }
            action = fixture.press(caster, defender.stand(), base, expected, InputMethod.CLICK, KingCrimsonGroundAdmissionFixture.ATTACK);
            fixture.premise(action != null, "Actual native attack action");
        }
        void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() != defender.stand()) return;
            Entity direct = event.getSource().getDirectEntity();
            if (primer != null && direct == primer.stand()) { primeIncoming++; return; }
            if (direct != caster.stand() || action == null) return;
            incoming++; incomingAmount = event.getAmount(); guardAtIncoming = defender.stand().isStandBlocking();
            KingCrimsonGroundAdmissionFixture.log("INCOMING " + fixture.id + " amount=" + incomingAmount + " guard=" + guardAtIncoming);
            if (kind == Kind.REFUSED) event.setCanceled(true);
        }
        void post(Entity entity) {
            if (action == null && primeAction == null && entity == defender.user() && defender.user().tickCount >= 61) afterWarmup();
            if (primer != null && action == null && entity == primer.stand() && primeAction.punchedTarget != null) {
                fixture.premise(primeAction.punchedTarget.getMainEntity() == defender.stand(), "Native prior heavy exact defender");
                AbilityInput.keyRelease(KingCrimsonGroundAdmissionFixture.ATTACK, primer.user());
                LivingComponentAction.getComponent(primer.stand()).setAction(null, SyncType.NO_SYNC);
                primer.type().forceUnsummon(primer.user(), primer.power()); primer.user().discard();
                beginAttack();
            }
            if (action == null || entity != caster.stand() || action.punchedTarget == null) return;
            fixture.premise(action.punchedTarget.getMainEntity() == defender.stand() && caster.stand().tickCount > startAge,
                    "Natural level ticks reached the actual guarded body");
            fixture.premise(guardBefore && phaseBefore == ActionPhase.PERFORM, "Held guard sampled immediately before native PERFORM");
            fixture.premise(defender.user().position().distanceTo(defender.stand().position()) > 3.5,
                    "Real linked user remained outside independent Ground area");
            fixture.premise((caster.stand().getAttackDamage() == 0) == rawZero(), "Actual raw admission stat at impact");
            float loss = healthBefore - defender.user().getHealth();
            if (kind == Kind.IMMUNITY || kind == Kind.RAW0) {
                if (kind == Kind.IMMUNITY) fixture.premise(immunityBefore && StandEntity.isStandAttackInvulnerable(defender.stand()),
                        "Native immunity remains active at immediate pre/post PERFORM");
                suite.checks.check(incoming == 0 && knockbacks == 0 && loss == 0,
                        fixture.id + " raw rejection before Incoming/knockback/user loss");
                suite.checks.check(defender.stand().isStandBlocking() && defender.stand().canStartBlocking(),
                        fixture.id + " rejected raw hit preserves real guard and no new blocking lock");
            }
            else if (sibling()) {
                suite.checks.check(incoming == 0 && loss == 0, fixture.id + " existing sibling raw0 hurt refusal retained");
                // 1.16 StandEntityPunch.onAttack rolls the guard break only after doAttack admitted the damage.
                suite.checks.check(defender.stand().isStandBlocking() && defender.stand().canStartBlocking(),
                        fixture.id + " sibling raw0 rejection preserves real guard and no new blocking lock");
            }
            else {
                suite.checks.check(!defender.stand().isStandBlocking() && !defender.stand().canStartBlocking(),
                        fixture.id + " admitted positiveRAW breaks/locks independently of M or hurt success");
                if (kind != Kind.ZERO_M) suite.checks.check(incoming == 1 && !guardAtIncoming && incomingAmount > 0,
                        fixture.id + " exactly one Incoming sees guard already broken");
                if (kind == Kind.ZERO_M || kind == Kind.REFUSED) suite.checks.check(loss == 0,
                        fixture.id + " M0 or native Incoming cancellation leaves user health unchanged");
                else suite.checks.check(loss > 0, fixture.id + " admitted actual linked-user health loss");
            }
            suite.checks.check(!defender.stand().hasEffect(ModStatusEffects.STUN) && !defender.user().hasEffect(ModStatusEffects.STUN),
                    fixture.id + " guard lock is not a STUN effect");
            KingCrimsonGroundAdmissionFixture.log("NATIVE-GUARD-IMPACT " + fixture.id + " nativeTicks="
                    + (caster.stand().tickCount - startAge) + " incoming=" + incoming + " knockbacks=" + knockbacks
                    + " guardBefore=" + guardBefore + " immuneBefore=" + immunityBefore + " guardAfter="
                    + defender.stand().isStandBlocking() + " canStartBlocking=" + defender.stand().canStartBlocking() + " loss=" + loss);
            done = true;
        }
        @Override public void close() { fixture.close(); }
    }

    // 1.16 StandEntity.getProximityRatio - 0.25, the Chop and Eye Jab guard-break chance
    private static float proximityChance(StandEntity stand, Entity target) {
        double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        double ratio = reach > 0 ? 1 - ActionTargetRange.getDistance(stand, target.getBoundingBox()) / reach : 0;
        return Mth.clamp((float) (ratio - 0.25), 0, 1);
    }

    private static long seedWithFirstRollsBelow(float bound) {
        for (long seed = 0; seed < 100000; seed++) {
            RandomSource random = RandomSource.create(seed);
            if (random.nextFloat() < bound && random.nextFloat() < bound && random.nextFloat() < bound) return seed;
        }
        return -1;
    }
}
