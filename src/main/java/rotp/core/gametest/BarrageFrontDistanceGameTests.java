package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BarrageFrontDistanceGameTests {
    private static final short KEY = 118;
    private static Suite active;
    private enum Kind { LOCKED, DIRECTIONAL, CLEAR, CLAMPS, LIMITED, RECOVERY, ARMS, MANUAL, GRAB,
        LOCKED_CLIPPED, RECOVERY_CLIPPED }

    private BarrageFrontDistanceGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240, batch = "barrage_front_distance")
    public static void followingBarrageRefreshesItsCurrentTargetDistance(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        log("IDENTITY production=" + sha(StandEntityBarrage.class) + " fixture=" + sha(BarrageFrontDistanceGameTests.class));
        helper.onEachTick(suite::tick);
    }

    @AfterBatch(batch = "barrage_front_distance")
    public static void releaseFrontDistanceBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final List<String> failures = new ArrayList<>();
        final Consumer<EntityTickEvent.Post> post;
        Scene scene;
        RuntimeException postFailure;
        int index;
        int checks;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping barrage-distance suites");
            this.helper = helper;
            helper.testInfo.addListener(this);
            active = this;
            post = event -> {
                if (scene != null && !closed && postFailure == null && event.getEntity() == scene.stand) {
                    try { scene.post(); }
                    catch (RuntimeException error) { postFailure = error; }
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        void tick() {
            if (closed) return;
            try {
                if (postFailure != null) throw postFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == Kind.values().length) {
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "Barrage front distance failed " + failures.size() + " checks; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, Kind.values()[index++]);
                    scene.prepare();
                }
                helper.assertTrue(++scene.worldTicks < 25, "Native distance scene did not tick: " + scene.kind);
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }

        void check(boolean passed, String detail) {
            checks++;
            log((passed ? "PASS " : "FAIL ") + scene.kind + " " + detail);
            if (!passed) failures.add(scene.kind + ": " + detail);
        }

        void close() {
            if (closed) return;
            closed = true;
            NeoForge.EVENT_BUS.unregister(post);
            if (scene != null) scene.close();
            if (active == this) active = null;
            log("CLEANUP listener=false");
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Kind kind;
        final ServerLevel level;
        final List<Cow> cows = new ArrayList<>();
        Player user;
        StandPower power;
        StandType type;
        StandEntity stand;
        StandEntityBarrage action;
        Cow target;
        Vec3 origin;
        Vec3 initialOffset;
        Vec3 initialRelative;
        Vec3 manualPosition;
        Vec3 retainedClip;
        float healthBeforeOutside;
        float healthBeforeRecovery;
        int nativeTicks;
        int worldTicks;
        boolean done;
        boolean closed;

        Scene(Suite suite, Kind kind) { this.suite = suite; this.kind = kind; level = suite.helper.getLevel(); }

        void prepare() {
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8,
                    chunk.getMiddleBlockZ() + 0.5);
            user = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.CREATIVE);
            user.getAbilities().instabuild = true;
            user.setNoGravity(true);
            user.noPhysics = true;
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            user.setYHeadRot(0);
            premise(level.addFreshEntity(user), "user added");
            power = PowerClass.STAND.attachGet(user);
            type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "registered Stand granted");
            premise(type.summon(user, power), "registered full Stand summoned");
            stand = power.getSummonedStandEntity();
            premise(stand != null && stand.getUser() == user, "Stand ownership");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(14);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(kind == Kind.LIMITED ? 0.8 : 3);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            StandEntity.updateStandStatAttributes(stand, user);
            power.setStamina(power.getMaxStamina());
            stand.updateStrengthMultipliers();
            stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
            stand.setNoGravity(true);
            if (kind == Kind.ARMS) stand.setArmsOnlyMode();
            if (kind == Kind.MANUAL) stand.setManuallyControlled(true);
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = kind != Kind.DIRECTIONAL;
            target = cow(1.2);
            if (kind == Kind.GRAB) stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(target);
            ActionTarget aimed = new ActionTarget(target);
            if (clippedRetention()) {
                aimed = HitResultUtil.clip(user.getEyePosition(1), user.getViewVector(1),
                        stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),
                        level, entity -> StandEntityAbility.canDefaultTargetEntityForAiming(stand, entity), user, 0);
                premise(aimed.getMainEntity() == target && aimed.getClipPos().isPresent(), "real clip factory selected the cow with a surface point");
                retainedClip = aimed.getClipPos().get();
                premise(near(retainedClip.z - user.getZ(), 0.75), "native initial cow surface geometry=" + retainedClip);
            }
            LivingComponentAction.getComponent(stand).entityAim.setTarget(aimed);
            Ability base = power.getAbility("barrage");
            var selected = power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD), "real hold admission");
            BufferingState buffering = BufferingState.clickOnly();
            var held = AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.HOLD, 0, buffering, base.getAbilityId());
            premise(buffering.isActionSuccess && held != null && held.action instanceof StandEntityBarrage, "registered held barrage");
            action = (StandEntityBarrage) held.action;
            if (clippedRetention()) premise(StandEntityPunchAbility.shouldRetainPunchTarget(stand, action.ability)
                    && !StandEntityBarrageAbility.isDirectionalBarrage(stand, action), "real retained-target mode");
            if (kind == Kind.GRAB) premise(action.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB, "real grab variation selected");
            initialOffset = action.getSyncedStandOffset();
            initialRelative = stand.offsetFromUser.getRelativeOffset();
            log("START " + kind + " action=" + action.id + " ability=" + selected.ability.name()
                    + " offset=" + initialOffset + " range=" + stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)
                    + " effective=" + stand.getEffectiveRange() + " max=" + stand.getMaxRange() + " initialClip=" + retainedClip);
        }

        void post() {
            if (done || closed) return;
            premise(stand.getCurStandAction() == action && (action.getPhase() == ActionPhase.PERFORM
                    || recovery() && action.getPhase() == ActionPhase.RECOVERY),
                    "same native action before its duration cap");
            nativeTicks++;
            if (nativeTicks == 1) {
                if (specialPosition()) suite.check(Objects.equals(action.getSyncedStandOffset(), initialOffset), "initial specialized offset unchanged");
                else assertOffset(0.5, "initial lower clamp");
                if (clippedRetention()) assertCombatClip("initial combat clip preserved");
                manualPosition = stand.position();
                target.moveTo(origin.x, origin.y + 0.4, origin.z + 2.8, 0, 0);
                if (recovery()) {
                    healthBeforeRecovery = target.getHealth();
                    AbilityInput.keyRelease(KEY, user);
                    premise(action.getPhase() == ActionPhase.RECOVERY, "real release entered recovery");
                }
            }
            if (nativeTicks == 4) {
                if (specialPosition()) {
                    suite.check(Objects.equals(action.getSyncedStandOffset(), initialOffset)
                            && stand.offsetFromUser.getRelativeOffset().equals(initialRelative), "arms/manual/grab offset remains owned by its mode");
                    if (kind == Kind.MANUAL) suite.check(stand.position().distanceToSqr(manualPosition) < 0.0001, "manual body is not pulled toward the target");
                    done = true;
                    return;
                }
                premise(action.punchedTarget != null && action.punchedTarget.getMainEntity() == target,
                        "moved target is still legal and selected");
                if (clippedRetention()) assertCombatClip("retained combat clip survives moving target");
                if (recovery()) {
                    assertOffset(1.35, "recovery follows the current target without attacking");
                    suite.check(target.getHealth() == healthBeforeRecovery, "recovery has no extra hit");
                    done = true;
                    return;
                }
                if (kind == Kind.LIMITED) {
                    assertOffset(0.8, "effective range caps the forward distance");
                    done = true;
                    return;
                }
                if (kind == Kind.DIRECTIONAL) {
                    ActionTarget clipped = StandEntityBarrageAbility.clipDirectionalBarrageTarget(stand, action, 1);
                    premise(clipped.getMainEntity() == target && clipped.getClipPos().isPresent(), "fresh directional surface target");
                    double clipZ = clipped.getClipPos().get().z - user.getZ();
                    premise(near(clipZ, 2.35), "cow surface geometry actual=" + clipZ);
                    assertOffset(0.9, "directional surface point, not center distance");
                }
                else assertOffset(1.35, clippedRetention() ? "retained clipped target uses current entity position" : "locked no-clip target moved out");
                if (kind == Kind.CLEAR) {
                    target.discard();
                    LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
                }
                else if (kind == Kind.CLAMPS) target.moveTo(origin.x, origin.y + 0.4, origin.z + 3.3, 0, 0);
                else user.moveTo(origin.x, origin.y, origin.z + 1, 0, 0);
            }
            if (nativeTicks == 7) {
                if (kind == Kind.CLEAR) {
                    assertOffset(1.5, "cleared target uses empty fallback");
                    suite.check(action.punchedTarget != null && action.punchedTarget.isEmpty(level), "discarded target is not reused");
                    target = cow(1.2);
                    LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
                }
                else if (kind == Kind.CLAMPS) {
                    assertOffset(1.5, "upper clamp");
                    healthBeforeOutside = target.getHealth();
                    target.moveTo(origin.x, origin.y + 0.4, origin.z + 6.5, 0, 0);
                    premise(target.chunkPosition().equals(user.chunkPosition()) && level.getEntity(target.getId()) == target,
                            "outside-range target remains present in the same loaded chunk");
                }
                else {
                    assertOffset(0.5, "owner approached the stationary target");
                    if (clippedRetention()) assertCombatClip("original combat clip survives owner movement");
                    done = true;
                }
            }
            if (nativeTicks == 10) {
                if (kind == Kind.CLEAR) {
                    assertOffset(0.5, "new close current target reacquired");
                    suite.check(action.punchedTarget != null && action.punchedTarget.getMainEntity() == target, "fresh target is selected in the same action");
                }
                else {
                    assertOffset(1.5, "out-of-range target falls back without extending reach");
                    suite.check(action.punchedTarget != null && action.punchedTarget.isEmpty(level)
                            && target.getHealth() == healthBeforeOutside, "outside target is neither retained nor hit");
                }
                done = true;
            }
            if (done) log("END " + kind + " nativeTicks=" + nativeTicks);
        }

        boolean specialPosition() { return kind == Kind.ARMS || kind == Kind.MANUAL || kind == Kind.GRAB; }

        boolean clippedRetention() { return kind == Kind.LOCKED_CLIPPED || kind == Kind.RECOVERY_CLIPPED; }

        boolean recovery() { return kind == Kind.RECOVERY || kind == Kind.RECOVERY_CLIPPED; }

        void assertCombatClip(String detail) {
            ActionTarget combat = action.punchedTarget;
            suite.check(combat != null && combat.getMainEntity() == target && combat.getClipPos().isPresent()
                    && combat.getClipPos().get().equals(retainedClip), detail + " actual=" + (combat != null ? combat.getClipPos() : null));
        }

        void assertOffset(double expected, String detail) {
            Vec3 synced = action.getSyncedStandOffset();
            suite.check(synced != null && near(synced.z, expected), detail + " synced=" + synced + " expected=" + expected);
            suite.check(near(stand.getZ() - user.getZ(), expected), detail + " actualBodyFront=" + (stand.getZ() - user.getZ()));
        }

        Cow cow(double distance) {
            Cow cow = EntityType.COW.create(level);
            premise(cow != null, "cow created");
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            cow.setHealth(500);
            cow.setNoAi(true); cow.setNoGravity(true); cow.noPhysics = true;
            cow.moveTo(origin.x, origin.y + 0.4, origin.z + distance, 0, 0);
            premise(level.addFreshEntity(cow), "cow added in loaded chunk");
            premise(near(cow.getBoundingBox().getXsize(), 0.9), "cow width0.9");
            cows.add(cow);
            return cow;
        }

        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, kind + " premise: " + detail); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (user != null) AbilityInput.keyRelease(KEY, user);
            if (stand != null) stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);
            for (Cow cow : cows) cow.discard();
            if (power != null && power.isSummoned()) type.forceUnsummon(user, power);
            if (user != null) user.discard();
        }
    }

    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 0.0002; }

    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Could not bind native class " + type.getName(), error); }
    }

    private static void log(String message) { JojoMod.getLogger().info("B-S11-04-R1 " + message); }
}
