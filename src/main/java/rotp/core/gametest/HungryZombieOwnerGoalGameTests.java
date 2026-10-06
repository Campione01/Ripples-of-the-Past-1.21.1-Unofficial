package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismZombieSummonAbility;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HungryZombieOwnerGoalGameTests {
    private HungryZombieOwnerGoalGameTests() {}

    @GameTest(template = "empty", batch = "hungry_zombie_follow_far", timeoutTicks = 160)
    public static void summonedZombieDoesNotStartFreshFarOwnerNavigation(GameTestHelper helper) { start(helper, Lane.FAR_FOLLOW); }

    @GameTest(template = "empty", batch = "hungry_zombie_follow_spectator", timeoutTicks = 160)
    public static void summonedZombieDoesNotAdmitNewSpectatorOwnerFollow(GameTestHelper helper) { start(helper, Lane.SPECTATOR); }

    @GameTest(template = "empty", batch = "hungry_zombie_follow_passenger", timeoutTicks = 160)
    public static void summonedPassengerDoesNotRequestFreshOwnerNavigation(GameTestHelper helper) { start(helper, Lane.PASSENGER); }

    @GameTest(template = "empty", batch = "hungry_zombie_owner_melee_far", timeoutTicks = 160)
    public static void summonedZombieDoesNotAdmitFreshFarOwnerMeleeTarget(GameTestHelper helper) { start(helper, Lane.OWNER_ATTACK); }

    private enum Lane { FAR_FOLLOW, SPECTATOR, PASSENGER, OWNER_ATTACK }
    private enum Phase { SUMMON, NEAR_PATH, NEAR_STOP, NEAR_TARGET, CACHE, OLD_TARGET_STOP, NEGATIVE }

    private static void start(GameTestHelper helper, Lane lane) {
        Fixture fixture = new Fixture(helper, lane);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 61, DRAIN_KEY = 62;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Lane lane;
        private final Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        private final Set<Entity> owned = new LinkedHashSet<>();
        private final List<HungryZombieEntity> emitted = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player owner;
        private PlayerPower power;
        private Ability summon;
        private EntityActionInstance summonAction;
        private VampirismBloodDrainAbility drain;
        private EntityActionInstance drainAction;
        private HeldInputEntry drainInput;
        private Cow controlTarget;
        private DamageSource controlDrainSource;
        private Float controlDrainApplied;
        private float controlHealthAfterMelee, controlDrainHealthAfter;
        private int controlMemoryStamp;
        private long controlTime, drainGeneration;
        private boolean controlPending, controlInjected;
        private HungryZombieEntity zombie;
        private PathNavigation navigation;
        private WrappedGoal follow, attackMove, ownerHurt, ownerAttack;
        private Cow cow;
        private Cow combatTarget;
        private Boat boat;
        private ChunkPos chunk;
        private Vec3 near, far;
        private AABB room;
        private double floorY;
        private int expectedCount, ownerPosts, zombiePosts, lastAge = -1, settled, observations;
        private long generation, bracketTime, phaseStart;
        private boolean pressed, bracket, nearReceipt, nearTargetReceipt, newFollowSeen, completed, closed;
        private Phase phase = Phase.SUMMON;
        private Throwable observerFailure;

        Fixture(GameTestHelper helper, Lane lane) { this.helper = helper; this.level = helper.getLevel(); this.lane = lane; }
        private void premise(boolean value, String message) { helper.assertTrue(value, "HZ-GUARD-PREMISE " + message); }
        private void oracle(boolean value, String message) { helper.assertTrue(value, "HZ-GUARD-ORACLE " + message); }
        private void log(String message) { JojoMod.LOGGER.info("HZ-GUARD {} {}", lane, message); }
        private boolean targetLane() { return lane == Lane.OWNER_ATTACK; }

        private void setUp() {
            expectedCount = level.getDifficulty().getId();
            premise(level.getDifficulty() != Difficulty.PEACEFUL && expectedCount >= 1 && expectedCount <= 3, "ordinary nonpeaceful summon scene");
            chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            floorY = helper.absolutePos(BlockPos.ZERO).getY() + 32D;
            double x = chunk.getMinBlockX(), z = chunk.getMinBlockZ();
            room = new AABB(x, floorY - 1, z, x + 16, floorY + 5, z + 16);
            premise(room.minY >= level.getMinBuildHeight() && room.maxY < level.getMaxBuildHeight()
                    && level.getWorldBorder().isWithinBounds(room), "owned room bounds");
            premise(level.getEntities((Entity) null, room.inflate(1)).isEmpty(), "foreign actor in owned room");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(room.maxX, room.maxY, room.maxZ))) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "room not empty air: " + pos);
            }
            for (int ix = 1; ix <= 14; ix++) for (int iz = 1; iz <= 14; iz++) {
                put(new BlockPos(chunk.getMinBlockX() + ix, (int) floorY - 1, chunk.getMinBlockZ() + iz));
                put(new BlockPos(chunk.getMinBlockX() + ix, (int) floorY + 4, chunk.getMinBlockZ() + iz));
            }
            for (int i = 0; i <= 15; i++) for (int h = 0; h < 4; h++) {
                put(new BlockPos(chunk.getMinBlockX() + i, (int) floorY + h, chunk.getMinBlockZ()));
                put(new BlockPos(chunk.getMinBlockX() + i, (int) floorY + h, chunk.getMinBlockZ() + 15));
            }
            for (int i = 1; i <= 14; i++) for (int h = 0; h < 4; h++) {
                put(new BlockPos(chunk.getMinBlockX(), (int) floorY + h, chunk.getMinBlockZ() + i));
                put(new BlockPos(chunk.getMinBlockX() + 15, (int) floorY + h, chunk.getMinBlockZ() + i));
            }
            observeEvents();
            owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HungryOwner"));
            owned.add(owner);
            ((FakePlayer) owner).setGameMode(GameType.SURVIVAL);
            moveOwner(new Vec3(x + 2.5, floorY, z + 2.5));
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            level.addNewPlayer((FakePlayer) owner);
            premise(level.getPlayerByUUID(owner.getUUID()) == owner, "actual server-list owner UUID resolution");
            power = PowerClass.PLAYER_POWER.attachGet(owner);
            premise(!power.hasPower() && !PowerClass.STAND.attachGet(owner).hasPower()
                    && power.trySetPowerType(ModPlayerPowers.VAMPIRISM.get()), "fresh Vampire grant");
            VampirismData data = PlayerPower.getPowerData(owner, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, owner);
            VampirismState.get(owner).blood().setCurrent(500F);
            data.setBloodLevel(VampirismState.get(owner).blood().current());
            owner.setHealth(owner.getMaxHealth());
            summon = power.getAbility("vampirism_zombie_summon");
            premise(summon instanceof VampirismZombieSummonAbility
                    && summon.abilityType == VampirismPowerType.VAMPIRE_ZOMBIE_SUMMON.get(), "actual registered summon type");
            var foundDrain = power.getAbility("vampirism_blood_drain");
            premise(foundDrain instanceof VampirismBloodDrainAbility && foundDrain.abilityType == VampirismPowerType.VAMPIRE_BLOOD_DRAIN.get(),
                    "actual registered control Drain type"); drain = (VampirismBloodDrainAbility) foundDrain;
            LivingComponentAction.getComponent(owner).entityAim.setTarget(ActionTarget.EMPTY);
            near = new Vec3(x + 10.5, floorY, z + 10.5);
            far = new Vec3(x + 14.5, floorY, z + 14.5);
            log("setup owner=" + owner.getUUID() + " cells=" + cells.size() + " count=" + expectedCount
                    + " standardServerActor=true creativeFlag=" + owner.isCreative() + " nativePlayerPhysics=false");
        }

        private void put(BlockPos pos) { cells.put(pos, level.getBlockState(pos)); premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "owned stone cell"); }
        private <T extends net.neoforged.bus.api.Event> void add(Consumer<T> listener, Class<T> type, EventPriority priority, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(priority, canceled, type, listener);
        }
        private void observe(Runnable operation) {
            if (closed || observerFailure != null) return;
            try { operation.run(); } catch (RuntimeException | Error error) { observerFailure = error; }
        }
        private void observeEvents() {
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == owner || event.getEntity() == zombie) premise(!event.isCanceled(), "owned natural tick canceled");
                if (event.getEntity() == zombie && controlPending && controlInjected)
                    premise(false, "fresh zombie tick interleaved before fully returned control source");
            });
            Consumer<EntityTickEvent.Post> opening = event -> observe(() -> {
                if (event.getEntity() == owner) {
                    premise(!bracket, "nested owner Post"); bracket = true; bracketTime = level.getGameTime();
                    if (controlPending) {
                        state(); premise(!controlInjected && controlTarget == cow && !ownerAttack.isRunning() && zombie.getTarget() == null,
                                "fresh natural target history before allowed-source preparation");
                        controlInjected = true; controlTime = level.getGameTime();
                        melee(controlTarget); controlMemoryStamp = owner.getLastHurtMobTimestamp(); controlHealthAfterMelee = controlTarget.getHealth();
                        pressControlDrain();
                    }
                }
            });
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level) return;
                if (owner == null || !(event.getEntity() instanceof HungryZombieEntity result) || !result.isEntityOwner(owner)) return;
                owned.add(result); emitted.add(result);
                observe(() -> {
                    premise(event.getEntity().getType() == ModEntityTypes.HUNGRY_ZOMBIE.get() && pressed && bracket,
                            "unexpected owned emitter/type/window");
                    premise(event.getEntity().getClass() == HungryZombieEntity.class && !event.isCanceled(), "unexpected emitted concrete class/join");
                    CompoundTag tag = result.saveWithoutId(new CompoundTag());
                    premise(emitted.size() <= expectedCount && result.tickCount == 0 && result.getOwner() == owner
                            && result.position().distanceTo(owner.position()) < 1E-5
                            && tag.hasUUID("Owner") && tag.getUUID("Owner").equals(owner.getUUID()) && tag.getBoolean("AbilitySummon")
                            && summonAction == LivingComponentAction.getCurEntityAction(owner) && summonAction.ability == summon
                            && summonAction.getPhase() == ActionPhase.PERFORM && summonAction.getPhaseTick() < 1
                            && level.getGameTime() == bracketTime, "not the real fresh owned PERFORM emission");
                    log("emission uuid=" + result.getUUID() + " owner=" + tag.getUUID("Owner") + " feet=" + result.position());
                });
            };
            Consumer<LivingDropsEvent> drops = event -> {
                if (!closed && event.getEntity() instanceof Cow && owned.contains(event.getEntity()))
                    for (ItemEntity drop : event.getDrops()) owned.add(drop);
            };
            Consumer<LivingIncomingDamageEvent> incoming = event -> observe(() -> {
                if (!controlPending || !controlInjected || event.getEntity() != controlTarget || !event.getSource().is(ModDamageTypes.BLOOD_DRAIN)) return;
                premise(bracket && !event.isCanceled() && event.getSource().getEntity() == owner && event.getSource().getDirectEntity() == owner
                        && controlDrainSource == null && drainHeld() == drainInput && LivingComponentAction.getCurEntityAction(owner) == drainAction
                        && drainAction.ability == drain && drainAction.getPhase() == ActionPhase.PERFORM && drainAction.getPhaseTick() < 1
                        && level.getGameTime() == controlTime, "first actual registered control Drain incoming receipt");
                controlDrainSource = event.getSource();
            });
            Consumer<LivingDamageEvent.Post> damage = event -> observe(() -> {
                if (!controlPending || event.getEntity() != controlTarget || event.getSource() != controlDrainSource) return;
                premise(controlDrainApplied == null && event.getNewDamage() > 0F && controlTarget.getHealth() < controlHealthAfterMelee,
                        "actual control Drain damage/HP receipt");
                controlDrainApplied = event.getNewDamage(); controlDrainHealthAfter = controlTarget.getHealth();
            });
            Consumer<EntityTickEvent.Post> closing = event -> observe(() -> {
                if (event.getEntity() == owner) {
                    premise(bracket && bracketTime == level.getGameTime(), "owner Post bracket");
                    if (controlPending && controlInjected) finishControlDrain();
                    ownerPosts++; bracket = false;
                }
                if (event.getEntity() == zombie) zombiePosts++;
            });
            add(pre, EntityTickEvent.Pre.class, EventPriority.LOWEST, true);
            add(opening, EntityTickEvent.Post.class, EventPriority.HIGHEST, false);
            add(join, EntityJoinLevelEvent.class, EventPriority.LOWEST, true);
            add(drops, LivingDropsEvent.class, EventPriority.LOWEST, true);
            add(incoming, LivingIncomingDamageEvent.class, EventPriority.LOWEST, true);
            add(damage, LivingDamageEvent.Post.class, EventPriority.LOWEST, false);
            add(closing, EntityTickEvent.Post.class, EventPriority.LOWEST, false);
        }

        private WrappedGoal goal(Iterable<WrappedGoal> goals, String name) {
            List<WrappedGoal> matches = new ArrayList<>();
            for (WrappedGoal goal : goals) if (goal.getGoal().getClass().getSimpleName().equals(name)) matches.add(goal);
            premise(matches.size() == 1, "unique registered goal " + name); return matches.get(0);
        }
        private void press() {
            premise(!owner.isSpectator() && !owner.isNoGravity() && !owner.isPassenger()
                    && owner.isAlive() && !owner.isOnFire() && VampirismState.get(owner).blood().current() >= 100F
                    && level.getPlayerByUUID(owner.getUUID()) == owner, "declared registered server caster prerequisites");
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(summon), owner, InputMethod.CLICK), "CLICK admission");
            pressed = true;
            var entry = AbilityInput.keyPress(KEY, summon, owner, null, InputMethod.CLICK, 0F, BufferingState.clickOnly(), summon.getAbilityId());
            premise(entry != null && entry.action instanceof VampirismZombieSummonAbility.ZombieSummonInstance, "actual summon action");
            summonAction = (EntityActionInstance) entry.action; generation = entry.generation;
            premise(generation > 0 && summonAction == LivingComponentAction.getCurEntityAction(owner), "owned input/action identity");
        }
        private void begin(Phase next) { phase = next; phaseStart = helper.getTick(); settled = 0; observations = 0; }
        private void moveOwner(Vec3 position) { owner.moveTo(position.x, position.y, position.z, owner.getYRot(), owner.getXRot()); }
        private boolean nearDistance() { double d = zombie.distanceToSqr(owner); return d > 100D && d <= 144D; }
        private void ready(Entity entity) {
            BlockPos min = BlockPos.containing(entity.getBoundingBox().minX, entity.getBoundingBox().minY, entity.getBoundingBox().minZ);
            BlockPos max = BlockPos.containing(entity.getBoundingBox().maxX, entity.getBoundingBox().maxY, entity.getBoundingBox().maxZ);
            premise(new ChunkPos(min).equals(chunk) && new ChunkPos(max).equals(chunk)
                    && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max), "owned actor outside ready chunk");
        }
        private void state() {
            premise(zombie.isAlive() && !zombie.isRemoved() && !zombie.isNoAi() && !zombie.isNoGravity() && !zombie.isOnFire()
                    && owner.isAlive() && zombie.getOwner() == owner && zombie.isEntityOwner(owner), "live owned natural AI identity");
            ready(owner); ready(zombie); if (cow != null && !cow.isRemoved()) ready(cow); if (boat != null) ready(boat);
        }
        private Cow makeCow() {
            Cow result = EntityType.COW.create(level); premise(result != null, "owned Cow creation"); owned.add(result);
            result.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D); result.setHealth(100F);
            Vec3 offset = zombie.position().subtract(owner.position()).multiply(1, 0, 1).normalize();
            if (offset.lengthSqr() == 0) offset = new Vec3(0, 0, 1);
            Vec3 pos = owner.position().add(offset.scale(0.8D)); result.moveTo(pos.x, floorY, pos.z, 0, 0);
            premise(level.addFreshEntity(result) && !result.isNoAi() && !result.isNoGravity() && result.isAlive(), "natural owned Cow join");
            return result;
        }
        private void melee(Cow target) {
            premise(owner.distanceToSqr(target) < 4D && owner.getMainHandItem().isEmpty() && owner.getOffhandItem().isEmpty(), "physical ordinary melee preparation");
            int old = owner.getLastHurtMobTimestamp(); float health = target.getHealth();
            combatTarget = target;
            try { owner.attack(target); } finally { combatTarget = null; }
            premise(target.isAlive() && target.getHealth() < health && owner.getLastHurtMob() == target
                    && owner.getLastHurtMobTimestamp() > old, "actual public melee did not produce fresh owner memory");
            log("public-melee target=" + target.getUUID() + " health=" + health + "->" + target.getHealth()
                    + " ownerTimestamp=" + owner.getLastHurtMobTimestamp() + " genuine-owner-memory-prime=true");
        }
        private HeldInputEntry drainHeld() {
            return owner.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT).map(input -> input.heldKeys.get(DRAIN_KEY)).orElse(null);
        }
        private void armAllowedTarget(Cow target, Phase next) {
            premise(!controlPending && drainHeld() == null && LivingComponentAction.getCurEntityAction(owner) == null, "fresh allowed-source control input");
            controlTarget = target; controlDrainSource = null; controlDrainApplied = null;
            controlPending = true; controlInjected = false; begin(next);
        }
        private void pressControlDrain() {
            premise(bracket && !owner.isSpectator() && owner.getMainHandItem().isEmpty() && owner.getOffhandItem().isEmpty()
                    && controlTarget.isAlive() && owner.distanceToSqr(controlTarget) < 4D && owner.hasLineOfSight(controlTarget)
                    && LivingComponentAction.getCurEntityAction(owner) == null, "physical registered control Drain preparation");
            Vec3 hit = controlTarget.getBoundingBox().getCenter(); Vec3 direction = hit.subtract(owner.getEyePosition());
            owner.setYRot((float) -Math.toDegrees(Math.atan2(direction.x, direction.z)));
            owner.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
            owner.setYHeadRot(owner.getYRot()); owner.yBodyRot = owner.getYRot();
            LivingComponentAction.getComponent(owner).entityAim.setTarget(ActionTarget.fromVanilla(new EntityHitResult(controlTarget, hit)));
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(drain), owner, InputMethod.HOLD), "registered control Drain HOLD admission");
            drainInput = AbilityInput.keyPress(DRAIN_KEY, drain, owner, null, InputMethod.HOLD, 0F, BufferingState.clickOnly(), drain.getAbilityId());
            premise(drainInput != null && drainInput.action instanceof VampirismBloodDrainAbility.BloodDrainInstance, "actual registered control Drain action");
            drainAction = (EntityActionInstance) drainInput.action; drainGeneration = drainInput.generation;
            premise(drainGeneration > 0 && drainHeld() == drainInput && drainAction == LivingComponentAction.getCurEntityAction(owner)
                    && drainAction.ability == drain && drainAction.getPhase() == ActionPhase.PERFORM && drainAction.getPhaseTick() < 1,
                    "production-initialized control PERFORM, without phase forcing");
        }
        private void finishControlDrain() {
            premise(controlTarget.isAlive() && controlDrainSource != null && controlDrainApplied != null && controlDrainApplied > 0F
                    && controlDrainHealthAfter < controlHealthAfterMelee && controlTarget.getLastDamageSource() == controlDrainSource
                    && controlDrainSource.is(ModDamageTypes.BLOOD_DRAIN) && controlDrainSource.getMsgId().startsWith("bloodDrain")
                    && controlDrainSource.getEntity() == owner && controlDrainSource.getDirectEntity() == owner
                    && owner.getLastHurtMob() == controlTarget && owner.getLastHurtMobTimestamp() == controlMemoryStamp
                    && level.getGameTime() == controlTime && drainHeld() == drainInput
                    && LivingComponentAction.getCurEntityAction(owner) == drainAction && drainAction.getPhase() == ActionPhase.PERFORM,
                    "fully returned genuine eligible source before fresh natural target decision");
            premise(AbilityInput.keyReleaseAndGetGeneration(DRAIN_KEY, owner) == drainGeneration && drainHeld() == null && drainAction.isOver(),
                    "exact control Drain generation release");
            log("ALLOWED-SOURCE target=" + controlTarget.getUUID() + " world=" + controlTime + " memory=" + controlMemoryStamp
                    + " applied=" + controlDrainApplied + " HP=" + controlHealthAfterMelee + "->" + controlDrainHealthAfter);
            controlPending = false; controlInjected = false;
        }
        private void attachPassenger() {
            boat = EntityType.BOAT.create(level); premise(boat != null, "owned Boat creation"); owned.add(boat);
            boat.moveTo(zombie.getX(), floorY, zombie.getZ(), 0, 0); premise(level.addFreshEntity(boat), "owned Boat join");
            premise(zombie.startRiding(boat) && zombie.getVehicle() == boat && zombie.getNavigation() == navigation, "public non-Mob passenger route");
        }
        private void ownerLeadControl() {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
            premise(owner.interactOn(zombie, InteractionHand.MAIN_HAND).consumesAction() && zombie.isLeashed()
                    && zombie.getLeashHolder() == owner && owner.getMainHandItem().isEmpty(), "ordinary owner lead attachment");
            zombie.dropLeash(true, false); owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            premise(!zombie.isLeashed(), "owned lead cleanup");
            log("owner-lead attach/cleanup only; leash-follow guard SOURCE_ONLY because vanilla leash also navigates");
        }
        private boolean releasedMoveBlocker() {
            if (zombie.getTarget() != null || attackMove.isRunning()) return false;
            for (WrappedGoal running : zombie.goalSelector.getAvailableGoals()) {
                premise(running == follow || !running.isRunning() || running.getPriority() > follow.getPriority()
                        || !running.getGoal().getFlags().contains(Goal.Flag.MOVE), "unrelated equal/higher-priority MOVE blocker");
            }
            return true;
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                premise(helper.getTick() < 130, "finite natural AI watchdog");
                if (!pressed) { if (ownerPosts >= 3) press(); again(); return; }
                if (phase == Phase.SUMMON) {
                    if (emitted.size() == expectedCount) {
                        premise(AbilityInput.keyReleaseAndGetGeneration(KEY, owner) == generation, "summon generation release");
                        zombie = emitted.get(0); navigation = zombie.getNavigation();
                        follow = goal(zombie.goalSelector.getAvailableGoals(), "HungryZombieFollowOwnerGoal");
                        attackMove = goal(zombie.goalSelector.getAvailableGoals(), "ZombieAttackGoal");
                        ownerHurt = goal(zombie.targetSelector.getAvailableGoals(), "HungryZombieOwnerHurtByTargetGoal");
                        ownerAttack = goal(zombie.targetSelector.getAvailableGoals(), "HungryZombieOwnerHurtTargetGoal");
                        premise(!follow.isRunning() && zombie.getTarget() == null, "fresh emitted idle goal history");
                        moveOwner(near); begin(Phase.NEAR_PATH);
                    }
                    again(); return;
                }
                state();
                if (zombie.tickCount == lastAge) { again(); return; }
                lastAge = zombie.tickCount;
                if (controlPending) {
                    premise(helper.getTick() - phaseStart < 45, "finite phase qualification watchdog " + phase);
                    again(); return;
                }
                switch (phase) {
                    case NEAR_PATH -> {
                        premise(nearDistance() && !zombie.farFromOwner(12), "near 10..12 cache/geometry");
                        if (follow.isRunning() && navigation.getPath() != null && owner.blockPosition().equals(navigation.getTargetPos())) {
                            nearReceipt = true; log("NEAR-POSITIVE uuid=" + zombie.getUUID() + " age=" + zombie.tickCount + " posts=" + zombiePosts + " nav=" + navigation.getTargetPos());
                            if (targetLane()) { cow = makeCow(); armAllowedTarget(cow, Phase.NEAR_TARGET); }
                            else { moveOwner(zombie.position().add(0.5, 0, 0.5)); begin(Phase.NEAR_STOP); }
                        }
                    }
                    case NEAR_STOP -> {
                        premise(zombie.distanceToSqr(owner) < 4D, "natural near stop-distance preparation");
                        if (!follow.isRunning() && navigation.isDone() && zombie.getTarget() == null) {
                            if (lane == Lane.SPECTATOR) {
                                ((FakePlayer) owner).setGameMode(GameType.SPECTATOR);
                                premise(owner.isSpectator() && zombie.getOwner() == owner, "actual spectator owner after natural follow stop");
                                moveOwner(near); begin(Phase.NEGATIVE); break;
                            }
                            if (lane == Lane.PASSENGER) ownerLeadControl();
                            cow = makeCow(); armAllowedTarget(cow, Phase.NEAR_TARGET);
                        }
                    }
                    case NEAR_TARGET -> {
                        WrappedGoal selected = ownerAttack;
                        if (selected.isRunning() && attackMove.isRunning() && !navigation.isDone() && zombie.getTarget() == cow && !follow.isRunning()) {
                            premise(!targetLane() || zombie.distanceToSqr(owner) <= 144D && !zombie.farFromOwner(12), "owner-event within12 admission control");
                            nearTargetReceipt = true; log("ACTUAL-MOVE-BLOCKER cow=" + cow.getUUID() + " ownerGoal=" + selected.getGoal().getClass().getSimpleName()
                                    + " distSq=" + zombie.distanceToSqr(owner) + " cachedFar=" + zombie.farFromOwner(12));
                            if (lane == Lane.PASSENGER) {
                                attachPassenger();
                                near = new Vec3(zombie.getX() + 8D, floorY, zombie.getZ() + 8D);
                            }
                            moveOwner(lane == Lane.FAR_FOLLOW || targetLane() ? far : near); begin(Phase.CACHE);
                        }
                    }
                    case CACHE -> {
                        boolean blockerValid = cow.isAlive() && zombie.getTarget() == cow && attackMove.isRunning() && !follow.isRunning();
                        if (!blockerValid) log("CACHE-FAIL uuid=" + zombie.getUUID() + " age=" + zombie.tickCount
                                + " cowAlive=" + cow.isAlive() + " cowUUID=" + cow.getUUID()
                                + " target=" + (zombie.getTarget() == null ? "none" : zombie.getTarget().getUUID())
                                + " attackMove=" + attackMove.isRunning() + " follow=" + follow.isRunning()
                                + " distSq=" + zombie.distanceToSqr(owner) + " cachedFar=" + zombie.farFromOwner(12)
                                + " navDone=" + navigation.isDone() + " navTarget=" + navigation.getTargetPos());
                        premise(blockerValid, "higher-priority real MOVE blocker lost");
                        boolean distant = lane == Lane.FAR_FOLLOW || targetLane();
                        boolean geometry = distant ? zombie.distanceToSqr(owner) > 144D : nearDistance();
                        settled = geometry && zombie.farFromOwner(12) == distant ? settled + 1 : 0;
                        if (settled >= 2) {
                            premise(lane != Lane.SPECTATOR || owner.isSpectator(), "spectator state lost");
                            premise(lane != Lane.PASSENGER || zombie.getVehicle() == boat && zombie.getNavigation() == navigation, "passenger route lost");
                            log("CACHE-ARM uuid=" + zombie.getUUID() + " age=" + zombie.tickCount + " distSq=" + zombie.distanceToSqr(owner)
                                    + " cachedFar=" + zombie.farFromOwner(12) + " oldNav=" + navigation.getTargetPos()
                                    + " spectator=" + owner.isSpectator() + " passenger=" + zombie.isPassenger());
                            cow.discard(); begin(targetLane() ? Phase.OLD_TARGET_STOP : Phase.NEGATIVE);
                        }
                    }
                    case OLD_TARGET_STOP -> {
                        premise(zombie.distanceToSqr(owner) > 144D && zombie.farFromOwner(12), "far cache expired before fresh event");
                        if (zombie.getTarget() == null && !ownerHurt.isRunning() && !ownerAttack.isRunning() && !attackMove.isRunning()) {
                            cow = makeCow(); armAllowedTarget(cow, Phase.NEGATIVE);
                        }
                    }
                    case NEGATIVE -> {
                        premise(nearReceipt && (lane == Lane.SPECTATOR || nearTargetReceipt), "positive receipts missing");
                        if (lane == Lane.FAR_FOLLOW || targetLane()) premise(zombie.distanceToSqr(owner) > 144D && zombie.farFromOwner(12), "negative far cache/geometry changed");
                        else premise(nearDistance() && !zombie.farFromOwner(12), "negative near cache/geometry changed");
                        premise(lane != Lane.SPECTATOR || owner.isSpectator(), "negative spectator changed");
                        premise(lane != Lane.PASSENGER || zombie.isPassenger() && zombie.getVehicle() == boat && zombie.getNavigation() == navigation, "negative passenger changed");
                        if (targetLane()) {
                            premise(cow.isAlive() && (zombie.getTarget() == null || zombie.getTarget() == cow), "unrelated target admission");
                            premise(controlTarget == cow && cow.getLastDamageSource() == controlDrainSource && controlDrainSource != null
                                    && controlDrainSource.is(ModDamageTypes.BLOOD_DRAIN) && controlDrainSource.getMsgId().startsWith("bloodDrain")
                                    && level.getGameTime() - controlTime < 40, "far oracle lacks fresh eligible victim source");
                            WrappedGoal selected = ownerAttack;
                            log("NEW-TARGET age=" + zombie.tickCount + " cachedFar=true running=" + selected.isRunning() + " target=" + (zombie.getTarget() == null ? "none" : zombie.getTarget().getUUID()));
                            oracle(!selected.isRunning() && zombie.getTarget() != cow, "fresh far owner-event target was admitted");
                        }
                        else {
                            if (releasedMoveBlocker()) {
                                newFollowSeen |= follow.isRunning();
                                boolean newOwnerPath = follow.isRunning() && navigation.getPath() != null && owner.blockPosition().equals(navigation.getTargetPos());
                                log("NEW-FOLLOW age=" + zombie.tickCount + " eventPosts=" + zombiePosts + " running=" + follow.isRunning() + " path=" + navigation.getTargetPos());
                                oracle(lane == Lane.SPECTATOR ? !follow.isRunning() : !newOwnerPath, "new forbidden owner follow/navigation was admitted");
                            }
                            else { again(); return; }
                        }
                        observations++;
                        if (observations >= 12) {
                            premise(targetLane() || lane == Lane.SPECTATOR || newFollowSeen, "natural new follow admission was not observed");
                            log("RESULT qualified=true uuid=" + zombie.getUUID() + " observations=" + observations + " native=false");
                            completed = true; close(); helper.succeed(); return;
                        }
                    }
                    default -> throw new IllegalStateException("Unexpected phase");
                }
                if (phase == Phase.CACHE && helper.getTick() - phaseStart >= 45) {
                    log("CACHE-WATCHDOG age=" + zombie.tickCount + " zombie=" + zombie.position()
                            + " owner=" + owner.position() + " distSq=" + zombie.distanceToSqr(owner)
                            + " cachedFar=" + zombie.farFromOwner(12) + " settled=" + settled
                            + " passenger=" + zombie.isPassenger() + " boat=" + (boat == null ? "none" : boat.position()));
                }
                premise(helper.getTick() - phaseStart < 45, "finite phase qualification watchdog " + phase);
                again();
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }
        private void again() { helper.runAfterDelay(1, this::poll); }
        private void cleanup(Runnable operation, List<Throwable> failures) { try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); } }
        @Override public void close() {
            if (closed) return; closed = true; List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures);
            listeners.clear();
            cleanup(() -> { if (pressed) AbilityInput.keyRelease(KEY, owner); }, failures);
            cleanup(() -> { if (owner != null) AbilityInput.keyRelease(DRAIN_KEY, owner); }, failures);
            cleanup(() -> { if (zombie != null) { if (zombie.isLeashed()) zombie.dropLeash(true, false); zombie.stopRiding(); } }, failures);
            cleanup(() -> { if (owner != null && owner.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                    .map(input -> !input.heldKeys.isEmpty()).orElse(false)) throw new IllegalStateException("owned held input remains"); }, failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            cleanup(() -> { if (owner != null) owner.getInventory().clearContent(); }, failures);
            for (Entity entity : owned) cleanup(() -> { if (!entity.isRemoved()) entity.discard(); }, failures);
            for (var cell : cells.entrySet()) cleanup(() -> level.setBlockAndUpdate(cell.getKey(), cell.getValue()), failures);
            cleanup(() -> {
                boolean restored = cells.entrySet().stream().allMatch(cell -> level.getBlockState(cell.getKey()).equals(cell.getValue()) && level.getBlockEntity(cell.getKey()) == null);
                boolean removed = owned.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null);
                log("cleanup exactCells=" + cells.size() + " restored=" + restored + " ownedRemoved=" + removed + " listeners=0 completed=" + completed);
                boolean playersRemoved = owned.stream().filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast)
                        .noneMatch(player -> level.players().contains(player) || level.getServer().getPlayerList().getPlayers().contains(player));
                if (!restored || !removed || !playersRemoved) throw new IllegalStateException("owned scene cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("Hungry Zombie fixture cleanup failed"); failures.forEach(error::addSuppressed); throw error; }
        }
        private void closeAfterFailure(Throwable error) { try { close(); } catch (RuntimeException | Error cleanup) { error.addSuppressed(cleanup); } }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { if (test.getError() != null) closeAfterFailure(test.getError()); else close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close();
        }
    }
}
