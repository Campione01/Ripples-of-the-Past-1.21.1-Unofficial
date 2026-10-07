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
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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
public final class HungryZombieBloodDrainTargetGameTests {
    private HungryZombieBloodDrainTargetGameTests() {}

    @GameTest(template = "empty", batch = "hungry_zombie_non_drain_source", timeoutTicks = 280)
    public static void freshNonDrainVictimSourceDoesNotAdmitOwnerTarget(GameTestHelper helper) { start(helper, Lane.MELEE); }

    @GameTest(template = "empty", batch = "hungry_zombie_registered_drain_source", timeoutTicks = 280)
    public static void actualRegisteredDrainOnThePrimedVictimAdmitsOwnerTarget(GameTestHelper helper) { start(helper, Lane.DRAIN); }

    @GameTest(template = "empty", batch = "hungry_zombie_expired_victim_source", timeoutTicks = 280)
    public static void naturallyExpiredVictimSourceAdmitsTheUnconsumedOwnerEvent(GameTestHelper helper) { start(helper, Lane.EXPIRED); }

    private enum Lane { MELEE, DRAIN, EXPIRED }
    private enum Phase { OWNER_READY, SUMMON, SETTLE, ARM, EXPIRE, OBSERVE }
    private record Before(int age, long time, DamageSource source, int ownerMemory, int victimAge, boolean fullUpdate) {}

    private static void start(GameTestHelper helper, Lane lane) {
        Fixture fixture = new Fixture(helper, lane);
        helper.testInfo.addListener(fixture);
        try { fixture.setUp(); helper.runAfterDelay(1, fixture::poll); }
        catch (RuntimeException | Error error) { fixture.closeAfterFailure(error); throw error; }
    }

    private static final class Attempt {
        final DamageSource source;
        final LivingIncomingDamageEvent incoming;
        final float healthBefore;
        final long time;
        final int ownerAge, victimAge;
        Float applied;
        float healthAfter;
        Attempt(LivingIncomingDamageEvent incoming, FakePlayer owner, Cow victim, long time) {
            this.source = incoming.getSource(); this.incoming = incoming; this.healthBefore = victim.getHealth();
            this.time = time; ownerAge = owner.tickCount; victimAge = victim.tickCount;
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short SUMMON_KEY = 67, DRAIN_KEY = 68;
        private static final int LIGHT_WAIT_TICKS = 100;
        private long readyTick;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Lane lane;
        private final Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        private final Set<Entity> owned = new LinkedHashSet<>();
        private final List<HungryZombieEntity> emitted = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private FakePlayer owner;
        private PlayerPower power;
        private Ability summon;
        private VampirismBloodDrainAbility drain;
        private EntityActionInstance summonAction, drainAction;
        private HeldInputEntry drainInput;
        private HungryZombieEntity zombie;
        private WrappedGoal ownerAttack;
        private Cow victim, combatVictim;
        private ChunkPos chunk;
        private AABB room;
        private Vec3 near;
        private double floorY;
        private Attempt meleeAttempt, drainAttempt;
        private Before before;
        private Phase phase = Phase.OWNER_READY;
        private Throwable observerFailure;
        private int expectedCount, ownerPosts, zombiePosts, victimPosts, settled, observations, decisions;
        private int ownerPreAge = -1, ownerLastAge = -1, zombieLastAge = -1, memoryStamp;
        private long ownerPreTime, ownerBracketTime, phaseStart, meleeTime = -1, drainTime = -1;
        private long summonGeneration, drainGeneration;
        private boolean ownerBracket, summonPressed, summonReleased, meleeCalling, meleeDone, drainPressed, drainReleased;
        private boolean sourceReady, expired, completed, closed;

        Fixture(GameTestHelper helper, Lane lane) { this.helper = helper; level = helper.getLevel(); this.lane = lane; }
        private void premise(boolean value, String message) { if (!value) helper.fail("HZ-SOURCE-PREMISE " + message + " | " + dump()); }
        private void oracle(boolean value, String message) { if (!value) helper.fail("HZ-SOURCE-ORACLE " + message + " | " + dump()); }
        // What a failed check saw, so that a failure in a later run explains itself.
        private String dump() {
            StringBuilder out = new StringBuilder("tick=" + helper.getTick() + " lane=" + lane + " phase=" + phase
                    + " sincePhase=" + (helper.getTick() - phaseStart) + " ownerPosts=" + ownerPosts + " zombiePosts=" + zombiePosts
                    + " settled=" + settled + " observations=" + observations + " decisions=" + decisions
                    + " meleeDone=" + meleeDone + " drainPressed=" + drainPressed + " sourceReady=" + sourceReady);
            if (owner != null) out.append(" owner=").append(owner.position()).append(" ownerAlive=").append(owner.isAlive())
                    .append(" ownerOnFire=").append(owner.isOnFire()).append(" ownerMemory=").append(owner.getLastHurtMobTimestamp());
            for (HungryZombieEntity each : emitted) out.append(' ').append(describe(each, each == zombie ? "subject" : "other"));
            if (victim != null) out.append(" victim=").append(victim.position()).append(" victimAlive=").append(victim.isAlive())
                    .append(" victimHealth=").append(victim.getHealth())
                    .append(" victimSource=").append(victim.getLastDamageSource() == null ? "null" : victim.getLastDamageSource().getMsgId());
            return out.toString();
        }
        private String describe(HungryZombieEntity each, String role) {
            List<String> goals = new ArrayList<>();
            for (WrappedGoal goal : each.goalSelector.getAvailableGoals()) if (goal.isRunning()) goals.add(goal.getGoal().getClass().getSimpleName());
            for (WrappedGoal goal : each.targetSelector.getAvailableGoals()) if (goal.isRunning()) goals.add(goal.getGoal().getClass().getSimpleName());
            return "zombie[" + role + " age=" + each.tickCount + " pos=" + each.position() + " motion=" + each.getDeltaMovement()
                    + " alive=" + each.isAlive() + " removed=" + each.getRemovalReason() + " health=" + each.getHealth()
                    + " onFire=" + each.isOnFire() + " baby=" + each.isBaby() + " noAi=" + each.isNoAi() + " noGravity=" + each.isNoGravity()
                    + " passenger=" + each.isPassenger() + " leashed=" + each.isLeashed() + " onGround=" + each.onGround()
                    + " ownerResolved=" + (owner != null && each.getOwner() == owner) + " ownerUuid=" + (owner != null && each.isEntityOwner(owner))
                    + " distSq=" + (owner == null ? "n/a" : String.valueOf(each.distanceToSqr(owner))) + " cachedFar12=" + each.farFromOwner(12D)
                    + " target=" + (each.getTarget() == null ? "none" : each.getTarget().getType().toShortString() + "@" + each.getTarget().position())
                    + " navDone=" + each.getNavigation().isDone() + " goals=" + goals + "]";
        }
        private void log(String message) { JojoMod.LOGGER.info("HZ-SOURCE {} {}", lane, message); }
        private void begin(Phase next) { phase = next; phaseStart = helper.getTick(); settled = 0; }
        private void observe(Runnable operation) {
            if (closed || completed || observerFailure != null) return;
            try { operation.run(); } catch (RuntimeException | Error error) { observerFailure = error; }
        }
        private <T extends net.neoforged.bus.api.Event> void add(Consumer<T> listener, Class<T> type, EventPriority priority, boolean canceled) {
            listeners.add(listener); NeoForge.EVENT_BUS.addListener(priority, canceled, type, listener);
        }

        // An idle zombie starts a random stroll on one goal update in 60. It ranks below the goals these tests examine,
        // so it cannot block or admit any of them, but it walked the zombie out of the distances the phases prepare.
        private static void withoutRandomStroll(HungryZombieEntity each) {
            List<Goal> strolls = new ArrayList<>();
            for (WrappedGoal goal : each.goalSelector.getAvailableGoals()) if (goal.getGoal() instanceof RandomStrollGoal) strolls.add(goal.getGoal());
            strolls.forEach(each.goalSelector::removeGoal);
        }
        // The light engine darkens the room some ticks after its roof is placed. Until then a zombie in it still reads
        // full sky light and, by day, catches fire on about one tick in 25.
        private int brightestSkyLight() {
            int brightest = 0;
            for (int ix = 1; ix <= 14; ix++) for (int iz = 1; iz <= 14; iz++) for (int h = 0; h < 4; h++) {
                brightest = Math.max(brightest, level.getBrightness(LightLayer.SKY,
                        new BlockPos(chunk.getMinBlockX() + ix, (int) floorY + h, chunk.getMinBlockZ() + iz)));
            }
            return brightest;
        }

        private void setUp() {
            premise(level.getDifficulty() == Difficulty.NORMAL, "ordinary Normal scene required");
            expectedCount = level.getDifficulty().getId();
            chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            floorY = helper.absolutePos(BlockPos.ZERO).getY() + 32D;
            double x = chunk.getMinBlockX(), z = chunk.getMinBlockZ();
            room = new AABB(x, floorY - 1, z, x + 16, floorY + 5, z + 16);
            premise(room.minY >= level.getMinBuildHeight() && room.maxY < level.getMaxBuildHeight()
                    && level.getWorldBorder().isWithinBounds(room) && level.getEntities((Entity) null, room.inflate(1)).isEmpty(), "owned room bounds/actors");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(room.minX, room.minY, room.minZ),
                    BlockPos.containing(room.maxX, room.maxY, room.maxZ))) {
                premise(level.isEmptyBlock(pos) && level.getFluidState(pos).isEmpty(), "room is not empty air: " + pos);
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
            registerObservers();
            owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HungrySource")); owned.add(owner);
            owner.setGameMode(GameType.SURVIVAL);
            moveOwner(new Vec3(x + 3.5, floorY, z + 3.5));
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            level.addNewPlayer(owner);
            premise(level.getPlayerByUUID(owner.getUUID()) == owner && !owner.isCreative() && !owner.isSpectator(), "real server-list owner admission");
            power = PowerClass.PLAYER_POWER.attachGet(owner);
            premise(!power.hasPower() && !PowerClass.STAND.attachGet(owner).hasPower()
                    && power.trySetPowerType(ModPlayerPowers.VAMPIRISM.get()), "fresh registered Vampire grant");
            VampirismData data = PlayerPower.getPowerData(owner, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, owner);
            VampirismState.get(owner).blood().setCurrent(500F); data.setBloodLevel(VampirismState.get(owner).blood().current());
            owner.setHealth(owner.getMaxHealth());
            summon = power.getAbility("vampirism_zombie_summon");
            var found = power.getAbility("vampirism_blood_drain");
            premise(summon instanceof VampirismZombieSummonAbility && summon.abilityType == VampirismPowerType.VAMPIRE_ZOMBIE_SUMMON.get()
                    && found instanceof VampirismBloodDrainAbility && found.abilityType == VampirismPowerType.VAMPIRE_BLOOD_DRAIN.get(), "actual registered producers");
            drain = (VampirismBloodDrainAbility) found;
            LivingComponentAction.getComponent(owner).entityAim.setTarget(ActionTarget.EMPTY);
            near = new Vec3(x + 10.5, floorY, z + 10.5);
            log("setup owner=" + owner.getUUID() + " cells=" + cells.size() + " count=" + expectedCount
                    + " standardFakePlayer=true nativePlayerPhysics=false incomingVulnerability=false");
        }

        private void put(BlockPos pos) { cells.put(pos.immutable(), level.getBlockState(pos)); premise(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "owned stone placement"); }
        private void moveOwner(Vec3 pos) { owner.moveTo(pos.x, pos.y, pos.z, owner.getYRot(), owner.getXRot()); }
        private WrappedGoal goal(Iterable<WrappedGoal> goals, String name) {
            List<WrappedGoal> matches = new ArrayList<>();
            for (WrappedGoal goal : goals) if (goal.getGoal().getClass().getSimpleName().equals(name)) matches.add(goal);
            premise(matches.size() == 1, "unique registered " + name); return matches.get(0);
        }
        private HeldInputEntry drainHeld() { return owner.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT).map(state -> state.heldKeys.get(DRAIN_KEY)).orElse(null); }
        private void ready(Entity entity) {
            AABB box = entity.getBoundingBox();
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ), max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            premise(new ChunkPos(min).equals(chunk) && new ChunkPos(max).equals(chunk)
                    && level.isPositionEntityTicking(min) && level.isPositionEntityTicking(max)
                    && box.minX >= room.minX && box.maxX <= room.maxX && box.minZ >= room.minZ && box.maxZ <= room.maxZ
                    && box.minY >= room.minY && box.maxY <= room.maxY, "owned actor outside ready room");
        }
        private void state() {
            premise(owner.isAlive() && !owner.isRemoved() && !owner.isSpectator() && !owner.isCreative()
                    && !owner.isPassenger() && !owner.isNoGravity() && !owner.isOnFire()
                    && level.getPlayerByUUID(owner.getUUID()) == owner && power.getPowerType() == ModPlayerPowers.VAMPIRISM.get(), "live owned caster identity");
            ready(owner);
            if (victim != null) { premise(victim.isAlive() && !victim.isRemoved() && !victim.isNoAi() && !victim.isNoGravity(), "natural living victim"); ready(victim); }
            if (zombie != null) {
                premise(zombie.isAlive() && !zombie.isRemoved() && !zombie.isNoAi() && !zombie.isNoGravity() && !zombie.isOnFire()
                        && !zombie.isPassenger() && !zombie.isLeashed() && zombie.getOwner() == owner && zombie.isEntityOwner(owner), "actual owned unconstrained zombie");
                ready(zombie);
            }
        }
        private void noOtherTargetGoal() {
            for (WrappedGoal goal : zombie.targetSelector.getAvailableGoals()) {
                premise(goal == ownerAttack || !goal.isRunning() || !goal.getGoal().getFlags().contains(Goal.Flag.TARGET), "unrelated running TARGET blocker");
            }
        }
        private boolean nearCache() { return zombie.distanceToSqr(owner) <= 144D && !zombie.farFromOwner(12D); }
        private void freshZombie() {
            state(); noOtherTargetGoal();
            premise(!ownerAttack.isRunning() && zombie.getTarget() == null && owner.getLastHurtMob() == null, "old owner event/goal history");
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> {
                if (closed || event.getLevel() != level) return;
                Entity entity = event.getEntity();
                // Retention is independent of fallible observations, including every later emission after a failure.
                if (owner != null && entity instanceof HungryZombieEntity result && result.isEntityOwner(owner)) {
                    owned.add(result); if (!emitted.contains(result)) emitted.add(result);
                    if (zombie == null) zombie = result;
                    withoutRandomStroll(result);
                    observe(() -> {
                        premise(summonPressed && ownerBracket && entity.getType() == ModEntityTypes.HUNGRY_ZOMBIE.get()
                                && entity.getClass() == HungryZombieEntity.class && !event.isCanceled(), "not the owned concrete production emission");
                        CompoundTag tag = result.saveWithoutId(new CompoundTag());
                        premise(emitted.size() <= expectedCount && result.tickCount == 0 && result.getOwner() == owner
                                && result.position().distanceTo(owner.position()) < 1E-5 && tag.hasUUID("Owner")
                                && tag.getUUID("Owner").equals(owner.getUUID()) && tag.getBoolean("AbilitySummon")
                                && summonAction == LivingComponentAction.getCurEntityAction(owner) && summonAction.ability == summon
                                && summonAction.getPhase() == ActionPhase.PERFORM && summonAction.getPhaseTick() < 1
                                && level.getGameTime() == ownerBracketTime, "real registered fresh summon receipt");
                        if (result == zombie) { ownerAttack = goal(zombie.targetSelector.getAvailableGoals(), "HungryZombieOwnerHurtTargetGoal"); premise(!ownerAttack.isRunning() && zombie.getTarget() == null, "fresh emitted target history"); }
                        log("emission uuid=" + result.getUUID() + " owner=" + owner.getUUID() + " age=" + result.tickCount);
                    });
                }
            };
            Consumer<LivingDropsEvent> drops = event -> {
                if (closed || event.getEntity() != victim) return;
                for (ItemEntity drop : event.getDrops()) owned.add(drop);
            };
            Consumer<LivingDeathEvent> death = event -> {
                if (!closed && event.getEntity() == victim) observe(() -> premise(false, "owned high-health victim died before qualification"));
            };
            Consumer<EntityTickEvent.Pre> pre = event -> observe(() -> {
                if (event.getEntity() == owner) {
                    premise(!event.isCanceled() && (ownerLastAge < 0 || owner.tickCount == ownerLastAge + 1), "consecutive actual owner Pre");
                    ownerPreAge = owner.tickCount; ownerPreTime = level.getGameTime();
                }
                if (event.getEntity() != zombie) return;
                premise(!event.isCanceled() && (zombieLastAge < 0 || zombie.tickCount == zombieLastAge + 1), "consecutive actual zombie Pre");
                if (phase != Phase.OBSERVE) return;
                state(); noOtherTargetGoal();
                premise(sourceReady && !ownerBracket && owner.getLastHurtMob() == victim && owner.getLastHurtMobTimestamp() == memoryStamp
                        && nearCache() && zombie.canAttack(victim) && zombie.wantsToAttack(victim, owner) && zombie.hasLineOfSight(victim), "qualified fresh near event/target eligibility");
                DamageSource source = victim.getLastDamageSource(); long age = level.getGameTime() - meleeTime;
                if (lane == Lane.MELEE) premise(age >= 0 && age < 40 && source == meleeAttempt.source && !source.getMsgId().startsWith("bloodDrain"), "non-null ordinary source expired/replaced");
                if (lane == Lane.DRAIN) premise(drainReleased && drainAttempt != null && level.getGameTime() - drainTime < 40
                        && source == drainAttempt.source && source.is(ModDamageTypes.BLOOD_DRAIN) && source.getMsgId().startsWith("bloodDrain"), "returned registered source lost");
                if (lane == Lane.EXPIRED) premise(expired && age > 40 && source == null, "natural nullable source changed before first decision");
                premise(before == null && !ownerAttack.isRunning() && zombie.getTarget() == null, "already admitted event before observed fresh decision");
                before = new Before(zombie.tickCount, level.getGameTime(), source, memoryStamp, victim.tickCount,
                        zombie.tickCount <= 1 || (zombie.tickCount + zombie.getId()) % 2 == 0);
            });
            Consumer<EntityTickEvent.Post> opening = event -> {
                if (closed || event.getEntity() != owner) return;
                boolean previous = ownerBracket; ownerBracket = true; ownerBracketTime = level.getGameTime();
                observe(() -> {
                    premise(!previous && ownerPreAge == owner.tickCount && ownerPreTime == ownerBracketTime, "actual owner component bracket");
                    if (phase != Phase.ARM) return;
                    state();
                    if (lane != Lane.EXPIRED) { freshZombie(); premise(nearCache(), "near cache not settled before public attack"); }
                    else premise(zombie == null && emitted.isEmpty(), "expiry control already has a decision consumer");
                    performMelee();
                    if (lane == Lane.DRAIN) pressDrain();
                });
            };
            Consumer<LivingIncomingDamageEvent> incoming = event -> observe(() -> {
                if (event.getEntity() != victim) return;
                DamageSource source = event.getSource();
                if (source.getEntity() instanceof HungryZombieEntity attacker && owned.contains(attacker) && attacker.getTarget() == victim) return;
                premise(ownerBracket && combatVictim == victim && source.getEntity() == owner && source.getDirectEntity() == owner
                        && !event.isCanceled() && event.getOriginalAmount() > 0F, "not the owned actual public/registered incoming damage");
                Attempt attempt = new Attempt(event, owner, victim, level.getGameTime());
                if (meleeCalling) {
                    premise(meleeAttempt == null && source.is(DamageTypes.PLAYER_ATTACK), "actual ordinary melee source"); meleeAttempt = attempt;
                }
                else {
                    premise(lane == Lane.DRAIN && drainPressed && drainAttempt == null && source.is(ModDamageTypes.BLOOD_DRAIN)
                            && drainHeld() == drainInput && LivingComponentAction.getCurEntityAction(owner) == drainAction
                            && drainAction.ability == drain && drainAction.getPhase() == ActionPhase.PERFORM && drainAction.getPhaseTick() < 1,
                            "not first real registered Drain PERFORM pulse"); drainAttempt = attempt;
                }
            });
            Consumer<LivingDamageEvent.Post> damage = event -> observe(() -> {
                if (event.getEntity() != victim) return;
                DamageSource source = event.getSource();
                if (source.getEntity() instanceof HungryZombieEntity attacker && owned.contains(attacker) && attacker.getTarget() == victim) return;
                Attempt attempt = meleeAttempt != null && source == meleeAttempt.source ? meleeAttempt : drainAttempt;
                premise(attempt != null && source == attempt.source && attempt.applied == null && !attempt.incoming.isCanceled()
                        && event.getNewDamage() > 0F && victim.getHealth() < attempt.healthBefore, "actual applied damage/HP receipt missing");
                attempt.applied = event.getNewDamage(); attempt.healthAfter = victim.getHealth();
                log("damage type=" + source.getMsgId() + " world=" + attempt.time + " ownerAge=" + attempt.ownerAge
                        + " victimAge=" + attempt.victimAge + " applied=" + attempt.applied + " HP=" + attempt.healthBefore + "->" + attempt.healthAfter);
            });
            Consumer<EntityTickEvent.Post> closing = event -> {
                if (closed) return;
                if (event.getEntity() == owner) {
                    observe(this::ownerClosing);
                    ownerLastAge = owner.tickCount; ownerPosts++; ownerBracket = false; combatVictim = null;
                }
                if (event.getEntity() == victim) victimPosts++;
                if (event.getEntity() == zombie) {
                    observe(this::zombieClosing);
                    zombieLastAge = zombie.tickCount; zombiePosts++; before = null;
                }
            };
            add(join, EntityJoinLevelEvent.class, EventPriority.LOWEST, true);
            add(drops, LivingDropsEvent.class, EventPriority.LOWEST, true);
            add(death, LivingDeathEvent.class, EventPriority.LOWEST, true);
            add(pre, EntityTickEvent.Pre.class, EventPriority.LOWEST, true);
            add(opening, EntityTickEvent.Post.class, EventPriority.HIGHEST, false);
            add(incoming, LivingIncomingDamageEvent.class, EventPriority.LOWEST, true);
            add(damage, LivingDamageEvent.Post.class, EventPriority.LOWEST, false);
            add(closing, EntityTickEvent.Post.class, EventPriority.LOWEST, false);
        }

        private Cow createVictim() {
            Cow result = EntityType.COW.create(level); premise(result != null, "owned Cow creation"); owned.add(result);
            result.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D); result.setHealth(100F);
            Vec3 offset = zombie == null ? new Vec3(1, 0, 0) : zombie.position().subtract(owner.position()).multiply(1, 0, 1).normalize();
            if (offset.lengthSqr() == 0) offset = new Vec3(1, 0, 0);
            Vec3 pos = owner.position().add(offset.scale(0.8D)); result.moveTo(pos.x, floorY, pos.z, 0, 0);
            premise(level.addFreshEntity(result) && !result.isNoAi() && !result.isNoGravity() && result.getLastDamageSource() == null, "natural fresh undamaged Cow admission");
            ready(result);
            if (lane != Lane.EXPIRED) for (HungryZombieEntity emittedZombie : emitted) premise(emittedZombie.distanceToSqr(result) > 25D, "incidental zombie melee can replace the first source");
            return result;
        }
        private void pressSummon() {
            state();
            premise(!summonPressed && LivingComponentAction.getCurEntityAction(owner) == null
                    && VampirismState.get(owner).blood().current() >= 100F, "fresh ordinary summon admission preparation");
            LivingComponentAction.getComponent(owner).entityAim.setTarget(ActionTarget.EMPTY);
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(summon), owner, InputMethod.CLICK), "registered summon CLICK admission");
            summonPressed = true; begin(Phase.SUMMON);
            var input = AbilityInput.keyPress(SUMMON_KEY, summon, owner, null, InputMethod.CLICK, 0F, BufferingState.clickOnly(), summon.getAbilityId());
            premise(input != null && input.action instanceof VampirismZombieSummonAbility.ZombieSummonInstance, "actual summon action");
            summonAction = (EntityActionInstance) input.action; summonGeneration = input.generation;
            premise(summonGeneration > 0 && summonAction == LivingComponentAction.getCurEntityAction(owner), "summon generation/action identity");
        }
        private void performMelee() {
            premise(!meleeDone && meleeAttempt == null && victim.isAlive() && owner.distanceToSqr(victim) < 4D
                    && owner.getMainHandItem().isEmpty() && owner.getOffhandItem().isEmpty() && owner.getLastHurtMob() == null, "physical fresh ordinary melee preparation");
            int old = owner.getLastHurtMobTimestamp(); float health = victim.getHealth();
            meleeCalling = true; combatVictim = victim;
            try { owner.attack(victim); } finally { meleeCalling = false; }
            premise(meleeAttempt != null && meleeAttempt.applied != null && meleeAttempt.applied > 0F && victim.isAlive()
                    && victim.getHealth() < health && victim.getLastDamageSource() == meleeAttempt.source
                    && owner.getLastHurtMob() == victim && owner.getLastHurtMobTimestamp() > old, "fully returned actual melee/memory/source");
            memoryStamp = owner.getLastHurtMobTimestamp(); meleeTime = level.getGameTime(); meleeDone = true;
            log("public-melee returned world=" + meleeTime + " ownerAge=" + owner.tickCount + " victimAge=" + victim.tickCount
                    + " memory=" + memoryStamp + " source=" + victim.getLastDamageSource().getMsgId());
        }
        private void pressDrain() {
            premise(meleeDone && !drainPressed && meleeAttempt.applied < 2F && ownerBracket
                    && LivingComponentAction.getCurEntityAction(owner) == null && owner.hasLineOfSight(victim), "genuine post-melee Drain preparation/cooldown eligibility");
            Vec3 hit = victim.getBoundingBox().getCenter(); Vec3 direction = hit.subtract(owner.getEyePosition());
            owner.setYRot((float) -Math.toDegrees(Math.atan2(direction.x, direction.z)));
            owner.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
            owner.setYHeadRot(owner.getYRot()); owner.yBodyRot = owner.getYRot();
            LivingComponentAction.getComponent(owner).entityAim.setTarget(ActionTarget.fromVanilla(new EntityHitResult(victim, hit)));
            AvailableAbilities available = new AvailableAbilities(); available.update(power, power.getMoveset());
            premise(AbilityInput.withConditionCheck(available.getContextVariationContainer(drain), owner, InputMethod.HOLD), "registered same-victim Drain HOLD admission");
            drainPressed = true;
            drainInput = AbilityInput.keyPress(DRAIN_KEY, drain, owner, null, InputMethod.HOLD, 0F, BufferingState.clickOnly(), drain.getAbilityId());
            premise(drainInput != null && drainInput.action instanceof VampirismBloodDrainAbility.BloodDrainInstance, "actual registered Drain action");
            drainAction = (EntityActionInstance) drainInput.action; drainGeneration = drainInput.generation;
            premise(drainGeneration > 0 && drainHeld() == drainInput && drainAction == LivingComponentAction.getCurEntityAction(owner)
                    && drainAction.ability == drain && drainAction.getPhase() == ActionPhase.PERFORM && drainAction.getPhaseTick() < 1,
                    "production-initialized first PERFORM, without phase forcing");
        }
        private void ownerClosing() {
            premise(ownerBracket && ownerPreAge == owner.tickCount && ownerBracketTime == level.getGameTime(), "completed actual owner Post bracket");
            if (phase == Phase.SUMMON && emitted.size() == expectedCount) {
                premise(!summonReleased && ownerAttack != null && AbilityInput.keyReleaseAndGetGeneration(SUMMON_KEY, owner) == summonGeneration, "exact summon release after all real emissions");
                summonReleased = true;
                if (lane == Lane.EXPIRED) { premise(expired && victim.getLastDamageSource() == null, "expiry changed during summon"); sourceReady = true; begin(Phase.OBSERVE); }
                else { moveOwner(near); begin(Phase.SETTLE); }
            }
            if (phase == Phase.ARM && meleeDone) {
                premise(owner.getLastHurtMob() == victim && owner.getLastHurtMobTimestamp() == memoryStamp && victim.isAlive(), "original real owner event lost");
                if (lane == Lane.DRAIN) {
                    premise(drainAttempt != null && drainAttempt.applied != null && drainAttempt.applied > 0F
                            && drainAttempt.healthAfter < meleeAttempt.healthAfter && drainAttempt.time == meleeTime
                            && drainAttempt.ownerAge == owner.tickCount && drainHeld() == drainInput
                            && drainAction == LivingComponentAction.getCurEntityAction(owner) && drainAction.getPhase() == ActionPhase.PERFORM
                            && victim.getLastDamageSource() == drainAttempt.source && drainAttempt.source.is(ModDamageTypes.BLOOD_DRAIN)
                            && drainAttempt.source.getMsgId().startsWith("bloodDrain"), "same-bracket fully returned real registered drain before first decision");
                    drainTime = drainAttempt.time;
                    premise(AbilityInput.keyReleaseAndGetGeneration(DRAIN_KEY, owner) == drainGeneration && drainHeld() == null && drainAction.isOver(), "exact genuine Drain generation release");
                    drainReleased = true;
                }
                else premise(victim.getLastDamageSource() == meleeAttempt.source, "ordinary source changed before observation");
                if (lane == Lane.EXPIRED) begin(Phase.EXPIRE);
                else { sourceReady = true; begin(Phase.OBSERVE); }
                log("source-ready world=" + level.getGameTime() + " source=" + victim.getLastDamageSource().getMsgId()
                        + " memory=" + memoryStamp + " subject=" + (zombie == null ? "not-emitted-yet" : zombie.getUUID()));
            }
            if (phase == Phase.EXPIRE) {
                state();
                premise(zombie == null && emitted.isEmpty() && owner.getLastHurtMob() == victim && owner.getLastHurtMobTimestamp() == memoryStamp
                        && victim.getHealth() == meleeAttempt.healthAfter, "expiry control history/damage changed");
                long age = level.getGameTime() - meleeTime; DamageSource source = victim.getLastDamageSource();
                if (age <= 40) premise(source == meleeAttempt.source, "source became null before actual >40 world ticks");
                else { premise(source == null, "source did not naturally expire"); expired = true; }
                if (age == 40 || age == 41) log("nullable-boundary elapsedWorld=" + age + " ownerAge=" + owner.tickCount
                        + " victimAge=" + victim.tickCount + " victimPosts=" + victimPosts + " source=" + (source == null ? "null" : source.getMsgId()));
            }
        }
        private void zombieClosing() {
            if (phase == Phase.SETTLE) {
                freshZombie(); settled = nearCache() ? settled + 1 : 0; return;
            }
            if (phase != Phase.OBSERVE) {
                if (!meleeDone && ownerAttack != null) premise(!ownerAttack.isRunning() && zombie.getTarget() == null, "old target history before priming");
                return;
            }
            premise(before != null && before.age() == zombie.tickCount && before.time() == level.getGameTime()
                    && before.ownerMemory() == memoryStamp && nearCache(), "actual qualified zombie Pre/Post decision pair");
            state(); noOtherTargetGoal(); observations++; if (before.fullUpdate()) decisions++;
            boolean admitted = ownerAttack.isRunning() && zombie.getTarget() == victim;
            log("decision uuid=" + zombie.getUUID() + " age=" + zombie.tickCount + " world=" + before.time()
                    + " victimAge=" + before.victimAge() + " memory=" + before.ownerMemory()
                    + " source=" + (before.source() == null ? "null" : before.source().getMsgId())
                    + " fullUpdate=" + before.fullUpdate() + " running=" + ownerAttack.isRunning() + " target=" + (zombie.getTarget() == null ? "none" : zombie.getTarget().getUUID()));
            if (lane == Lane.MELEE) {
                oracle(!ownerAttack.isRunning() && zombie.getTarget() != victim, "fresh non-drain victim source admitted the owner target");
                if (observations >= 12) { premise(decisions >= 6, "insufficient natural target-selector opportunities"); completed = true; }
            }
            else {
                premise(zombie.getTarget() == null || zombie.getTarget() == victim, "unrelated target in positive control");
                if (admitted) { premise(decisions > 0, "positive admission without actual natural selector update"); completed = true; }
                else oracle(observations < 12, "qualified drain/null source did not admit the genuine prior owner event");
            }
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure instanceof RuntimeException error) throw error;
                if (observerFailure instanceof Error error) throw error;
                if (completed) { log("RESULT qualified=true observations=" + observations + " naturalDecisions=" + decisions + " native=false"); close(); helper.succeed(); return; }
                state();
                if (phase == Phase.OWNER_READY) {
                    int skyLight = brightestSkyLight();
                    premise(helper.getTick() < LIGHT_WAIT_TICKS, "room sky light did not settle, brightest=" + skyLight);
                    if (ownerPosts < 3 || skyLight != 0) { helper.runAfterDelay(1, this::poll); return; }
                    readyTick = helper.getTick();
                    if (lane == Lane.EXPIRED) { victim = createVictim(); begin(Phase.ARM); }
                    else pressSummon();
                }
                premise(helper.getTick() - readyTick < 150, "finite production/actor/source watchdog");
                if (phase == Phase.SETTLE && settled >= 2 && LivingComponentAction.getCurEntityAction(owner) == null) {
                    freshZombie(); premise(nearCache(), "settled near owner cache"); victim = createVictim(); begin(Phase.ARM);
                }
                if (phase == Phase.EXPIRE && expired) {
                    Vec3 farthest = null; double distance = -1D;
                    for (double x : new double[] { chunk.getMinBlockX() + 2.5D, chunk.getMinBlockX() + 13.5D })
                        for (double z : new double[] { chunk.getMinBlockZ() + 2.5D, chunk.getMinBlockZ() + 13.5D }) {
                            Vec3 pos = new Vec3(x, floorY, z); double candidate = pos.distanceToSqr(victim.position());
                            if (candidate > distance) { distance = candidate; farthest = pos; }
                        }
                    premise(farthest != null && distance > 25D, "null control incidental-melee isolation"); moveOwner(farthest); pressSummon();
                }
                premise(helper.getTick() - phaseStart < (phase == Phase.EXPIRE ? 55 : 45), "finite phase qualification watchdog " + phase);
                helper.runAfterDelay(1, this::poll);
            }
            catch (RuntimeException | Error error) { closeAfterFailure(error); throw error; }
        }
        private void cleanup(Runnable operation, List<Throwable> failures) { try { operation.run(); } catch (RuntimeException | Error error) { failures.add(error); } }
        @Override public void close() {
            if (closed) return; closed = true; List<Throwable> failures = new ArrayList<>();
            for (Object listener : listeners) cleanup(() -> NeoForge.EVENT_BUS.unregister(listener), failures); listeners.clear();
            cleanup(() -> { if (owner != null) AbilityInput.keyRelease(SUMMON_KEY, owner); }, failures);
            cleanup(() -> { if (owner != null) AbilityInput.keyRelease(DRAIN_KEY, owner); }, failures);
            cleanup(() -> { if (owner != null && owner.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                    .map(state -> !state.heldKeys.isEmpty()).orElse(false)) throw new IllegalStateException("owned input remains held"); }, failures);
            cleanup(() -> { if (power != null) power.setPowerType(null); }, failures);
            cleanup(() -> { if (owner != null) owner.stopUsingItem(); }, failures);
            cleanup(() -> { if (owner != null) owner.getInventory().clearContent(); }, failures);
            for (Entity entity : owned) cleanup(() -> { if (!entity.isRemoved()) entity.discard(); }, failures);
            for (var cell : cells.entrySet()) cleanup(() -> level.setBlockAndUpdate(cell.getKey(), cell.getValue()), failures);
            cleanup(() -> {
                boolean restored = cells.entrySet().stream().allMatch(cell -> level.getBlockState(cell.getKey()).equals(cell.getValue()) && level.getBlockEntity(cell.getKey()) == null);
                boolean removed = owned.stream().allMatch(entity -> entity.isRemoved() && level.getEntity(entity.getUUID()) == null);
                boolean playersGone = owner == null || !level.players().contains(owner) && !level.getServer().getPlayerList().getPlayers().contains(owner);
                log("cleanup cells=" + cells.size() + " actors=" + owned.size() + " restored=" + restored + " removed=" + removed + " playersGone=" + playersGone + " completed=" + completed);
                if (!restored || !removed || !playersGone) throw new IllegalStateException("owned source scene cleanup incomplete");
            }, failures);
            if (!failures.isEmpty()) { IllegalStateException error = new IllegalStateException("Hungry Zombie source fixture cleanup failed"); failures.forEach(error::addSuppressed); throw error; }
        }
        private void closeAfterFailure(Throwable error) { try { close(); } catch (RuntimeException | Error cleanup) { error.addSuppressed(cleanup); } }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { if (test.getError() != null) closeAfterFailure(test.getError()); else close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { if (oldTest.getError() != null) closeAfterFailure(oldTest.getError()); else close(); }
    }
}
