package rotp.addons.kingcrimson.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase4Spec;
import rotp.addons.kingcrimson.ability.KingCrimsonChopAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonDesperateEyejabAbility;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

final class KingCrimsonStrikeEffectFixture implements GameTestListener, AutoCloseable {
    enum Strike { CHOP, EYEJAB, IMPALE }
    record Added(Holder<MobEffect> holder, int ticks, int amplifier) {}
    final GameTestHelper helper;
    final Strike strike;
    final boolean eyejab;
    final String fix;
    final List<String> failures = new ArrayList<>();
    final List<Added> added = new ArrayList<>();
    KingCrimsonGroundAdmissionFixture scene;
    LivingEntity target;
    int index, ticks, checks, warmup;
    Runnable admission;
    boolean done, closed;
    RuntimeException eventFailure;

    KingCrimsonStrikeEffectFixture(GameTestHelper helper, boolean eyejab) {
        this(helper, eyejab ? Strike.EYEJAB : Strike.CHOP);
    }
    KingCrimsonStrikeEffectFixture(GameTestHelper helper, Strike strike) {
        this.helper = helper; this.strike = strike; eyejab = strike == Strike.EYEJAB;
        fix = switch (strike) { case CHOP -> "D-KC-CHOP-01"; case EYEJAB -> "D-KC-EYEJAB-01"; case IMPALE -> "KC-IMPALE-SLOWNESS"; };
    }
    void start() { helper.testInfo.addListener(this); helper.onEachTick(this::tick); }
    void tick() {
        if (closed) return;
        try {
            if (eventFailure != null) throw eventFailure;
            if (done) { scene.close(); scene = null; done = false; }
            if (scene == null) {
                if (index == 6) {
                    close(); log(fix + " SUMMARY scenes=6 checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                    helper.assertTrue(failures.isEmpty(), fix + " behavioral failures=" + failures.size()); helper.succeed(); return;
                }
                prepare(index++); ticks = 0;
            }
            if (admission != null && warmup-- <= 0) {
                Runnable ready = admission; admission = null; ready.run(); ticks = 0;
            }
            helper.assertTrue(++ticks < 70, "Native effect scene deadline");
        } catch (RuntimeException | Error error) { close(); throw error; }
    }
    void prepare(int mode) {
        added.clear();
        boolean player = mode % 2 == 1, canceled = mode == 2 || mode == 3, shortStamina = mode >= 4;
        scene = new KingCrimsonGroundAdmissionFixture(helper, fix + "/" + mode, 1);
        var caster = scene.actor(scene.origin, scene.origin, 0, 1, 28);
        target = player ? scene.actor(scene.origin.add(0, 0, 1.5), scene.origin.add(0, 0, 10), 180, 1, 28).user()
                : scene.cow(scene.origin.add(0, 0, 1.5));
        warmup = player ? 65 : 0;
        int targetAge = target.tickCount;
        admission = () -> {
        scene.premise(!player || target.tickCount >= targetAge + 60,
                "Player target naturally ticked past sixty-tick spawn protection");
        if (eyejab) caster.user().setHealth(60);
        caster.stand().setFinisherMeter(0.75F);
        // 1.16 has no stamina condition on these strikes: an empty bar still strikes and pays at PERFORM.
        if (shortStamina) caster.power().setStamina(0);
        String baseName = switch (strike) { case CHOP -> "kingcrimson_chop"; case EYEJAB -> "heavy_punch"; case IMPALE -> "kingcrimson_impale"; };
        String expected = switch (strike) {
            case CHOP -> "kingcrimson_chop"; case EYEJAB -> "kingcrimson_desperate_eyejab"; case IMPALE -> "kingcrimson_impale"; };
        LivingComponentAction.getComponent(caster.user()).entityAim.setTarget(new ActionTarget(target));
        LivingComponentAction.getComponent(caster.stand()).entityAim.setTarget(new ActionTarget(target));
        var base = caster.power().getAbility(baseName);
        scene.premise(base != null, "Actual registered base " + baseName);
        var selected = caster.power().updateAvailableMoves().getContextVariationContainer(base);
        scene.premise(selected != null && selected.ability.name().equals(expected), "Owner-preserved registered context " + expected);
        boolean admitted = AbilityInput.withConditionCheck(selected, caster.user(), InputMethod.CLICK);
        check(admitted, scene.id + " native condition-checked input admitted=" + admitted
                + (shortStamina ? " with stamina " + caster.power().getStamina() : ""));
        scene.premise(admitted, "Positive input required for native hit controls");
        int[] attempts = {0}, cancellations = {0};
        scene.listen(MobEffectEvent.Added.class, event -> {
            if (event.getEntity() == target) {
                var effect = event.getEffectInstance();
                added.add(new Added(effect.getEffect(), effect.getDuration(), effect.getAmplifier()));
                log("EFFECT " + scene.id + " holder=" + BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value())
                        + " ticks=" + effect.getDuration() + " amplifier=" + effect.getAmplifier());
            }
        });
        // Once the impact is checked the target's own bleeding may hurt it before the scene closes.
        scene.listen(LivingIncomingDamageEvent.class, event -> {
            if (event.getEntity() == target && !done) {
                scene.premise(event.getSource().getEntity() == caster.stand()
                        || event.getSource().getEntity() == caster.user(), "Actual damage source is the owned Stand/user");
                attempts[0]++; if (canceled) { event.setCanceled(true); cancellations[0]++; }
            }
        });
        BufferingState buffer = BufferingState.clickOnly();
        var input = AbilityInput.keyPress(KingCrimsonGroundAdmissionFixture.ATTACK, selected.ability, caster.user(), null,
                InputMethod.CLICK, 0, buffer, base.getAbilityId());
        EntityActionInstance action = input != null && input.action instanceof EntityActionInstance value
                ? value : caster.stand().getCurStandAction();
        scene.premise(buffer.isActionSuccess && action != null && action.ability == selected.ability,
                "Registered keypress installed real strike action");
        int bodyAge = caster.stand().tickCount;
        scene.listen(EntityTickEvent.Post.class, event -> {
            if (event.getEntity() != caster.stand() || done || eventFailure != null || action.punchedTarget == null) return;
            try {
                scene.premise(caster.stand().tickCount > bodyAge && action.punchedTarget.getMainEntity() == target,
                        "Natural body tick hit the exact target");
                check(attempts[0] == 1, scene.id + " exactly one actual damage admission attempt=" + attempts[0]);
                if (canceled) {
                    check(cancellations[0] == 1 && target.getHealth() == 200 && added.isEmpty()
                            && target.getActiveEffects().isEmpty(), scene.id + " canceled hurt receives no effects");
                } else {
                    check(target.getHealth() < 200, scene.id + " admitted native hit damages actual target");
                    if (shortStamina) {
                        check(caster.power().getStamina() <= 0.01F && caster.power().getAbilityCooldown(expected) > 0,
                                scene.id + " the empty bar paid at PERFORM stays at 0: stamina=" + caster.power().getStamina()
                                        + " cooldown=" + caster.power().getAbilityCooldown(expected));
                    }
                    if (strike == Strike.IMPALE) {
                        // 1.16 KingCrimsonImpale: field_76421_d is Slowness, not Mining Fatigue
                        effect(MobEffects.MOVEMENT_SLOWDOWN, 140, 3);
                        effect(MobEffects.WEAKNESS, 140, 2);
                        effect(KingCrimsonAddon.GAPING_WOUND, 140, KingCrimsonConfig.impaleGapingWoundAmplifier());
                        effect(KingCrimsonAddon.BLEEDING, 140, 0);
                        check(!target.hasEffect(MobEffects.DIG_SLOWDOWN), scene.id + " no substituted Mining Fatigue");
                    } else if (eyejab) {
                        effect(MobEffects.BLINDNESS, 80, 9);
                        effect(MobEffects.MOVEMENT_SLOWDOWN, 80, 0);
                        boolean left = target.hasEffect(KingCrimsonAddon.HALF_BLINDNESS_LEFT);
                        boolean right = target.hasEffect(KingCrimsonAddon.HALF_BLINDNESS_RIGHT);
                        check(left != right, scene.id + " exactly one retained half-blindness side");
                        if (left || right) effect(left ? KingCrimsonAddon.HALF_BLINDNESS_LEFT : KingCrimsonAddon.HALF_BLINDNESS_RIGHT, 60, 0);
                        check(!target.hasEffect(MobEffects.WEAKNESS), scene.id + " no substituted Weakness");
                    } else {
                        effect(MobEffects.MOVEMENT_SLOWDOWN, 80, 3);
                        effect(ModStatusEffects.BLEEDING, 80, 1);
                        check(target.hasEffect(KingCrimsonAddon.MANGLED_BODY) == player, scene.id + " Mangled Body remains player-only");
                        if (player) effect(KingCrimsonAddon.MANGLED_BODY, 80, 0);
                        check(!target.hasEffect(MobEffects.DIG_SLOWDOWN), scene.id + " no substituted Mining Fatigue");
                    }
                }
                log("NATIVE-IMPACT " + scene.id + " registered=" + expected + " bodyTicks="
                        + (caster.stand().tickCount - bodyAge) + " player=" + player + " canceled=" + canceled);
                done = true;
            } catch (RuntimeException error) { eventFailure = error; }
        });
        };
    }
    void effect(Holder<MobEffect> holder, int duration, int amplifier) {
        var installed = target.getEffect(holder);
        boolean observed = added.stream().anyMatch(value -> value.holder.equals(holder) && value.ticks == duration && value.amplifier == amplifier);
        check(observed && installed != null && installed.getEffect().equals(holder)
                        && installed.getDuration() == duration && installed.getAmplifier() == amplifier,
                scene.id + " exact effect=" + BuiltInRegistries.MOB_EFFECT.getKey(holder.value()) + " duration=" + duration + " amplifier=" + amplifier);
        // 1.16 EffectInstance(effect, duration, amplifier, false, false): no particles, no HUD icon
        check(installed != null && !installed.isAmbient() && !installed.isVisible() && !installed.showIcon(),
                scene.id + " hidden effect=" + BuiltInRegistries.MOB_EFFECT.getKey(holder.value())
                        + " icon=" + (installed != null && installed.showIcon()));
    }
    void check(boolean passed, String detail) { checks++; log((passed ? "PASS " : "FAIL ") + detail); if (!passed) failures.add(detail); }
    @Override public void close() { if (closed) return; closed = true; if (scene != null) scene.close(); }
    @Override public void testStructureLoaded(GameTestInfo test) {}
    @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
    @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
    @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    static void log(String message) { JojoMod.getLogger().info("KC-STRIKE " + message); }
    static void identity(GameTestHelper helper, Class<?> fixture) {
        try {
            Class<?> strike = KingCrimsonChopAbility.class.getSuperclass();
            Class<?> action = null;
            for (Class<?> nested : strike.getDeclaredClasses()) if (nested.getSimpleName().equals("KingCrimsonStrikeAction")) action = nested;
            helper.assertTrue(action != null, "Native strike action exists");
            for (Class<?> type : new Class<?>[] {StandPower.class, StandEntity.class, EntityActionInstance.class, AbilityInput.class,
                    DamageSourceModified.class, strike, action, KingCrimsonChopAbility.class, KingCrimsonDesperateEyejabAbility.class,
                    KingCrimsonPhase4Spec.class, KingCrimsonGroundAdmissionFixture.class, KingCrimsonStrikeEffectFixture.class, fixture}) {
                try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                    helper.assertTrue(stream != null, "Native class bytes exist " + type.getName());
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                    String expected = System.getProperty("kc.strike.expected." + type.getName());
                    var source = type.getProtectionDomain().getCodeSource();
                    helper.assertTrue(source != null && (!Boolean.getBoolean("kc.strike.bound") || expected != null)
                            && (expected == null || hash.equalsIgnoreCase(expected)), "Native archive binding " + type.getName());
                    log("LOADED " + type.getName() + " source=" + source.getLocation() + " sha256=" + hash);
                }
            }
        } catch (Exception error) { throw new IllegalStateException("Native strike binding failed", error); }
    }
}
