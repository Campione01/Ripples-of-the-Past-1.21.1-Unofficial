package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.impl.stands.starplatinum.SPStarFingerEntity;
import rotp.core.impl.stands.starplatinum.StarFingerAbility.StarFingerInstance;
import rotp.core.impl.stands.starplatinum.StarInhaleAbility.InhaleAbilityInstance;
import rotp.core.impl.stands.starplatinum.StarPlatinumZoomAbility.StarPlatinumZoom;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Controlled server geometry/lifecycle only, not real mouse input or client interpolation acceptance. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarSpecialtyOffsetGameTests {
    private static final short KEY = 123;
    private static final short MANUAL_KEY = 124;
    private static final float VIEW_YAW = 60;
    private static final float BODY_YAW = 0;
    private static Suite active;

    private StarSpecialtyOffsetGameTests() {}
    private enum Aim { EMPTY, ENTITY, BLOCK, MANUAL }
    private record Spec(String ability, float pitch, HumanoidArm side, double scale, Aim aim, boolean natural) {}

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "star_specialty_offset")
    public static void registeredSpecialtiesUseViewYawOnly(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            log("IDENTITY finger=" + sha(StarFingerInstance.class) + " zoom=" + sha(StarPlatinumZoom.class)
                    + " inhale=" + sha(InhaleAbilityInstance.class) + " fixture=" + sha(StarSpecialtyOffsetGameTests.class));
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = "star_specialty_offset")
    public static void releaseOffsetBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static List<Spec> cases() {
        List<Spec> specs = new ArrayList<>();
        for (String name : List.of("star_finger", "enhanced_eyesight", "inhale")) {
            specs.add(new Spec(name, 0, HumanoidArm.RIGHT, 1, Aim.EMPTY, !name.equals("enhanced_eyesight")));
            specs.add(new Spec(name, 30, HumanoidArm.LEFT, 1.5, Aim.EMPTY, false));
            specs.add(new Spec(name, -60, HumanoidArm.RIGHT, 0.75, Aim.EMPTY, false));
            specs.add(new Spec(name, 0, HumanoidArm.RIGHT, 1, Aim.ENTITY, false));
            specs.add(new Spec(name, 0, HumanoidArm.RIGHT, 1, Aim.BLOCK, false));
            specs.add(new Spec(name, 0, HumanoidArm.RIGHT, 1, Aim.MANUAL, false));
        }
        specs.add(new Spec("guard", 30, HumanoidArm.LEFT, 1.5, Aim.EMPTY, false));
        return specs;
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
            helper.assertTrue(active == null, "Overlapping specialty offset suites");
            this.helper = helper;
            level = helper.getLevel();
            helper.testInfo.addListener(this);
            active = this;
        }
        void listen() {
            Consumer<EntityTickEvent.Pre> pre = event -> {
                if (scene != null && !closed && event.getEntity() == scene.stand) scene.setInputOrientation();
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene == null || closed || observerFailure != null || event.getEntity() != scene.stand) return;
                try { scene.post(); }
                catch (RuntimeException error) { observerFailure = error; }
            };
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (scene != null && !closed && event.getLevel() == level && event.getEntity() instanceof SPStarFingerEntity finger
                        && finger.getOwner() == scene.stand) scene.projectiles.add(finger);
            };
            listeners.add(pre); listeners.add(post); listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, join);
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
                        helper.assertTrue(failures.isEmpty(), "Specialty view-yaw geometry failed; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, specs.get(index++));
                    scene.prepare();
                }
                helper.assertTrue(++scene.worldTicks < 125, "Native offset lifecycle did not complete: " + scene.id());
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
        Cow target;
        StandPower power;
        StandType type;
        StandEntity stand;
        EntityActionInstance action;
        BlockPos block;
        BlockState originalBlock;
        Vec3 idleAbsolute;
        Vec3 manualAnchor;
        int posts;
        int worldTicks;
        int startedAt;
        int clearedTicks;
        boolean measured;
        boolean released;
        boolean done;
        boolean closed;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.level; }
        String id() { return spec.ability + "/" + spec.aim + "/pitch" + spec.pitch + "/" + spec.side + "/scale" + spec.scale; }
        InputMethod input() { return spec.ability.equals("star_finger") ? InputMethod.CLICK : InputMethod.HOLD; }
        Vec3 relative() {
            return switch (spec.ability) {
                case "star_finger" -> new Vec3(0, stand.Y_OFFSET, 0.5);
                case "enhanced_eyesight" -> new Vec3(-0.25, -0.3, -0.25);
                case "inhale" -> new Vec3(0, stand.Y_OFFSET, -0.25);
                default -> new Vec3(0, stand.Y_OFFSET, 0.3);
            };
        }
        void prepare() {
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 128,
                    chunk.getMiddleBlockZ() + 0.5);
            premise(origin.y < level.getMaxBuildHeight() - 8, "bounded elevated test area");
            user = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.SURVIVAL);
            GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
            user.getAttribute(Attributes.SCALE).setBaseValue(spec.scale);
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = spec.side;
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, VIEW_YAW, spec.pitch);
            premise(level.addFreshEntity(user), "Survival owner added");
            power = PowerClass.STAND.attachGet(user);
            type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "registered Stand granted");
            power.skipProgression();
            premise(type.summon(user, power), "registered Stand summoned");
            stand = power.getSummonedStandEntity();
            premise(stand != null && stand.getUser() == user && !stand.isArmsOnlyMode(), "full same-user Stand ownership");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setNoGravity(true);
            power.setStamina(power.getMaxStamina());
            setInputOrientation();
        }
        void setInputOrientation() {
            // A controlled fixture input, never an offset/phase/result setter or real-mouse claim.
            user.setYRot(VIEW_YAW); user.setXRot(spec.pitch);
            user.yBodyRot = BODY_YAW; user.yBodyRotO = BODY_YAW;
            user.yHeadRot = VIEW_YAW; user.yHeadRotO = VIEW_YAW;
        }
        void post() {
            if (done || closed) return;
            posts++;
            if (action == null) {
                if (posts < 5) return;
                suite.check(stand.isFollowingUser() && stand.offsetFromUser.isIdle()
                        && stand.offsetFromUser.getRotations() == StandOffsetFromUser.Rotations.HEAD, "settled native idle remains HEAD/follow");
                checkPosition(oracle(stand.offsetFromUser.idleOffset, VIEW_YAW), "native idle view-yaw position (1.16 taskOffset)");
                idleAbsolute = stand.offsetFromUser.getAbsoluteOffset(user, false);
                prepareAim();
                if (spec.aim == Aim.MANUAL) {
                    press("manual_control", MANUAL_KEY, InputMethod.CLICK);
                    premise(stand.isManuallyControlled() && !stand.isFollowingUser(), "actual registered O-control branch");
                    manualAnchor = stand.position();
                }
                press(spec.ability, KEY, input());
                action = stand.getCurStandAction();
                premise(action != null && action.getPhase() != null && action.getPhase() != ActionPhase.RECOVERY, "registered active specialty");
                startedAt = posts;
                suite.check(action.getSyncedStandOffset().distanceTo(relative()) < 1.0E-8
                        && stand.offsetFromUser.getRelativeOffset().distanceTo(relative()) < 1.0E-8, "unchanged numeric/Y offset and action sync payload");
                suite.check(stand.offsetFromUser.getRotations() == StandOffsetFromUser.Rotations.HEAD, "selected yaw-only HEAD projection");
                suite.check(action.standRotationTarget == null, "real raw aim did not become retained rotation target");
                return;
            }
            int elapsed = posts - startedAt;
            if (elapsed == 1) {
                float amount = stand.offsetFromUser.getLerpAmount();
                Vec3 actual = stand.offsetFromUser.getAbsoluteOffset(user, false);
                suite.check(amount == 0.25F && stand.offsetFromUser.getAbsoluteOffset(user, true)
                        .distanceTo(idleAbsolute.lerp(actual, amount)) < 1.0E-7, "native first offset tick retains four-tick interpolation");
            }
            if (!measured && elapsed >= 6 && stand.getCurStandAction() == action && !action.isOver()) {
                measured = true;
                suite.check(stand.offsetFromUser.getLerpAmount() == 1 && Math.abs(Mth.wrapDegrees(user.getYRot() - user.yBodyRot)) == 60,
                        "native transition settled with measured body/view divergence");
                double widthScale = user.getDimensions(Pose.STANDING).width() / (double) user.getType().getDimensions().width();
                suite.check(Math.abs(widthScale - spec.scale) < 1.0E-5 && stand.getUserBroadcastedSettings().orElseThrow().standSide == spec.side,
                        "real vanilla scale and broadcast Stand-side setting");
                if (spec.aim == Aim.MANUAL) {
                    suite.check(stand.isManuallyControlled() && !stand.isFollowingUser(), "active specialty does not turn O-control into follow");
                    checkPosition(manualAnchor, "native O-control anchor not overwritten by follow offset");
                }
                else {
                    Vec3 expected = oracle(relative(), VIEW_YAW);
                    checkPosition(expected, "actual native follow uses view-yaw position, not body or aimed center");
                    suite.check(Math.abs(stand.getY() - expected.y) < 1.0E-5, "pitch does not orbit retained vertical offset");
                    suite.check(Math.abs(Mth.wrapDegrees(stand.yBodyRot - VIEW_YAW)) < 1.0E-5, "native active body copy follows view yaw");
                }
                log("SAMPLE " + id() + " view=" + user.getYRot() + " body=" + user.yBodyRot + " user=" + user.position()
                        + " stand=" + stand.position() + " relative=" + relative() + " phaseTick=" + action.getPhaseTick());
            }
            if (measured && !released && !spec.natural && input() == InputMethod.HOLD && elapsed >= 8) {
                released = true;
                AbilityInput.keyRelease(KEY, user);
            }
            if (stand.getCurStandAction() == null && action.isOver() && ++clearedTicks >= 6) {
                suite.check(measured && stand.offsetFromUser.isIdle() && stand.offsetFromUser.getRotations() == StandOffsetFromUser.Rotations.HEAD,
                        "native natural/release clear restores idle HEAD");
                if (spec.aim == Aim.MANUAL) {
                    checkPosition(manualAnchor, "clear does not overwrite O-control anchor");
                    press("manual_control", MANUAL_KEY, InputMethod.CLICK);
                    suite.check(!stand.isManuallyControlled(), "registered O exit retained");
                }
                else checkPosition(oracle(stand.offsetFromUser.idleOffset, VIEW_YAW), "native cleared idle position unchanged");
                done = true;
            }
        }
        void prepareAim() {
            if (spec.aim == Aim.ENTITY) {
                Vec3 direction = user.getLookAngle();
                Vec3 point = user.getEyePosition().add(direction.scale(2.3)).add(0.5 * 0.3, 0, Math.sin(Math.toRadians(VIEW_YAW)) * 0.3);
                target = EntityType.COW.create(level);
                premise(target != null, "real living ray target");
                target.setNoAi(true); target.setNoGravity(true); target.setInvulnerable(true);
                target.moveTo(point.x, point.y - 0.7, point.z);
                premise(level.addFreshEntity(target), "real living target added");
            }
            else if (spec.aim == Aim.BLOCK) {
                block = BlockPos.containing(user.getEyePosition().add(user.getLookAngle().scale(2.3)));
                originalBlock = level.getBlockState(block);
                premise(originalBlock.isAir(), "isolated full-cube ray fixture");
                level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            }
            ActionTarget raw = HitResultUtil.clip(user.getEyePosition(), user.getLookAngle(),
                    stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),
                    level, entity -> StandEntityAbility.canDefaultTargetEntityForAiming(stand, entity), user, 0);
            if (spec.aim == Aim.ENTITY || spec.aim == Aim.BLOCK) {
                premise(spec.aim == Aim.ENTITY ? raw.getType() == ActionTarget.TargetType.ENTITY && raw.getEntity() == target
                        : raw.getType() == ActionTarget.TargetType.BLOCK && raw.getBlockPos().equals(block), "production ray actually picked edge target");
                Vec3 center = spec.aim == Aim.ENTITY ? target.getBoundingBox().getCenter() : Vec3.atCenterOf(block);
                Vec3 toCenter = center.subtract(user.getEyePosition());
                float centerYaw = (float) Math.toDegrees(Math.atan2(-toCenter.x, toCenter.z));
                premise(Math.abs(Mth.wrapDegrees(centerYaw - VIEW_YAW)) > 2, "target center yaw is distinct from view yaw");
                log("RAY " + id() + " type=" + raw.getType() + " uuid=" + (target != null ? target.getUUID() : "none")
                        + " block=" + block + " clip=" + raw.getClipPos() + " centerYaw=" + centerYaw + " viewYaw=" + VIEW_YAW);
            }
            else premise(raw.isEmpty(level), "production ray is really empty");
            // Feed the real pick into the normal public aim-input channel, never an action target.
            LivingComponentAction.getComponent(stand).entityAim.setTarget(raw);
            LivingComponentAction.getComponent(user).entityAim.setTarget(raw);
        }
        Vec3 oracle(Vec3 relative, float yaw) {
            double left = spec.side == HumanoidArm.LEFT ? -relative.x : relative.x;
            double radians = Math.toRadians(yaw);
            // 1.16 getAbsoluteVec scaled across by the user's width ratio and up by its height ratio
            double widthScale = user.getBbWidth() / (double) user.getType().getDimensions().width();
            double heightScale = user.getBbHeight() / (double) user.getType().getDimensions().height();
            // 1.16 StandEntity.updatePosition: the offset from the user's feet
            return user.position().add((left * Math.cos(radians) - relative.z * Math.sin(radians)) * widthScale,
                    relative.y * heightScale,
                    (relative.z * Math.cos(radians) + left * Math.sin(radians)) * widthScale);
        }
        void checkPosition(Vec3 expected, String detail) {
            // Vec3 yaw rotation uses Minecraft float trig; the independent oracle uses double trig.
            suite.check(stand.position().distanceTo(expected) < 1.0E-4, detail + " actual=" + stand.position() + " expected=" + expected);
        }
        void press(String name, short key, InputMethod method) {
            var ability = power.getAbility(name);
            var selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            premise(selected != null && AbilityInput.withConditionCheck(selected, user, method), "registered " + name + " admission");
            BufferingState input = BufferingState.clickOnly();
            AbilityInput.keyPress(key, selected.ability, user, null, method, 0, input, ability.getAbilityId());
            premise(input.isActionSuccess, "registered " + name + " input success");
        }
        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, id() + " premise: " + detail); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (user != null) { AbilityInput.keyRelease(KEY, user); AbilityInput.keyRelease(MANUAL_KEY, user); }
            for (SPStarFingerEntity projectile : projectiles) projectile.discard();
            if (stand != null && stand.isManuallyControlled()) StandEntityManualControlToggle.off(level, stand, false);
            if (power != null && type != null && power.isSummoned()) type.forceUnsummon(user, power);
            if (target != null) target.discard();
            if (block != null) level.setBlockAndUpdate(block, originalBlock);
            if (user != null) user.discard();
        }
    }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Native class binding failed " + type.getName(), error); }
    }
    private static void log(String message) { JojoMod.getLogger().info("D-SP-SPECIAL-04 " + message); }
}
