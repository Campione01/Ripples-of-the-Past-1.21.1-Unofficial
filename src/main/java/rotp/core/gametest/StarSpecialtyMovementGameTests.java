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
import rotp.core.impl.stands.starplatinum.SPStarFingerEntity;
import rotp.core.impl.stands.starplatinum.StarFingerAbility.StarFingerInstance;
import rotp.core.impl.stands.starplatinum.StarInhaleAbility.InhaleAbilityInstance;
import rotp.core.impl.stands.starplatinum.StarPlatinumZoomAbility.StarPlatinumZoom;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Factor/lifecycle evidence only; actual normal and controlled-Stand motion require clients. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarSpecialtyMovementGameTests {
    private static final short KEY_A = 121;
    private static final short KEY_B = 122;
    private static Suite active;

    private StarSpecialtyMovementGameTests() {}
    private enum End { NATURAL, RELEASE, LATCHED_RELEASE, GUARD, ROLLBACK, FORCE }
    private record Spec(String ability, End end, int ticks, float factor) {}

    @GameTest(template = "empty", timeoutTicks = 800, batch = "star_specialty_movement")
    public static void registeredSpecialtyLifecycleWalkPenalty(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            log("IDENTITY finger=" + sha(StarFingerInstance.class) + " zoom=" + sha(StarPlatinumZoom.class)
                    + " inhale=" + sha(InhaleAbilityInstance.class) + " fixture=" + sha(StarSpecialtyMovementGameTests.class));
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = "star_specialty_movement")
    public static void releaseMovementBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    // owner rule 2026-10-15: only the guard slows its user (0.5); Star Finger, Inhale and Zoom keep full speed
    private static List<Spec> cases() {
        return List.of(new Spec("star_finger", End.NATURAL, 20, 1F),
                new Spec("star_finger", End.GUARD, 4, 1F),
                new Spec("star_finger", End.FORCE, 4, 1F),
                new Spec("inhale", End.NATURAL, 80, 1F), new Spec("inhale", End.RELEASE, 7, 1F),
                new Spec("inhale", End.GUARD, 7, 1F), new Spec("inhale", End.ROLLBACK, 7, 1F),
                new Spec("inhale", End.FORCE, 7, 1F),
                new Spec("enhanced_eyesight", End.RELEASE, 7, 1F),
                new Spec("enhanced_eyesight", End.LATCHED_RELEASE, 40, 1F),
                new Spec("enhanced_eyesight", End.GUARD, 7, 1F),
                new Spec("enhanced_eyesight", End.ROLLBACK, 7, 1F),
                new Spec("guard", End.RELEASE, 7, 0.5F), new Spec("time_stop", End.FORCE, 2, 1F));
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final List<Spec> specs = cases();
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        RuntimeException observerFailure;
        int index;
        int checks;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping specialty movement suites");
            this.helper = helper;
            level = helper.getLevel();
            helper.testInfo.addListener(this);
            active = this;
        }
        void listen() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (scene != null && !closed && event.getLevel() == level && event.getEntity() instanceof SPStarFingerEntity finger
                        && finger.getOwner() == scene.stand) scene.projectiles.add(finger);
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene == null || closed || observerFailure != null || event.getEntity() != scene.stand) return;
                try { scene.post(); }
                catch (RuntimeException error) { observerFailure = error; }
            };
            listeners.add(join); listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
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
                        helper.assertTrue(failures.isEmpty(), "Specialty movement factor/lifecycle failed; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, specs.get(index++));
                    scene.prepare();
                }
                helper.assertTrue(++scene.worldTicks < Math.max(scene.spec.ticks, 20) + 30, "Native lifecycle did not complete: " + scene.id());
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
            log("CLEANUP listeners=0");
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
        final List<SPStarFingerEntity> projectiles = new ArrayList<>();
        Player user;
        FakePlayer observer;
        RecordingConnection connection;
        StandPower power;
        StandType type;
        StandEntity stand;
        EntityActionInstance action;
        int worldTicks;
        int posts;
        int afterClear;
        int afterRollbackUntil;
        int sendFailures;
        boolean firstNativeChecked;
        boolean requested;
        boolean failSend;
        Ability pressed;
        boolean done;
        boolean closed;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.level; }
        String id() { return spec.ability + "-" + spec.end; }
        InputMethod input() { return spec.ability.equals("star_finger") ? InputMethod.CLICK : InputMethod.HOLD; }

        void prepare() {
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 128,
                    chunk.getMiddleBlockZ() + 0.5);
            premise(origin.y < level.getMaxBuildHeight() - 4, "bounded elevated test area");
            user = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            premise(level.addFreshEntity(user), "Survival owner added");
            observer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "SpecialtyMoveObserver"));
            observer.setNoGravity(true);
            observer.moveTo(origin.x + 2, origin.y, origin.z - 2, 0, 0);
            connection = new RecordingConnection(this, observer);
            observer.connection = connection;
            premise(level.addFreshEntity(observer), "observer added");
            connection.chunkSender.dropChunk(observer, observer.chunkPosition());
            power = PowerClass.STAND.attachGet(user);
            type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "registered Stand granted");
            power.skipProgression();
            premise(type.summon(user, power), "registered Stand summoned");
            stand = power.getSummonedStandEntity();
            premise(stand != null && stand.getUser() == user, "same-user Stand ownership");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
            power.setStamina(power.getMaxStamina());
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            premise(connection.packets.stream().anyMatch(packet -> packet instanceof ClientboundAddEntityPacket add
                    && add.getId() == stand.getId()), "actual tracking observer");
            suite.check(stand.getCurStandAction() == null && factor() == 1, "idle/before-start effective factor1");
            press(spec.ability, KEY_A, input());
            action = stand.getCurStandAction();
            premise(action != null && action.getPhase() != null && action.getPhase() != ActionPhase.RECOVERY,
                    "real registered active phase");
            if (spec.ability.equals("star_finger")) premise(action instanceof StarFingerInstance
                    && action.phasesLength.getFloat(ActionPhase.PERFORM) == 20, "unchanged real Finger20");
            if (spec.ability.equals("inhale")) premise(action instanceof InhaleAbilityInstance
                    && action.phasesLength.getFloat(ActionPhase.PERFORM) == 80, "unchanged real Inhale80");
            if (spec.ability.equals("enhanced_eyesight")) premise(action instanceof StarPlatinumZoom, "real Zoom instance");
            checkActive("registered activation");
            log("START " + id() + " phase=" + action.getPhase() + " action=" + action.id + " owner=" + user.getUUID());
        }

        void post() {
            if (done || closed) return;
            posts++;
            if (!firstNativeChecked && stand.getCurStandAction() == action && !action.isOver()) {
                firstNativeChecked = true;
                checkActive("first actual native phase tick=" + action.getPhaseTick());
            }
            if (!requested && spec.end != End.NATURAL && stand.getCurStandAction() == action && !action.isOver()
                    && action.getFullTicksPassed() >= spec.ticks) requestEnd();
            if (spec.end == End.ROLLBACK && requested && input() == InputMethod.HOLD && !action.isOver()
                    && action.getFullTicksPassed() >= afterRollbackUntil) AbilityInput.keyRelease(KEY_A, user);
            if (stand.getCurStandAction() == null && action.isOver()) {
                if (++afterClear == 1) {
                    suite.check(factor() == 1, "native removed/no-current-action effective factor1");
                    if (spec.end == End.NATURAL || spec.end == End.RELEASE || spec.end == End.LATCHED_RELEASE
                            || spec.end == End.ROLLBACK || spec.end == End.GUARD) suite.check(action.userWalkSpeed == 1,
                                    "actual recovery callback factor1");
                    if (spec.ability.equals("inhale")) suite.check(power.getAbilityCooldown("inhale") > 0,
                            "D02 actual removal/release cooldown retained");
                }
                if (afterClear >= 2) { log("END " + id() + " posts=" + posts + " factor=" + factor()); done = true; }
            }
        }

        void requestEnd() {
            requested = true;
            if (spec.end == End.RELEASE || spec.end == End.LATCHED_RELEASE) {
                checkActive(spec.end == End.LATCHED_RELEASE ? "server virtual-key HOLD stays active without physical-key inspection; not GUI proof" : "held before release");
                AbilityInput.keyRelease(KEY_A, user);
                suite.check(action.isOver() && action.userWalkSpeed == 1, "real release passes recovery and restores factor1");
            }
            else if (spec.end == End.FORCE) action.forceStop();
            else if (spec.end == End.GUARD) {
                boolean succeeded = press("guard", KEY_B, InputMethod.HOLD);
                // a held Inhale or Zoom holds the guard back: 1.16's client sent no press of a held action while the
                // power held one (ActionsOverlayGui.onActionClick); its own key ends the hold
                suite.check(!succeeded && stand.getCurStandAction() == action && !action.isOver(), spec.ability.equals("star_finger")
                        ? "Finger preserves existing refusal of immediate guard cancellation"
                        : "a guard pressed during the held " + spec.ability + " waits");
                checkActive("uncancelled " + spec.ability + " continues its native phase");
                AbilityInput.keyRelease(KEY_B, user);
                if (input() == InputMethod.HOLD) {
                    AbilityInput.keyRelease(KEY_A, user);
                }
            }
            else if (spec.end == End.ROLLBACK) {
                failSend = true;
                try { press("punch", KEY_B, InputMethod.CLICK); premise(false, "expected actual tracking failure absent"); }
                catch (InjectedSendFailure expected) { log("EXPECTED-FAULT " + id() + " " + expected.getMessage()); }
                finally { failSend = false; }
                suite.check(sendFailures == 1 && stand.getCurStandAction() == action && !action.isOver(),
                        "actual input rollback restores same action");
                checkActive("rollback reinstallation reuses real phase");
                if (spec.ability.equals("inhale")) suite.check(power.getAbilityCooldown("inhale") == 0,
                        "D02 rollback compensation preserved");
                afterRollbackUntil = (int) action.getFullTicksPassed() + 3;
            }
        }

        void checkActive(String reason) {
            suite.check(action.getPowerUser() == user && stand.getUser() == user && action.userWalkSpeed == spec.factor
                    && factor() == spec.factor, reason + " raw=" + action.userWalkSpeed + " StandResolver=" + factor()
                            + " expected(owner rule 2026-10-15)=" + spec.factor + " phase=" + action.getPhase());
        }
        float factor() {
            StandEntity currentStand = power.getSummonedStandEntity();
            if (currentStand == null || currentStand.isRemoved()) return 1;
            EntityActionInstance current = currentStand.getCurStandAction();
            return currentStand.getUserWalkSpeed(current != null ? current.userWalkSpeed : 1);
        }
        boolean press(String name, short key, InputMethod method) {
            var ability = power.getAbility(name);
            var selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            premise(selected != null && AbilityInput.withConditionCheck(selected, user, method), "registered " + name + " admission");
            pressed = selected.ability;
            BufferingState input = BufferingState.clickOnly();
            AbilityInput.keyPress(key, selected.ability, user, null, method, 0, input, ability.getAbilityId());
            return input.isActionSuccess;
        }
        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, id() + " premise: " + detail); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            failSend = false;
            if (user != null) { AbilityInput.keyRelease(KEY_A, user); AbilityInput.keyRelease(KEY_B, user); }
            for (SPStarFingerEntity projectile : projectiles) projectile.discard();
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
            if (scene.failSend && packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof TrEntityActionInstancePacket && scene.stand.getCurStandAction() != scene.action) {
                scene.failSend = false;
                scene.sendFailures++;
                scene.premise(scene.stand.getCurStandAction() != null
                        && scene.stand.getCurStandAction().ability == scene.pressed, "real installed registered punch before fault");
                throw new InjectedSendFailure("actual tracking-send failure after installed punch");
            }
        }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
    }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Native class binding failed " + type.getName(), error); }
    }
    private static void log(String message) { JojoMod.getLogger().info("D-SP-SPECIAL-03 " + message); }
}
