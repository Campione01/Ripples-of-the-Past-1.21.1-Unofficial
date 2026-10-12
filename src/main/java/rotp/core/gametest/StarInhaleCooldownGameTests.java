package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.impl.stands.starplatinum.StarInhaleAbility;
import rotp.core.impl.stands.starplatinum.StarInhaleAbility.InhaleAbilityInstance;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionInstancePacket;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarInhaleCooldownGameTests {
    private static final short KEY_A = 119;
    private static final short KEY_B = 120;
    private static Suite active;

    private StarInhaleCooldownGameTests() {}

    private enum Stop { GUARD, ZOOM, RELEASE, TIMEOUT, STAMINA, FORCE, LOCKED, EMPTY_STAMINA, REFUSED_REPLACE, ROLLBACK }
    private record Spec(Stop stop, int ticks) {}

    @GameTest(template = "empty", timeoutTicks = 1000, batch = "star_inhale_cooldown")
    public static void registeredHoldCancellationCooldown(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            log("IDENTITY ability=" + sha(StarInhaleAbility.class) + " instance=" + sha(InhaleAbilityInstance.class)
                    + " fixture=" + sha(StarInhaleCooldownGameTests.class));
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = "star_inhale_cooldown")
    public static void releaseInhaleBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static List<Spec> cases() {
        return List.of(new Spec(Stop.LOCKED, 0), new Spec(Stop.EMPTY_STAMINA, 0),
                new Spec(Stop.GUARD, 0), new Spec(Stop.RELEASE, 0),
                new Spec(Stop.GUARD, 1), new Spec(Stop.GUARD, 7), new Spec(Stop.GUARD, 40),
                new Spec(Stop.ZOOM, 7), new Spec(Stop.RELEASE, 1), new Spec(Stop.RELEASE, 7),
                new Spec(Stop.RELEASE, 40), new Spec(Stop.TIMEOUT, 80), new Spec(Stop.STAMINA, 7),
                new Spec(Stop.FORCE, 7), new Spec(Stop.FORCE, 40),
                new Spec(Stop.REFUSED_REPLACE, 7), new Spec(Stop.ROLLBACK, 7), new Spec(Stop.ROLLBACK, 40));
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final List<Spec> specs = cases();
        Scene scene;
        RuntimeException observerFailure;
        int index;
        int checks;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping Inhale cooldown suites");
            this.helper = helper;
            level = helper.getLevel();
            helper.testInfo.addListener(this);
            active = this;
        }

        void listen() {
            Consumer<EntityTickEvent.Pre> pre = event -> {
                if (scene != null && !closed && event.getEntity() == scene.stand && scene.starveNextTick) {
                    scene.power.setStamina(0);
                    scene.starveNextTick = false;
                    scene.starved = true;
                }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene == null || closed || observerFailure != null || event.getEntity() != scene.stand) return;
                try { scene.post(); }
                catch (RuntimeException error) { observerFailure = error; }
            };
            Consumer<RipplesAbilityKeyPressEvent> refuse = event -> {
                if (scene != null && !closed && scene.refuseReplacement && event.getEntity() == scene.user
                        && event.ability == scene.power.getAbility("guard")) {
                    scene.refusedEvents++;
                    event.setCanceled(true);
                }
            };
            listeners.add(pre); listeners.add(post); listeners.add(refuse);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RipplesAbilityKeyPressEvent.class, refuse);
        }

        void tick() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == specs.size()) {
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "Inhale cooldown lifecycle failed; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, specs.get(index++));
                    scene.prepare();
                }
                helper.assertTrue(++scene.worldTicks < scene.spec.ticks + 25, "Native Inhale observation window did not complete: " + scene.id());
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }

        void check(boolean passed, String detail) {
            checks++;
            log((passed ? "PASS " : "FAIL ") + scene.id() + " " + detail);
            if (!passed) failures.add(scene.id() + ": " + detail);
        }

        void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            if (scene != null) scene.close();
            if (active == this) active = null;
            log("CLEANUP listeners=0 released=" + (active == null));
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Spec spec;
        final ServerLevel level;
        Player user;
        FakePlayer observer;
        RecordingConnection connection;
        StandPower power;
        StandType type;
        StandEntity stand;
        StarInhaleAbility ability;
        InhaleAbilityInstance action;
        int worldTicks;
        int actionPosts;
        int stopTicks;
        int expected;
        int afterStopPosts;
        int resumedUntil;
        int refusedEvents;
        int rollbackFailures;
        int cooldownAtFailedSend;
        boolean requested;
        boolean settled;
        boolean starveNextTick;
        boolean starved;
        boolean refuseReplacement;
        boolean failReplacementSend;
        boolean done;
        boolean closed;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.level; }
        String id() { return spec.stop + "-" + spec.ticks; }

        void prepare() {
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8,
                    chunk.getMiddleBlockZ() + 0.5);
            user = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            premise(level.addFreshEntity(user), "Survival owner added");
            observer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "InhaleObserver"));
            observer.setNoGravity(true);
            observer.moveTo(origin.x + 2, origin.y, origin.z - 2, 0, 0);
            connection = new RecordingConnection(this, observer);
            observer.connection = connection;
            premise(level.addFreshEntity(observer), "tracking observer added");
            connection.chunkSender.dropChunk(observer, observer.chunkPosition());
            power = PowerClass.STAND.attachGet(user);
            type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "registered Star Platinum granted");
            power.setResolveLevel(spec.stop == Stop.LOCKED ? 1 : 2);
            premise(type.summon(user, power), "registered Stand summoned");
            stand = power.getSummonedStandEntity();
            premise(stand != null && stand.getUser() == user, "Stand ownership");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            premise(connection.packets.stream().anyMatch(packet -> packet instanceof ClientboundAddEntityPacket add
                    && add.getId() == stand.getId()), "observer tracks real Stand");
            ability = (StarInhaleAbility) power.getAbility("inhale");
            premise(ability != null && ability.getCooldown(power, 7) > 0 && !user.isCreative(), "cooldown is enabled for actual registered ability");
            power.setStamina(spec.stop == Stop.EMPTY_STAMINA ? 0 : power.getMaxStamina());
            var selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            boolean admitted = AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD);
            if (spec.stop == Stop.LOCKED || spec.stop == Stop.EMPTY_STAMINA) {
                suite.check(!admitted && stand.getCurStandAction() == null && power.getAbilityCooldown("inhale") == 0,
                        "real early admission refusal creates no action/cooldown");
                done = true;
                return;
            }
            premise(admitted, "registered Inhale HOLD admission");
            BufferingState input = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY_A, selected.ability, user, null, InputMethod.HOLD, 0, input, ability.getAbilityId());
            premise(input.isActionSuccess && stand.getCurStandAction() instanceof InhaleAbilityInstance, "real registered held action");
            action = (InhaleAbilityInstance) stand.getCurStandAction();
            premise(action.getPhase() == ActionPhase.PERFORM && action.getFullTicksPassed() == 0
                    && action.phasesLength.getFloat(ActionPhase.PERFORM) == 80 && power.getAbilityCooldown("inhale") == 0,
                    "native initial phase/lifetime and no key-press cooldown");
            log("START " + id() + " action=" + action.id + " generation=" + action.networkGeneration()
                    + " owner=" + user.getUUID() + " phase=" + action.getPhase());
            if (spec.ticks == 0) requestStop();
        }

        void post() {
            if (done || closed) return;
            actionPosts++;
            if (!requested && action.getPhase() == ActionPhase.PERFORM
                    && Math.floor(action.getFullTicksPassed()) >= spec.ticks && spec.stop != Stop.TIMEOUT) requestStop();
            if ((spec.stop == Stop.ROLLBACK || spec.stop == Stop.REFUSED_REPLACE) && requested && !settled
                    && action.getPhase() == ActionPhase.PERFORM && action.getFullTicksPassed() >= resumedUntil) {
                stopTicks = (int) Math.floor(action.getFullTicksPassed());
                expected = ability.getCooldown(power, stopTicks);
                AbilityInput.keyRelease(KEY_A, user);
                observeSettlement("release after continued registered hold");
            }
            if (!settled && (spec.stop == Stop.TIMEOUT || starved || spec.stop == Stop.FORCE && requested)
                    && stand.getCurStandAction() != action) {
                if (spec.stop == Stop.TIMEOUT) { stopTicks = 80; expected = ability.getCooldown(power, stopTicks); }
                observeSettlement("native completion/clear");
            }
            if (settled && ++afterStopPosts >= 3) {
                suite.check(stand.getCurStandAction() != action, "native component clears the stopped action");
                int before = power.getAbilityCooldown("inhale");
                int totalBefore = power.getAbilityCooldownTotal("inhale");
                AbilityInput.keyRelease(KEY_A, user);
                suite.check(power.getAbilityCooldown("inhale") == before && power.getAbilityCooldownTotal("inhale") == totalBefore,
                        "late release/clear does not restart timer remaining=" + before + " total=" + totalBefore);
                if (expected > 1) suite.check(before < expected, "actual player power ticks decay the timer remaining=" + before + " initial=" + expected);
                log("END " + id() + " nativePosts=" + actionPosts + " held=" + stopTicks + " expected=" + expected
                        + " remaining=" + before + " refusedEvents=" + refusedEvents + " rollbackFailures=" + rollbackFailures);
                done = true;
            }
        }

        void requestStop() {
            requested = true;
            stopTicks = (int) Math.floor(action.getFullTicksPassed());
            expected = ability.getCooldown(power, stopTicks);
            premise(stopTicks == spec.ticks && stand.getCurStandAction() == action, "actual native held clock=" + stopTicks);
            if (spec.stop == Stop.GUARD || spec.stop == Stop.ZOOM) {
                pressReplacement(spec.stop == Stop.ZOOM ? "enhanced_eyesight" : "guard");
                suite.check(stand.getCurStandAction() != action && action.isOver(), "registered replacement clears old action identity");
                observeSettlement("registered replacement");
            }
            else if (spec.stop == Stop.RELEASE) {
                AbilityInput.keyRelease(KEY_A, user);
                observeSettlement("normal key release");
            }
            else if (spec.stop == Stop.STAMINA) starveNextTick = true;
            else if (spec.stop == Stop.FORCE) {
                action.forceStop();
                premise(action.isOver() && action.getPhaseTick() == stopTicks, "native forceStop retains actual elapsed phase clock");
            }
            else if (spec.stop == Stop.REFUSED_REPLACE) {
                refuseReplacement = true;
                boolean success = pressReplacement("guard");
                refuseReplacement = false;
                suite.check(!success && refusedEvents == 1 && stand.getCurStandAction() == action && !action.isOver()
                        && power.getAbilityCooldown("inhale") == 0, "canceled registered press leaves original hold and zero cooldown");
                resumedUntil = stopTicks + 3;
            }
            else if (spec.stop == Stop.ROLLBACK) {
                failReplacementSend = true;
                try { pressReplacement("guard"); premise(false, "expected actual replacement-packet failure did not occur"); }
                catch (InjectedSendFailure expectedFailure) { log("EXPECTED-FAULT " + id() + " " + expectedFailure.getMessage()); }
                finally { failReplacementSend = false; }
                suite.check(rollbackFailures == 1 && stand.getCurStandAction() == action && action.getPhase() == ActionPhase.PERFORM
                        && action.getFullTicksPassed() == stopTicks, "actual AbilityInput rollback restores original identity/phase/clock");
                suite.check(power.getAbilityCooldown("inhale") == 0 && power.getAbilityCooldownTotal("inhale") == 0,
                        "failed replacement has no premature cooldown failedSendSaw=" + cooldownAtFailedSend);
                resumedUntil = stopTicks + 3;
            }
        }

        boolean pressReplacement(String name) {
            Ability replacement = power.getAbility(name);
            var selected = power.updateAvailableMoves().getContextVariationContainer(replacement);
            premise(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD), "registered replacement admission " + name);
            BufferingState input = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY_B, selected.ability, user, null, InputMethod.HOLD, 0, input, replacement.getAbilityId());
            return input.isActionSuccess;
        }

        void observeSettlement(String reason) {
            settled = true;
            suite.check(action.isOver(), "registered stop ends the old action: " + reason);
            suite.check(power.getAbilityCooldown("inhale") == expected && power.getAbilityCooldownTotal("inhale") == expected,
                    reason + " held=" + stopTicks + " expected=" + expected + " actual=" + power.getAbilityCooldown("inhale")
                            + " total=" + power.getAbilityCooldownTotal("inhale"));
        }

        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, id() + " premise: " + detail); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            failReplacementSend = false;
            refuseReplacement = false;
            if (user != null) { AbilityInput.keyRelease(KEY_A, user); AbilityInput.keyRelease(KEY_B, user); }
            if (power != null && type != null && power.isSummoned()) type.forceUnsummon(user, power);
            if (observer != null) observer.discard();
            if (user != null) user.discard();
        }
    }

    private static final class InjectedSendFailure extends RuntimeException {
        InjectedSendFailure(String detail) { super(detail); }
    }

    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        final Scene scene;
        final List<Packet<?>> packets = new ArrayList<>();
        RecordingConnection(Scene scene, FakePlayer observer) {
            super(observer.getServer(), observer.connection.getConnection(), observer,
                    CommonListenerCookie.createInitial(observer.getGameProfile(), false));
            this.scene = scene;
        }
        @Override public void send(Packet<?> packet) {
            if (packet instanceof ClientboundBundlePacket bundle) { bundle.subPackets().forEach(this::send); return; }
            packets.add(packet);
            if (scene.failReplacementSend && packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof TrEntityActionInstancePacket && scene.stand.getCurStandAction() != scene.action) {
                scene.failReplacementSend = false;
                scene.rollbackFailures++;
                scene.cooldownAtFailedSend = scene.power.getAbilityCooldown("inhale");
                EntityActionInstance replacement = scene.stand.getCurStandAction();
                scene.premise(replacement != null && replacement.ability == scene.power.getAbility("guard"),
                        "fault must observe the actual installed registered guard");
                throw new InjectedSendFailure("registered guard installed before tracking send; inhaleCooldown=" + scene.cooldownAtFailedSend);
            }
        }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
    }

    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Could not bind native class " + type.getName(), error); }
    }
    private static void log(String message) { JojoMod.getLogger().info("D-SP-SPECIAL-02 " + message); }
}
