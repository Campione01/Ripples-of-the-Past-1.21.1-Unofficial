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
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.CenterOfGravityAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/**
 * 1.16 CenterOfGravity (holdToFire(10), no recovery): once fired, any other Stand action ends the field at once and
 * its cancelable action sound with it. Each held target gets one still spark per tick, and a player in the field is
 * only slowed: the server never resyncs that player's own velocity.
 */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonCenterCancelGameTests {
    private static final short PUNCH_KEY = 212;
    private static Scene active;

    private CMoonCenterCancelGameTests() {}

    @GameTest(template = "empty", batch = "cmCenterCancel", timeoutTicks = 300)
    public static void anotherStandActionEndsTheFieldAtOnce(GameTestHelper helper) {
        GravityTestSupport.identity(helper, CenterOfGravityAbility.class, CenterOfGravityAbility.CenterAction.class,
                CMoonCenterCancelGameTests.class);
        Scene scene = new Scene(helper);
        helper.onEachTick(scene::tick);
    }

    @AfterBatch(batch = "cmCenterCancel")
    public static void release(ServerLevel level) {
        if (active != null) active.close();
    }

    private static final class Scene implements GameTestListener {
        final GameTestHelper helper;
        final GravityTestSupport.Actor actor;
        final Cow target;
        final RecordingServerPlayer watcher;
        final List<String> failures = new ArrayList<>();
        final Consumer<MobEffectEvent.Added> fieldListener = this::field;
        EntityActionInstance center;
        int ticks, performTicks, fieldsAfterCancel, cancelTick = -1;
        boolean cancelled, closed;

        Scene(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping CM Center cancel scene");
            this.helper = helper;
            actor = new GravityTestSupport.Actor(helper, false, false, false);
            Cow cow = null;
            RecordingServerPlayer player = null;
            try {
                cow = new Cow(EntityType.COW, actor.level);
                cow.setNoAi(true);
                cow.setNoGravity(true);
                cow.noPhysics = true;
                Vec3 pos = actor.origin.add(2, 0, 0);
                cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
                actor.premise(actor.level.addFreshEntity(cow), "actual non-Stand field recipient");
                player = new RecordingServerPlayer(helper, "cm-center-watcher", actor.origin.add(0, 0, 3));
            }
            catch (RuntimeException | Error failure) {
                if (cow != null) cow.discard();
                if (player != null) player.close();
                actor.closeAfterFailure(failure);
                throw failure;
            }
            target = cow;
            watcher = player;
            active = this;
            helper.testInfo.addListener(this);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Added.class, fieldListener);
        }

        void tick() {
            if (closed) return;
            try {
                ticks++;
                if (center == null) {
                    if (ticks >= 3) pressCenter();
                    return;
                }
                if (!cancelled) {
                    trackStand();
                    if (center.getPhase() == ActionPhase.PERFORM && ++performTicks == 5) {
                        checkField();
                        cancel();
                    }
                    helper.assertTrue(ticks < 80, "Center never reached its fifth PERFORM tick");
                    return;
                }
                if (ticks - cancelTick >= 5) {
                    check(fieldsAfterCancel == 0, "the field went on after the punch replaced Center: "
                            + fieldsAfterCancel + " fresh Slowness fields");
                    finish();
                }
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        void pressCenter() {
            Ability ability = actor.power.getAbility(CMoonAddon.CENTER_OF_GRAVITY);
            var checked = actor.resolved(ability);
            actor.premise(checked.conditionCheck.isPositive()
                    && AbilityInput.withConditionCheck(checked, actor.user, InputMethod.HOLD), "Center input admitted");
            BufferingState buffering = BufferingState.clickOnly();
            AbilityInput.keyPress(GravityTestSupport.KEY, checked.ability, actor.user, null, InputMethod.HOLD,
                    0, buffering, ability.getAbilityId());
            center = actor.stand.getCurStandAction();
            actor.premise(buffering.isActionSuccess && center instanceof CenterOfGravityAbility.CenterAction
                    && center.getPhase() == ActionPhase.BUTTON_CHARGE, "real Center charge started");
            watcher.clear();
        }

        // the watcher's client would see the Stand, so it hears whatever the server sends for the Stand
        void trackStand() {
            ChunkMap.TrackedEntity tracked = actor.level.getChunkSource().chunkMap.entityMap.get(actor.stand.getId());
            actor.premise(tracked != null, "the Stand is tracked");
            tracked.seenBy.add(watcher.player.connection);
        }

        void checkField() {
            List<ClientboundLevelParticlesPacket> sparks = watcher.particles().stream()
                    .filter(packet -> packet.getParticle().getType() == CMoonAddon.HAMON_SPARK_PURPLE.get())
                    .toList();
            check(sparks.size() >= 5 && sparks.stream().allMatch(packet -> packet.getCount() == 0
                            && packet.getXDist() == 0 && packet.getYDist() == 0 && packet.getZDist() == 0
                            && packet.getMaxSpeed() == 0),
                    "1.16 \"/particle\" defaults: one still spark per target and tick, got "
                            + sparks.stream().map(packet -> packet.getCount() + "@" + packet.getXDist()).toList());
            check(sparks.stream().anyMatch(packet -> packet.getX() == target.getX() && packet.getZ() == target.getZ()
                            && Math.abs(packet.getY() - (target.getY() + 0.3D)) < 0.1D),
                    "no spark at the held target's position + 0.3");
            long ownMotion = watcher.packets(ClientboundSetEntityMotionPacket.class).stream()
                    .filter(packet -> packet.getId() == watcher.player.getId())
                    .count();
            check(ownMotion == 0 && !watcher.player.hurtMarked,
                    "1.16 never resynced a held player's own velocity; motion packets to that player: " + ownMotion);
            check(watcher.standSounds(CMoonAddon.C_MOON_CENTER_OF_GRAVITY_SOUND.get()).isEmpty(),
                    "the server sent the Center sound as a lingering Stand sound nothing can stop");
            check(!((CenterOfGravityAbility.CenterAction) center).isPerformSoundOver(),
                    "the Center sound stop condition fired during PERFORM");
        }

        void cancel() {
            Ability punch = actor.power.getAbility(CMoonAddon.PUNCH);
            var checked = actor.resolved(punch);
            actor.premise(AbilityInput.withConditionCheck(checked, actor.user, InputMethod.CLICK), "punch input admitted");
            String centerName = center.ability instanceof Ability ability ? ability.name() : CMoonAddon.CENTER_OF_GRAVITY;
            int cooldown = actor.power.getAbilityCooldown(centerName);
            BufferingState buffering = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(PUNCH_KEY, checked.ability, actor.user, null, InputMethod.CLICK,
                    0, buffering, punch.getAbilityId());
            EntityActionInstance now = actor.stand.getCurStandAction();
            check(buffering.isActionSuccess && now != null && now.ability == checked.ability,
                    "1.16 isCancelable: the punch must replace the performing field at once, not wait in the buffer"
                            + " (success=" + buffering.isActionSuccess + " current=" + now + ")");
            check(center.isOver() && ((CenterOfGravityAbility.CenterAction) center).isPerformSoundOver(),
                    "the cut-short Center action is still running, so its sound would not stop");
            check(cooldown > 0 && actor.power.getAbilityCooldown(centerName) == cooldown,
                    "1.16 keeps the cooldown set when the field fired: " + cooldown + " -> "
                            + actor.power.getAbilityCooldown(centerName));
            cancelled = true;
            cancelTick = ticks;
        }

        void field(MobEffectEvent.Added event) {
            if (cancelled && !closed && event.getEntity() == target
                    && event.getEffectInstance().is(MobEffects.MOVEMENT_SLOWDOWN)) {
                fieldsAfterCancel++;
            }
        }

        void check(boolean passed, String message) {
            if (!passed) failures.add(message);
        }

        void finish() {
            close();
            helper.assertTrue(failures.isEmpty(), "CM-CENTER-CANCEL failed: " + failures);
            helper.succeed();
        }

        void close() {
            if (closed) return;
            closed = true;
            active = null;
            NeoForge.EVENT_BUS.unregister(fieldListener);
            Throwable failure = null;
            try { AbilityInput.keyRelease(PUNCH_KEY, actor.user); }
            catch (RuntimeException | Error error) { failure = error; }
            try { target.discard(); }
            catch (RuntimeException | Error error) { if (failure == null) failure = error; }
            try { watcher.close(); }
            catch (RuntimeException | Error error) { if (failure == null) failure = error; }
            if (failure != null) {
                actor.closeAfterFailure(failure);
                if (failure instanceof RuntimeException error) throw error;
                throw (Error) failure;
            }
            actor.close();
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { close(); }
    }
}
