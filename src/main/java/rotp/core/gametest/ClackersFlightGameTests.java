package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersFlightGameTests {
    private static final double EPSILON = 1.0E-7D;
    // Official 1.16.5 AbstractArrow: float literals promoted to double vector arithmetic.
    private static final double AIR_DRAG = (double) 0.99F;
    private static final double GRAVITY = (double) 0.05F;
    private static final double EYE_OFFSET = (double) 0.1F;

    private ClackersFlightGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_registered_air_flight", timeoutTicks = 100)
    public static void registeredClackersThrowKeepsDonorAirFlight(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_registered_grounding", timeoutTicks = 100)
    public static void registeredClackersLandingStopsAndSynchronizesGroundedState(GameTestHelper helper) {
        start(helper, true);
    }

    private static void start(GameTestHelper helper, boolean grounded) {
        Fixture fixture = new Fixture(helper, grounded);
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

    private record Frame(long time, int age, Vec3 position, Vec3 velocity, Vec3 ownerPosition,
            Vec3 ownerVelocity, double ownerEyeY, boolean noGravity, boolean noPhysics,
            boolean inGround, boolean inWater, boolean ticking) {}

    private record Step(Frame before, Frame after) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean grounded;
        private final List<Object> listeners = new ArrayList<>();
        private final List<ClackersEntity> spawned = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();
        private final List<Frame> groundedFrames = new ArrayList<>();
        private final Map<BlockPos, BlockState> originalWall = new LinkedHashMap<>();
        private Player user;
        private AABB clearSpace;
        private ClackersEntity clackers;
        private ClackersEntity syncCopy;
        private Frame initial;
        private Frame before;
        private Frame landing;
        private long blockImpactAt = -1;
        private RuntimeException observerFailure;
        private int userTicks;
        private int useStartUserTicks;
        private int measuredUseTicks;
        private int impacts;
        private boolean using;
        private boolean releasing;
        private boolean released;
        private boolean closed;

        Fixture(GameTestHelper helper, boolean grounded) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.grounded = grounded;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            double y = template.getY() + 32.0D;
            clearSpace = new AABB(x + 4, y - 2, z + 1, x + 13, y + 6, z + 15.75D);
            helper.assertTrue(clearSpace.maxY < level.getMaxBuildHeight(), "Clackers fixture exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(clearSpace.minX, clearSpace.minY, clearSpace.minZ),
                    BlockPos.containing(clearSpace.maxX, clearSpace.maxY, clearSpace.maxZ))) {
                helper.assertTrue(level.isEmptyBlock(pos), "Clackers air corridor is obstructed");
            }
            helper.assertTrue(level.getEntities((Entity) null, clearSpace).isEmpty(), "Clackers corridor contains another entity");
            if (grounded) {
                for (int wallX = x + 7; wallX <= x + 9; wallX++) {
                    for (int wallY = (int) y; wallY <= (int) y + 2; wallY++) {
                        BlockPos pos = new BlockPos(wallX, wallY, z + 7);
                        originalWall.put(pos, level.getBlockState(pos));
                        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    }
                }
            }
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(x + 8.5D, y, z + 2.5D, 0, 0);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CLACKERS.get()));
            helper.assertTrue(level.addFreshEntity(user), "Could not add Clackers user");
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.CLACKER_VOLLEY.get());
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.CLACKER_VOLLEY.get()) && hamon.getEnergy() > 200,
                    "Clacker Volley skill or charge energy is missing");
            registerObservers();
            log("setup owner=" + user.getUUID() + " pos=" + user.position() + " chunk=" + chunk
                    + " ownerNoGravity=" + user.isNoGravity() + " energy=" + hamon.getEnergy() + " wallCells=" + originalWall.size());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() == level && event.getEntity() instanceof ClackersEntity entity && entity.getOwner() == user) {
                    spawned.add(entity);
                    if (!releasing || clackers != null || event.isCanceled()) throw new IllegalStateException("Unexpected Clackers spawn");
                    clackers = entity;
                    initial = frame();
                    log("spawn uuid=" + entity.getUUID() + " actualUseTicks=" + measuredUseTicks + " " + initial);
                }
            });
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == clackers && (grounded ? groundedFrames.size() < 5 : steps.size() < 3)) {
                    if (event.isCanceled() || before != null) throw new IllegalStateException("Clackers tick was canceled or unpaired");
                    before = frame();
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) userTicks++;
                if (event.getEntity() == clackers && (grounded ? groundedFrames.size() < 5 : steps.size() < 3)) {
                    Frame after = frame();
                    if (before == null || before.age != after.age || before.time != after.time) {
                        throw new IllegalStateException("Clackers post tick lacks its matching pre tick");
                    }
                    if (grounded) {
                        if (landing == null && blockImpactAt >= 0 && after.inGround) {
                            landing = after;
                            log("landing " + landing);
                        }
                        else if (landing != null && after.age > landing.age) {
                            groundedFrames.add(after);
                            log("grounded tick=" + groundedFrames.size() + " pre=" + before + " post=" + after);
                        }
                    }
                    else {
                        steps.add(new Step(before, after));
                        log("flight tick=" + steps.size() + " pre=" + before + " post=" + after);
                    }
                    before = null;
                }
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (event.getProjectile() == clackers) {
                    impacts++;
                    if (grounded) {
                        if (event.isCanceled() || !(event.getRayTraceResult() instanceof BlockHitResult hit)
                                || !originalWall.containsKey(hit.getBlockPos())) {
                            throw new IllegalStateException("Grounded Clackers missed its owned wall");
                        }
                        if (blockImpactAt < 0) {
                            blockImpactAt = level.getGameTime();
                            log("wall-impact age=" + clackers.tickCount + " block=" + hit.getBlockPos()
                                    + " face=" + hit.getDirection() + " pos=" + hit.getLocation());
                        }
                    }
                    else {
                        log("unexpected-impact type=" + event.getRayTraceResult().getType()
                                + " pos=" + event.getRayTraceResult().getLocation() + " canceled=" + event.isCanceled());
                    }
                }
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
        }

        private Frame frame() {
            return new Frame(level.getGameTime(), clackers.tickCount, clackers.position(), clackers.getDeltaMovement(),
                    user.position(), user.getDeltaMovement(), user.getEyeY(), clackers.isNoGravity(), clackers.noPhysics,
                    clackers.isInGround(), clackers.isInWater(), level.isPositionEntityTicking(clackers.blockPosition()));
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Clackers flight observer failure", error);
            }
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Clackers watchdog: using=" + user.isUsingItem()
                        + " useTicks=" + user.getTicksUsingItem() + " flightTicks=" + steps.size()
                        + " groundedTicks=" + groundedFrames.size());
                if (!using && userTicks >= 2) {
                    helper.assertTrue(level.isPositionEntityTicking(user.blockPosition()), "Clackers user is not entity-ticking");
                    ItemStack held = user.getMainHandItem();
                    helper.assertTrue(held.is(ModItems.CLACKERS.get()) && held.getCount() == 1, "Wrong registered item in hand");
                    helper.assertTrue(held.use(level, user, InteractionHand.MAIN_HAND).getResult().consumesAction()
                                    && user.isUsingItem() && user.getUseItem().is(ModItems.CLACKERS.get()), "Registered Clackers use failed");
                    using = true;
                    useStartUserTicks = userTicks;
                    log("use-start naturalUserTicks=" + userTicks);
                }
                if (using && !released) {
                    helper.assertTrue(user.isUsingItem(), "Clackers use stopped before the measured release");
                    int ticks = user.getTicksUsingItem();
                    if (ticks >= 20) {
                        measuredUseTicks = ticks;
                        helper.assertTrue(ticks == 20 && userTicks - useStartUserTicks >= 20, "Clackers charge did not use20 natural ticks");
                        helper.assertTrue(user.getDeltaMovement().lengthSqr() < EPSILON * EPSILON, "Clackers owner is moving at release");
                        releasing = true;
                        try {
                            // This computes remaining duration from the real use state and dispatches the item's release.
                            user.releaseUsingItem();
                        }
                        finally {
                            releasing = false;
                        }
                        released = true;
                        helper.assertTrue(clackers != null && !user.isUsingItem() && user.getMainHandItem().isEmpty(),
                                "Measured Survival release did not throw and consume its item");
                        log("release measuredUseTicks=" + ticks + " naturalUserTicks=" + userTicks);
                    }
                }
                if (grounded ? groundedFrames.size() == 5 : steps.size() == 3) {
                    if (grounded) validateGrounded();
                    else validate();
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
            helper.assertTrue(spawned.size() == 1 && clackers.getType() == ModEntityTypes.CLACKERS.get()
                            && clackers.getOwner() == user && clackers.isAlive() && user.isAlive() && impacts == 0,
                    "Clackers flight was interrupted or had the wrong production entity");
            List<Frame> frames = new ArrayList<>();
            frames.add(initial);
            for (Step step : steps) {
                frames.add(step.before);
                frames.add(step.after);
            }
            for (Frame sample : frames) {
                helper.assertTrue(sample.ticking && clearSpace.contains(sample.position) && !sample.inWater
                                && !sample.inGround && !sample.noPhysics, "Clackers sample is not unobstructed ticking air flight");
                helper.assertTrue(sample.ownerPosition.distanceToSqr(initial.ownerPosition) < EPSILON * EPSILON
                                && sample.ownerVelocity.lengthSqr() < EPSILON * EPSILON, "Clackers owner moved during the fixture");
            }
            Vec3 expectedSpawn = new Vec3(initial.ownerPosition.x, initial.ownerEyeY - EYE_OFFSET, initial.ownerPosition.z);
            Vec3 expectedPosition = initial.position;
            Vec3 expectedVelocity = initial.velocity;
            double maximumPositionError = 0;
            double maximumVelocityError = 0;
            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                Frame previous = i == 0 ? initial : steps.get(i - 1).after;
                helper.assertTrue(step.before.age == previous.age + 1
                                && step.before.position.distanceToSqr(previous.position) < EPSILON * EPSILON
                                && step.before.velocity.distanceToSqr(previous.velocity) < EPSILON * EPSILON,
                        "Clackers samples contain an external move or skipped natural tick");
                expectedPosition = expectedPosition.add(expectedVelocity);
                expectedVelocity = expectedVelocity.scale(AIR_DRAG).add(0, -GRAVITY, 0);
                maximumPositionError = Math.max(maximumPositionError, step.after.position.distanceTo(expectedPosition));
                maximumVelocityError = Math.max(maximumVelocityError, step.after.velocity.distanceTo(expectedVelocity));
                log("oracle tick=" + (i + 1) + " expectedPosition=" + expectedPosition + " expectedVelocity=" + expectedVelocity
                        + " positionError=" + step.after.position.distanceTo(expectedPosition)
                        + " velocityError=" + step.after.velocity.distanceTo(expectedVelocity));
            }
            double spawnError = initial.position.distanceTo(expectedSpawn);
            boolean gravityFlagsMatch = frames.stream().noneMatch(Frame::noGravity);
            log("result spawnError=" + spawnError + " noGravityAtSpawn=" + initial.noGravity
                    + " donorGravityFlags=" + gravityFlagsMatch + " maxPositionError=" + maximumPositionError
                    + " maxVelocityError=" + maximumVelocityError + " impacts=" + impacts);
            // Separate launch-root and flight comparisons; flight is anchored to measured p0 and v0.
            helper.assertTrue(spawnError <= EPSILON && gravityFlagsMatch && maximumPositionError <= EPSILON && maximumVelocityError <= EPSILON,
                    "Clackers donor air flight differs: spawn=" + spawnError + ", noGravity=" + initial.noGravity
                            + ", position=" + maximumPositionError + ", velocity=" + maximumVelocityError);
        }

        private void validateGrounded() {
            helper.assertTrue(spawned.size() == 1 && clackers.getType() == ModEntityTypes.CLACKERS.get()
                            && clackers.getOwner() == user && clackers.isAlive() && user.isAlive()
                            && blockImpactAt >= 0 && landing != null && landing.ticking && landing.inGround
                            && landing.velocity.lengthSqr() < EPSILON * EPSILON && impacts > 0,
                    "Clackers lacks a real landing from its registered item throw");
            Frame previous = landing;
            for (Frame sample : groundedFrames) {
                helper.assertTrue(sample.age == previous.age + 1 && sample.time > previous.time && sample.ticking
                                && clearSpace.contains(sample.position) && !sample.inWater && !sample.noPhysics,
                        "Grounded Clackers sample is not a consecutive natural ticking frame");
                helper.assertTrue(sample.inGround && sample.position.distanceToSqr(landing.position) < EPSILON * EPSILON
                                && sample.velocity.lengthSqr() < EPSILON * EPSILON,
                        "Grounded Clackers drifted or resumed motion");
                previous = sample;
            }
            helper.assertTrue(originalWall.keySet().stream().allMatch(pos -> level.getBlockState(pos).is(Blocks.STONE)),
                    "Clackers changed its owned stone wall");
            // Standard entity-data propagation only: this object is never added to a level or ticked.
            syncCopy = new ClackersEntity(ModEntityTypes.CLACKERS.get(), level);
            helper.assertTrue(!syncCopy.isInGround(), "Fresh Clackers unexpectedly starts grounded");
            var values = clackers.getEntityData().getNonDefaultValues();
            if (values != null) syncCopy.getEntityData().assignValues(values);
            log("ground-propagation-contract stableTicks=" + groundedFrames.size() + " payloadEntries="
                    + (values == null ? 0 : values.size()) + " sourceGrounded=" + clackers.isInGround()
                    + " freshCopyGrounded=" + syncCopy.isInGround() + " unspawnedCopy=true nativeClientProof=false");
            helper.assertTrue(syncCopy.isInGround(), "Landed state did not survive standard SynchedEntityData propagation");
        }

        private void log(String message) {
            JojoMod.LOGGER.info("{} {}", grounded ? "CLACKERS-GROUND" : "CLACKERS-AIR", message);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                // Cancel an unfinished use without manufacturing another release projectile during cleanup.
                if (user != null) user.stopUsingItem();
            }
            finally {
                try {
                    for (ClackersEntity entity : spawned) if (!entity.isRemoved()) entity.discard();
                    if (syncCopy != null) syncCopy.discard();
                    if (user != null) user.discard();
                }
                finally {
                    for (var entry : originalWall.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                    boolean restored = originalWall.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue()));
                    log("cleanup listeners=0 spawned=" + spawned.size() + " wallRestored=" + restored + " wallCells=" + originalWall.size());
                    if (!restored) throw new IllegalStateException("Clackers fixture wall was not exactly restored");
                }
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            close();
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            close();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            close();
        }
    }
}
