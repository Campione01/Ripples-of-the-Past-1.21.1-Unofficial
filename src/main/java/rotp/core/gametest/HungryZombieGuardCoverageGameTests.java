package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismZombieSummonAbility;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;
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
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * Runtime cover for three Hungry Zombie facts that had only been read in source: the 1.16 ZombieOwnerHurtByTargetGoal
 * far-owner guard, the ZombieFollowOwnerGoal leash guard, and how soon after its click the summon emits.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HungryZombieGuardCoverageGameTests {
    private static final String BATCH = "hungry_zombie_guard_coverage";

    private HungryZombieGuardCoverageGameTests() {}

    @GameTest(template = "empty", batch = BATCH)
    public static void zombieNearItsOwnerTargetsTheOwnersAttacker(GameTestHelper helper) {
        ownerHurtBy(helper, 8.0D, true);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void zombieFarFromItsOwnerIgnoresTheOwnersAttacker(GameTestHelper helper) {
        ownerHurtBy(helper, 14.0D, false);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void unleashedZombieNavigatesToItsOwner(GameTestHelper helper) {
        followOwner(helper, false);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void leashedZombieDoesNotNavigateToItsOwner(GameTestHelper helper) {
        followOwner(helper, true);
    }

    @GameTest(template = "empty", batch = "hungry_zombie_summon_click_timing", timeoutTicks = 60)
    public static void summonEmitsAtCasterPoseWithinOneTickOfClick(GameTestHelper helper) {
        SummonTiming fixture = new SummonTiming(helper);
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

    private static void ownerHurtBy(GameTestHelper helper, double ownerDistance, boolean targeted) {
        try (Scene scene = Scene.open(helper, ownerDistance)) {
            Zombie attacker = EntityType.ZOMBIE.create(scene.level);
            scene.premise(attacker != null, "could not create the attacker");
            attacker.setNoAi(true);
            attacker.setNoGravity(true);
            attacker.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 3.0D, 180.0F, 0.0F);
            scene.owned.add(attacker);
            scene.premise(scene.level.addFreshEntity(attacker), "attacker was not added");

            // The zombie caches its owner distance at the end of its own tick.
            scene.zombie.tick();
            boolean far = ownerDistance > 12.0D;
            scene.premise(scene.zombie.farFromOwner(12.0D) == far && scene.zombie.getTarget() == null
                            && Math.abs(scene.zombie.distanceTo(scene.owner) - ownerDistance) < 0.5D
                            && scene.zombie.hasLineOfSight(attacker),
                    "owner distance cache did not settle: far=" + scene.zombie.farFromOwner(12.0D)
                            + " distance=" + scene.zombie.distanceTo(scene.owner) + " target=" + scene.zombie.getTarget());
            scene.owner.tickCount = 40;
            scene.owner.setLastHurtByMob(attacker);
            scene.premise(scene.owner.getLastHurtByMob() == attacker && scene.owner.getLastHurtByMobTimestamp() == 40,
                    "owner has no fresh attacker memory");
            WrappedGoal ownerHurtBy = scene.goal(scene.zombie.targetSelector.getAvailableGoals(),
                    "HungryZombieOwnerHurtByTargetGoal");
            scene.zombie.tick();
            boolean running = ownerHurtBy.isRunning();
            Entity target = scene.zombie.getTarget();
            JojoMod.LOGGER.info("HZ-COVERAGE owner-hurt-by distance={} far={} goalRunning={} target={}",
                    ownerDistance, far, running, target);
            helper.assertTrue(running == targeted && (target == attacker) == targeted,
                    "Owner's attacker at owner distance " + ownerDistance + ": goalRunning=" + running
                            + " target=" + target + " expected targeted=" + targeted
                            + " (1.16 ZombieOwnerHurtByTargetGoal refuses when farFromOwner(12))");
        }
        helper.succeed();
    }

    private static void followOwner(GameTestHelper helper, boolean leashed) {
        try (Scene scene = Scene.open(helper, 11.0D)) {
            ArmorStand holder = null;
            if (leashed) {
                holder = EntityType.ARMOR_STAND.create(scene.level);
                scene.premise(holder != null, "could not create the leash holder");
                holder.setNoGravity(true);
                holder.moveTo(scene.origin.x, scene.origin.y, scene.origin.z - 1.0D, 0.0F, 0.0F);
                scene.owned.add(holder);
                scene.premise(scene.level.addFreshEntity(holder), "leash holder was not added");
                scene.zombie.setLeashedTo(holder, true);
                scene.premise(scene.zombie.isLeashed() && scene.zombie.getLeashHolder() == holder, "zombie was not leashed");
            }
            WrappedGoal follow = scene.goal(scene.zombie.goalSelector.getAvailableGoals(), "HungryZombieFollowOwnerGoal");
            PathNavigation navigation = scene.zombie.getNavigation();
            boolean followRan = false;
            boolean ownerPath = false;
            int ticks = 0;
            while (ticks < 12 && !ownerPath) {
                scene.zombie.tick();
                ticks++;
                double distanceSq = scene.zombie.distanceToSqr(scene.owner);
                scene.premise(scene.zombie.isAlive() && scene.zombie.getTarget() == null && !scene.zombie.farFromOwner(12.0D)
                                && distanceSq <= 144.0D && scene.zombie.isLeashed() == leashed
                                && (!leashed || scene.zombie.getLeashHolder() == holder),
                        "follow scene changed at tick " + ticks + ": distanceSq=" + distanceSq
                                + " leashed=" + scene.zombie.isLeashed() + " target=" + scene.zombie.getTarget());
                followRan |= follow.isRunning();
                ownerPath = navigation.getPath() != null && scene.owner.blockPosition().equals(navigation.getTargetPos());
            }
            JojoMod.LOGGER.info("HZ-COVERAGE follow leashed={} ticks={} followRan={} ownerPath={} navTarget={} onGround={}",
                    leashed, ticks, followRan, ownerPath, navigation.getTargetPos(), scene.zombie.onGround());
            scene.premise(followRan, "the owner-follow goal never ran, so its leash check was not reached");
            helper.assertTrue(ownerPath == !leashed,
                    "Zombie 11 blocks from its owner, leashed=" + leashed + ": ownerPath=" + ownerPath + " after " + ticks
                            + " ticks, navTarget=" + navigation.getTargetPos() + " owner=" + scene.owner.blockPosition()
                            + " (1.16 ZombieFollowOwnerGoal does not navigate while leashed)");
        }
        helper.succeed();
    }

    private static final class Scene implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        private final List<Entity> owned = new ArrayList<>();
        private Vec3 origin;
        private FakePlayer owner;
        private PlayerPower power;
        private HungryZombieEntity zombie;

        private Scene(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
        }

        private static Scene open(GameTestHelper helper, double ownerDistance) {
            Scene scene = new Scene(helper);
            try {
                scene.setUp(ownerDistance);
                return scene;
            }
            catch (RuntimeException | Error error) {
                scene.close();
                throw error;
            }
        }

        private void premise(boolean condition, String message) {
            helper.assertTrue(condition, "HZ-COVERAGE premise: " + message);
        }

        private void setUp(double ownerDistance) {
            premise(level.getDifficulty() != Difficulty.PEACEFUL, "requires a non-peaceful world");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            int floorY = template.getY() + 40;
            int x0 = chunk.getMinBlockX();
            int z0 = chunk.getMinBlockZ();
            origin = new Vec3(x0 + 8.5D, floorY, z0 + 1.5D);
            AABB room = new AABB(x0 + 6, floorY - 1, z0, x0 + 11, floorY + 3, z0 + 16);
            premise(room.maxY < level.getMaxBuildHeight() && level.getEntities((Entity) null, room.inflate(2.0D)).isEmpty(),
                    "scene is out of bounds or holds a foreign entity");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(room.maxX - 1, room.maxY - 1, room.maxZ - 1))) {
                premise(level.isEmptyBlock(pos), "scene is not empty at " + pos);
            }
            for (int x = 7; x <= 9; x++) {
                for (int z = 0; z <= 15; z++) {
                    BlockPos pos = new BlockPos(x0 + x, floorY - 1, z0 + z);
                    cells.put(pos, level.getBlockState(pos));
                    premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "could not place the floor");
                }
            }
            owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HungryCoverOwner"));
            owned.add(owner);
            owner.setGameMode(GameType.SURVIVAL);
            owner.moveTo(origin.x, origin.y, origin.z + ownerDistance, 180.0F, 0.0F);
            level.addNewPlayer(owner);
            power = PowerClass.PLAYER_POWER.attachGet(owner);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            VampirismData data = PlayerPower.getPowerData(owner, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, owner);

            zombie = ModEntityTypes.HUNGRY_ZOMBIE.get().create(level);
            premise(zombie != null, "could not create the Hungry Zombie");
            owned.add(zombie);
            zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            zombie.setOwner(owner);
            premise(level.addFreshEntity(zombie) && !zombie.isNoAi() && zombie.getOwner() == owner
                            && zombie.isEntityOwner(owner) && level.getPlayerByUUID(owner.getUUID()) == owner,
                    "zombie does not resolve its vampire owner");
        }

        private WrappedGoal goal(Iterable<WrappedGoal> goals, String simpleName) {
            List<WrappedGoal> matches = new ArrayList<>();
            for (WrappedGoal goal : goals) {
                if (goal.getGoal().getClass().getSimpleName().equals(simpleName)) matches.add(goal);
            }
            premise(matches.size() == 1, "registered goal " + simpleName + " is not unique: " + matches.size());
            return matches.get(0);
        }

        @Override
        public void close() {
            try {
                if (zombie != null && zombie.isLeashed()) zombie.dropLeash(true, false);
                if (power != null) power.setPowerType(null);
            }
            finally {
                try {
                    for (Entity entity : owned) {
                        if (!entity.isRemoved()) entity.discard();
                    }
                }
                finally {
                    for (var cell : cells.entrySet()) level.setBlockAndUpdate(cell.getKey(), cell.getValue());
                    cells.clear();
                }
            }
        }
    }

    private record Emission(long gameTime, int ownerPosts, int age, Vec3 position, float yRot, float xRot, Vec3 motion,
            Vec3 ownerPosition, float ownerYRot, float ownerXRot, boolean ownerTag, boolean summonTag,
            ActionPhase phase, float phaseTick) {}

    private static final class SummonTiming implements GameTestListener {
        private static final short KEY = 74;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<Object> listeners = new ArrayList<>();
        private final List<HungryZombieEntity> zombies = new ArrayList<>();
        private final List<Emission> emissions = new ArrayList<>();
        private FakePlayer owner;
        private PlayerPower power;
        private Ability summon;
        private EntityActionInstance action;
        private Throwable observerFailure;
        private int ownerPosts;
        private int pressPosts;
        private int emittedAtPressReturn;
        private long pressTime = -1L;
        private boolean pressed;
        private boolean closed;

        private SummonTiming(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
        }

        private void premise(boolean condition, String message) {
            helper.assertTrue(condition, "HZ-COVERAGE summon premise: " + message);
        }

        private void setUp() {
            premise(level.getDifficulty() != Difficulty.PEACEFUL, "requires a non-peaceful world");
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            Vec3 origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 8.5D);
            premise(level.getEntitiesOfClass(HungryZombieEntity.class,
                            new AABB(origin, origin).inflate(17.0D, 512.0D, 17.0D)).isEmpty(),
                    "scene already holds a Hungry Zombie");
            owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HungrySummoner"));
            owner.setGameMode(GameType.SURVIVAL);
            owner.moveTo(origin.x, origin.y, origin.z, 37.0F, -12.0F);
            level.addNewPlayer(owner);
            power = PowerClass.PLAYER_POWER.attachGet(owner);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            VampirismData data = PlayerPower.getPowerData(owner, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, owner);
            VampirismState.get(owner).blood().setCurrent(500.0F);
            data.setBloodLevel(VampirismState.get(owner).blood().current());
            summon = power.getAbility("vampirism_zombie_summon");
            premise(summon instanceof VampirismZombieSummonAbility
                    && summon.abilityType == VampirismPowerType.VAMPIRE_ZOMBIE_SUMMON.get(), "registered summon is missing");

            Consumer<EntityTickEvent.Post> post = event -> {
                if (!closed && event.getEntity() == owner) ownerPosts++;
            };
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level || !(event.getEntity() instanceof HungryZombieEntity zombie)
                        || !zombie.isEntityOwner(owner)) {
                    return;
                }
                zombies.add(zombie);
                try {
                    CompoundTag tag = zombie.saveWithoutId(new CompoundTag());
                    emissions.add(new Emission(level.getGameTime(), ownerPosts, zombie.tickCount, zombie.position(),
                            zombie.getYRot(), zombie.getXRot(), zombie.getDeltaMovement(), owner.position(),
                            owner.getYRot(), owner.getXRot(),
                            tag.hasUUID("Owner") && tag.getUUID("Owner").equals(owner.getUUID()),
                            tag.getBoolean("AbilitySummon"), action != null ? action.getPhase() : null,
                            action != null ? action.getPhaseTick() : Float.NaN));
                }
                catch (RuntimeException | Error error) {
                    observerFailure = error;
                }
            };
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityTickEvent.Post.class, post);
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 40, "summon did not emit in time: ownerPosts=" + ownerPosts + " pressed=" + pressed);
                int expected = level.getDifficulty().getId();
                if (!pressed) {
                    if (ownerPosts >= 2) {
                        AvailableAbilities available = new AvailableAbilities();
                        available.update(power, power.getMoveset());
                        premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(summon), owner,
                                InputMethod.CLICK), "registered CLICK was not admitted");
                        pressTime = level.getGameTime();
                        pressPosts = ownerPosts;
                        pressed = true;
                        var entry = AbilityInput.keyPress(KEY, summon, owner, null, InputMethod.CLICK, 0.0F,
                                BufferingState.clickOnly(), summon.getAbilityId());
                        premise(entry != null && entry.action instanceof VampirismZombieSummonAbility.ZombieSummonInstance
                                && entry.action == LivingComponentAction.getCurEntityAction(owner), "CLICK installed no summon action");
                        action = (EntityActionInstance) entry.action;
                        emittedAtPressReturn = emissions.size();
                    }
                }
                else if (emissions.size() >= expected) {
                    JojoMod.LOGGER.info("HZ-COVERAGE summon pressTime={} pressPosts={} emittedAtPressReturn={} emissions={}",
                            pressTime, pressPosts, emittedAtPressReturn, emissions);
                    boolean prompt = emissions.stream().allMatch(emission -> emission.gameTime - pressTime >= 0L
                            && emission.gameTime - pressTime <= 1L && emission.ownerPosts == pressPosts);
                    boolean atCaster = emissions.stream().allMatch(emission -> emission.age == 0
                            && emission.position.distanceTo(emission.ownerPosition) < 1.0E-9D
                            && emission.yRot == emission.ownerYRot && emission.xRot == emission.ownerXRot
                            && emission.yRot == 37.0F && emission.xRot == -12.0F
                            && emission.motion.lengthSqr() == 0.0D);
                    boolean owned = emissions.stream().allMatch(emission -> emission.ownerTag && emission.summonTag
                            && emission.phase == ActionPhase.PERFORM && emission.phaseTick < 1.0F);
                    helper.assertTrue(emissions.size() == expected && prompt && atCaster && owned,
                            "Summon click: count=" + emissions.size() + "/" + expected + " withinOneTick=" + prompt
                                    + " atCasterPoseWithoutMotion=" + atCaster + " ownedFirstPerform=" + owned
                                    + " pressTime=" + pressTime + " emissions=" + emissions);
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

        private void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            try {
                if (pressed) AbilityInput.keyRelease(KEY, owner);
                if (power != null) power.setPowerType(null);
            }
            finally {
                for (HungryZombieEntity zombie : zombies) {
                    if (!zombie.isRemoved()) zombie.discard();
                }
                if (owner != null && !owner.isRemoved()) owner.discard();
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
