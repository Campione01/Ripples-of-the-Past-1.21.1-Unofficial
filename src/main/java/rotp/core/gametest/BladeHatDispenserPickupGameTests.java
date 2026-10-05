package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.BladeHatEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.itemtracking.ItemTracking;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BladeHatDispenserPickupGameTests {
    private BladeHatDispenserPickupGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "blade_hat_dispenser_pickup", timeoutTicks = 50)
    public static void ownerlessDispenserHatCanBeCaughtInOrdinaryFlight(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private record HatState(UUID id, int age, Vec3 position, Vec3 velocity, AABB box,
            UUID owner, boolean leftOwner, boolean ground, boolean noPhysics,
            boolean noGravity, boolean returning, boolean removed, AbstractArrow.Pickup pickup) {}

    private record TouchWindow(HatState hat, AABB playerBox, AABB query, List<UUID> candidates,
            long time, int playerAge, int takes, int inventoryCount) {}

    private static final class Collector extends Player {
        private final Fixture fixture;

        Collector(Fixture fixture) {
            super(fixture.level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "hat-catch-player"));
            this.fixture = fixture;
        }

        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public void aiStep() {
            fixture.observe(fixture::beforeAiStep);
            super.aiStep();
            fixture.observe(fixture::afterAiStep);
        }
        @Override public void take(Entity entity, int count) {
            fixture.observe(() -> fixture.onTake(entity, count));
            super.take(entity, count);
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<Object> listeners = new ArrayList<>();
        private final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        private final List<BladeHatEntity> shots = new ArrayList<>();
        private final ItemStack expectedItem = new ItemStack(ModItems.BLADE_HAT.get());
        private Collector player;
        private PlayerPower power;
        private StandPower stand;
        private DispenserBlockEntity dispenser;
        private BlockPos dispenserPos;
        private BlockPos buttonPos;
        private BlockState dispenserState;
        private BlockState buttonState;
        private ChunkPos chunk;
        private AABB room;
        private Vec3 playerPosition;
        private Vec3 muzzle;
        private BladeHatEntity hat;
        private HatState flightPre;
        private TouchWindow pendingTouch;
        private TouchWindow completedTouch;
        private RuntimeException observerFailure;
        private int warmTicks;
        private int takes;
        private int impacts;
        private long pressedAt;
        private long flightTime;
        private boolean powered;
        private boolean insideAiStep;
        private boolean userTickActive;
        private boolean closed;

        Fixture(GameTestHelper helper) { this.helper = helper; level = helper.getLevel(); }

        private void setUp() {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            chunk = new ChunkPos(origin);
            int x = chunk.getMinBlockX(), z = chunk.getMinBlockZ(), floor = origin.getY() + 32;
            BlockPos min = new BlockPos(x + 4, floor, z + 3);
            BlockPos max = new BlockPos(x + 11, floor + 5, z + 13);
            room = AABB.encapsulatingFullBlocks(min, max);
            log("room min=" + min + " max=" + max + " ticking=" + level.isPositionEntityTicking(origin));
            premise(min.getY() >= level.getMinBuildHeight() && max.getY() < level.getMaxBuildHeight()
                    && level.getEntities((Entity) null, room).isEmpty(), "Room unavailable");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                premise(level.isPositionEntityTicking(pos) && level.isEmptyBlock(pos)
                        && level.getFluidState(pos).isEmpty() && level.getBlockEntity(pos) == null,
                        "Room cell is not clear and naturally ticking: " + pos);
            }
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x + 5, floor, z + 4),
                    new BlockPos(x + 10, floor, z + 12))) saveAndSet(pos, Blocks.STONE.defaultBlockState());
            dispenserPos = new BlockPos(x + 6, floor + 1, z + 5);
            buttonPos = dispenserPos.above();
            dispenserState = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH);
            buttonState = Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR)
                    .setValue(ButtonBlock.FACING, Direction.SOUTH);
            saveAndSet(dispenserPos, dispenserState);
            saveAndSet(buttonPos, buttonState);
            premise(level.getBlockEntity(dispenserPos) instanceof DispenserBlockEntity, "Dispenser missing");
            dispenser = (DispenserBlockEntity) level.getBlockEntity(dispenserPos);
            dispenser.setItem(0, expectedItem.copy());
            muzzle = Vec3.atCenterOf(dispenserPos).add(0, 0, 0.7);
            playerPosition = new Vec3(x + 7.75, floor + 1, z + 9.2);
            player = new Collector(this);
            GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
            player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z, 0, 0);
            power = PowerClass.PLAYER_POWER.attachGet(player);
            stand = PowerClass.STAND.attachGet(player);
            registerObservers();
            premise(level.addFreshEntity(player), "Collector join failed");
            log("setup chunk=" + chunk + " room=" + room + " player=" + playerPosition
                    + " dispenser=" + dispenserPos + " muzzle=" + muzzle + " saved=" + saved.size());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level || !(event.getEntity() instanceof BladeHatEntity created)
                        || created.getType() != ModEntityTypes.BLADE_HAT.get()
                        || !room.intersects(created.getBoundingBox()) || !isOwnedEmission(created)) return;
                if (shots.stream().noneMatch(shot -> shot.getUUID().equals(created.getUUID()))) shots.add(created);
                log("retained-emission id=" + created.getUUID() + " position=" + created.position()
                        + " time=" + level.getGameTime() + " ownedCount=" + shots.size());
                observe(() -> {
                    premise(hat == null && shots.size() == 1 && created.position().distanceToSqr(muzzle) < 1.0E-12,
                            "Unrelated or duplicate hat emission");
                    hat = created;
                    log("join " + state() + " slot=" + dispenser.getItem(0) + " armorQuery=" + armorCandidates());
                    premise(!event.isCanceled() && created.getType() == ModEntityTypes.BLADE_HAT.get()
                            && level.getGameTime() == pressedAt + 4 && dispenser.getItem(0).getCount() == 1
                            && armorCandidates().isEmpty() && level.hasNeighborSignal(dispenserPos),
                            "Registered scheduled Armor-first route unavailable");
                    requireHat();
                    premise(hat.tickCount == 0 && !hat.leftOwner && hat.getBaseDamage() == 6
                            && hat.getDeltaMovement().z > 0 && takes == 0 && inventoryCount() == 0,
                            "Unexpected initial hat state");
                });
            };
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == player) {
                    premise(!event.isCanceled() && !userTickActive, "Collector natural tick canceled or nested");
                    userTickActive = true;
                }
                if (event.getEntity() != hat || completedTouch != null) return;
                premise(!event.isCanceled() && flightPre == null, "Hat natural tick canceled or nested");
                requireHat();
                flightPre = state();
                flightTime = level.getGameTime();
                checkSegment(hat.position(), hat.position().add(hat.getDeltaMovement()), "flight-pre");
                log("flight-pre " + flightPre);
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == player) {
                    premise(userTickActive && !insideAiStep, "Collector natural tick pair missing");
                    userTickActive = false;
                }
                if (event.getEntity() != hat || completedTouch != null) return;
                premise(flightPre != null && flightTime == level.getGameTime() && flightPre.age == hat.tickCount,
                        "Hat natural Pre/Post pair missing");
                requireHat();
                premise(hat.leftOwner && !hat.isRemoved(), "Natural left-owner update missing");
                checkSegment(hat.position(), hat.position().add(hat.getDeltaMovement()), "leaf-lookahead");
                log("flight-post " + state());
                flightPre = null;
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (event.getProjectile() != hat) return;
                impacts++;
                log("unexpected-impact result=" + event.getRayTraceResult() + " canceled=" + event.isCanceled());
                premise(false, "Unexpected damage or block impact");
            });
            listeners.add(join); listeners.add(pre); listeners.add(post); listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
        }

        private void beforeAiStep() {
            premise(userTickActive && !insideAiStep, "aiStep was not naturally ticked");
            insideAiStep = true;
            if (completedTouch != null || !powered || hat == null) return;
            requireActor();
            requireHat();
            AABB query = player.getBoundingBox().inflate(1, 0.5, 1);
            List<UUID> candidates = level.getEntities(player, query).stream().map(Entity::getUUID).toList();
            log("ai-pre playerAge=" + player.tickCount + " box=" + player.getBoundingBox()
                    + " query=" + query + " candidates=" + candidates + " hat=" + state());
            premise(insideChunk(query) && level.getEntities(player, room).stream().allMatch(e -> e == hat),
                    "Foreign touch candidate or unavailable query");
            if (!candidates.contains(hat.getUUID())) return;
            premise(candidates.size() == 1 && hat.leftOwner && hat.tickCount > 0 && hat.tickCount <= 6
                    && flightPre == null && inventoryCount() == 0 && takes == 0 && impacts == 0,
                    "First touch did not have ordinary airborne admission");
            pendingTouch = new TouchWindow(state(), player.getBoundingBox(), query, candidates,
                    level.getGameTime(), player.tickCount, takes, inventoryCount());
        }

        private void afterAiStep() {
            premise(insideAiStep, "aiStep observation pair missing");
            insideAiStep = false;
            warmTicks++;
            if (!powered) log("warm ai=" + warmTicks + " position=" + player.position() + " velocity="
                    + player.getDeltaMovement() + " onGround=" + player.onGround() + " hp=" + player.getHealth());
            if (pendingTouch == null) return;
            requireActor();
            log("ai-post before=" + pendingTouch + " after=" + state() + " takes=" + takes
                    + " inventory=" + player.getInventory().items + " reason=" + hat.getRemovalReason());
            premise(player.getBoundingBox().equals(pendingTouch.playerBox)
                    && hat.position().equals(pendingTouch.hat.position) && hat.tickCount == pendingTouch.hat.age
                    && level.getGameTime() == pendingTouch.time && player.tickCount == pendingTouch.playerAge
                    && hat.getDeltaMovement().equals(pendingTouch.hat.velocity)
                    && !hat.isInGround() && !hat.isNoPhysics() && !hat.isReturningToOwner()
                    && hat.getOwner() == null && hat.leftOwner && hat.pickup == AbstractArrow.Pickup.ALLOWED,
                    "Touch window changed movement, identity or pickup admission");
            if (!hat.isRemoved()) {
                premise(level.getEntities(player, pendingTouch.query).contains(hat),
                        "Live hat escaped the actual vanilla touch query");
            }
            completedTouch = pendingTouch;
            pendingTouch = null;
        }

        private void onTake(Entity entity, int count) {
            log("take entity=" + entity.getUUID() + " count=" + count + " insideAi=" + insideAiStep);
            premise(entity == hat && insideAiStep && pendingTouch != null && count == 1 && takes == 0
                    && !hat.isRemoved() && inventoryCount() == 1, "Take callback lacks the natural touch window");
            takes++;
        }

        private void requireActor() {
            premise(player.isAlive() && player.getHealth() == player.getMaxHealth()
                    && player.position().distanceToSqr(playerPosition) < 1.0E-12 && player.onGround()
                    && !player.isCreative() && !player.isSpectator() && !player.getAbilities().instabuild
                    && !player.getAbilities().invulnerable && !player.getAbilities().flying
                    && !player.isNoGravity() && !player.isPassenger() && !player.isUsingItem()
                    && !player.isShiftKeyDown() && player.getActiveEffects().isEmpty()
                    && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && player.getShoulderEntityLeft().isEmpty() && player.getShoulderEntityRight().isEmpty()
                    && power.getPowerType() == null && stand.getPowerType() == null && !stand.hasPower()
                    && stand.getSummonedStandEntity() == null && level.isPositionEntityTicking(player.blockPosition()),
                    "Collector lost its ordinary stationary admission");
        }

        private void requireHat() {
            premise(hat.getOwner() == null && hat.pickup == AbstractArrow.Pickup.ALLOWED && !hat.isRemoved()
                    && hat.isNoGravity() && !hat.isNoPhysics() && !hat.isInGround() && !hat.isReturningToOwner()
                    && hat.getPickupItem().getCount() == 1 && ItemStack.isSameItemSameComponents(hat.getPickupItem(), expectedItem)
                    && ItemTracking.getTrackerId(hat.getPickupItem()) == null
                    && insideChunk(hat.getBoundingBox()) && level.isPositionEntityTicking(hat.blockPosition()),
                    "Hat lost ordinary ownerless airborne state");
        }

        private void checkSegment(Vec3 start, Vec3 end, String phase) {
            AABB query = hat.getBoundingBox().expandTowards(hat.getDeltaMovement()).inflate(1);
            List<Entity> candidates = level.getEntities(hat, query, e -> !e.isSpectator() && e.canBeHitByProjectile());
            HitResult.Type collider = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, hat)).getType();
            HitResult.Type outline = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, hat)).getType();
            AABB target = player.getBoundingBox().inflate((double) 0.3F);
            boolean targetClip = target.contains(start) || target.clip(start, end).isPresent();
            log(phase + " start=" + start + " end=" + end + " query=" + query + " candidates="
                    + candidates.stream().map(Entity::getUUID).toList() + " target=" + target
                    + " targetClip=" + targetClip + " collider=" + collider + " outline=" + outline);
            premise(insideChunk(query) && candidates.stream().allMatch(e -> e == player) && !targetClip
                    && collider == HitResult.Type.MISS && outline == HitResult.Type.MISS,
                    "Flight is not collision-free beside the collector");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(query.minX, query.minY, query.minZ),
                    BlockPos.containing(query.maxX, query.maxY, query.maxZ))) {
                premise(level.isPositionEntityTicking(pos) && level.getFluidState(pos).isEmpty(),
                        "Flight query has unavailable cells or fluid");
            }
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                premise(helper.getTick() < 35, "Watchdog expired before a proved touch");
                if (warmTicks >= 3 && !powered) {
                    requireActor();
                    premise(saved.size() == 56 && armorCandidates().isEmpty() && inventoryCount() == 0
                            && player.getInventory().isEmpty() && dispenser.getItem(0).getCount() == 1
                            && level.getBlockState(dispenserPos).equals(dispenserState)
                            && level.getBlockState(buttonPos).equals(buttonState) && !level.hasNeighborSignal(dispenserPos),
                            "Redstone/Armor-first input is not ordinary");
                    log("press warm=" + warmTicks + " armorQuery=" + armorCandidates() + " player=" + player.getBoundingBox());
                    powered = true;
                    pressedAt = level.getGameTime();
                    BlockPos relativeButton = buttonPos.subtract(helper.absolutePos(BlockPos.ZERO));
                    premise(helper.absolutePos(relativeButton).equals(buttonPos), "Button input transform does not match this scene");
                    helper.pressButton(relativeButton);
                    premise(level.getBlockState(buttonPos).getValue(ButtonBlock.POWERED)
                            && level.getBlockState(dispenserPos).getValue(DispenserBlock.TRIGGERED), "Button did not power dispenser");
                }
                if (hat != null) premise(dispenser.isEmpty() && shots.size() == 1, "Natural dispenser debit missing");
                if (completedTouch != null) {
                    log("result window=" + completedTouch + " hat=" + state() + " takes=" + takes
                            + " inventory=" + inventoryCount() + " impacts=" + impacts + " removal=" + hat.getRemovalReason());
                    requireActor();
                    helper.assertTrue(takes == 1 && inventoryCount() == 1 && hat.isRemoved()
                            && player.getInventory().items.stream().filter(s -> !s.isEmpty()).count() == 1
                            && hat.getRemovalReason() == Entity.RemovalReason.DISCARDED && impacts == 0,
                            "HAT-ORACLE: naturally admitted ownerless airborne hat was not collected");
                    close(); helper.succeed(); return;
                }
                if (hat != null) premise(hat.tickCount <= 6, "No natural eligible touch by age6");
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }

        private boolean isOwnedEmission(BladeHatEntity created) {
            return powered && level.getGameTime() == pressedAt + 4 && created.getOwner() == null
                    && created.getPickupItem().getCount() == 1
                    && ItemStack.isSameItemSameComponents(created.getPickupItem(), expectedItem)
                    && ItemTracking.getTrackerId(created.getPickupItem()) == null
                    && dispenser != null && level.getBlockEntity(dispenserPos) == dispenser
                    && level.getBlockState(dispenserPos).equals(dispenserState.setValue(DispenserBlock.TRIGGERED, true))
                    && level.hasNeighborSignal(dispenserPos) && dispenser.getItem(0).getCount() == 1
                    && ItemStack.isSameItemSameComponents(dispenser.getItem(0), expectedItem)
                    && armorCandidates().isEmpty();
        }

        private List<UUID> armorCandidates() {
            return level.getEntitiesOfClass(LivingEntity.class, new AABB(dispenserPos.relative(Direction.SOUTH)),
                    EntitySelector.NO_SPECTATORS.and(new EntitySelector.MobCanWearArmorEntitySelector(expectedItem)))
                    .stream().map(Entity::getUUID).toList();
        }
        private int inventoryCount() {
            return player.getInventory().items.stream().filter(s -> ItemStack.isSameItemSameComponents(s, expectedItem))
                    .mapToInt(ItemStack::getCount).sum();
        }
        private boolean insideChunk(AABB box) {
            return box.minX >= chunk.getMinBlockX() && box.maxX < chunk.getMinBlockX() + 16
                    && box.minZ >= chunk.getMinBlockZ() && box.maxZ < chunk.getMinBlockZ() + 16;
        }
        private HatState state() {
            return new HatState(hat.getUUID(), hat.tickCount, hat.position(), hat.getDeltaMovement(), hat.getBoundingBox(),
                    hat.getOwner() == null ? null : hat.getOwner().getUUID(), hat.leftOwner, hat.isInGround(),
                    hat.isNoPhysics(), hat.isNoGravity(), hat.isReturningToOwner(), hat.isRemoved(), hat.pickup);
        }
        private void saveAndSet(BlockPos pos, BlockState state) {
            BlockPos key = pos.immutable();
            saved.put(key, level.getBlockState(key));
            premise(level.setBlock(key, state, 3), "Owned block setup failed");
        }
        private void premise(boolean condition, String message) { helper.assertTrue(condition, "HAT-PREMISE: " + message); }
        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException error) {
                observerFailure = error;
                log("observer-failure player=" + (player == null ? null : player.position())
                        + " onGround=" + (player != null && player.onGround()) + " userTickActive=" + userTickActive
                        + " insideAi=" + insideAiStep + " hat=" + (hat == null ? null : state()));
                JojoMod.LOGGER.error("HAT-DISPENSER observer failure", error);
            }
        }
        private void log(String message) { JojoMod.LOGGER.info("HAT-DISPENSER {}", message); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            List<Throwable> failures = new ArrayList<>();
            try {
                for (Object listener : listeners) cleanupStep(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
                listeners.clear();
                for (BladeHatEntity shot : shots) cleanupStep(() -> { if (!shot.isRemoved()) shot.discard(); }, failures);
                if (player != null) {
                    cleanupStep(player::stopUsingItem, failures);
                    cleanupStep(() -> player.getInventory().clearContent(), failures);
                    cleanupStep(player::discard, failures);
                }
            }
            finally {
                cleanupStep(() -> {
                    if (dispenser != null && level.getBlockEntity(dispenserPos) == dispenser) dispenser.clearContent();
                }, failures);
                List<BlockPos> positions = new ArrayList<>(saved.keySet());
                for (int i = positions.size() - 1; i >= 0; i--) {
                    BlockPos pos = positions.get(i);
                    cleanupStep(() -> level.setBlock(pos, saved.get(pos), 3), failures);
                }
                for (BlockPos pos : positions) cleanupStep(() -> {
                    if (!saved.get(pos).isAir() || !level.getBlockState(pos).equals(saved.get(pos))
                            || level.getBlockEntity(pos) != null) {
                        throw new IllegalStateException("HAT-CLEANUP: original-air restoration failed at " + pos);
                    }
                }, failures);
            }
            if (!failures.isEmpty()) {
                IllegalStateException failure = new IllegalStateException("HAT-CLEANUP: owned cleanup was not verified");
                failures.forEach(failure::addSuppressed);
                throw failure;
            }
            log("cleanup-verified listeners=0 ownedHats=" + shots.size() + " restoredAirCells=" + saved.size()
                    + " expectedFullScene=56 restoredBlockEntities=0");
        }
        private void cleanupStep(Runnable cleanup, List<Throwable> failures) {
            try { cleanup.run(); }
            catch (RuntimeException | Error error) { failures.add(error); }
        }
        private void closeAfterFailure(Throwable primary) {
            try { close(); }
            catch (RuntimeException | Error cleanup) {
                primary.addSuppressed(cleanup);
                JojoMod.LOGGER.error("HAT-DISPENSER cleanup failure; original failure retained", cleanup);
            }
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) {
            if (test.getError() != null) closeAfterFailure(test.getError()); else close();
        }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }
}
