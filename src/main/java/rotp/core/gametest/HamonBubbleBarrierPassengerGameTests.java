package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonBubbleBarrierAbility;
import rotp.core.impl.powers.hamon.entity.HamonBubbleBarrierEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
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

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonBubbleBarrierPassengerGameTests {
    private static final double EPSILON = 1.0E-4D;

    private HamonBubbleBarrierPassengerGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "barrier_passenger_pig", timeoutTicks = 100)
    public static void pigRidesCenteredInsideRisingBarrier(GameTestHelper helper) {
        start(helper, EntityType.PIG, "pig");
    }

    @GameTest(template = "empty", skyAccess = true, batch = "barrier_passenger_cow", timeoutTicks = 100)
    public static void cowRidesCenteredInsideRisingBarrier(GameTestHelper helper) {
        start(helper, EntityType.COW, "cow");
    }

    private static void start(GameTestHelper helper, EntityType<? extends Mob> targetType, String label) {
        Fixture fixture = new Fixture(helper, targetType, label);
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

    private record Sample(long time, int barrierAge, int passengerAge, int barrierTicks,
            UUID vehicle, Vec3 barrierPos, float barrierHeight, Vec3 passengerPos,
            float passengerHeight, boolean alive, boolean stunned, boolean noAi,
            boolean noGravity, Direction gravityDirection, boolean riderSits,
            Vec3 ridingAnchor, AABB hitbox) {
        double centerError() {
            return passengerPos.y + passengerHeight * 0.5D - barrierPos.y - barrierHeight * 0.5D;
        }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private static final short KEY = 24;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final EntityType<? extends Mob> targetType;
        private final String label;
        private final List<Object> listeners = new ArrayList<>();
        private Player user;
        private Mob target;
        private PlayerPower power;
        private HamonData hamon;
        private Ability ability;
        private EntityActionInstance action;
        private HamonBubbleBarrierEntity barrier;
        private Vec3 spawnPosition;
        private RuntimeException observerFailure;
        private Sample first;
        private Sample second;
        private int userTicks;
        private int barrierTicks;
        private int pressUserTicks;
        private int firedUserTicks = -1;
        private int impactPassengerAge;
        private long firedAt = -1;
        private long impactAt = -1;
        private long passengerPostAt = -1;
        private boolean pressed;
        private boolean released;
        private boolean closed;

        Fixture(GameTestHelper helper, EntityType<? extends Mob> targetType, String label) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.targetType = targetType;
            this.label = label;
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(BlockPos.ZERO);
            ChunkPos chunk = new ChunkPos(template);
            Vec3 origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 32.0D,
                    chunk.getMinBlockZ() + 5.0D);
            AABB space = new AABB(origin.x - 3, origin.y - 1, origin.z - 2,
                    origin.x + 3, origin.y + 8, origin.z + 7);
            helper.assertTrue(space.maxY < level.getMaxBuildHeight(), "Barrier fixture exceeds build height");
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(space.minX, space.minY, space.minZ),
                    BlockPos.containing(space.maxX, space.maxY, space.maxZ))) {
                helper.assertTrue(level.isEmptyBlock(pos), "Barrier fixture is obstructed");
            }
            helper.assertTrue(level.getEntities((Entity) null, space).isEmpty(), "Barrier fixture contains another entity");
            user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            user.setYHeadRot(0);
            user.yBodyRot = 0;
            user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOAP.get()));
            helper.assertTrue(level.addFreshEntity(user), "Could not add Barrier user");
            power = PowerClass.PLAYER_POWER.attachGet(user);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
            hamon.learnSkill(ModHamonSkills.BUBBLE_BARRIER.get());
            // Twenty paid hold ticks need headroom beyond the novice's 1000 energy capacity.
            hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(10), true, true);
            hamon.setBreathStability(hamon.getMaxBreathStability());
            hamon.setEnergy(hamon.getMaxEnergy());
            helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.BUBBLE_BARRIER.get()) && hamon.getEnergy() > 1500,
                    "Barrier skill or charge energy setup failed");
            ability = power.getAbility("bubble_barrier");
            helper.assertTrue(ability instanceof HamonBubbleBarrierAbility
                            && ability.abilityType == HamonPowerType.HAMON_BUBBLE_BARRIER.get(), "Missing registered Barrier");
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            target = targetType.create(level);
            helper.assertTrue(target != null, "Could not create Barrier passenger target");
            // Keep AI enabled: the real STUN effect rejects mobs that already have NoAI.
            target.setNoGravity(true);
            target.setPos(origin.x, user.getEyeY() - 0.3D - target.getBbHeight() * 0.5D, origin.z + 3.5D);
            helper.assertTrue(level.addFreshEntity(target) && !target.isNoAi() && !target.isInvulnerable()
                            && target.isAlive() && !EntityHamonChargeState.get(target).hasHamonCharge()
                            && PlayerPower.getPowerData(target, ModPlayerPowers.PILLAR_MAN).isEmpty()
                            && HamonAbilityHelpers.hamonDamageAmount(target, 0.1F) > 0
                            && HamonAbilityHelpers.configHamonDamageMultiplier() > 0, "Invalid vulnerable AI-enabled target");
            helper.assertTrue(DirectionalGravityApi.getEffectiveDirection(user) == Direction.DOWN
                            && DirectionalGravityApi.getEffectiveDirection(target) == Direction.DOWN, "Fixture gravity direction is not DOWN");
            registerObservers();
            log("setup user=" + user.getUUID() + " target=" + target.getUUID() + " feet=" + target.position()
                    + " height=" + target.getBbHeight() + " noAi=" + target.isNoAi() + " userNoGravity=" + user.isNoGravity()
                    + " targetNoGravity=" + target.isNoGravity() + " direction=DOWN energy=" + hamon.getEnergy());
        }

        private void registerObservers() {
            Consumer<EntityJoinLevelEvent> join = event -> observe(() -> {
                if (event.getLevel() == level && event.getEntity() instanceof HamonBubbleBarrierEntity created && created.getOwner() == user) {
                    if (barrier != null || event.isCanceled()) throw new IllegalStateException("Unexpected Barrier spawn");
                    barrier = created;
                    spawnPosition = created.position();
                    if (!created.saveWithoutId(new CompoundTag()).getBoolean("Charging")
                            || created.getBoundingBox().inflate(0.3D).intersects(target.getBoundingBox())) {
                        throw new IllegalStateException("Target overlaps the charging Barrier");
                    }
                    log("spawn uuid=" + created.getUUID() + " pos=" + spawnPosition + " height=" + created.getBbHeight()
                            + " targetDistance=" + created.distanceTo(target) + " charging=true");
                }
            });
            Consumer<ProjectileImpactEvent> impact = event -> observe(() -> {
                if (event.getProjectile() == barrier) {
                    if (event.isCanceled() || !(event.getRayTraceResult() instanceof EntityHitResult hit) || hit.getEntity() != target
                            || barrier.saveWithoutId(new CompoundTag()).getBoolean("Charging") || firedAt < 0
                            || firedUserTicks - pressUserTicks < 20) {
                        throw new IllegalStateException("Barrier impact was early, canceled or on another target");
                    }
                    if (impactAt < 0) {
                        impactAt = level.getGameTime();
                        impactPassengerAge = target.tickCount;
                        log("impact time=" + impactAt + " barrierAge=" + barrier.tickCount + " passengerAge=" + target.tickCount
                                + " targetHeight=" + target.getBbHeight() + " pos=" + barrier.position() + " target=" + target.position());
                    }
                }
            });
            Consumer<EntityTickEvent.Post> post = event -> observe(() -> {
                if (event.getEntity() == user) {
                    userTicks++;
                    if (pressed && barrier != null && firedAt < 0) {
                        boolean charging = barrier.saveWithoutId(new CompoundTag()).getBoolean("Charging");
                        if (charging && barrier.position().distanceToSqr(spawnPosition) > EPSILON * EPSILON) {
                            throw new IllegalStateException("Charging Barrier moved before firing");
                        }
                        if (!charging) {
                            firedAt = level.getGameTime();
                            firedUserTicks = userTicks;
                            log("fired time=" + firedAt + " naturalHoldTicks=" + (firedUserTicks - pressUserTicks)
                                    + " phase=" + action.getPhase() + " velocity=" + barrier.getDeltaMovement());
                        }
                    }
                }
                if (event.getEntity() == barrier) barrierTicks++;
                if (event.getEntity() == target && barrier != null && target.getVehicle() == barrier) {
                    passengerPostAt = level.getGameTime();
                }
            });
            // NeoForge's entity Post is inside rideTick, before positionRider; sample only after the level finishes.
            Consumer<LevelTickEvent.Post> levelPost = event -> observe(() -> {
                if (event.getLevel() == level && barrier != null && impactAt >= 0 && level.getGameTime() > impactAt
                        && passengerPostAt == level.getGameTime() && target.tickCount > impactPassengerAge && target.getVehicle() == barrier) {
                    if (!level.isPositionEntityTicking(target.blockPosition()) || !level.isPositionEntityTicking(barrier.blockPosition())) {
                        throw new IllegalStateException("Riding Barrier left its ticking fixture");
                    }
                    Sample sample = new Sample(level.getGameTime(), barrier.tickCount, target.tickCount, barrierTicks,
                            target.getVehicle().getUUID(), barrier.position(), barrier.getBbHeight(), target.position(), target.getBbHeight(),
                            target.isAlive() && barrier.isAlive(), target.hasEffect(ModStatusEffects.STUN), target.isNoAi(),
                            target.isNoGravity(), DirectionalGravityApi.getEffectiveDirection(target), barrier.shouldRiderSit(),
                            barrier.getPassengerRidingPosition(target), target.getHitbox());
                    if (first == null) {
                        first = sample;
                        log("passenger-first " + sample + " centerError=" + sample.centerError());
                    }
                    else if (second == null && sample.time > first.time) {
                        second = sample;
                        log("passenger-next " + sample + " centerError=" + sample.centerError());
                    }
                }
            });
            listeners.add(join);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, join);
            listeners.add(impact);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ProjectileImpactEvent.class, impact);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            listeners.add(levelPost);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LevelTickEvent.Post.class, levelPost);
        }

        private void observe(Runnable observation) {
            if (closed || observerFailure != null) return;
            try {
                observation.run();
            }
            catch (RuntimeException error) {
                observerFailure = error;
                JojoMod.LOGGER.error("Barrier passenger observer failure", error);
            }
        }

        private void poll() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                helper.assertTrue(helper.getTick() < 80, "Barrier watchdog: userTicks=" + userTicks + " fired=" + firedAt
                        + " impact=" + impactAt + " mounted=" + (target.getVehicle() == barrier)
                        + " phase=" + (action == null ? "none" : action.getPhase() + "/" + action.getPhaseTick()));
                if (!pressed && userTicks >= 2) press();
                if (firedAt >= 0 && !released) {
                    AbilityInput.keyRelease(KEY, user);
                    released = true;
                }
                if (first != null && second != null) {
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

        private void press() {
            helper.assertTrue(level.isPositionEntityTicking(user.blockPosition()) && level.isPositionEntityTicking(target.blockPosition()),
                    "Barrier user or target is not entity-ticking");
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD),
                    "Registered Barrier HOLD was not admitted");
            pressed = true;
            pressUserTicks = userTicks;
            var input = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            helper.assertTrue(input != null && input.action instanceof HamonBubbleBarrierAbility.BubbleBarrierInstance shot
                            && shot == LivingComponentAction.getComponent(user).getAction() && shot.ability == ability
                            && shot.getPhase() == ActionPhase.WINDUP && shot.phasesLength.getFloat(ActionPhase.WINDUP) == 20.0F,
                    "Registered Barrier did not install its 20-tick windup action");
            action = (EntityActionInstance) input.action;
            log("press ability=" + ability.getAbilityId() + " windup=20 userTicks=" + userTicks);
        }

        private void validate() {
            helper.assertTrue(barrier.getType() == ModEntityTypes.HAMON_BUBBLE_BARRIER.get() && barrier.getOwner() == user
                            && firedUserTicks - pressUserTicks >= 20 && impactAt >= firedAt,
                    "Barrier spawn, charge or impact proof is invalid");
            helper.assertTrue(second.time > first.time && second.passengerAge > first.passengerAge
                            && second.barrierTicks > first.barrierTicks && second.barrierPos.y > first.barrierPos.y + EPSILON,
                    "Barrier did not continue rising through natural passenger ticks");
            for (Sample sample : List.of(first, second)) {
                helper.assertTrue(sample.alive && sample.stunned && sample.vehicle.equals(barrier.getUUID())
                                && sample.barrierHeight > 0 && sample.passengerHeight > 0 && sample.gravityDirection == Direction.DOWN,
                        "Barrier passenger lifecycle or dimensions are invalid");
                helper.assertTrue(Math.abs(sample.passengerPos.x - sample.barrierPos.x) < EPSILON
                                && Math.abs(sample.passengerPos.z - sample.barrierPos.z) < EPSILON,
                        "Barrier passenger is not horizontally centered");
                helper.assertTrue(Math.abs(sample.centerError()) < EPSILON,
                        "Barrier passenger center error=" + sample.centerError() + ", riderSits=" + sample.riderSits);
                helper.assertTrue(!sample.riderSits, "Barrier must retain donor non-seated rider policy");
                double midpointY = sample.barrierPos.y + sample.barrierHeight * 0.5D;
                helper.assertTrue(sample.ridingAnchor.distanceToSqr(new Vec3(sample.barrierPos.x, midpointY, sample.barrierPos.z))
                                < EPSILON * EPSILON, "Barrier riding anchor is not at its midpoint: " + sample.ridingAnchor);
                helper.assertTrue(Math.abs(sample.hitbox.minY - midpointY) < EPSILON
                                && Math.abs(sample.hitbox.maxY - sample.passengerPos.y - sample.passengerHeight) < EPSILON,
                        "Barrier riding hitbox extends outside the centered captive: " + sample.hitbox);
            }
        }

        private void log(String message) {
            JojoMod.LOGGER.info("BARRIER-PASSENGER {} {}", label, message);
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
                if (barrier != null && !barrier.isRemoved()) barrier.discard();
                if (target != null) target.discard();
                if (user != null) user.discard();
                log("cleanup listeners=0");
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
