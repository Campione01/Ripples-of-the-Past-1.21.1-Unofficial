package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.PillarmanPowerType;
import rotp.core.impl.powers.pillarman.PillarmanRibEntity;
import rotp.core.impl.powers.pillarman.abilities.PillarmanRibsBladesAbility;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanRibStartupGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final double DIRECTION_EPSILON = 2.0E-4D;
    private static final float STEP = 8.0F / 21.0F;
    private static final float DONOR_PITCH = (float) Math.toDegrees(Math.atan(0.1D));
    private static final Vec3 DIRECTION = new Vec3(0.0D, -1.0D / Math.sqrt(101.0D), 10.0D / Math.sqrt(101.0D));
    private static final List<Vec3> ORIGINS = List.of(
            new Vec3(-0.18D, -0.50D, 0.0D), new Vec3(-0.22D, -0.60D, 0.0D),
            new Vec3(-0.22D, -0.70D, 0.0D), new Vec3(-0.18D, -0.80D, 0.0D),
            new Vec3(0.18D, -0.50D, 0.0D), new Vec3(0.22D, -0.65D, 0.0D),
            new Vec3(0.22D, -0.85D, 0.0D), new Vec3(0.18D, -0.95D, 0.0D));

    private PillarmanRibStartupGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "pillarman_rib_startup", timeoutTicks = 100)
    public static void registeredRibsKeepDonorInitialEyeBeforeTwoNaturalSteps(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        helper.testInfo.addListener(fixture);
        try {
            fixture.setUp();
            helper.runAfterDelay(1, fixture::poll);
        }
        catch (RuntimeException | Error error) {
            fixture.close();
            throw error;
        }
    }

    private record State(long time, int age, Vec3 eye, Vec3 root, Vec3 tip, Vec3 delta,
            double distance, boolean retracting, boolean removed) {}

    private record Step(State before, State after) {}

    private static final class Tracked {
        final PillarmanRibEntity rib;
        final int originIndex;
        final State join;
        final List<Step> firstSteps = new ArrayList<>();
        State pendingPre;
        int postTicks;
        double maxDistance;
        boolean retracted;

        Tracked(PillarmanRibEntity rib, int originIndex, State join) {
            this.rib = rib;
            this.originIndex = originIndex;
            this.join = join;
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 32;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> roof = new LinkedHashMap<>();
        private final Map<UUID, Tracked> ribs = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player user;
        private Vec3 userPosition;
        private PlayerPower power;
        private PillarmanData data;
        private Ability ability;
        private EntityActionInstance action;
        private RuntimeException observerFailure;
        private int userTicks;
        private int poweredAtTicks = -1;
        private boolean pressed;
        private boolean pressing;
        private boolean released;
        private boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(chunk.getMinBlockX(), y - 4, chunk.getMinBlockZ());
            BlockPos max = new BlockPos(chunk.getMaxBlockX(), y + 8, chunk.getMaxBlockZ());
            helper.assertTrue(max.getY() < level.getMaxBuildHeight(), "Rib fixture exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Rib sky fixture is obstructed");
            }
            helper.assertTrue(level.getEntities((Entity) null, AABB.encapsulatingFullBlocks(min, max)).isEmpty(),
                    "Rib fixture contains another entity");
            userPosition = new Vec3(chunk.getMinBlockX() + 8.0D, y, chunk.getMinBlockZ() + 4.0D);
            BlockPos shade = BlockPos.containing(userPosition).above(7);
            for (BlockPos pos : BlockPos.betweenClosed(shade.offset(-1, 0, -1), shade.offset(1, 0, 1))) {
                roof.put(pos.immutable(), level.getBlockState(pos));
                helper.assertTrue(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "Could not place rib shade");
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(userPosition.x, userPosition.y, userPosition.z, 0.0F, 0.0F);
            user.setYHeadRot(0.0F);
            user.yBodyRot = 0.0F;
            helper.assertTrue(level.addFreshEntity(user), "Could not add rib user");
            registerObservers();
            log("setup user=" + user.getUUID() + " pos=" + userPosition + " chunk=" + chunk + " noGravity=true shadeBlocks=" + roof.size());
        }

        private boolean shadeReady() {
            BlockPos eye = BlockPos.containing(user.getEyePosition());
            BlockPos sun = BlockPos.containing(user.getX(), Math.round(user.getY(1.0D)), user.getZ());
            return roof.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE))
                    && !level.canSeeSky(eye) && !level.canSeeSky(sun);
        }

        private void grantPower() {
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
            data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
            data.setEvolutionStage(2, user);
            data.setMode(PillarmanMode.NONE, user);
            data.setEnergy(user, 300.0F);
            user.setHealth(user.getMaxHealth());
            ability = power.getAbility("pillarman_ribs_blades");
            helper.assertTrue(ability instanceof PillarmanRibsBladesAbility
                            && ability.abilityType == PillarmanPowerType.PILLAR_MAN_RIBS_BLADES.get(), "Missing registered Ribs Blades");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            poweredAtTicks = userTicks;
            log("power stage=" + data.getEvolutionStage() + " mode=" + data.getMode() + " energy=" + data.getEnergy()
                    + " sky=" + level.getBrightness(LightLayer.SKY, BlockPos.containing(user.getEyePosition())));
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() != level || !(event.getEntity() instanceof PillarmanRibEntity rib) || rib.getOwner() != user) return;
                EntityActionInstance current = LivingComponentAction.getCurEntityAction(user);
                helper.assertTrue(!event.isCanceled() && pressed && current instanceof PillarmanRibsBladesAbility.RibsBladesInstance
                                && current.ability == ability && current.getPhase() == ActionPhase.PERFORM
                                && (action == null || current == action), "Rib join did not come from the live registered action");
                action = current;
                helper.assertTrue(rib.getType() == ModEntityTypes.PILLAR_MAN_RIBS.get()
                                && rib.ticksLifespan() == 21 && rib.getSpeedFactor() == 1.0D && !rib.isAttachedToAnEntity(),
                        "Rib type, owner-bound lifetime or speed is invalid");
                CompoundTag nbt = rib.saveWithoutId(new CompoundTag());
                Vec3 offset = new Vec3(nbt.getDouble("XOriginOffset"), nbt.getDouble("YOriginOffset"), 0.0D);
                int index = originIndex(offset);
                helper.assertTrue(index >= 0 && Math.abs(nbt.getFloat("XRotOffset") - DONOR_PITCH) < EPSILON
                                && Math.abs(nbt.getFloat("YRotOffset")) < EPSILON, "Rib authored origin or angle changed");
                Tracked tracked = new Tracked(rib, index, state(rib));
                helper.assertTrue(ribs.put(rib.getUUID(), tracked) == null && ribs.size() <= 8, "Duplicate or extra rib");
                log("join uuid=" + rib.getUUID() + " origin=" + index + " duringKeyPress=" + pressing + " state=" + tracked.join);
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                Tracked tracked = ribs.get(event.getEntity().getUUID());
                if (tracked == null) return;
                helper.assertTrue(!event.isCanceled() && tracked.pendingPre == null
                                && level.isPositionEntityTicking(tracked.rib.blockPosition()) && tracked.rib.getOwner() == user
                                && tracked.rib.ticksLifespan() == 21 && !tracked.rib.isAttachedToAnEntity(),
                        "Rib left its natural ticking/owner-bound fixture");
                tracked.pendingPre = state(tracked.rib);
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                helper.assertTrue(!ribs.containsKey(event.getProjectile().getUUID()), "Empty rib fixture produced a real impact");
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) userTicks++;
                Tracked tracked = ribs.get(event.getEntity().getUUID());
                if (tracked == null) return;
                State after = state(tracked.rib);
                State before = tracked.pendingPre;
                helper.assertTrue(before != null && before.time == after.time && before.age == after.age, "Missing rib Pre/Post pair");
                tracked.postTicks++;
                tracked.maxDistance = Math.max(tracked.maxDistance, after.distance);
                tracked.retracted |= after.retracting;
                if (tracked.firstSteps.size() < 2) {
                    tracked.firstSteps.add(new Step(before, after));
                    log("step uuid=" + tracked.rib.getUUID() + " number=" + tracked.firstSteps.size() + " pre=" + before + " post=" + after);
                }
                tracked.pendingPre = null;
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private State state(PillarmanRibEntity rib) {
            CompoundTag nbt = rib.saveWithoutId(new CompoundTag());
            return new State(level.getGameTime(), rib.tickCount, user.getEyePosition(1.0F), rib.getOriginPoint(1.0F),
                    rib.position(), rib.getDeltaMovement(), nbt.getDouble("Distance"), nbt.getBoolean("IsRetracting"), rib.isRemoved());
        }

        private void press() {
            helper.assertTrue(level.isPositionEntityTicking(user.blockPosition()) && shadeReady() && !user.isOnFire()
                            && !user.isCreative() && !user.getAbilities().instabuild && data.getEvolutionStage() == 2
                            && data.getMode() == PillarmanMode.NONE && !data.isStoneFormEnabled() && data.getEnergy() >= 60.0F,
                    "Rib user failed Survival/stage/mode/resource/shade admission");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.CLICK),
                    "Registered rib CLICK failed admission");
            pressed = true;
            pressing = true;
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.CLICK,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            pressing = false;
            helper.assertTrue(input != null && input.action instanceof PillarmanRibsBladesAbility.RibsBladesInstance started
                            && started.ability == ability && (action == null || action == started)
                            && started.phasesLength.getFloat(ActionPhase.WINDUP) == 0.0F
                            && started.phasesLength.getFloat(ActionPhase.PERFORM) == 1.0F, "Registered rib input installed the wrong action");
            action = (EntityActionInstance) input.action;
            helper.assertTrue(ribs.size() == 8 || LivingComponentAction.getCurEntityAction(user) == action,
                    "Rib action vanished without its real volley");
            log("press ability=" + ability.getAbilityId() + " userTicks=" + userTicks + " synchronousJoins=" + ribs.size());
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Rib watchdog: userTicks=" + userTicks + " powered=" + (power != null)
                        + " ribs=" + ribs.size() + " sky=" + level.getBrightness(LightLayer.SKY, BlockPos.containing(user.getEyePosition())));
                helper.assertTrue(user.isAlive() && near(user.position(), userPosition) && !user.isOnFire()
                                && user.getYRot() == 0.0F && user.getXRot() == 0.0F && user.yBodyRot == 0.0F,
                        "Rib owner moved, rotated, died or burned");
                if (power == null && userTicks >= 2 && shadeReady()) grantPower();
                if (!pressed && poweredAtTicks >= 0 && userTicks >= poweredAtTicks + 2) press();
                if (ribs.size() == 8 && !released) {
                    AbilityInput.keyRelease(KEY, user);
                    released = true;
                }
                if (ribs.size() == 8 && ribs.values().stream().allMatch(t -> t.rib.isRemoved())) {
                    validate();
                    close();
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        private void validate() {
            for (int i = 0; i < ORIGINS.size(); i++) {
                final int index = i;
                helper.assertTrue(ribs.values().stream().filter(t -> t.originIndex == index).count() == 1, "Rib volley lost an authored origin");
            }
            double maxJoinEyeError = 0.0D;
            for (Tracked tracked : ribs.values()) {
                State previous = tracked.join;
                Vec3 root = tracked.join.eye.add(ORIGINS.get(tracked.originIndex));
                helper.assertTrue(tracked.join.age == 0 && !tracked.join.removed && near(tracked.join.root, root)
                                && near(tracked.join.delta, Vec3.ZERO) && tracked.join.distance == 0.0D
                                && !tracked.join.retracting && tracked.firstSteps.size() == 2, "Rib Join state is invalid");
                float expectedDistance = 0.0F;
                for (int i = 0; i < 2; i++) {
                    Step step = tracked.firstSteps.get(i);
                    helper.assertTrue(step.before.age == i + 1 && step.after.age == step.before.age
                                    && step.before.time >= previous.time && step.after.time == step.before.time
                                    && near(step.before.tip, previous.tip) && near(step.before.delta, previous.delta)
                                    && Math.abs(step.before.distance - expectedDistance) < EPSILON,
                            "Rib first-two-step continuity is invalid");
                    expectedDistance += STEP;
                    Vec3 expectedTip = root.add(DIRECTION.scale(expectedDistance));
                    helper.assertTrue(near(step.before.eye, tracked.join.eye) && near(step.after.eye, tracked.join.eye)
                                    && near(step.before.root, root) && near(step.after.root, root)
                                    && step.after.tip.distanceTo(expectedTip) < DIRECTION_EPSILON
                                    && near(step.after.delta, step.after.tip.subtract(step.before.tip))
                                    && Math.abs(step.after.distance - expectedDistance) < EPSILON
                                    && !step.before.retracting && !step.after.retracting && !step.before.removed && !step.after.removed,
                            "Natural rib endpoint/delta disagrees with fixed donor geometry");
                    previous = step.after;
                }
                helper.assertTrue(tracked.postTicks > 2 && tracked.retracted && tracked.maxDistance > previous.distance
                                && tracked.rib.getRemovalReason() == Entity.RemovalReason.DISCARDED, "Rib did not retract and expire naturally");
                maxJoinEyeError = Math.max(maxJoinEyeError, tracked.join.tip.distanceTo(tracked.join.eye));
            }
            log("result ribs=8 allOrigins=true twoSteps=true allExpired=true maxJoinEyeError=" + maxJoinEyeError
                    + " postTicks=" + ribs.values().stream().map(t -> t.postTicks).distinct().toList());
            helper.assertTrue(maxJoinEyeError < EPSILON, "Rib initial tip differs from donor eye before natural movement: error=" + maxJoinEyeError);
        }

        private static int originIndex(Vec3 offset) {
            for (int i = 0; i < ORIGINS.size(); i++) if (near(offset, ORIGINS.get(i))) return i;
            return -1;
        }

        private static boolean near(Vec3 a, Vec3 b) { return a.distanceToSqr(b) < EPSILON * EPSILON; }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Rib startup observer failure", error);
            }
        }

        private void log(String message) { JojoMod.LOGGER.info("RIB-STARTUP {}", message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                if (user != null) AbilityInput.keyRelease(KEY, user);
            }
            finally {
                try {
                    for (Tracked tracked : ribs.values()) if (!tracked.rib.isRemoved()) tracked.rib.discard();
                    if (user != null) {
                        try { LivingComponentAction.getComponent(user).setAction(null, user, SyncType.NO_SYNC); }
                        finally { user.discard(); }
                    }
                }
                finally {
                    for (var entry : roof.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                    boolean restored = roof.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue()));
                    log("cleanup listeners=0 roofRestored=" + restored + " roofCells=" + roof.size());
                    if (!restored) throw new IllegalStateException("Rib shade was not exactly restored");
                }
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}
        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
