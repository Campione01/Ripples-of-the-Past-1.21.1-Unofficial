package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
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
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProjectileDispenserGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final Direction FACING = Direction.SOUTH;

    private ProjectileDispenserGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "knife_dispenser_origin", timeoutTicks = 80)
    public static void registeredKnifeDispenserKeepsDonorOriginAndPickup(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "molotov_dispenser_origin", timeoutTicks = 80)
    public static void registeredMolotovDispenserKeepsDonorOriginAndConfiguration(GameTestHelper helper) {
        start(helper, true);
    }

    private static void start(GameTestHelper helper, boolean molotov) {
        Fixture fixture = new Fixture(helper, molotov);
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

    private record Frame(long time, int age, Vec3 position, Vec3 motion, boolean noGravity, boolean removed) {}
    private record Step(Frame before, Frame after) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final boolean molotov;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<Projectile> spawned = new ArrayList<>();
        private final List<ItemEntity> drops = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();
        private AABB room;
        private BlockPos dispenserPos;
        private BlockPos powerPos;
        private DispenserBlockEntity dispenser;
        private Item item;
        private EntityType<?> projectileType;
        private ProjectileItem.DispenseConfig config;
        private Projectile projectile;
        private Frame join;
        private Frame pending;
        private AbstractArrow.Pickup pickup;
        private RuntimeException observerFailure;
        private long poweredAt;
        private boolean powered;
        private boolean closed;
        private int impacts;

        Fixture(GameTestHelper helper, boolean molotov) {
            this.helper = helper;
            level = helper.getLevel();
            this.molotov = molotov;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(chunk.getMinBlockX() + 2, y - 2, chunk.getMinBlockZ() + 2);
            BlockPos max = new BlockPos(chunk.getMinBlockX() + 13, y + 6, chunk.getMinBlockZ() + 14);
            helper.assertTrue(min.getY() >= level.getMinBuildHeight() && max.getY() < level.getMaxBuildHeight(),
                    "Dispenser fixture exceeds build height");
            room = AABB.encapsulatingFullBlocks(min, max);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Dispenser air volume is occupied");
            }
            helper.assertTrue(level.getEntities((Entity) null, room).isEmpty(), "Dispenser fixture contains another entity");
            dispenserPos = new BlockPos(chunk.getMinBlockX() + 8, y, chunk.getMinBlockZ() + 4);
            powerPos = dispenserPos.west();
            for (BlockPos pos : List.of(dispenserPos.below(), powerPos.below(), dispenserPos, powerPos)) {
                original.put(pos, level.getBlockState(pos));
            }
            level.setBlockAndUpdate(dispenserPos.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(powerPos.below(), Blocks.STONE.defaultBlockState());
            helper.assertTrue(level.setBlockAndUpdate(dispenserPos, Blocks.DISPENSER.defaultBlockState()
                            .setValue(DispenserBlock.FACING, FACING).setValue(DispenserBlock.TRIGGERED, false)),
                    "Could not place registered dispenser");
            helper.assertTrue(level.getBlockEntity(dispenserPos) instanceof DispenserBlockEntity,
                    "Dispenser block entity is missing");
            dispenser = (DispenserBlockEntity) level.getBlockEntity(dispenserPos);
            item = molotov ? ModItems.MOLOTOV.get() : ModItems.KNIFE.get();
            projectileType = molotov ? ModEntityTypes.MOLOTOV.get() : ModEntityTypes.KNIFE.get();
            helper.assertTrue(item instanceof ProjectileItem, "Registered item lacks its dispenser contract");
            config = ((ProjectileItem) item).createDispenseConfig();
            dispenser.setItem(0, new ItemStack(item, 2));
            dispenser.setChanged();
            helper.assertTrue(!level.hasNeighborSignal(dispenserPos) && !level.hasNeighborSignal(dispenserPos.above()),
                    "Dispenser was powered before the owned activation");
            registerObservers();
            log("setup dispenser=" + dispenserPos + " facing=" + FACING + " item=" + item
                    + " count=2 configPower=" + config.power() + " configUncertainty=" + config.uncertainty()
                    + " expectedPower=" + expectedPower() + " expectedUncertainty=" + expectedUncertainty());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> joinListener = event -> {
                if (closed || event.getLevel() != level || !room.contains(event.getEntity().position())) return;
                if (event.getEntity() instanceof ItemEntity drop) drops.add(drop);
                if (event.getEntity().getType() != projectileType || !(event.getEntity() instanceof Projectile created)) return;
                spawned.add(created);
                observe(() -> {
                    helper.assertTrue(powered && !event.isCanceled() && projectile == null && spawned.size() == 1
                                    && level.getGameTime() - poweredAt >= 4, "Projectile did not come from one scheduled redstone activation");
                    projectile = created;
                    join = frame();
                    pickup = created instanceof KnifeEntity knife ? knife.pickup : null;
                    log("join uuid=" + created.getUUID() + " state=" + join + " owner=" + created.getOwner()
                            + " pickup=" + pickup + " inventoryAtJoin=" + dispenser.getItem(0).getCount()
                            + " donorOrigin=" + expectedOrigin() + " originError=" + join.position.distanceTo(expectedOrigin())
                            + " elapsedScheduledTicks=" + (level.getGameTime() - poweredAt));
                    helper.assertTrue(created.getOwner() == null && join.age == 0 && !join.noGravity && !join.removed
                                    && join.motion.lengthSqr() > 0 && level.isPositionEntityTicking(created.blockPosition()),
                            "Dispenser projectile lacks ordinary ownerless natural-flight state");
                });
            };
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() != projectile) return;
                helper.assertTrue(!event.isCanceled() && pending == null && projectile.getOwner() == null
                                && level.isPositionEntityTicking(projectile.blockPosition()), "Dispenser projectile natural tick invalid");
                pending = frame();
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (event.getProjectile() != projectile) return;
                impacts++;
                log("unexpected-impact age=" + projectile.tickCount + " type=" + event.getRayTraceResult().getType()
                        + " at=" + event.getRayTraceResult().getLocation());
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() != projectile) return;
                Frame after = frame();
                helper.assertTrue(pending != null && pending.time == after.time && pending.age == after.age,
                        "Missing natural dispenser projectile Pre/Post pair");
                steps.add(new Step(pending, after));
                log("flight pre=" + pending + " post=" + after);
                pending = null;
            });
            listeners.add(joinListener);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, joinListener);
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private Frame frame() {
            return new Frame(level.getGameTime(), projectile.tickCount, projectile.position(), projectile.getDeltaMovement(),
                    projectile.isNoGravity(), projectile.isRemoved());
        }

        private Vec3 expectedOrigin() {
            return Vec3.atCenterOf(dispenserPos).add(FACING.getStepX() * 0.7D, FACING.getStepY() * 0.7D, FACING.getStepZ() * 0.7D);
        }

        private float expectedPower() { return molotov ? 1.1F * 1.25F : 1.1F; }
        private float expectedUncertainty() { return molotov ? 6.0F * 0.5F : 6.0F; }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 60, "Dispenser watchdog powered=" + powered + " joins=" + spawned.size()
                        + " flightSteps=" + steps.size() + " remaining=" + dispenser.getItem(0).getCount());
                if (!powered && helper.getTick() >= 2) {
                    helper.assertTrue(level.isPositionEntityTicking(dispenserPos), "Owned dispenser chunk is not naturally ticking");
                    powered = true;
                    poweredAt = level.getGameTime();
                    helper.assertTrue(level.setBlockAndUpdate(powerPos, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                            "Could not apply owned redstone neighbor");
                    helper.assertTrue(level.hasNeighborSignal(dispenserPos)
                                    && level.getBlockState(dispenserPos).getValue(DispenserBlock.TRIGGERED),
                            "Natural neighbor update did not arm the dispenser");
                    log("power applied time=" + poweredAt + " triggered=true count=" + dispenser.getItem(0).getCount());
                }
                if (projectile != null && steps.size() >= 2 && level.getGameTime() - poweredAt >= 8) {
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
            helper.assertTrue(spawned.size() == 1 && projectile.getType() == projectileType && projectile.getOwner() == null
                            && !projectile.isRemoved() && impacts == 0 && drops.isEmpty()
                            && level.getBlockEntity(dispenserPos) == dispenser && level.hasNeighborSignal(dispenserPos)
                            && level.getBlockState(dispenserPos).getValue(DispenserBlock.TRIGGERED)
                            && dispenser.getItem(0).is(item) && dispenser.getItem(0).getCount() == 1,
                    "Natural dispenser activation/type/consumption/empty-flight controls failed");
            for (int slot = 1; slot < dispenser.getContainerSize(); slot++) {
                helper.assertTrue(dispenser.getItem(slot).isEmpty(), "Dispenser acquired unexpected inventory contents");
            }
            Frame previous = join;
            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                Vec3 expectedMotion = step.before.motion.scale((double) 0.99F).subtract(0, 0.05D, 0);
                helper.assertTrue(step.before.age == i + 1 && !step.before.noGravity && !step.after.noGravity
                                && !step.before.removed && !step.after.removed && near(step.before.position, previous.position)
                                && near(step.before.motion, previous.motion)
                                && near(step.after.position, step.before.position.add(step.before.motion))
                                && near(step.after.motion, expectedMotion), "Dispenser projectile did not follow natural free-flight recurrence");
                previous = step.after;
            }
            // Report all caller outcomes before the first donor assertion can stop the case.
            log("controls valid=true joins=1 remaining=1 scheduledDelay=" + (join.time - poweredAt)
                    + " flightSteps=" + steps.size() + " origin=" + join.position + " expectedOrigin=" + expectedOrigin()
                    + " error=" + join.position.distanceTo(expectedOrigin()) + " pickup=" + pickup
                    + " configPower=" + config.power() + " expectedPower=" + expectedPower()
                    + " configUncertainty=" + config.uncertainty() + " expectedUncertainty=" + expectedUncertainty()
                    + " observedLaunch=" + join.motion + " donorRequestedDirection=(0,0.1F,1); randomSpreadNotInferred=true");
            helper.assertTrue(near(join.position, expectedOrigin()), "Registered dispenser changed donor spawn point; error="
                    + join.position.distanceTo(expectedOrigin()));
            helper.assertTrue(config.power() == expectedPower() && config.uncertainty() == expectedUncertainty(),
                    "Registered projectile dispenser configuration differs from donor");
            if (!molotov) helper.assertTrue(pickup == AbstractArrow.Pickup.ALLOWED, "Dispenser knife no longer allows pickup");
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Projectile dispenser fixture observer failed", error);
            }
        }

        private static boolean near(Vec3 a, Vec3 b) { return a.distanceToSqr(b) < EPSILON * EPSILON; }
        private void log(String message) { JojoMod.LOGGER.info("PROJECTILE-DISPENSER {} {}", molotov ? "molotov" : "knife", message); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                for (Projectile created : spawned) if (!created.isRemoved()) created.discard();
                for (ItemEntity drop : drops) if (!drop.isRemoved()) drop.discard();
                if (dispenser != null) dispenser.clearContent();
            }
            finally {
                // Clear owned inventory first so replacing the dispenser cannot drop the unused item.
                if (powerPos != null && original.containsKey(powerPos)) level.setBlockAndUpdate(powerPos, original.get(powerPos));
                for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                boolean restored = original.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())
                        && level.getBlockEntity(entry.getKey()) == null);
                log("cleanup listeners=0 exactRestore=" + restored + " blocks=" + original.size() + " entities=" + spawned.size());
                if (!restored) throw new IllegalStateException("Dispenser fixture blocks/block entities not exactly restored");
            }
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
