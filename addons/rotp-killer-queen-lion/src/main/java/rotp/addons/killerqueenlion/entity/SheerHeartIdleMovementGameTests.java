package rotp.addons.killerqueenlion.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.KillerQueenGameTests;
import rotp.addons.killerqueenlion.state.KillerQueenStateService;
import rotp.core.init.ModGamerules;
import rotp.core.powersystem.PowerClass;

@GameTestHolder(KillerQueenLionAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SheerHeartIdleMovementGameTests {
    private static final double EPSILON = 1.0E-7D;

    private SheerHeartIdleMovementGameTests() {}

    @GameTest(template = "empty", templateNamespace = KillerQueenLionAddon.MOD_ID,
            batch = "r575_idle_move", timeoutTicks = 280)
    public static void normalSpeedIdleKeepsSwellMoveOwnership(GameTestHelper helper) {
        assertIdleMoveOwnership(helper, false);
    }

    @GameTest(template = "empty", templateNamespace = KillerQueenLionAddon.MOD_ID,
            batch = "review2_band_idle_move", timeoutTicks = 460)
    public static void bandTargetKeepsMoveAcrossBothPostBlastAiParities(GameTestHelper helper) {
        assertIdleMoveOwnership(helper, true);
    }

    private static void assertIdleMoveOwnership(GameTestHelper helper, boolean bandTarget) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "IdleMoveProbe"));
        Creeper target = EntityType.CREEPER.create(level);
        SheerHeartAttackEntity sheer = KillerQueenLionAddon.SHEER_HEART_ENTITY.get().create(level);
        helper.assertTrue(target != null && sheer != null, "Missing idle movement fixtures");
        Difficulty oldDifficulty = level.getDifficulty();
        boolean oldBreakRule = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
        AtomicBoolean finished = new AtomicBoolean();
        int[] blasts = { 0 };
        List<Integer> blastTicks = new ArrayList<>();
        Consumer<ExplosionEvent.Detonate> listener = event -> {
            if (event.getLevel() == level && event.getExplosion().getDirectSourceEntity() == owner) {
                blasts[0]++;
                blastTicks.add(sheer.tickCount);
                if (bandTarget) {
                    LogUtils.getLogger().info(
                            "T-B-2 blast: number={}, id={}, entityTick={}, nextParity={}, distance={}, LOS={}, pos={}",
                            blasts[0], sheer.getId(), sheer.tickCount, (sheer.tickCount + 1 + sheer.getId()) & 1,
                            sheer.distanceTo(target), sheer.getSensing().hasLineOfSight(target), sheer.position());
                }
            }
        };
        Runnable cleanup = () -> {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            NeoForge.EVENT_BUS.unregister(listener);
            KillerQueenStateService.recallSheerHeart(owner);
            sheer.discard();
            target.discard();
            var power = PowerClass.STAND.get(owner);
            if (power != null && power.hasPower()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            owner.discard();
            KillerQueenGameTests.sheerHeartPad(helper, false);
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(oldBreakRule, level.getServer());
            level.getServer().setDifficulty(oldDifficulty, true);
        };
        try {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(false, level.getServer());
            BlockPos pad = KillerQueenGameTests.sheerHeartPad(helper, true);
            owner.setNoGravity(true);
            owner.setPos(Vec3.atBottomCenterOf(pad));
            level.addNewPlayer(owner);
            var power = PowerClass.STAND.attachGet(owner);
            power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
            power.setResolveLevel(4);
            helper.assertTrue(KillerQueenLionAddon.KILLER_QUEEN.get().summon(owner, power), "Idle caster summon failed");
            var stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Missing idle caster Stand");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0D);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            target.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            target.setHealth(2000.0F);
            sheer.bind(owner, stand, target);
            helper.assertTrue(!sheer.isRemoved(), "Idle fixture authority failed");
            sheer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3D);
            sheer.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            sheer.setPos(owner.position().add(8, 0, -3));
            target.setPos(sheer.position().add(0, 0, 2.75D));
            sheer.setDeltaMovement(Vec3.ZERO);
            target.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(target);
            level.addFreshEntity(sheer);
            helper.assertTrue(KillerQueenStateService.linkSheerHeart(owner, sheer), "Idle fixture link failed");
            Vec3[] targetPosition = { target.position() };
            Vec3 spawnPosition = sheer.position();
            IdleTrace trace = new IdleTrace();
            NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, listener);
            helper.onEachTick(() -> {
                if (finished.get()) {
                    return;
                }
                try {
                    helper.assertTrue(target.isAlive() && !sheer.isRemoved(), "Idle fixture died or lost authority");
                    helper.assertTrue(!sheer.isNoAi()
                                    && sheer.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.3D,
                            "Normal-speed production AI was disabled by the fixture");
                    helper.assertTrue(target.position().distanceToSqr(targetPosition[0]) < EPSILON,
                            "Target moved and contaminated the idle test");
                    helper.assertTrue(level.getEntities(sheer, sheer.getBoundingBox().inflate(0.001D)).isEmpty(),
                            "Body contact contaminated the idle test");
                    int idle = sheer.postExplosionIdleTicks();
                    if (blasts[0] == 0) {
                        if (bandTarget && !trace.targetMoved && sheer.swellTicks() >= 3
                                && sheer.swellTicks() < SheerHeartAttackEntity.SWELL_TICKS) {
                            helper.assertTrue(IdleTrace.moveGoals(sheer).equals(List.of("SwellGoal")),
                                    "T-B-2 target moved before the natural SwellGoal acquired MOVE");
                            targetPosition[0] = spawnPosition.add(0, 0, 5.0D);
                            target.setPos(targetPosition[0]);
                            trace.targetMoved = true;
                        }
                        if (trace.stationaryWindupTicks % 20 == 0) {
                            LogUtils.getLogger().info(
                                    "R575 pre-blast: observedTicks={}, entityTicks={}, pos={}, target={}, targetDistance={}, LOS={}, swell={}, MOVE={}",
                                    trace.stationaryWindupTicks, sheer.tickCount, sheer.position(),
                                    sheer.getTarget() != null ? sheer.getTarget().getUUID() : null,
                                    sheer.distanceTo(target), sheer.getSensing().hasLineOfSight(target),
                                    sheer.swellTicks(), IdleTrace.moveGoals(sheer));
                        }
                        helper.assertTrue(sheer.position().distanceToSqr(spawnPosition) < EPSILON
                                        && sheer.getDeltaMovement().horizontalDistanceSqr() < EPSILON,
                                "Pre-blast movement contaminated the old-Motion control");
                        trace.stationaryWindupTicks++;
                        return;
                    }
                    helper.assertTrue(blasts[0] <= (bandTarget ? 2 : 1),
                            "An extra blast occurred before idle measurement finished");
                    if (bandTarget) {
                        helper.assertTrue(trace.targetMoved && sheer.getTarget() == target
                                        && sheer.distanceToSqr(target) > SheerHeartAttackEntity.SWELL_START_DISTANCE_SQR
                                        && sheer.distanceToSqr(target) <= SheerHeartAttackEntity.SWELL_KEEP_DISTANCE_SQR
                                        && sheer.getSensing().hasLineOfSight(target),
                                "T-B-2 retained target left the visible 3..7-block discriminator band");
                        int blastTick = blastTicks.getLast();
                        if (sheer.tickCount == blastTick + 1) {
                            int parity = (sheer.tickCount + sheer.getId()) & 1;
                            trace.observedParities |= 1 << parity;
                            LogUtils.getLogger().info(
                                    "T-B-2 actual post-blast AI: blast={}, id={}, entityTick={}, parity={}, idle={}, MOVE={}",
                                    blasts[0], sheer.getId(), sheer.tickCount, parity, idle, IdleTrace.moveGoals(sheer));
                        }
                        helper.assertTrue(IdleTrace.moveGoals(sheer).equals(List.of("SwellGoal")),
                                "T-B-2 idle lost SwellGoal MOVE: blast=" + blasts[0] + ", entityTick="
                                        + sheer.tickCount + ", parity=" + ((sheer.tickCount + sheer.getId()) & 1)
                                        + ", MOVE=" + IdleTrace.moveGoals(sheer));
                    }
                    if (trace.idleStart == null) {
                        helper.assertTrue(idle > 0 && trace.stationaryWindupTicks >= 5
                                        && sheer.getDeltaMovement().horizontalDistanceSqr() < EPSILON,
                                "First natural explosion did not begin idle at rest");
                        trace.idleStart = sheer.position();
                        trace.startIdle = idle;
                    }
                    if (idle > 0) {
                        trace.sample(sheer);
                        return;
                    }
                    LogUtils.getLogger().info(
                            "R575 idle summary: windupRestTicks={}, idleSamples={}, startIdle={}, maxDisplacement={}, maxMotion={}, foreignMoveGoal={}, final={}",
                            trace.stationaryWindupTicks, trace.samples, trace.startIdle, trace.maxDisplacement,
                            trace.maxMotion, trace.foreignMoveGoal, sheer.position());
                    helper.assertTrue(trace.startIdle >= 199 && trace.samples >= 199,
                            "The complete natural 200-tick idle was not measured");
                    helper.assertTrue(!trace.foreignMoveGoal && trace.maxDisplacement < EPSILON
                                    && trace.maxMotion < EPSILON,
                            "R575 idle MOVE ownership leaked: foreignGoal=" + trace.foreignMoveGoal
                                    + ", displacement=" + trace.maxDisplacement + ", motion=" + trace.maxMotion);
                    if (bandTarget && blasts[0] == 1) {
                        trace.idleStart = null;
                        trace.samples = 0;
                        return;
                    }
                    if (bandTarget) {
                        helper.assertTrue(blastTicks.get(1) - blastTicks.getFirst() == 201
                                        && trace.observedParities == 3,
                                "T-B-2 did not cover both actual AI parities at the natural 201-tick repeat cadence");
                        LogUtils.getLogger().info("T-B-2 complete: blastTicks={}, observedParityMask={}, secondIdleSamples={}",
                                blastTicks, trace.observedParities, trace.samples);
                    }
                    cleanup.run();
                    helper.succeed();
                }
                catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
            });
            helper.runAfterDelay(bandTarget ? 450 : 270, () -> {
                if (!finished.get()) {
                    cleanup.run();
                    helper.fail("R575 did not finish one natural blast and its complete idle");
                }
            });
        }
        catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static final class IdleTrace {
        private Vec3 idleStart;
        private int stationaryWindupTicks;
        private int startIdle;
        private int samples;
        private double maxDisplacement;
        private double maxMotion;
        private boolean foreignMoveGoal;
        private boolean targetMoved;
        private int observedParities;
        private List<String> previousGoals = List.of();

        private static List<String> moveGoals(SheerHeartAttackEntity sheer) {
            return sheer.goalSelector.getAvailableGoals().stream()
                    .filter(goal -> goal.isRunning())
                    .filter(goal -> goal.getFlags().contains(Goal.Flag.MOVE))
                    .map(goal -> goal.getGoal().getClass().getSimpleName()).toList();
        }

        private void sample(SheerHeartAttackEntity sheer) {
            int idle = sheer.postExplosionIdleTicks();
            List<String> goals = moveGoals(sheer);
            samples++;
            maxDisplacement = Math.max(maxDisplacement, sheer.position().distanceTo(idleStart));
            maxMotion = Math.max(maxMotion, sheer.getDeltaMovement().horizontalDistance());
            foreignMoveGoal |= goals.stream().anyMatch(name -> !name.equals("SwellGoal"));
            if (samples == 1 || idle == 1 || idle % 20 == 0 || !goals.equals(previousGoals)) {
                var move = sheer.getMoveControl();
                LogUtils.getLogger().info(
                        "R575 idle trace: idle={}, swell={}, pos={}, motion={}, MOVE={}, navDone={}, hasWanted={}, wanted=({},{},{}), speed={}, forward={}",
                        idle, sheer.swellTicks(), sheer.position(), sheer.getDeltaMovement(), goals,
                        sheer.getNavigation().isDone(), move.hasWanted(), move.getWantedX(), move.getWantedY(),
                        move.getWantedZ(), sheer.getSpeed(), sheer.zza);
            }
            previousGoals = goals;
        }
    }
}
