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
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismFreezeAbility;
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
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.LiquidOnlyClipContext;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampireFreezePlacementGameTests {
    private static final short KEY = 25;
    private static final BlockState ICE = Blocks.FROSTED_ICE.defaultBlockState();

    private VampireFreezePlacementGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "vampire_freeze_placement", timeoutTicks = 100)
    public static void registeredFreezeKeepsLivingOccupiedWaterUnfrozen(GameTestHelper helper) {
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

    private record Query(BlockPos pos, BlockState state, boolean airAbove, boolean survives,
            boolean unobstructed, boolean noCollision, boolean cowOverlap) {}

    private record Before(long time, int age, Vec3 userPosition, float blood,
            Query occupied, Query empty, AABB cowBox, boolean cowBlocksBuilding, boolean cowCollidable) {}

    private record Result(long time, int age, BlockState occupied, BlockState empty,
            float blood, boolean grounded, ActionPhase phase, List<BlockEvent.EntityPlaceEvent> placements) {}

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final List<Object> listeners = new ArrayList<>();
        private final List<BlockEvent.EntityPlaceEvent> placements = new ArrayList<>();
        private Player user;
        private Cow cow;
        private PlayerPower power;
        private VampirismData data;
        private Ability ability;
        private EntityActionInstance action;
        private BlockPos occupied;
        private BlockPos empty;
        private Before before;
        private Result result;
        private RuntimeException observerFailure;
        private int userTicks;
        private boolean pressed;
        private boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
        }

        private void setUp() {
            helper.assertTrue(level.getDifficulty() != Difficulty.PEACEFUL && !level.dimensionType().ultraWarm(),
                    "Freeze placement requires a non-Peaceful, non-ultrawarm world; global settings are not changed");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int y = template.getY() + 32;
            BlockPos min = new BlockPos(x + 4, y - 1, z + 4);
            BlockPos max = new BlockPos(x + 12, y + 4, z + 12);
            helper.assertTrue(y + 5 < level.getMaxBuildHeight(), "Freeze placement enclosure exceeds build height");
            helper.assertTrue(level.getEntities((Entity) null, AABB.encapsulatingFullBlocks(min, max)).isEmpty(),
                    "Freeze placement enclosure contains another entity");
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                helper.assertTrue(level.isEmptyBlock(pos), "Freeze placement enclosure is obstructed at " + pos);
                original.put(pos.immutable(), level.getBlockState(pos));
            }
            // Two-layer floor contains source water; the owned roof prevents vampire sunlight damage.
            for (BlockPos pos : original.keySet()) {
                if (pos.getY() <= y || pos.getY() == max.getY()
                        || pos.getX() == min.getX() || pos.getX() == max.getX()
                        || pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) {
                    level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                }
            }
            occupied = new BlockPos(x + 6, y, z + 8);
            empty = new BlockPos(x + 10, y, z + 8);
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.moveTo(x + 8.5D, y + 1.125D, z + 8.5D, 0, 0);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the ordinary gravity-enabled vampire user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, user);
            VampirismState.get(user).blood().setCurrent(100);
            data.setBloodLevel(100);
            user.setHealth(user.getMaxHealth());
            ability = power.getAbility("vampirism_freeze");
            helper.assertTrue(ability instanceof VampirismFreezeAbility
                            && ability.abilityType == VampirismPowerType.VAMPIRE_FREEZE.get(),
                    "Fixture did not resolve registered Vampire Freeze");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            registerObservers();
            log("setup user=" + user.getUUID() + " occupied=" + occupied + " empty=" + empty
                    + " savedBlocks=" + original.size() + " difficulty=" + level.getDifficulty());
        }

        private void registerObservers() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == user && pressed && result == null) {
                    if (event.isCanceled() || before != null) throw new IllegalStateException("Freeze tick canceled or duplicated");
                    requireEligible();
                    Query occupiedQuery = query(occupied);
                    Query emptyQuery = query(empty);
                    requireQueries(occupiedQuery, emptyQuery);
                    before = new Before(level.getGameTime(), user.tickCount, user.position(),
                            VampirismState.get(user).blood().current(), occupiedQuery, emptyQuery,
                            cow.getBoundingBox(), cow.blocksBuilding, cow.canBeCollidedWith());
                    log("first-eligible-pre " + before);
                }
            });
            Consumer<BlockEvent.EntityPlaceEvent> place = event -> observe(() -> {
                if (event.getEntity() == user && pressed && result == null
                        && (event.getPos().equals(occupied) || event.getPos().equals(empty))) {
                    if (before == null) throw new IllegalStateException("Freeze placement occurred outside observed user tick");
                    placements.add(event);
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) {
                    userTicks++;
                    if (pressed && result == null) {
                        if (before == null || before.time != level.getGameTime() || before.age != user.tickCount) {
                            throw new IllegalStateException("Freeze placement lacks its paired natural pre tick");
                        }
                        result = new Result(level.getGameTime(), user.tickCount,
                                level.getBlockState(occupied), level.getBlockState(empty),
                                VampirismState.get(user).blood().current(), user.onGround(), action.getPhase(),
                                List.copyOf(placements));
                        log("first-eligible-post time=" + result.time + " age=" + result.age
                                + " occupied=" + result.occupied + " empty=" + result.empty
                                + " blood=" + before.blood + "->" + result.blood + " grounded=" + result.grounded
                                + " phase=" + result.phase + " placements=" + placements.stream()
                                        .map(p -> p.getPos() + "/canceled=" + p.isCanceled()).toList());
                    }
                }
            });
            listeners.add(pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTickEvent.Pre.class, pre);
            listeners.add(place);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockEvent.EntityPlaceEvent.class, place);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private Query query(BlockPos pos) {
            VoxelShape shape = ICE.getCollisionShape(level, pos);
            if (shape.isEmpty()) throw new IllegalStateException("Frosted ice has no collision shape");
            boolean overlap = Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()),
                    Shapes.create(cow.getBoundingBox()), BooleanOp.AND);
            return new Query(pos, level.getBlockState(pos), level.getBlockState(pos.above()).isAir(),
                    ICE.canSurvive(level, pos), level.isUnobstructed(ICE, pos, CollisionContext.empty()),
                    level.noCollision(shape.bounds().move(pos)), overlap);
        }

        private void requireQueries(Query occupiedQuery, Query emptyQuery) {
            if (!sourceWater(occupiedQuery.state) || !sourceWater(emptyQuery.state)
                    || !occupiedQuery.airAbove || !emptyQuery.airAbove || !occupiedQuery.survives || !emptyQuery.survives
                    || occupiedQuery.unobstructed || !occupiedQuery.noCollision || !occupiedQuery.cowOverlap
                    || !emptyQuery.unobstructed || !emptyQuery.noCollision || emptyQuery.cowOverlap
                    || !cow.isAlive() || cow.isRemoved() || !cow.blocksBuilding || cow.canBeCollidedWith()) {
                throw new IllegalStateException("Freeze placement discriminator is invalid: occupied=" + occupiedQuery
                        + ", empty=" + emptyQuery + ", cowBlocksBuilding=" + cow.blocksBuilding
                        + ", cowCollidable=" + cow.canBeCollidedWith());
            }
        }

        private void requireEligible() {
            if (!user.onGround() || user.isNoGravity() || !user.isAlive() || user.isOnFire()
                    || level.canSeeSky(user.blockPosition()) || !level.isPositionEntityTicking(user.blockPosition())
                    || !level.isPositionEntityTicking(cow.blockPosition()) || !data.isVampireAtFullPower()
                    || data.getCuringStage(user) > 1 || VampirismState.get(user).blood().current() < 0.45F
                    || !user.getMainHandItem().isEmpty()
                    || LivingComponentAction.getComponent(user).getAction() != action
                    || action.getPhase() != ActionPhase.PERFORM
                    || LivingComponentAction.getAim(user).getTarget().getType() != ActionTarget.TargetType.EMPTY
                    || !ability.checkMainModLogicConditions(power).isPositive() || !ability.checkSpecificConditions(power).isPositive()
                    || !occupied.closerToCenterThan(user.position(), 4) || !empty.closerToCenterThan(user.position(), 4)
                    || occupied.getY() != user.blockPosition().getY() - 1 || empty.getY() != occupied.getY()) {
                throw new IllegalStateException("Registered Freeze is not naturally grounded and eligible on the observed tick");
            }
            Vec3 eye = user.getEyePosition();
            Vec3 look = user.getLookAngle();
            if (level.clip(new LiquidOnlyClipContext(eye.add(look), eye.add(look.scale(8)),
                    ClipContext.Fluid.SOURCE_ONLY, user)).getType() != HitResult.Type.MISS) {
                throw new IllegalStateException("The liquid ray, rather than the grounded scan, could reach fixture water");
            }
        }

        private void press() {
            // Install the source cells immediately before HOLD, after natural grounding, not during warmup.
            level.setBlockAndUpdate(occupied, Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(empty, Blocks.WATER.defaultBlockState());
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not create ordinary placement-blocking Cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.setPos(occupied.getX() + 0.5D, occupied.getY() + 0.5D, occupied.getZ() + 0.5D);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add placement-blocking Cow");
            Query occupiedQuery = query(occupied);
            Query emptyQuery = query(empty);
            requireQueries(occupiedQuery, emptyQuery);
            log("pre-input occupied=" + occupiedQuery + " empty=" + emptyQuery + " cow=" + cow.getUUID()
                    + " cowBox=" + cow.getBoundingBox() + " blocksBuilding=" + cow.blocksBuilding
                    + " collidable=" + cow.canBeCollidedWith() + " cowNoAI=true cowNoGravity=true userGrounded=" + user.onGround());
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD),
                    "Registered Vampire Freeze HOLD was not admitted");
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            helper.assertTrue(input != null && input.action instanceof VampirismFreezeAbility.FreezeInstance freeze
                            && freeze == LivingComponentAction.getComponent(user).getAction() && freeze.ability == ability,
                    "Registered HOLD did not install the real player Freeze action");
            action = (EntityActionInstance) input.action;
            pressed = true;
            log("press ability=" + ability.getAbilityId() + " naturalUserTicks=" + userTicks + " onGround=" + user.onGround());
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Freeze placement watchdog: grounded=" + user.onGround()
                        + " naturalUserTicks=" + userTicks + " pressed=" + pressed);
                if (!pressed && userTicks >= 2 && user.onGround()) press();
                if (result != null) {
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
            helper.assertTrue(before != null && result.time == before.time && result.age == before.age
                            && result.grounded && result.phase == ActionPhase.PERFORM && result.blood < before.blood,
                    "Freeze result did not follow the first eligible paid natural hold tick");
            helper.assertTrue(result.placements.stream().noneMatch(BlockEvent.EntityPlaceEvent::isCanceled)
                            && result.placements.stream().filter(p -> p.getPos().equals(empty)).count() == 1,
                    "The empty-cell control lacked one uncanceled production placement event");
            helper.assertTrue(result.empty.is(Blocks.FROSTED_ICE), "Eligible empty source-water control did not freeze");
            helper.assertTrue(sourceWater(result.occupied),
                    "Freeze ignored living placement obstruction: occupied=" + result.occupied + ", control=" + result.empty);
            log("result occupiedWater=true emptyFrostedIce=true firstEligibleTick=true nativeProof=false");
        }

        private static boolean sourceWater(BlockState state) {
            return state.is(Blocks.WATER) && state.getValue(LiquidBlock.LEVEL) == 0;
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Freeze placement observer failure", error);
            }
        }

        private void log(String message) {
            JojoMod.LOGGER.info("FREEZE-PLACEMENT {}", message);
        }

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
                    if (cow != null) cow.discard();
                    if (user != null) user.discard();
                }
                finally {
                    for (var entry : original.entrySet()) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                    boolean restored = original.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue()));
                    log("cleanup listeners=0 restored=" + restored + " savedBlocks=" + original.size());
                    if (!restored) throw new IllegalStateException("Freeze placement enclosure was not exactly restored");
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
