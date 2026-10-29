package rotp.addons.kingcrimson.gametest;

import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.ability.KingCrimsonProjectileThrowAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonTripleProjectileThrowAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowRecoveryEntryGameTests {
    private KingCrimsonThrowRecoveryEntryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 2400, batch = "kc_throw_recovery_entry")
    public static void throwRecoverySamplesFreshGetterAndGlobalHeavyOperand(GameTestHelper helper) {
        identity(helper, KingCrimsonThrowRecoveryEntryGameTests.class);
        var suite = new RecoverySuite(helper);
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    private static final class RecoverySuite extends KingCrimsonThrowGameTestFixture.Suite {
        int kind, recoveryAge, endAge, performAge, expectedRecovery, expectedPerform;
        int previousPerformTick, previousRecoveryTick, tickSpawns, userLifecycleTicks;
        float initialRecovery, initialWindup, initialStamina, tickStamina;
        double initialSpeed, recoverySpeed;
        float recoveryLastHeavy;
        ActionPhase before;
        long lastLifecycleTime;
        int lastResolveDuration;
        boolean triple, sampled, mutatedRecovery;

        RecoverySuite(GameTestHelper helper) { super(helper, "D-KC-THROW-RECOVERY-ENTRY", 20); }

        @Override void prepare(int index) {
            kind = index % 10; triple = index >= 10;
            var mode = kind == 2 ? KingCrimsonThrowGameTestFixture.Mode.RESOLVE : KingCrimsonThrowGameTestFixture.Mode.FINITE;
            scene = new KingCrimsonThrowGameTestFixture(helper, "recovery/" + (triple ? "triple/" : "single/") + kind, mode, 600, 73);
            if (kind == 2) {
                scene.user.removeEffect(ModStatusEffects.RESOLVE);
                scene.premise(scene.user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 3, 0)), "Actual short Resolve installed");
                scene.power.setStamina(600);
                scene.premise(scene.power.getStamina() < scene.power.getMaxStamina() / 2, "Finite depletion masked by initial Resolve only");
            } else {
                scene.power.setStamina(scene.power.getMaxStamina());
            }
            double raw = kind == 1 || kind == 8 ? 28 : kind == 6 ? 32 : kind == 7 ? 36 : 14;
            float current = kind == 3 ? 0.7F : 0;
            KingCrimsonThrowTimingFixture.stats(scene, raw, current);
            snapshot(scene, kind == 3 ? 0.1F : kind == 9 ? 0.75F : 0, current);
            initialSpeed = scene.stand.getAttackSpeed(); initialStamina = scene.power.getStamina();
            scene.premise(initialSpeed == raw, "Actual initial requested speed");
            scene.hand(new ItemStack(Items.ARROW, 3));
            var press = scene.press(triple ? KingCrimsonThrowGameTestFixture.TRIPLE : KingCrimsonThrowGameTestFixture.SINGLE,
                    KingCrimsonThrowGameTestFixture.FIRST);
            scene.premise(press.conditions() && press.admitted() && !press.buffered(), "Real condition-checked registered CLICK");
            identity(helper, press.action().getClass());
            initialWindup = scene.action.phasesLength.getFloat(ActionPhase.WINDUP);
            initialRecovery = scene.action.phasesLength.getFloat(ActionPhase.RECOVERY);
            checks.check(initialRecovery == KingCrimsonThrowTimingFixture.donorRecovery(initialSpeed, scene.stand.getLastHeavyFinisherValue()),
                    scene.id + " unchanged startup heavy RECOVERY initialization");
            checks.check(initialWindup == StandStatFormulas.getHeavyAttackWindup(initialSpeed, current), scene.id + " unchanged initial WINDUP");
            recoveryAge = endAge = -1; previousPerformTick = previousRecoveryTick = -1;
            sampled = mutatedRecovery = false;
            userLifecycleTicks = 0;
            lastLifecycleTime = -1; lastResolveDuration = 3;
            performAge = scene.action.getPhase() == ActionPhase.PERFORM ? scene.inputStandAge : -1;
            expectedPerform = performAge >= 0 ? KingCrimsonThrowTimingFixture.donorPerform(initialSpeed, current) : -1;
            if (performAge >= 0) checks.check(scene.action.getAnimPhaseLength() == expectedPerform,
                    scene.id + " skipped WINDUP starts PERFORM with the donor light-recovery length");
            scene.duringWindup = () -> {
                if (kind == 3) snapshot(scene, 0.9F, 0.1F);
                if (kind == 4 || kind == 5) KingCrimsonThrowTimingFixture.stats(scene, kind == 4 ? 32 : 36, 0);
            };
            if (kind == 7) scene.beforePerform = () -> {
                KingCrimsonThrowTimingFixture.stats(scene, 14, 0.8F);
                snapshot(scene, 0.4F, 0.8F);
            };
            scene.listen(EntityTickEvent.Pre.class, event -> {
                if (event.getEntity() != scene.stand || scene.eventFailure != null) return;
                try {
                    if (kind == 2) {
                        // The isolated player has no network tick to invoke the real living-player lifecycle.
                        scene.premise(!scene.level.getServer().getConnection().getConnections().contains(scene.user.connection.getConnection()),
                                "Owned synthetic connection has no server network driver");
                        long time = scene.level.getGameTime();
                        scene.premise(lastLifecycleTime < 0 || time == lastLifecycleTime + 1, "Exactly one lifecycle call per consecutive Stand world tick");
                        var effect = scene.user.getEffect(ModStatusEffects.RESOLVE);
                        int duration = effect == null ? 0 : effect.getDuration();
                        scene.premise(duration == lastResolveDuration, "No independent effect-aging driver between owned calls");
                        int age = scene.user.tickCount;
                        scene.user.doTick(); userLifecycleTicks++;
                        effect = scene.user.getEffect(ModStatusEffects.RESOLVE);
                        lastResolveDuration = effect == null ? 0 : effect.getDuration(); lastLifecycleTime = time;
                        scene.premise(lastResolveDuration == Math.max(duration - 1, 0), "Actual player lifecycle decremented finite Resolve once");
                        KingCrimsonThrowGameTestFixture.log("RESOLVE-LIFECYCLE " + scene.id + " tick=" + userLifecycleTicks
                                + " worldTime=" + time + " calls=1 userAge=" + age + "->" + scene.user.tickCount
                                + " duration=" + duration + "->" + lastResolveDuration + " order=StandPre-before-native-refresh-and-actionPost");
                    }
                    before = scene.action.getPhase(); tickSpawns = scene.spawns.size(); tickStamina = scene.power.getStamina();
                    if (before == ActionPhase.PERFORM) {
                        int tick = (int) scene.action.getPhaseTick();
                        checks.check(tick > previousPerformTick, scene.id + " PERFORM clock advances without restart");
                        previousPerformTick = tick;
                    }
                    if (before == ActionPhase.RECOVERY) {
                        int tick = (int) scene.action.getPhaseTick();
                        checks.check(tick > previousRecoveryTick, scene.id + " RECOVERY clock advances without restart");
                        previousRecoveryTick = tick;
                    }
                } catch (RuntimeException error) { scene.eventFailure = error; }
            });
            scene.listen(EntityTickEvent.Post.class, event -> {
                if (event.getEntity() != scene.stand || scene.eventFailure != null) return;
                try {
                    ActionPhase phase = scene.action.getPhase();
                    if (phase == ActionPhase.PERFORM && performAge < 0) {
                        performAge = scene.stand.tickCount;
                        expectedPerform = KingCrimsonThrowTimingFixture.donorPerform(scene.stand.getAttackSpeed(), scene.stand.getFinisherMeter());
                        checks.check(scene.action.getAnimPhaseLength() == expectedPerform, scene.id + " closed fresh PERFORM sampling retained");
                    }
                    if (phase == ActionPhase.PERFORM) checks.check(scene.action.getAnimPhaseLength() == expectedPerform,
                            scene.id + " PERFORM length stays frozen");
                    if (before == ActionPhase.PERFORM && previousPerformTick > 0) checks.check(scene.spawns.size() == tickSpawns
                            && scene.power.getStamina() == tickStamina, scene.id + " later PERFORM has no repeated emission/debit");
                    if (!sampled && scene.performed && (phase == ActionPhase.RECOVERY || phase == null)) {
                        sampled = true; recoveryAge = scene.stand.tickCount;
                        recoverySpeed = scene.stand.getAttackSpeed(); recoveryLastHeavy = scene.stand.getLastHeavyFinisherValue();
                        expectedRecovery = KingCrimsonThrowTimingFixture.donorRecovery(recoverySpeed, recoveryLastHeavy);
                        checks.check(scene.action.phasesLength.getFloat(ActionPhase.RECOVERY) == expectedRecovery,
                                scene.id + " entry heavy getter/global operand expected=" + expectedRecovery + " actual=" + scene.action.phasesLength.getFloat(ActionPhase.RECOVERY));
                        checks.check((phase == ActionPhase.RECOVERY) == (expectedRecovery > 0), scene.id + " native zero/nonpositive RECOVERY skip semantics");
                        if (phase == ActionPhase.RECOVERY) checks.check(scene.action.getAnimPhaseLength() == expectedRecovery,
                                scene.id + " entry animation cache matches heavy duration");
                        checks.check(recoveryAge - performAge == expectedPerform, scene.id + " PERFORM elapsed duration unchanged");
                        if (kind == 2) {
                            scene.premise(userLifecycleTicks >= 3 && !scene.user.hasEffect(ModStatusEffects.RESOLVE)
                                    && recoverySpeed != initialSpeed, "Actual player lifecycle expired Resolve before entry getter refresh");
                            scene.premise(scene.preStamina == initialStamina && scene.preGain == 0, "No invented WINDUP regeneration");
                            checks.check(expectedRecovery != initialRecovery, scene.id + " ordinary Resolve-expiry path discriminates admission cache");
                        }
                        if (kind == 3) checks.check(expectedRecovery != KingCrimsonThrowTimingFixture.donorRecovery(recoverySpeed, 0.1F)
                                && expectedRecovery != KingCrimsonThrowTimingFixture.donorRecovery(recoverySpeed, scene.stand.getFinisherMeter()),
                                scene.id + " global last-heavy differs from startup/current finisher");
                        KingCrimsonThrowGameTestFixture.log("RECOVERY-ENTRY " + scene.id + " speed=" + recoverySpeed + " globalLast=" + recoveryLastHeavy
                                + " current=" + scene.stand.getFinisherMeter() + " initialSpeed=" + initialSpeed + " initialRecovery=" + initialRecovery
                                + " expected=" + expectedRecovery + " phase=" + phase + " age=" + recoveryAge);
                    }
                    if (sampled && phase == ActionPhase.RECOVERY) {
                        checks.check(scene.action.phasesLength.getFloat(ActionPhase.RECOVERY) == expectedRecovery
                                && scene.action.getAnimPhaseLength() == expectedRecovery, scene.id + " RECOVERY remains fixed within phase");
                        if (!mutatedRecovery) {
                            mutatedRecovery = true;
                            KingCrimsonThrowTimingFixture.stats(scene, 9, 0.99F);
                            snapshot(scene, 0.05F, 0.99F);
                        }
                    }
                    if (sampled && phase == null && endAge < 0) {
                        endAge = scene.stand.tickCount;
                        checks.check(endAge - recoveryAge == Math.max(expectedRecovery, 0), scene.id + " natural heavy RECOVERY elapsed duration expected=" + expectedRecovery
                                + " actual=" + (endAge - recoveryAge));
                    }
                } catch (RuntimeException error) { scene.eventFailure = error; }
            });
        }

        @Override boolean verify() {
            if (endAge < 0) return false;
            scene.premise(scene.performed && sampled && scene.stand.tickCount > scene.inputStandAge, "Natural registered action reached all phase entries");
            if (initialWindup > 0) scene.premise(scene.windupChanged, "Native WINDUP tick executed fixture mutation");
            scene.checkPayment(checks, true);
            int count = triple ? 3 : 1;
            checks.check(scene.spawns.size() == count && scene.postAmmo == scene.preAmmo - count, scene.id + " exactly one emitted batch and ammo debit");
            checks.check((kind == 2 ? scene.preCooldown == 0 : scene.preCooldown > 0)
                    && scene.preCooldown == scene.postCooldown, scene.id + " actual finite/Resolve admission cooldown untouched at perform");
            checks.check(scene.action.phasesLength.getFloat(ActionPhase.WINDUP) == initialWindup, scene.id + " initial WINDUP length unchanged by later getters");
            checks.check(scene.action.phasesLength.getFloat(ActionPhase.PERFORM) == expectedPerform, scene.id + " closed PERFORM length unchanged by RECOVERY mutation");
            return true;
        }
        @Override void release() {}
    }

    private static void snapshot(KingCrimsonThrowGameTestFixture scene, float saved, float current) {
        scene.stand.setFinisherMeter(saved);
        scene.stand.setHeavyPunchFinisher();
        scene.stand.setFinisherMeter(current);
    }

    private static void identity(GameTestHelper helper, Class<?> role) {
        for (Class<?> type : new Class<?>[] {StandPower.class, StandEntity.class, EntityActionInstance.class, AbilityInput.class,
                ActionInputBuffer.class, StandAbilityStamina.class, StandStatFormulas.class, ResolveModeEffect.class,
                KingCrimsonProjectileThrowAbility.class, KingCrimsonTripleProjectileThrowAbility.class,
                KingCrimsonThrowGameTestFixture.class, KingCrimsonThrowTimingFixture.class, role}) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                helper.assertTrue(stream != null, "Actual class resource " + type.getName());
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                String expected = System.getProperty("kc.throw.expected." + type.getName());
                var source = type.getProtectionDomain().getCodeSource();
                helper.assertTrue(source != null && (!Boolean.getBoolean("kc.throw.bound") || expected != null)
                        && (expected == null || expected.equalsIgnoreCase(actual)), "Bound native identity " + type.getName());
                KingCrimsonThrowGameTestFixture.log("RECOVERY-IDENTITY " + type.getName() + " sha256=" + actual
                        + " source=" + source.getLocation().toURI().toASCIIString());
            } catch (Exception error) { throw new IllegalStateException("Native recovery identity", error); }
        }
    }
}
