package rotp.addons.cmoon.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.CenterOfGravityAbility;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.JojoModConfig;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonGravityDurationGameTests {
    private static Suite active;
    private CMoonGravityDurationGameTests() {}

    @GameTest(template = "empty", batch = "cmCenterDuration", timeoutTicks = 1500)
    public static void admittedCenterRenewsFieldFor100NaturalTicksAndKeepsPayment(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CMoonAddon.class, CenterOfGravityAbility.class,
                CenterOfGravityAbility.CenterAction.class, CMoonStandEntity.class, StandPower.class,
                StandAbilityStamina.class, EntityActionInstance.class, AbilityInput.class,
                JojoModConfig.class, GravityTestSupport.class, CMoonGravityDurationGameTests.class);
        var suite = new Suite(helper);
        suite.listen();
        helper.onEachTick(suite::tick);
    }

    @AfterBatch(batch = "cmCenterDuration")
    public static void releaseDuration(ServerLevel level) { if (active != null) active.close(); }

    private enum Kind {
        FINITE100(100), ODD101(101), FINITE600(600), RESOLVE_EXPIRES(100),
        CREATIVE0(0), CONFIG_OFF0(0), OWNER_LOST600(600), CANCEL_CHARGE100(100);
        final float budget;
        Kind(float budget) { this.budget = budget; }
        boolean creative() { return this == CREATIVE0; }
        boolean configOff() { return this == CONFIG_OFF0; }
        boolean infinite() { return creative() || configOff(); }
        boolean cancel() { return this == CANCEL_CHARGE100; }
        boolean ownerLost() { return this == OWNER_LOST600; }
    }

    private record Field(long worldTick, int nativeStandTick, int phaseTick, float effective,
            float stored, Vec3 velocity, int duration, int amplifier, boolean ambient,
            boolean visible, boolean icon, boolean impulse, boolean marked) {}

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        RuntimeException observerFailure;
        int index, checks;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping CM duration suite");
            this.helper = helper;
            active = this;
            helper.testInfo.addListener(this);
        }

        void listen() {
            Consumer<EntityTickEvent.Pre> pre = event -> {
                if (scene != null && !closed) try { scene.pre(event.getEntity()); }
                catch (RuntimeException failure) { observerFailure = failure; }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene != null && !closed) try { scene.post(event.getEntity()); }
                catch (RuntimeException failure) { observerFailure = failure; }
            };
            Consumer<MobEffectEvent.Added> effect = event -> {
                if (scene != null && !closed) try { scene.field(event); }
                catch (RuntimeException failure) { observerFailure = failure; }
            };
            listeners.addAll(List.of(pre, post, effect));
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Added.class, effect);
        }

        void tick() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == Kind.values().length) {
                        GravityTestSupport.log("SUMMARY role=duration scenes=" + index + " checks=" + checks
                                + " failures=" + failures.size() + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "CM admitted duration failed; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, Kind.values()[index++]);
                }
                scene.tick();
                helper.assertTrue(++scene.ticks < 170, "Natural CM duration scene timeout");
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void check(boolean passed, String message) {
            checks++;
            String detail = scene.kind + ": " + message;
            GravityTestSupport.log((passed ? "PASS " : "FAIL ") + detail);
            if (!passed) failures.add(detail);
        }

        void close() {
            if (closed) return;
            closed = true;
            listeners.forEach(NeoForge.EVENT_BUS::unregister);
            listeners.clear();
            if (scene != null) scene.close();
            active = null;
            GravityTestSupport.log("DURATION-CLEANUP actors=0 listeners=0 configRestored=true chunkRestored=true");
        }
        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { close(); }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Kind kind;
        final GravityTestSupport.Actor actor;
        final Cow target;
        final Ability ability;
        final List<Field> fields = new ArrayList<>();
        final List<String> debitTimeline = new ArrayList<>();
        EntityActionInstance action;
        long pressed = -1, entry = -1, ended = -1, ownerRemoved = -1, resolveExpired = -1;
        float preUserStamina, zeroGainSamples;
        int ticks, previousUserTick = -1, previousStandTick = -1;
        boolean released, done, closed, gainZero = true, destroyZero = true;
        boolean oddBeforeDebit, oddAfterDebit, noFreshAfterEnd = true;

        Scene(Suite suite, Kind kind) {
            this.suite = suite;
            this.kind = kind;
            GravityTestSupport.Actor ownedActor = null;
            Cow ownedTarget = null;
            try {
                actor = ownedActor = new GravityTestSupport.Actor(suite.helper, kind.creative(), kind.configOff(),
                        kind == Kind.RESOLVE_EXPIRES, 70);
                ability = actor.power.getAbility(CMoonAddon.CENTER_OF_GRAVITY);
                actor.premise(ability != null && ability.getClass() == CenterOfGravityAbility.class,
                        "actual registered Center ability");
                target = ownedTarget = new Cow(EntityType.COW, actor.level);
                target.setNoAi(true);
                target.setNoGravity(true);
                target.noPhysics = true;
                Vec3 pos = actor.origin.add(2, 0, 0);
                target.moveTo(pos.x, pos.y, pos.z, 0, 0);
                actor.premise(actor.level.addFreshEntity(target), "actual non-Stand field recipient");
            } catch (RuntimeException | Error failure) {
                if (ownedTarget != null) try { ownedTarget.discard(); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                if (ownedActor != null) ownedActor.closeAfterFailure(failure);
                throw failure;
            }
        }

        void tick() {
            long now = actor.level.getGameTime();
            if (pressed < 0) { if (ticks >= 3) press(); return; }
            if (!released && (kind.cancel() ? now - pressed >= 2 : entry >= 0)) {
                AbilityInput.keyRelease(GravityTestSupport.KEY, actor.user);
                released = true;
            }
            if (kind.ownerLost() && ownerRemoved < 0 && fields.size() >= 70) {
                ownerRemoved = now;
                actor.user.discard();
                GravityTestSupport.log("DURATION-OWNER-REMOVED worldTick=" + now + " fields=" + fields.size()
                        + " lastPhase=" + action.getPhase() + " lastPhaseTick=" + action.getPhaseTick());
            }
            if (ended >= 0 && now >= ended + 22) { finish(); done = true; }
        }

        void press() {
            actor.premise(actor.user.tickCount >= 3 && actor.stand.tickCount >= 3
                    && !actor.power.isPausedInStoppedTime(), "actual native user/Stand ticks before input");
            actor.debitTo(kind.budget);
            var checked = actor.resolved(ability);
            actor.premise(checked.conditionCheck.isPositive()
                    && AbilityInput.withConditionCheck(checked, actor.user, InputMethod.HOLD),
                    "actual admitted checked HOLD at literal getter floor");
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(GravityTestSupport.KEY, checked.ability, actor.user, null, InputMethod.HOLD,
                    0, buffer, ability.getAbilityId());
            action = actor.stand.getCurStandAction();
            actor.premise(buffer.isActionSuccess && action != null
                    && action.getClass() == CenterOfGravityAbility.CenterAction.class && action.ability == ability
                    && action.getPhase() == ActionPhase.BUTTON_CHARGE
                    && actor.power.getAbilityCooldown(ability.name()) == 0,
                    "real charge factory, no manufactured PERFORM or keyPress cooldown");
            pressed = actor.level.getGameTime();
            GravityTestSupport.log("DURATION-PRESS kind=" + kind + " worldTick=" + pressed
                    + " nativeUserTick=" + actor.user.tickCount + " nativeStandTick=" + actor.stand.tickCount
                    + " stored=" + actor.stored() + " effective=" + actor.power.getStamina()
                    + " infinite=" + actor.power.isStaminaInfinite() + " Resolve=" + actor.user.hasEffect(ModStatusEffects.RESOLVE)
                    + " unlock=" + actor.power.getResolveLevel() + " gain=" + actor.power.getStaminaTickGain()
                    + " DestroyRegen=" + actor.user.getPersistentData().getInt(CMoonAddon.MOD_ID + ":DestroyStaminaRegenTicks"));
        }

        void pre(Entity entity) {
            if (action == null || closed || done || entity != actor.user || actor.user.isRemoved()) return;
            preUserStamina = actor.power.getStamina();
            if (ended < 0) {
                gainZero &= actor.power.getStaminaTickGain() == 0;
                destroyZero &= actor.user.getPersistentData().getInt(CMoonAddon.MOD_ID + ":DestroyStaminaRegenTicks") == 0;
                zeroGainSamples++;
            }
        }

        void post(Entity entity) {
            if (action == null || closed || done) return;
            long now = actor.level.getGameTime();
            if (entity == actor.user && !actor.user.isRemoved()) {
                actor.premise(previousUserTick < 0 || actor.user.tickCount == previousUserTick + 1,
                        "consecutive natural user ticks, no extra doTick/power tick");
                previousUserTick = actor.user.tickCount;
                if (ended < 0) debitTimeline.add(now + ":" + preUserStamina + "->" + actor.power.getStamina());
                if (kind == Kind.RESOLVE_EXPIRES && resolveExpired < 0 && !actor.user.hasEffect(ModStatusEffects.RESOLVE))
                    resolveExpired = now;
            }
            if (entity == actor.stand) {
                actor.premise(previousStandTick < 0 || actor.stand.tickCount == previousStandTick + 1,
                        "consecutive natural Stand ticks, no action/component driver");
                previousStandTick = actor.stand.tickCount;
                EntityActionInstance current = actor.stand.getCurStandAction();
                if (entry < 0 && current == action && action.getPhase() == ActionPhase.PERFORM) {
                    entry = now;
                    GravityTestSupport.log("DURATION-PER-ENTRY kind=" + kind + " worldTick=" + now
                            + " phaseTick=" + action.getPhaseTick() + " gain=" + actor.power.getStaminaTickGain());
                }
                if (kind == Kind.ODD101 && fields.size() == 51) oddAfterDebit = actor.power.getStamina() == 0;
                if (ended < 0 && (current != action || actor.stand.isRemoved() || action.isOver())) {
                    ended = now;
                    GravityTestSupport.log("DURATION-END kind=" + kind + " worldTick=" + now + " entry=" + entry
                            + " elapsed=" + (entry < 0 ? -1 : now - entry) + " fields=" + fields.size()
                            + " stored=" + actor.stored() + " effective=" + actor.power.getStamina()
                            + " nativeUserTick=" + actor.user.tickCount + " nativeStandTick=" + actor.stand.tickCount
                            + " removed=" + actor.stand.isRemoved());
                }
            }
        }

        void field(MobEffectEvent.Added event) {
            if (event.getEntity() != target || !event.getEffectInstance().is(MobEffects.MOVEMENT_SLOWDOWN)) return;
            actor.premise(action != null && action.getPhase() == ActionPhase.PERFORM
                    && actor.stand.getCurStandAction() == action, "field emitted by actual current Center PERFORM");
            if (ended >= 0) noFreshAfterEnd = false;
            MobEffectInstance effect = event.getEffectInstance();
            Field field = new Field(actor.level.getGameTime(), actor.stand.tickCount, (int) action.getPhaseTick(),
                    actor.power.getStamina(), actor.stored(), target.getDeltaMovement(), effect.getDuration(),
                    effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon(),
                    target.hasImpulse, target.hurtMarked);
            fields.add(field);
            if (kind == Kind.ODD101 && field.phaseTick() == 50) oddBeforeDebit = field.effective() == 1;
            if (field.phaseTick() == 0 || field.phaseTick() == 49 || field.phaseTick() == 50
                    || field.phaseTick() == 70 || field.phaseTick() == 99)
                GravityTestSupport.log("DURATION-FIELD kind=" + kind + " sample=" + field);
        }

        boolean at(int phaseTick) { return fields.stream().anyMatch(field -> field.phaseTick() == phaseTick); }
        Field sample(int phaseTick) { return fields.stream().filter(field -> field.phaseTick() == phaseTick).findFirst().orElse(null); }

        void finish() {
            boolean full = !kind.cancel() && !kind.ownerLost();
            int expectedFields = kind.cancel() ? 0 : kind.ownerLost() ? 70 : 100;
            suite.check(kind.cancel() ? entry < 0 : entry - pressed == 10, "natural ten-tick charge or real early cancellation");
            suite.check(action.phasesLength.getFloat(ActionPhase.PERFORM) == 100, "registered configured PERFORM remains100");
            suite.check(fields.size() == expectedFields, "renewed field calls expected=" + expectedFields + " actual=" + fields.size());
            // 1.16's Stand task ran the perform from the Stand's tick after the fire (entry is seen at the fire's
            // end), so the 100th perform tick, where it ends, is 100 ticks after entry
            suite.check(kind.cancel() ? ended - pressed <= 3 : kind.ownerLost() ? ended <= ownerRemoved + 1
                    : ended - entry == 100, "natural admitted100 expiry or safe cancellation/owner loss");
            suite.check(at(0) == !kind.cancel(), "first actual PERFORM field, not a manual perform-start call");
            suite.check(at(49) == !kind.cancel(), "field remains renewed at phase49");
            suite.check(at(50) == !kind.cancel(), "field remains renewed when finite100 can no longer pay2");
            suite.check(at(70) == full, "field renewed aroundPER70, not the old twenty-tick Slowness tail");
            suite.check(at(99) == full, "field renewed on last admitted PERFORM tick99");
            Field first = sample(0), second = sample(1);
            suite.check(kind.cancel() ? fields.isEmpty() : first != null && second != null
                    && (kind.infinite() ? first.effective() == second.effective()
                            : first.effective() == kind.budget && first.effective() - second.effective() == 2),
                    "real queued2 debit applied by natural user tick; genuine infinite getter unchanged");
            suite.check(kind != Kind.ODD101 || oddBeforeDebit && oddAfterDebit,
                    "odd budget1 field-before-debit, then failed debit clamps0 without ending");
            suite.check(fields.stream().allMatch(field -> field.duration() == 20 && field.amplifier() == 25
                    && !field.ambient() && !field.visible() && field.icon() && !field.impulse() && !field.marked()
                    && field.velocity().equals(new Vec3(0, 0.03D, 0))), "donor field flags/motion and fresh Slowness20/amp25 retained");
            suite.check(gainZero && destroyZero && zeroGainSamples > 0
                    && (kind != Kind.RESOLVE_EXPIRES || resolveExpired > entry && resolveExpired < entry + 70),
                    "gain0/no Destroy regen and genuine Resolve expiry during admitted action");
            suite.check(noFreshAfterEnd && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                    && !actor.stand.isCounterActive()
                    && (kind.cancel() ? actor.power.getAbilityCooldown(ability.name()) == 0
                            : !kind.ownerLost() || actor.stand.isRemoved()),
                    "no fresh field after exit, bounded Slowness tail expires, cancellation CD0/owner removal safe");
            GravityTestSupport.log("DURATION-TIMELINE kind=" + kind + " userDebits=" + debitTimeline
                    + " gainSamples=" + zeroGainSamples + " ResolveExpired=" + resolveExpired
                    + " ownerRemoved=" + ownerRemoved + " checks=14");
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try { target.discard(); }
            catch (RuntimeException | Error failure) {
                actor.closeAfterFailure(failure);
                throw failure;
            }
            actor.close();
        }
    }
}
