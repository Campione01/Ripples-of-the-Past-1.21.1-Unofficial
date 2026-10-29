package rotp.addons.reworked.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.addons.reworked.ReworkedAddon;
import rotp.addons.reworked.ReworkedConfig;
import rotp.addons.reworked.ReworkedGameEvents;
import rotp.addons.reworked.ability.ReworkedStandAbilities;
import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands.magiciansred.MRFlameEntity;
import rotp.core.impl.stands.magiciansred.MagiciansRedFlameBurstAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedKickAbility;
import rotp.core.impl.stands.starplatinum.HeavyPunchUppercutAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModStands;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * 1.16 Reworked GameplayEventHandler: addTWResolveInTS pays The World's user 20 / learning points of resolve on
 * every tick of their own stopped time while they are on the ground and not sprinting, and getTimeStop starts the
 * automatic projectile stop on RE_THE_WORLD_TIME_STOP, whose per-tick stamina is 8.875 * 100 / its trained ticks.
 *
 * <p>The test player has no client, so nothing moves it: its ground, sneak and sprint flags stay as set.
 */
@GameTestHolder(ReworkedTimeStopGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class ReworkedTimeStopGameTests {
    public static final String NAMESPACE = "rejojo_timestop";

    private static final float TRAINED_POINTS = 95.0F;
    private static final float RESOLVE_PER_TICK = 20.0F / TRAINED_POINTS;
    private static final int WINDOW_TICKS = 4;
    // 1.16 TheWorldTSBlinkRecovery read the time-stop ticks of its own untrained action.
    private static final int RECOVERY_TICKS = 4;

    private ReworkedTimeStopGameTests() {}

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 80, batch = "rejojoTsItemResolve")
    public static void disabledTimeStopResolveKeepsRoadRollerAndKnifeRewards(GameTestHelper helper) {
        Fixture fixture = user(helper, "rejojo-item-resolve");
        ServerPlayer player = fixture.player();
        StandPower power = fixture.power();
        boolean enabled = ReworkedConfig.ENABLE_TS_RESOLVE.get();
        try {
            ReworkedConfig.ENABLE_TS_RESOLVE.set(false);
            power.setResolveLevel(4);
            power.getResolveCounter().resetResolveValue(power);
            helper.assertTrue(ModStands.THE_WORLD.get().summon(player, power), "Could not summon The World.");
            player.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 400, 0, false, false, true));
            TimeStopState stopped = state(player);
            helper.assertTrue(stopped.tryPutInstance(new TimeStopState.Instance(
                            player.getId(), 400, 400, new ChunkPos(player.blockPosition()),
                            JojoModConfig.getCommonConfigInstance(false).timeStopChunkRange.get(),
                            player.getId(), "the_world_time_stop", 0.0F)), "Could not stop time for item rewards.");
            float before = resolve(power);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROAD_ROLLER.get()));
            ReworkedGameEvents.onRightClickItem(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
            helper.assertTrue(Math.abs(resolve(power) - before - 250.0F) < 0.01F,
                    "enableTSResolve=false removed the donor's road-roller reward.");
            power.getResolveCounter().resetResolveValue(power);
            before = resolve(power);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KNIFE.get(), 5));
            player.setShiftKeyDown(false);
            ReworkedGameEvents.onRightClickItem(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
            helper.assertTrue(Math.abs(resolve(power) - before - 40.0F) < 0.01F,
                    "enableTSResolve=false removed the donor's five-knife reward.");
            before = resolve(power);
            player.setShiftKeyDown(true);
            ReworkedGameEvents.onRightClickItem(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
            helper.assertTrue(resolve(power) == before, "A single knife incorrectly awards resolve.");
        } finally {
            ReworkedConfig.ENABLE_TS_RESOLVE.set(enabled);
            cleanup(fixture);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 80, batch = "rejojoTsResolve")
    public static void stoppedTimeResolveNeedsTheGroundNotTheSneakKey(GameTestHelper helper) {
        Fixture fixture = user(helper, "rejojo-ts-resolve");
        ServerPlayer player = fixture.player();
        StandPower power = fixture.power();
        float[] mark = new float[1];
        helper.runAfterDelay(2, () -> guarded(fixture, () -> {
            helper.assertTrue(ReworkedConfig.ENABLE_TS_RESOLVE.get(), "Setup: enableTSResolve is off");
            PowerSkillUnlocks.Result unlock =
                    ReworkedAddon.POWER_SKILL_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
            power.getCurTypeData().setAbilityLearningProgressPoints(
                    TimeStopLearning.TIME_STOP, TRAINED_POINTS, TRAINED_POINTS, power);
            helper.assertTrue(power.isAbilityUnlocked(TimeStopLearning.TIME_STOP)
                            && TimeStopLearning.getSavedTimeStopTicks(power) == 100,
                    "Setup: the time stop is not trained to 100 ticks (unlock " + unlock + ", ticks "
                            + TimeStopLearning.getSavedTimeStopTicks(power) + ")");
            player.addEffect(new MobEffectInstance(ModStatusEffects.TIME_STOP, 400, 0, false, false, true));
            TimeStopState state = state(player);
            helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(
                            player.getId(), 400, 400, new ChunkPos(player.blockPosition()),
                            JojoModConfig.getCommonConfigInstance(false).timeStopChunkRange.get(),
                            player.getId(), "the_world_time_stop", 0.0F))
                            && state.isTimeStopped(player),
                    "Setup: time did not stop around the user");
            pose(player, true, false, false);
            mark[0] = resolve(power);
        }));
        helper.runAfterDelay(2 + WINDOW_TICKS, () -> guarded(fixture, () -> {
            float gained = resolve(power) - mark[0];
            helper.assertTrue(paidEveryTick(gained),
                    "1.16 addTWResolveInTS pays 20 / learning points on every tick the user stands on the ground"
                            + " in their stopped time, without the sneak key; in " + WINDOW_TICKS
                            + " ticks the user gained " + gained + ", expected about "
                            + WINDOW_TICKS * RESOLVE_PER_TICK);
            pose(player, false, true, false);
            mark[0] = resolve(power);
        }));
        helper.runAfterDelay(2 + 2 * WINDOW_TICKS, () -> guarded(fixture, () -> {
            float gained = resolve(power) - mark[0];
            helper.assertTrue(gained == 0.0F,
                    "1.16 skips the tick when the user is not on the ground (func_233570_aj_), sneaking or not;"
                            + " a user sneaking in mid-air gained " + gained);
            pose(player, true, true, false);
            mark[0] = resolve(power);
        }));
        helper.runAfterDelay(2 + 3 * WINDOW_TICKS, () -> guarded(fixture, () -> {
            float gained = resolve(power) - mark[0];
            helper.assertTrue(paidEveryTick(gained),
                    "1.16 has no sneak condition: a user sneaking on the ground is paid as well, gained " + gained);
            pose(player, true, false, true);
            mark[0] = resolve(power);
        }));
        helper.runAfterDelay(2 + 4 * WINDOW_TICKS, () -> {
            try {
                float gained = resolve(power) - mark[0];
                helper.assertTrue(gained == 0.0F,
                        "1.16 skips the tick while the user sprints (func_70051_ag); a sprinting user gained "
                                + gained);
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 80, batch = "rejojoTsAutomatic")
    public static void automaticStopChargesTheUntrainedTimeStopRate(GameTestHelper helper) {
        Fixture fixture = user(helper, "rejojo-ts-auto");
        ServerPlayer player = fixture.player();
        StandPower power = fixture.power();
        ServerLevel level = helper.getLevel();
        List<TimeStopState.Instance> added = new ArrayList<>();
        List<TimeStopState.Instance> removed = new ArrayList<>();
        float[] staminaAtRemoval = new float[] { Float.NaN };
        Consumer<TimeStopLifecycleEvent.Added> onAdded = event -> {
            if (event.getInstance().userId() == player.getId()) {
                added.add(event.getInstance());
            }
        };
        Consumer<TimeStopLifecycleEvent.Removed> onRemoved = event -> {
            if (event.getInstance().userId() == player.getId()) {
                removed.add(event.getInstance());
                staminaAtRemoval[0] = power.getStamina();
            }
        };
        Snowball[] threat = new Snowball[1];
        Cow[] thrower = new Cow[1];
        Runnable end = () -> {
            NeoForge.EVENT_BUS.unregister(onAdded);
            NeoForge.EVENT_BUS.unregister(onRemoved);
            if (threat[0] != null) {
                threat[0].discard();
            }
            if (thrower[0] != null) {
                thrower[0].discard();
            }
            cleanup(fixture);
        };
        helper.runAfterDelay(2, () -> guarded(end, () -> {
            power.setResolveLevel(2);
            helper.assertTrue(power.getResolveLevel() >= 2
                            && !power.isAbilityUnlocked(TimeStopLearning.TIME_STOP),
                    "Setup: The World must be at Resolve level 2 with its time stop locked (level "
                            + power.getResolveLevel() + ")");
            helper.assertTrue(!power.isStaminaInfinite() && power.getMaxStamina() >= 100.0F,
                    "Setup: the user needs a finite stamina bar of at least 100");
            power.setStamina(100.0F);
            NeoForge.EVENT_BUS.addListener(
                    EventPriority.LOWEST, false, TimeStopLifecycleEvent.Added.class, onAdded);
            NeoForge.EVENT_BUS.addListener(
                    EventPriority.LOWEST, false, TimeStopLifecycleEvent.Removed.class, onRemoved);
            // 1.16 skips a projectile whose owner is the user's Stand, and with the Stand away that is
            // every ownerless one (null == null), so the snowball gets a thrower.
            Vec3 feet = player.position();
            Cow cow = EntityType.COW.create(level);
            cow.setPos(feet.add(0.0D, 0.0D, 1.0D));
            cow.setNoAi(true);
            cow.setNoGravity(true);
            level.addFreshEntity(cow);
            thrower[0] = cow;
            // Four ticks of flight end inside the user's box.
            Snowball snowball = new Snowball(level, feet.x - 3.0D, feet.y + 1.0D, feet.z);
            snowball.setOwner(cow);
            snowball.setNoGravity(true);
            snowball.setDeltaMovement(0.75D, 0.0D, 0.0D);
            level.addFreshEntity(snowball);
            threat[0] = snowball;
        }));
        helper.runAfterDelay(12, () -> {
            try {
                helper.assertTrue(added.size() == 1,
                        "The projectile did not start exactly one automatic time stop: " + added.size()
                                + " (time stop unlocked: "
                                + power.isAbilityUnlocked(TimeStopLearning.TIME_STOP) + ")");
                helper.assertTrue(power.isAbilityUnlocked(TimeStopLearning.TIME_STOP)
                                && TimeStopLearning.getSavedTimeStopTicks(power)
                                        == TimeStopLearning.MIN_TIME_STOP_TICKS,
                        "1.16 unlockAction leaves a trained action at 0 points, 5 ticks; the port has "
                                + TimeStopLearning.getSavedTimeStopTicks(power));
                TimeStopState.Instance stop = added.getFirst();
                float donorCost = ReworkedStandAbilities.TIME_STOP_STAMINA_COST_TICK * 100.0F
                        / TimeStopLearning.MIN_TIME_STOP_TICKS;
                helper.assertTrue(stop.totalTicks() == 4 && Math.abs(stop.staminaCostTick() - donorCost) < 0.01F,
                        "1.16 TimeStop.getStaminaCostTicking: the automatic stop runs on RE_THE_WORLD_TIME_STOP"
                                + " and costs 8.875 * 100 / its 5 trained ticks = " + donorCost
                                + " stamina per tick, but the port's instance charges " + stop.staminaCostTick());
                // 1.16 TimeStopInstance.tick: a user who cannot pay a tick is drained and time resumes.
                helper.assertTrue(removed.size() == 1 && staminaAtRemoval[0] == 0.0F
                                && removed.getFirst().ticksPassed() < 4,
                        "1.16: a user with 100 stamina cannot pay the first " + donorCost
                                + ", so the stop ends early with the bar at 0; the port ended " + removed.size()
                                + " stop(s) with " + staminaAtRemoval[0] + " stamina after "
                                + (removed.isEmpty() ? -1 : removed.getFirst().ticksPassed()) + " tick(s)");
            } finally {
                end.run();
            }
            helper.succeed();
        });
    }

    /**
     * 1.16 TheWorldTSBlinkRecovery.perform never refused for stamina: it capped the 4 skipped ticks by the stamina over
     * its own costs (both 0, so only an empty bar changes them, to the minimum 5), then paid the Reworked blink's start
     * cost plus those ticks and ignored the result.
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 80, batch = "rejojoTsRecoveryStamina")
    public static void theWorldBlinkRecoveryGoesOnOnAShortBar(GameTestHelper helper) {
        Fixture fixture = user(helper, "rejojo-ts-recovery");
        ServerPlayer player = fixture.player();
        StandPower power = fixture.power();
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(!power.isStaminaInfinite() && power.getMaxStamina() > 100.0F,
                        "Setup: the user needs a finite stamina bar above 100");
                helper.assertTrue(ModStands.THE_WORLD.get().summon(player, power), "Could not summon The World.");
                StandEntity stand = power.getSummonedStandEntity();
                helper.assertTrue(stand != null && stand.getUser() == player, "Setup: no summoned The World");
                stand.summonLockTicks = 0;
                Ability recovery = power.getAbility(ReworkedStandAbilities.THE_WORLD_BLINK_RECOVERY_NAME);
                helper.assertTrue(recovery instanceof ReworkedStandAbilities.ReworkedTheWorldBlinkRecoveryAbility,
                        "Setup: The World has no Reworked TS blink recovery: " + recovery);

                float fullCost = pressRecovery(helper, player, power, stand, recovery, power.getMaxStamina(),
                        RECOVERY_TICKS);
                helper.assertTrue(fullCost > 10.0F,
                        "Setup: the recovery on a full bar cost " + fullCost + ", not more than the short bar's 10");
                pressRecovery(helper, player, power, stand, recovery, 10.0F, RECOVERY_TICKS);
                pressRecovery(helper, player, power, stand, recovery, 0.0F, TimeStopLearning.MIN_TIME_STOP_TICKS);
            } finally {
                if (power.isSummoned()) {
                    ModStands.THE_WORLD.get().forceUnsummon(player, power);
                }
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    /**
     * 1.16 TheWorldTSBlinkRecovery.perform (025 :105) set the TS punch's cooldown to
     * theWorldTimeStopPunch.getCooldown(power, impliedTicks) * 3, and the TS punch was a plain cooldown(50)
     * (025 init :95, no cooldown getter override): 150 ticks in Survival, none in Resolve (StandAction's
     * multiplier 0) or in Creative (Action.getCooldownAdditional :373-375).
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 80, batch = "rejojoTsRecoveryCooldown")
    public static void theWorldBlinkRecoveryCoolsTheTsPunchDownByItsOwnCooldownAsIn116(GameTestHelper helper) {
        Fixture fixture = user(helper, "rejojo-ts-recovery-cd");
        ServerPlayer player = fixture.player();
        StandPower power = fixture.power();
        helper.runAfterDelay(2, () -> {
            List<String> wrong = new ArrayList<>();
            try {
                helper.assertTrue(ModStands.THE_WORLD.get().summon(player, power), "Could not summon The World.");
                StandEntity stand = power.getSummonedStandEntity();
                helper.assertTrue(stand != null && stand.getUser() == player, "Setup: no summoned The World");
                stand.summonLockTicks = 0;
                Ability recovery = power.getAbility(ReworkedStandAbilities.THE_WORLD_BLINK_RECOVERY_NAME);
                helper.assertTrue(recovery instanceof ReworkedStandAbilities.ReworkedTheWorldBlinkRecoveryAbility,
                        "Setup: The World has no Reworked TS blink recovery: " + recovery);
                recoveryPunchCooldown(helper, player, power, stand, recovery, "in Survival", 150, wrong);
                player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0, false, false));
                recoveryPunchCooldown(helper, player, power, stand, recovery, "in Resolve", 0, wrong);
                player.removeEffect(ModStatusEffects.RESOLVE);
                player.setGameMode(GameType.CREATIVE);
                helper.assertTrue(power.isUserCreative(), "Setup: the The World user is not in Creative");
                recoveryPunchCooldown(helper, player, power, stand, recovery, "as a Creative user", 0, wrong);
            } finally {
                if (power.isSummoned()) {
                    ModStands.THE_WORLD.get().forceUnsummon(player, power);
                }
                cleanup(fixture);
            }
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 TheWorldTSBlinkRecovery: the TS punch's getCooldown x 3: " + String.join("; ", wrong));
            helper.succeed();
        });
    }

    private static void recoveryPunchCooldown(
            GameTestHelper helper, ServerPlayer player, StandPower power, StandEntity stand, Ability recovery,
            String when, int expected, List<String> wrong) {
        Ability tsPunch = power.getAbility(ReworkedStandAbilities.THE_WORLD_TS_PUNCH_NAME);
        helper.assertTrue(tsPunch instanceof ReworkedStandAbilities.ReworkedTheWorldTSPunchAbility,
                "Setup: The World has no Reworked TS punch: " + tsPunch);
        EntityActionInstance punch = ((ReworkedStandAbilities.ReworkedTheWorldTSPunchAbility) tsPunch)
                .initActionOnAbilityUse(player.level(), player, stand, null);
        punch.phasesLength.put(ActionPhase.RECOVERY, 200.0F);
        LivingComponentAction.getComponent(stand).setAction(punch, player, SyncType.NO_SYNC);
        punch.setPhase(ActionPhase.RECOVERY, 0);
        power.setAbilityCooldown(TimeStopCooldowns.TIME_STOP_BLINK, 0);
        power.setAbilityCooldown(tsPunch.name(), 0);
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(stand.getCurStandAction() == punch && punch.getPhase() == ActionPhase.RECOVERY
                        && recovery.checkSpecificConditions(power).isPositive(),
                "Setup: the TS blink recovery cannot be pressed during the TS punch's recovery " + when);
        BufferingState buffer = BufferingState.clickOnly();
        AbilityInput.keyPress((short) 153, recovery, player, null, InputMethod.CLICK, 0.0F, buffer,
                recovery.getAbilityId());
        AbilityInput.keyRelease((short) 153, player);
        helper.assertTrue(buffer.isActionSuccess && stand.getCurStandAction() == null,
                "Setup: the TS blink recovery did not go on " + when);
        int cooldown = power.getAbilityCooldown(tsPunch.name());
        if (cooldown != expected) {
            wrong.add("the TS punch cools down " + cooldown + " ticks " + when + ", the donor's " + expected);
        }
    }

    /**
     * 1.16 TheWorldTimeStop.getHoldDurationToFire (RE_THE_WORLD_TIME_STOP is one) asked IStandPower.getResolveLevel,
     * the stored level: the 20-tick charge came from stored level 4, and a Resolve effect over a lower stored level
     * kept the 35 ticks (the effect only raised the stored level when it started).
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 80, batch = "rejojoTsCharge")
    public static void theWorldChargeShortensOnTheStoredResolveLevelOnlyAsIn116(GameTestHelper helper) {
        Fixture fixture = user(helper, "rejojo-ts-charge");
        ServerPlayer player = fixture.player();
        StandPower power = fixture.power();
        helper.runAfterDelay(2, () -> {
            try {
                Ability timeStop = power.getAbility(TimeStopLearning.TIME_STOP);
                helper.assertTrue(timeStop instanceof ReworkedStandAbilities.ReworkedTimeStopAbility,
                        "Setup: The World has no Reworked time stop: " + timeStop);
                power.setResolveLevel(4);
                helper.assertTrue(chargeTicks(timeStop, player) == ReworkedStandAbilities.THE_WORLD_RESOLVE_HOLD_TO_FIRE_TICKS,
                        "1.16 charged The World's Reworked time stop for 20 ticks from stored Resolve level 4; the port "
                                + chargeTicks(timeStop, player));
                player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, 4, false, false, true));
                power.setResolveLevel(2);
                helper.assertTrue(power.getResolveLevel() == 2
                                && ResolveModeEffect.getEffectiveResolveLevel(player, power) >= 4,
                        "Setup: stored level 2 under a level 4 Resolve effect: " + power.getResolveLevel() + ", "
                                + ResolveModeEffect.getEffectiveResolveLevel(player, power));
                helper.assertTrue(chargeTicks(timeStop, player) == ReworkedStandAbilities.THE_WORLD_HOLD_TO_FIRE_TICKS,
                        "1.16 read the stored level 2 and charged 35 ticks under a Resolve effect of level 4; the port "
                                + chargeTicks(timeStop, player));
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    private static int chargeTicks(Ability timeStop, ServerPlayer player) {
        EntityActionInstance action = ((EntityActionType) timeStop)
                .initActionOnAbilityUse(player.level(), player, player, null);
        return (int) action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE);
    }

    /**
     * 1.16 Reworked's TimeStopInstance.onRemoved overwrite (025 :66-86) cooled down the TimeStop action that started a
     * stop by its own rule: 3 per tick passed, times 3 for Star Platinum and (-90 r^2 + 105) / 22.5 for The World, r its
     * Resolve ratio, and the time stop's blink by a sixth of that. The time stop is pressed through the key and held
     * until it fires; the cooldowns are read as the stop ends.
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 200, batch = "rejojoTsPressedCdSp")
    public static void starPlatinumPressedTimeStopTakesTheLegacyCooldownAsIn116(GameTestHelper helper) {
        new PressedStopCooldownScene(helper, ModStands.STAR_PLATINUM.get(), "rejojo-ts-cd-sp").start();
    }

    // The World from Resolve level 3 stops time without its Stand (1.16 TheWorldTimeStop's autoSummonStand).
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 200, batch = "rejojoTsPressedCdTw")
    public static void theWorldPressedTimeStopWithoutItsStandTakesTheLegacyCooldownAsIn116(GameTestHelper helper) {
        new PressedStopCooldownScene(helper, ModStands.THE_WORLD.get(), "rejojo-ts-cd-tw").start();
    }

    // Star Platinum with its Stand out, or The World at Resolve 3 not out; the untrained stop lasts 5 ticks after its
    // 35-tick opening.
    private static final class PressedStopCooldownScene {
        private static final short TIME_STOP_KEY = 156;
        private static final int GIVE_UP_AT = 160;

        private final GameTestHelper helper;
        private final EntityStandType standType;
        private final boolean starPlatinum;
        private final String name;
        private final List<Object> listeners = new ArrayList<>();
        private Fixture fixture;
        private EntityActionInstance pressed;
        private String atStart;
        private TimeStopState.Instance ended;
        private float ratioAtEnd = Float.NaN;
        private int timeStopCooldown = -1;
        private int blinkCooldown = -1;
        private boolean released;
        private boolean closed;
        private int ticks;

        private PressedStopCooldownScene(GameTestHelper helper, EntityStandType standType, String name) {
            this.helper = helper;
            this.standType = standType;
            this.starPlatinum = standType == ModStands.STAR_PLATINUM.get();
            this.name = name;
        }

        private void start() {
            try {
                fixture = user(helper, name, standType);
                listen();
                helper.onEachTick(this::tick);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void listen() {
            ServerPlayer player = fixture.player();
            Consumer<TimeStopLifecycleEvent.Added> added = event -> {
                if (event.getInstance().userId() == player.getId() && atStart == null) {
                    StandEntity stand = fixture.power().getSummonedStandEntity();
                    atStart = "at the start the user's Stand-power slot held "
                            + LivingComponentAction.getCurEntityAction(player, PowerClass.STAND)
                            + ", its own slot " + LivingComponentAction.getCurEntityAction(player)
                            + ", the Stand's " + (stand != null ? stand.getCurStandAction() : "(not out)");
                }
            };
            Consumer<TimeStopLifecycleEvent.Removed> removed = event -> {
                if (event.getInstance().userId() == player.getId() && ended == null) {
                    StandPower power = fixture.power();
                    ended = event.getInstance();
                    ratioAtEnd = power.getResolveCounter().getResolveRatio(power);
                    timeStopCooldown = power.getAbilityCooldown(TimeStopCooldowns.TIME_STOP);
                    blinkCooldown = power.getAbilityCooldown(TimeStopCooldowns.TIME_STOP_BLINK);
                }
            };
            listeners.add(added);
            listeners.add(removed);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TimeStopLifecycleEvent.Added.class, added);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TimeStopLifecycleEvent.Removed.class, removed);
        }

        private void tick() {
            if (closed) {
                return;
            }
            try {
                ticks++;
                ServerPlayer player = fixture.player();
                if (pressed == null) {
                    if (ticks >= 3 && helper.getLevel().isPositionEntityTicking(player.blockPosition())) {
                        press();
                    }
                }
                else if (ended != null) {
                    check();
                    close();
                    helper.succeed();
                    return;
                }
                else if (atStart != null && !released) {
                    // the stop has fired from the full charge: letting the key go now changes nothing
                    released = true;
                    AbilityInput.keyRelease(TIME_STOP_KEY, player);
                }
                helper.assertTrue(ticks < GIVE_UP_AT, pressed == null
                        ? "Setup: the user never ticked"
                        : atStart == null
                                ? "Setup: the held time stop never stopped time: " + pressed + " in "
                                        + pressed.getPhase() + ", over " + pressed.isOver()
                                : "Setup: the stop never ended");
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void press() {
            ServerPlayer player = fixture.player();
            StandPower power = fixture.power();
            if (starPlatinum) {
                helper.assertTrue(standType.summon(player, power) && power.getSummonedStandEntity() != null,
                        "Could not summon Star Platinum.");
                power.getSummonedStandEntity().summonLockTicks = 0;
            }
            else {
                power.setResolveLevel(3);
                helper.assertTrue(!power.isSummoned(), "Setup: The World is out");
            }
            PowerSkillUnlocks.Result unlock =
                    ReworkedAddon.POWER_SKILL_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
            Ability timeStop = power.getAbility(TimeStopLearning.TIME_STOP);
            helper.assertTrue(timeStop instanceof ReworkedStandAbilities.ReworkedTimeStopAbility
                            && timeStop.isAbilityUnlocked(power)
                            && power.getAbility(TimeStopCooldowns.TIME_STOP_BLINK) != null,
                    "Setup: no unlocked Reworked time stop with a blink: " + timeStop + " (unlock " + unlock + ")");
            power.setStamina(power.getMaxStamina());
            power.setAbilityCooldown(TimeStopCooldowns.TIME_STOP, 0);
            power.setAbilityCooldown(TimeStopCooldowns.TIME_STOP_BLINK, 0);
            // as ClAbilityInputPacket takes a held press: the key's resolution, its condition check, the press
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            var resolved = available.getContextVariationContainer(timeStop);
            helper.assertTrue(resolved != null && resolved.ability == timeStop
                            && AbilityInput.withConditionCheck(resolved, player, InputMethod.HOLD),
                    "Setup: the time stop key was refused: " + (resolved == null ? "nothing"
                            : resolved.ability.name() + " " + resolved.conditionCheck));
            BufferingState buffer = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(TIME_STOP_KEY, resolved.ability, player, null, InputMethod.HOLD, 0.0F, buffer,
                    timeStop.getAbilityId());
            pressed = LivingComponentAction.getCurEntityAction(player, PowerClass.STAND);
            helper.assertTrue(buffer.isActionSuccess && pressed != null && pressed.ability == timeStop
                            && pressed.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "Setup: the time stop does not charge in the user's Stand-power slot: went off "
                            + buffer.isActionSuccess + ", buffered " + buffer.shouldBuffer + ", the slot holds "
                            + pressed);
        }

        private void check() {
            StandPower power = fixture.power();
            int ticksPassed = Math.max(ended.ticksPassed(), 0);
            // 1.16 Reworked's getTimeStopCooldown overwrite: timeStopCooldownPerTick 3 (no Resolve effect here)
            float cooldown = 3.0F * ticksPassed;
            float multiplier = starPlatinum
                    ? 3.0F
                    : (float) (Math.pow(ratioAtEnd, 2.0) * -90.0 + 105.0) / 22.5F;
            int expected = (int) (cooldown * multiplier);
            int expectedBlink = (int) (cooldown * 0.16666667F * multiplier);
            int core = TimeStopCooldowns.timeStopCooldown(power, ticksPassed);
            int coreBlink = TimeStopCooldowns.timeStopBlinkCooldown(power, ticksPassed);
            helper.assertTrue(ticksPassed > 0 && expected != core && expectedBlink != coreBlink,
                    "Setup: the stop ran " + ticksPassed + " ticks, so the donor's cooldowns " + expected + " / "
                            + expectedBlink + " do not differ from the core's " + core + " / " + coreBlink);
            helper.assertTrue(timeStopCooldown == expected && blinkCooldown == expectedBlink,
                    "1.16 Reworked cooled the pressed " + (starPlatinum ? "Star Platinum" : "The World")
                            + " time stop down by " + multiplier + " as its " + ticksPassed + "-tick stop ended: "
                            + expected + " ticks, its blink " + expectedBlink + "; the port set " + timeStopCooldown
                            + " and " + blinkCooldown + " (the core's own " + core + " / " + coreBlink + "); "
                            + atStart);
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Object listener : listeners) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            listeners.clear();
            if (fixture == null) {
                return;
            }
            ServerPlayer player = fixture.player();
            // dropped, not let go: a release in the charge would start a shorter stop
            player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.remove(TIME_STOP_KEY);
            LivingComponentAction.getComponent(player, PowerClass.STAND).setAction(null, SyncType.NO_SYNC);
            if (fixture.power().isSummoned()) {
                standType.forceUnsummon(player, fixture.power());
            }
            cleanup(fixture);
        }
    }

    /**
     * 1.16 TheWorldTSBlinkRecovery.isUnlocked asked getResolveLevel() >= 4 itself, then the TS punch (3, then the time
     * stop): unlike a learned unlock it follows the stored level down, and up again.
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 80, batch = "rejojoTsRecoveryUnlock")
    public static void theWorldBlinkRecoveryUnlockFollowsTheStoredResolveLevelAsIn116(GameTestHelper helper) {
        Fixture fixture = user(helper, "rejojo-ts-unlock");
        ServerPlayer player = fixture.player();
        StandPower power = fixture.power();
        helper.runAfterDelay(2, () -> {
            try {
                PowerSkillUnlocks.Result unlock =
                        ReworkedAddon.POWER_SKILL_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
                helper.assertTrue(unlock == PowerSkillUnlocks.Result.UNLOCKED
                                || unlock == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                        "Setup: could not unlock the time stop: " + unlock);
                Ability recovery = power.getAbility(ReworkedStandAbilities.THE_WORLD_BLINK_RECOVERY_NAME);
                helper.assertTrue(recovery instanceof ReworkedStandAbilities.ReworkedTheWorldBlinkRecoveryAbility,
                        "Setup: The World has no Reworked TS blink recovery: " + recovery);
                power.setResolveLevel(4);
                helper.assertTrue(recovery.getUnlockConditionCheck(power).isPositive(),
                        "1.16 unlocked the TS blink recovery at stored Resolve level 4 with the time stop unlocked");
                power.setResolveLevel(3);
                helper.assertTrue(power.getHighestResolveLevel() >= 4
                                && !recovery.getUnlockConditionCheck(power).isPositive(),
                        "1.16 asked the stored level (3) and locked the TS blink recovery again, whatever the highest "
                                + "level reached (" + power.getHighestResolveLevel() + ")");
                power.setResolveLevel(4);
                helper.assertTrue(recovery.getUnlockConditionCheck(power).isPositive(),
                        "1.16 unlocked the TS blink recovery again at stored Resolve level 4");
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    /**
     * 1.16 ReTheWorldTSHeavyAttack.isUnlocked (025 :122-127, noResolveUnlock() at init :95) asked getResolveLevel() >= 3
     * itself, then RE_THE_WORLD_TIME_STOP.isUnlocked: Resolve 3 alone did not unlock the TS punch before the Reworked
     * time stop was awakened, and the stored level, not the highest one reached, decided.
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 80, batch = "rejojoTsPunchUnlock")
    public static void theWorldTsPunchUnlockWaitsForTheReworkedTimeStopAsIn116(GameTestHelper helper) {
        Fixture fixture = user(helper, "rejojo-ts-punch-unlock");
        StandPower power = fixture.power();
        helper.runAfterDelay(2, () -> {
            try {
                Ability tsPunch = power.getAbility(ReworkedStandAbilities.THE_WORLD_TS_PUNCH_NAME);
                helper.assertTrue(tsPunch instanceof ReworkedStandAbilities.ReworkedTheWorldTSPunchAbility,
                        "Setup: The World has no Reworked TS punch: " + tsPunch);
                List<String> wrong = new ArrayList<>();
                for (int resolveLevel : new int[] {3, 4}) {
                    power.setResolveLevel(resolveLevel);
                    if (tsPunch.isAbilityUnlocked(power)) {
                        wrong.add("unlocked at stored Resolve " + resolveLevel + " with the time stop locked (skill "
                                + power.getCurTypeData().isSkillUnlocked(tsPunch.name()) + ")");
                    }
                }
                PowerSkillUnlocks.Result unlock =
                        ReworkedAddon.POWER_SKILL_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
                helper.assertTrue(unlock == PowerSkillUnlocks.Result.UNLOCKED
                                && power.isAbilityUnlocked(TimeStopLearning.TIME_STOP),
                        "Setup: could not awaken the Reworked time stop: " + unlock);
                power.setResolveLevel(3);
                if (!tsPunch.isAbilityUnlocked(power)) {
                    wrong.add("locked at stored Resolve 3 with the time stop awakened (skill "
                            + power.getCurTypeData().isSkillUnlocked(tsPunch.name()) + ", check "
                            + tsPunch.getUnlockConditionCheck(power).isPositive() + ")");
                }
                power.setResolveLevel(2);
                if (power.getHighestResolveLevel() < 4 || tsPunch.isAbilityUnlocked(power)) {
                    wrong.add("unlocked at stored Resolve 2 with the time stop awakened, highest level "
                            + power.getHighestResolveLevel());
                }
                power.setResolveLevel(3);
                if (!tsPunch.isAbilityUnlocked(power)) {
                    wrong.add("locked again at stored Resolve 3");
                }
                helper.assertTrue(wrong.isEmpty(),
                        "1.16 unlocked the Reworked TS punch at stored Resolve 3 once the Reworked time stop was"
                                + " awakened, and only then; the port's TS punch was " + String.join("; ", wrong));
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    // Puts The World's TS punch in its recovery, presses the TS blink recovery on the given bar and returns what it
    // paid.
    private static float pressRecovery(
            GameTestHelper helper, ServerPlayer player, StandPower power, StandEntity stand, Ability recovery,
            float stamina, int expectedTicks) {
        Ability tsPunch = power.getAbility(ReworkedStandAbilities.THE_WORLD_TS_PUNCH_NAME);
        helper.assertTrue(tsPunch instanceof ReworkedStandAbilities.ReworkedTheWorldTSPunchAbility,
                "Setup: The World has no Reworked TS punch: " + tsPunch);
        EntityActionInstance punch = ((ReworkedStandAbilities.ReworkedTheWorldTSPunchAbility) tsPunch)
                .initActionOnAbilityUse(player.level(), player, stand, null);
        punch.phasesLength.put(ActionPhase.RECOVERY, 200.0F);
        LivingComponentAction.getComponent(stand).setAction(punch, player, SyncType.NO_SYNC);
        punch.setPhase(ActionPhase.RECOVERY, 0);
        power.setAbilityCooldown(TimeStopCooldowns.TIME_STOP_BLINK, 0);
        power.setStamina(stamina);
        helper.assertTrue(stand.getCurStandAction() == punch && punch.getPhase() == ActionPhase.RECOVERY
                        && recovery.checkSpecificConditions(power).isPositive(),
                "Setup: the TS blink recovery cannot be pressed during the TS punch's recovery");
        float before = power.getStamina();
        int tickCount = player.tickCount;
        BufferingState buffer = BufferingState.clickOnly();
        AbilityInput.keyPress((short) 153, recovery, player, null, InputMethod.CLICK, 0.0F, buffer,
                recovery.getAbilityId());
        AbilityInput.keyRelease((short) 153, player);
        int skipped = player.tickCount - tickCount;
        helper.assertTrue(buffer.isActionSuccess && skipped == expectedTicks && stand.getCurStandAction() == null,
                "1.16 TheWorldTSBlinkRecovery goes on with " + before + " stamina and skips " + expectedTicks
                        + " ticks; the port " + (buffer.isActionSuccess ? "went on" : "refused it") + ", skipped "
                        + skipped + " ticks, stand action " + stand.getCurStandAction());
        helper.assertTrue(stamina >= power.getMaxStamina() ? power.getStamina() < before : power.getStamina() == 0.0F,
                "1.16 pays the blink's start cost plus the skipped ticks and ignores the result; from " + before
                        + " the bar is at " + power.getStamina());
        return before - power.getStamina();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120, batch = "rejojoSpHeavy")
    public static void heavyPunchPerformsOneTickWithDetachedPulses(GameTestHelper helper) {
        new HeavyScene(helper, false, false).start();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120, batch = "rejojoSpUppercut")
    public static void uppercutPerformsOneTickWithDetachedPulses(GameTestHelper helper) {
        new HeavyScene(helper, true, false).start();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120, batch = "rejojoSpBlinkHeavy")
    public static void blinkHeavyPunchPerformsOneTickWithDetachedPulses(GameTestHelper helper) {
        new HeavyScene(helper, false, true).start();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120, batch = "rejojoSpBlinkUppercut")
    public static void blinkUppercutPerformsOneTickWithDetachedPulses(GameTestHelper helper) {
        new HeavyScene(helper, true, true).start();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120, batch = "rejojoSpBlinkHeavyStep")
    public static void blinkHeavyPunchStepsAfterItsTargetInTheWindup(GameTestHelper helper) {
        new HeavyScene(helper, false, true, false, true).start();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120,
            batch = "rejojoSpBlinkUppercutStep")
    public static void blinkUppercutStepsAfterItsTargetInTheWindup(GameTestHelper helper) {
        new HeavyScene(helper, true, true, false, true).start();
    }

    // 1.16 ReStarPlatinumHeavyPunch.replaceAction (:48-53) asked for the blink before StandEntityHeavyAttack's finisher
    // variation: with Shift and the plain heavy punch in its windup, the heavy key gave the blink heavy punch even once
    // the meter had reached the uppercut's threshold.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120,
            batch = "rejojoSpBlinkHeavyFullMeter")
    public static void blinkHeavyPunchTakesTheHeavyKeyOverTheUppercutAsIn116(GameTestHelper helper) {
        new HeavyScene(helper, false, true, false, false, true).start();
    }

    // 1.16 StarPlatinumBlinkPunch (025 :88-207) was a StandEntityActionModifier, and ReStarPlatinumHeavyPunch.replaceAction
    // (:48-53) gave it for any running task but the uppercut in WINDUP: Shift and the heavy key in a light punch's windup
    // added the blink to that punch, which went on; right after the punch's own perform the blink took the user to the
    // target and struck it with the heavy attack's punch (standPerform :117-129), once per task (its modifiers a set).
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120, batch = "rejojoSpBlinkLightPunch")
    public static void blinkModifiesALightPunchInItsWindupAsIn116(GameTestHelper helper) {
        new LightPunchBlinkScene(helper).start();
    }

    // 1.16 StarPlatinumBlinkPunch.standPerform (025 :117-129) blinked by the running task's target (blink :131-147;
    // TimeStopInstant.calcBlinkPos :139-171 cast its own ray only for a task with none), which the task's own strike had
    // just made what it struck in reach (StandEntity.punch :1788-1796), and turned the user to it; only then the target
    // of the ray before the blink became the task's (:121) and took the strike (attackTarget, no reach).
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoSpBlinkTaskTarget")
    public static void blinkGoesByTheRunningTasksTargetAsIn116(GameTestHelper helper) {
        new BlinkTaskTargetScene(helper).start();
    }

    // 1.16 StarPlatinumBlinkPunch.standPerform (025 :119) cast the strike's ray before the blink from the user, also when
    // the Stand was manually controlled and blinked itself, as far as min(user.getSpeed() * the blink's ticks, 192)
    // reached: the bare movement speed, without the 2.1585 of the blink's own distance, so about half as far.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoSpBlinkStrikeRay")
    public static void blinkStrikesWhatItsOwnRayFromTheUserFindsAsIn116(GameTestHelper helper) {
        new BlinkStrikeRayScene(helper).start();
    }

    // 1.16 StarPlatinumBlinkPunch was a modifier of the heavy punch's or uppercut's own task too (025 :88-90; the Shift
    // press only added it, StandEntityActionModifier.perform core :20-26, so the task kept its target): StandEntityTask.tick
    // (:124-125) ran the attack's own standPerform first, StandEntity.punch at its task target in reach, then the blink's,
    // which struck its ray's target with the punch of its heavy attack (the uppercut's for the uppercut blink, :97-104,
    // registered :90-91) and cooled itself down (:171): two strikes in that tick.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoSpBlinkHeavyTask")
    public static void blinkOnAHeavyTaskStrikesAfterTheTasksOwnStrikeAsIn116(GameTestHelper helper) {
        new HeavyTaskBlinkScene(helper).start();
    }

    // 1.16 StarPlatinumBlinkPunch.standPerform (025 :121) offered its ray's target to the task through
    // StandEntity.setTaskTarget, which StandEntityTask.keepTarget (core :76-87) took only if the task's own action kept
    // it, by the range from the Stand where it stood (a blinked user's Stand followed on its next tick), and then struck
    // task.getTarget() (:124): a refused target left the task's own to be struck.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoSpBlinkKeptTarget")
    public static void blinkStrikesWhatTheTaskKeptAsIn116(GameTestHelper helper) {
        new BlinkKeptTargetScene(helper).start();
    }

    // 1.16 TimeStopInstant.calcBlinkPos (core :139-171), run for the one that blinks (StarPlatinumBlinkPunch.blink :141,
    // the Stand when manually controlled), cast its own ray for a task with no target excluding only Stands of that
    // blinker (:143-144): a manually controlled Stand's ray met its own user and the Stand blinked beside it.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoSpBlinkManualRay")
    public static void aManuallyControlledBlinksOwnRayMeetsItsUserAsIn116(GameTestHelper helper) {
        new BlinkManualRayScene(helper).start();
    }

    // 1.16 TimeStopInstant.calcBlinkPos (core :139-171) cast JojoModUtil.rayTrace, whose block mode is OUTLINE: a short
    // grass on the way is the block the blink lands on, not the wall beyond it.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoSpBlinkGrass")
    public static void blinkLandsOnTheGrassItsOutlineRayMeetsAsIn116(GameTestHelper helper) {
        new BlinkGrassScene(helper).start();
    }

    // 1.16 Action.getVisibleAction (core :252-258) kept a replacement that was not unlocked off the key, giving it its own
    // action: StarPlatinumBlinkPunch.isUnlocked (025 :113-115) followed the Reworked time stop, so before that was unlocked
    // Shift and the heavy key in a running action's windup gave ReStarPlatinumHeavyPunch itself, whose replaceAction
    // (:48-53) had asked for the blink before the finisher variation, and the press went on as a heavy punch.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoSpBlinkLocked")
    public static void blinkStaysOffTheKeyUntilTheTimeStopIsUnlockedAsIn116(GameTestHelper helper) {
        new BlinkLockedScene(helper).start();
    }

    // 1.16 StarPlatinumBlinkPunch modified the heavy punch's or uppercut's own task, so Shift and the heavy key again in
    // its windup resolved to the same blink once more (ReStarPlatinumHeavyPunch.replaceAction :48-53; for the uppercut
    // task StandEntityHeavyAttack.replaceAction :82-91 handed over to ReStarPlatinumUppercut.replaceAction :49-55), whose
    // StandEntityActionModifier.perform added it to the task's modifier set again (StandEntityTask.addModifierAction
    // :89-94): nothing happened, nothing was queued, and the task blinked and struck once.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoSpBlinkPressedAgain")
    public static void blinkPressedAgainInItsWindupDoesNothingAsIn116(GameTestHelper helper) {
        new BlinkAgainScene(helper).start();
    }

    // 1.16 ReStarPlatinumHeavyPunch.replaceAction (:48-53) turned the heavy key into the blink wherever it came from. The
    // port resolves the heavy key to the blink (replaceWithSubAbility), but ClAbilityInputPacket.resolveServerInputAbility
    // takes the ability the client requested, unrefused, when the server resolved another one (a client a tick behind, or
    // the Shift+RMB grab click): the key press event then swaps it for the blink.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoSpBlinkClientSent")
    public static void blinkTakesTheHeavyKeyTheClientSentInsteadAsIn116(GameTestHelper helper) {
        new BlinkClientSentScene(helper).start();
    }

    // 1.16 ReStarPlatinumHeavyPunch.replaceAction (:48-53) made Shift and the heavy key the blink in a running task's
    // windup, and the HUD drew the key by it (Action.getVisibleAction). The port's Shift+RMB is the grab click, which the
    // server swaps for the blink (onAbilityKeyPress); the client, whose copy of the Stand's action is of the power type's
    // base-moveset ability, must resolve that slot to the blink as well, and to the grab outside the windup.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoSpBlinkGrabSlot")
    public static void blinkTakesTheShiftGrabClickSlotOnTheClientTooAsIn116(GameTestHelper helper) {
        new BlinkGrabSlotScene(helper).start();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120, batch = "rejojoMrHeavy")
    public static void magiciansRedHeavyPunchPerformsOneTickWithDetachedPulses(GameTestHelper helper) {
        new HeavyScene(helper, false, false, true, false).start();
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120, batch = "rejojoMrKick")
    public static void magiciansRedKickPerformsOneTickWithDetachedPulses(GameTestHelper helper) {
        new HeavyScene(helper, true, false, true, false).start();
    }

    // 1.16 ReMagiciansRedLightAttack (:48-55), ReMagiciansRedHeavyPunch (:47-54) and ReMagiciansRedKick (:51-67)
    // .standPerform gave Slow Burn (the kick primed or set it off) once the strike was done whenever the task target was
    // a LivingEntity, whether the hurt went through or not: a raised shield did not stop it, and a non-living target gave
    // it to nothing.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60, batch = "rejojoMrSlowBurnHurtOrNot")
    public static void magiciansRedSlowBurnLandsHurtOrNotAsIn116(GameTestHelper helper) {
        new SlowBurnScene(helper).start();
    }

    /**
     * 1.16 RE_MAGICIANS_RED_KICK, the finisher variation of Reworked's heavy punch (the SHIFT variation of the Reworked
     * punch on Magician's Red's hotbar), set no Resolve level (025 init :98-99, :157-163), so
     * StandType.unlockNewActions unlocked it at Resolve 0, and with it the finisher meter
     * (StandUtil.isFinisherMechanicUnlocked: Resolve 1, or the Stand's finisher punch unlocked).
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "rejojoMrKickUnlock")
    public static void magiciansRedKickAndItsMeterUnlockAtResolveZeroAsIn116(GameTestHelper helper) {
        Fixture fixture = user(helper, "rejojo-mr-kick-unlock", ModStands.MAGICIANS_RED.get());
        StandPower power = fixture.power();
        helper.runAfterDelay(2, () -> {
            try {
                Ability kick = power.getAbility("kick");
                helper.assertTrue(kick instanceof ReworkedStandAbilities.ReworkedMagiciansRedKickAbility,
                        "Setup: Magician's Red has no Reworked kick: " + kick);
                helper.assertTrue(power.getResolveLevel() == 0 && power.getHighestResolveLevel() == 0,
                        "Setup: a new Magician's Red user is at Resolve " + power.getResolveLevel() + ", highest "
                                + power.getHighestResolveLevel());
                helper.assertTrue(power.getCurTypeData().isSkillUnlocked("kick") && kick.isAbilityUnlocked(power),
                        "1.16 unlocked Reworked's finisher kick at Resolve 0; the port: skill "
                                + power.getCurTypeData().isSkillUnlocked("kick") + ", ability "
                                + kick.getUnlockConditionCheck(power).isPositive() + ", level "
                                + kick.getRequiredResolveLevel(power));
                helper.assertTrue(StandEntity.isFinisherMechanicUnlocked(power),
                        "1.16 opened the finisher meter at Resolve 0 through the unlocked finisher kick; the port"
                                + " keeps it locked");
            } finally {
                cleanup(fixture);
            }
            helper.succeed();
        });
    }

    /**
     * 1.16 RE_MAGICIANS_RED_FLAME_BURST had noResolveUnlock() (025 init :100), so no Resolve level unlocked it. The
     * Reworked punch was trained (isTrained(), at most 1 point): a punch whose task target was living added a 40th
     * (ReMagiciansRedLightAttack.standPerform :48-55), and the full training unlocked the flame burst (onMaxTraining
     * :57-58). A creative user got it as every action (StandAction.canBeUnlocked), for good.
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 200,
            batch = "rejojoMrFlameBurstUnlock")
    public static void magiciansRedFlameBurstUnlocksOnlyByTheTrainedPunchAsIn116(GameTestHelper helper) {
        new FlameBurstTrainingScene(helper).start();
    }

    /**
     * 1.16 StandType.unlockNewActions walked the Stand type's own actions, so a creative user of a Stand with no flame
     * burst was never asked for one. The port's creative unlock asked Power.getAbility for it on every creative
     * player's tick, which logs "Invalid ability id" for each such Stand; the lookup goes through the moveset now.
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 100,
            batch = "rejojoCreativeFlameBurstLookup")
    public static void creativeUsersOfOtherStandsAreNotWarnedAboutTheFlameBurstAsIn116(GameTestHelper helper) {
        WarnCapture capture = new WarnCapture();
        Fixture other = null;
        Fixture magiciansRed = null;
        try {
            other = user(helper, "rejojo-creative-sp", ModStands.STAR_PLATINUM.get());
            magiciansRed = user(helper, "rejojo-creative-mr", ModStands.MAGICIANS_RED.get());
            StandPower otherPower = other.power();
            StandPower mrPower = magiciansRed.power();
            helper.assertTrue(otherPower.getMoveset().getAbility("flame_burst") == null
                            && mrPower.getMoveset().getAbility("flame_burst")
                                    instanceof ReworkedStandAbilities.ReworkedMagiciansRedFlameBurstAbility,
                    "Setup: Star Platinum has a flame burst, or Magician's Red lacks the Reworked one");
            helper.assertTrue(!mrPower.getCurTypeData().isSkillUnlocked(
                            ReworkedStandAbilities.MAGICIANS_RED_FLAME_BURST_SKILL),
                    "Setup: the flame burst skill starts unlocked");
            capture.install();
            otherPower.getAbility("flame_burst");
            helper.assertTrue(capture.invalidAbilityIdWarnings() == 1,
                    "Setup: the log capture did not see Power.getAbility's warning: " + capture.messages);
            capture.messages.clear();

            other.player().setGameMode(GameType.CREATIVE);
            magiciansRed.player().setGameMode(GameType.CREATIVE);
            Fixture otherUser = other;
            Fixture mrUser = magiciansRed;
            int[] ticks = {0};
            helper.onEachTick(() -> {
                if (++ticks[0] < 6) {
                    return;
                }
                try {
                    helper.assertTrue(otherUser.power().isUserCreative() && mrUser.power().isUserCreative(),
                            "Setup: the users are not creative");
                    helper.assertTrue(capture.invalidAbilityIdWarnings() == 0,
                            "1.16 looked up no flame burst in a Stand without one; the port logged: "
                                    + capture.messages);
                    helper.assertTrue(unlockedSkills(otherPower).stream().noneMatch(each -> each.contains("flame")),
                            "A creative Star Platinum user got a flame burst skill: " + unlockedSkills(otherPower));
                    helper.assertTrue(mrUser.power().getCurTypeData().isSkillUnlocked(
                                    ReworkedStandAbilities.MAGICIANS_RED_FLAME_BURST_SKILL)
                                    && mrUser.power().getMoveset().getAbility("flame_burst")
                                            .isAbilityUnlocked(mrUser.power()),
                            "1.16 unlocked the Reworked flame burst for a creative Magician's Red user; the port"
                                    + " did not");
                } finally {
                    capture.remove();
                    cleanup(otherUser);
                    cleanup(mrUser);
                }
                helper.succeed();
            });
        } catch (RuntimeException | Error failure) {
            capture.remove();
            if (other != null) {
                cleanup(other);
            }
            if (magiciansRed != null) {
                cleanup(magiciansRed);
            }
            throw failure;
        }
    }

    private static List<String> unlockedSkills(StandPower power) {
        List<String> unlocked = new ArrayList<>();
        for (String name : power.getCurTypeData().getAllSkills().keySet()) {
            if (power.getCurTypeData().isSkillUnlocked(name)) {
                unlocked.add(name);
            }
        }
        unlocked.sort(null);
        return unlocked;
    }

    private static final class WarnCapture extends AbstractAppender {
        private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
        private boolean installed;

        private WarnCapture() {
            super("rejojoFlameBurstWarnCapture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            if (event.getLevel() == org.apache.logging.log4j.Level.WARN) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        }

        private void install() {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            start();
            context.getConfiguration().addAppender(this);
            context.getConfiguration().getRootLogger().addAppender(this, null, null);
            context.updateLoggers();
            installed = true;
        }

        private void remove() {
            if (!installed) {
                return;
            }
            installed = false;
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.getConfiguration().getRootLogger().removeAppender(getName());
            context.updateLoggers();
            stop();
        }

        private long invalidAbilityIdWarnings() {
            synchronized (messages) {
                return messages.stream().filter(each -> each.contains("Invalid ability id")
                        && each.contains("flame_burst")).count();
            }
        }
    }

    private static final class FlameBurstTrainingScene {
        private static final short PUNCH = 171;
        private static final float PER_HIT = 1.0F / 40.0F;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private Fixture fixture;
        private Fixture creative;
        private StandEntity stand;
        private Cow cow;
        private EntityActionInstance punch;
        private int hits;
        private int ticks;
        private int creativeAt;
        private boolean closed;

        private FlameBurstTrainingScene(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
        }

        private void start() {
            try {
                setUp();
                helper.onEachTick(this::tick);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void setUp() {
            fixture = user(helper, "rejojo-mr-training", ModStands.MAGICIANS_RED.get());
            ServerPlayer player = fixture.player();
            StandPower power = fixture.power();
            helper.assertTrue(power.getAbility("flame_burst")
                            instanceof ReworkedStandAbilities.ReworkedMagiciansRedFlameBurstAbility
                            && power.getAbility("punch")
                                    instanceof ReworkedStandAbilities.ReworkedMagiciansRedPunchAbility,
                    "Setup: Magician's Red has no Reworked punch and flame burst");
            var skill = power.getCurTypeData().getAllSkills().get(
                    ReworkedStandAbilities.MAGICIANS_RED_FLAME_BURST_SKILL);
            helper.assertTrue(skill != null && skill.hidden && !skill.isStarting,
                    "1.16's flame burst unlock had no skill menu entry or starting unlock (a hidden skill in the"
                            + " port); the skill is " + (skill == null ? "missing" : "hidden " + skill.hidden
                            + ", starting " + skill.isStarting));
            for (int resolveLevel = 0; resolveLevel <= 4; resolveLevel++) {
                power.setResolveLevel(resolveLevel);
                helper.assertTrue(!flameBurstUnlocked(power),
                        "1.16 unlocked the Reworked flame burst at no Resolve level (noResolveUnlock); the port has"
                                + " it at stored level " + resolveLevel + " (skill " + skillUnlocked(power) + ")");
            }
            helper.assertTrue(points(power) == 0.0F, "Setup: the Reworked punch starts trained " + points(power));
            helper.assertTrue(ModStands.MAGICIANS_RED.get().summon(player, power), "Could not summon Magician's Red.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.getUser() == player && !stand.isArmsOnlyMode(),
                    "Setup: no full Magician's Red body");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(16.0D);
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Setup: no cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            cow.setHealth(200.0F);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            placeCow();
            helper.assertTrue(level.addFreshEntity(cow), "Setup: the cow was not added");
            player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
        }

        private void placeCow() {
            Vec3 at = fixture.player().position().add(0.0D, 0.0D, 1.5D);
            cow.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            cow.setDeltaMovement(Vec3.ZERO);
        }

        private void tick() {
            if (closed) {
                return;
            }
            try {
                ticks++;
                helper.assertTrue(ticks < 180, "The scene did not finish: hits " + hits + ", punch "
                        + (punch != null ? punch.getPhase() : null) + ", creative " + (creative != null));
                if (creative != null) {
                    creativeTick();
                    return;
                }
                if (punch == null) {
                    if (ticks >= 3 && level.isPositionEntityTicking(stand.blockPosition())) {
                        press();
                    }
                    return;
                }
                if (punch.punchedTarget == null) {
                    return;
                }
                helper.assertTrue(punch.punchedTarget.getMainEntity() == cow,
                        "Setup: the punch struck " + punch.punchedTarget + ", not the cow");
                StandPower power = fixture.power();
                hits++;
                if (hits == 1) {
                    helper.assertTrue(Math.abs(points(power) - PER_HIT) < 1.0E-6F && !flameBurstUnlocked(power),
                            "1.16 trained the Reworked punch by a 40th of its 1 point for a punch at a living target"
                                    + " and kept the flame burst locked; the port has " + points(power)
                                    + " points, flame burst " + flameBurstUnlocked(power));
                    power.getCurTypeData().setAbilityLearningProgressPoints("punch", 1.0F - PER_HIT, 1.0F, power);
                    helper.assertTrue(!flameBurstUnlocked(power), "Setup: the flame burst unlocked below full training");
                    LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
                    placeCow();
                    punch = null;
                    return;
                }
                helper.assertTrue(points(power) >= 1.0F && skillUnlocked(power) && flameBurstUnlocked(power),
                        "1.16 ReMagiciansRedLightAttack.onMaxTraining unlocked the flame burst once a punch trained"
                                + " the Reworked punch fully; the port has " + points(power) + " points, skill "
                                + skillUnlocked(power) + ", flame burst " + flameBurstUnlocked(power));
                startCreative();
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void press() {
            ServerPlayer player = fixture.player();
            StandPower power = fixture.power();
            power.setStamina(power.getMaxStamina());
            ActionTarget aim = new ActionTarget(cow);
            LivingComponentAction.getComponent(player).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
            Ability base = power.getAbility("punch");
            var selected = power.updateAvailableMoves().getContextVariationContainer(base);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, player, InputMethod.CLICK),
                    "Setup: the punch key was refused");
            BufferingState buffer = BufferingState.clickOnly();
            var held = AbilityInput.keyPress(PUNCH, selected.ability, player, null, InputMethod.CLICK, 0.0F, buffer,
                    base.getAbilityId());
            AbilityInput.keyRelease(PUNCH, player);
            helper.assertTrue(buffer.isActionSuccess, "Setup: the punch key did not succeed");
            punch = held != null && held.action instanceof EntityActionInstance action
                    ? action
                    : stand.getCurStandAction();
            helper.assertTrue(punch != null && punch.ability == base,
                    "Setup: the punch key did not start the Reworked punch: " + punch);
        }

        private void startCreative() {
            creative = user(helper, "rejojo-mr-training-creative", ModStands.MAGICIANS_RED.get());
            StandPower power = creative.power();
            power.setResolveLevel(4);
            helper.assertTrue(!flameBurstUnlocked(power), "Setup: a survival user at Resolve 4 has the flame burst");
            creative.player().setGameMode(GameType.CREATIVE);
            creativeAt = ticks;
        }

        private void creativeTick() {
            if (ticks - creativeAt < 3) {
                return;
            }
            StandPower power = creative.power();
            helper.assertTrue(power.isUserCreative() && flameBurstUnlocked(power),
                    "1.16 unlocked the Reworked flame burst for a creative user; the port: creative "
                            + power.isUserCreative() + ", skill " + skillUnlocked(power));
            creative.player().setGameMode(GameType.SURVIVAL);
            helper.assertTrue(!power.isUserCreative() && flameBurstUnlocked(power),
                    "1.16 kept the flame burst a creative user had unlocked, back in survival");
            close();
            helper.succeed();
        }

        private static boolean flameBurstUnlocked(StandPower power) {
            return power.getAbility("flame_burst").isAbilityUnlocked(power);
        }

        private static boolean skillUnlocked(StandPower power) {
            return power.getCurTypeData().isSkillUnlocked(ReworkedStandAbilities.MAGICIANS_RED_FLAME_BURST_SKILL);
        }

        private static float points(StandPower power) {
            return power.getCurTypeData().getAbilityLearningProgressPoints("punch");
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Fixture each : new Fixture[] {fixture, creative}) {
                if (each == null) {
                    continue;
                }
                if (each.power().isSummoned()) {
                    ModStands.MAGICIANS_RED.get().forceUnsummon(each.player(), each.power());
                }
                cleanup(each);
            }
            if (stand != null && !stand.isRemoved()) {
                stand.discard();
            }
            if (cow != null) {
                cow.discard();
            }
        }
    }

    /**
     * 1.16 ReMagiciansRedLightAttack is a plain StandEntityLightAttack: StandEntityPunch pushed its guard counter
     * (addKnockback(guardCounter)) apart from the native hurt's 0.4, as half the counter on a hit and the same half
     * again as the tail.
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 120, batch = "rejojoMrPunchCounter")
    public static void magiciansRedPunchPushesItsGuardCounterInDetachedPulses(GameTestHelper helper) {
        new LightCounterScene(helper).start();
    }

    /**
     * 1.16 Reworked built Star Platinum's heavy punch and uppercut, and Magician's Red's heavy punch and kick, from a
     * plain StandEntityHeavyAttack.Builder, so they perform for one tick, and StandEntityPunch.doAttack pushed with
     * the native hurt (0.4), then half the extra knockback on a hit and the same half again as the tail. All four
     * sampled their recovery on entry (StandEntityHeavyAttack.getStandRecoveryTicks). The blink was a modifier of the
     * heavy task, which went on as it was, so the Stand kept stepping after the target through the windup
     * (StandEntityHeavyAttack.getOffsetFromUser) and the recovery was read on entry as well.
     */
    private static final class HeavyScene {
        private static final short ATTACK = 151;
        private static final short REPEAT = 152;
        private static final short HEAVY_IN_RECOVERY = 154;
        private static final short PUNCH_IN_RECOVERY = 155;
        private static final float RAISED_SPEED = 28.0F;

        // The cow's distance at the press, and where it backs away to in the step scene; the Stand keeps its own
        // distance minus 1 + half the cow's width.
        private static final double STEP_NEAR = 2.0D;
        private static final double STEP_FAR = 3.0D;

        private final GameTestHelper helper;
        // the uppercut, or Magician's Red's kick
        private final boolean uppercut;
        private final boolean blink;
        private final boolean magiciansRed;
        private final boolean stepScene;
        // the meter reaches the uppercut's threshold in the heavy punch's windup
        private final boolean meterRises;
        private final ServerLevel level;
        private final List<Float> pulses = new ArrayList<>();
        private final List<Object> listeners = new ArrayList<>();
        private Player user;
        private StandPower power;
        private StandEntity stand;
        private Cow target;
        private EntityActionInstance action;
        private Throwable eventFailure;
        private float impactStrength = Float.NaN;
        private float impactFinisher = Float.NaN;
        private float firstFinisher = Float.NaN;
        private boolean repeated;
        private boolean speedRaised;
        private boolean struck;
        // 1: the heavy key is pressed in the blink uppercut's recovery next, 2: then the punch
        private int recoveryRow;
        private boolean done;
        private boolean closed;
        private int ticks;
        private int beganAt;
        private int backedAwayAt = -1;
        private double offsetAtRepeat = Double.NaN;

        private HeavyScene(GameTestHelper helper, boolean uppercut, boolean blink) {
            this(helper, uppercut, blink, false, false);
        }

        private HeavyScene(
                GameTestHelper helper, boolean uppercut, boolean blink, boolean magiciansRed, boolean stepScene) {
            this(helper, uppercut, blink, magiciansRed, stepScene, false);
        }

        private HeavyScene(GameTestHelper helper, boolean uppercut, boolean blink, boolean magiciansRed,
                boolean stepScene, boolean meterRises) {
            this.helper = helper;
            this.uppercut = uppercut;
            this.blink = blink;
            this.magiciansRed = magiciansRed;
            this.stepScene = stepScene;
            this.meterRises = meterRises;
            level = helper.getLevel();
        }

        private String id() {
            if (magiciansRed) {
                return uppercut ? "Magician's Red kick" : "Magician's Red heavy punch";
            }
            return (blink ? "blink " : "") + (uppercut ? "uppercut" : "heavy punch");
        }

        private EntityStandType standType() {
            return magiciansRed ? ModStands.MAGICIANS_RED.get() : ModStands.STAR_PLATINUM.get();
        }

        private void start() {
            try {
                setUp();
                helper.onEachTick(this::tick);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void setUp() {
            Vec3 origin = chunkMiddle(helper);
            user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "rejojo-sp-heavy")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Setup: the Star Platinum user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(standType());
            power.setResolveLevel(2);
            helper.assertTrue(standType().summon(user, power), "Could not summon the Stand.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.getUser() == user, "Setup: no summoned Stand");
            if (magiciansRed) {
                helper.assertTrue(power.getAbility("heavy_punch")
                                instanceof ReworkedStandAbilities.ReworkedMagiciansRedHeavyPunchAbility
                                && power.getAbility("kick")
                                        instanceof ReworkedStandAbilities.ReworkedMagiciansRedKickAbility,
                        "Setup: Reworked did not replace Magician's Red's heavy punch and kick");
            }
            else {
                helper.assertTrue(power.getAbility("heavy_punch")
                                instanceof ReworkedStandAbilities.ReworkedStarPlatinumHeavyPunchAbility
                                && power.getAbility("finisher_uppercut")
                                        instanceof ReworkedStandAbilities.ReworkedStarPlatinumUppercutAbility,
                        "Setup: Reworked did not replace Star Platinum's heavy punch and uppercut");
            }
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            // The windup step keeps a following Stand at its distance from the target; a Stand left where it is
            // does not step.
            stand.setCanFollowUser(stepScene);
            stand.setNoGravity(true);
            stand.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15.0D);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16.0D);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12.0D);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8.0D);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8.0D);
            StandEntity.updateStandStatAttributes(stand, user);
            power.setStamina(power.getMaxStamina());
            stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            if (uppercut && !magiciansRed) {
                unlock("uppercut");
            }
            if (blink) {
                unlock(TimeStopLearning.TIME_STOP);
                // Five blink ticks at the default 0.1 cover about a block; this puts the target within the blink.
                user.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(1.0D);
            }

            target = EntityType.COW.create(level);
            helper.assertTrue(target != null, "Setup: no cow");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            target.setHealth(200.0F);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
            target.moveTo(origin.x, origin.y, origin.z + (stepScene ? STEP_NEAR : blink ? 3.0D : 1.7D), 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(target), "Setup: the cow was not added");
            ActionTarget aim = new ActionTarget(target);
            LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
            user.lookAt(EntityAnchorArgument.Anchor.EYES, aim.getCenterPos());
            user.setYHeadRot(user.getYRot());
            stand.lookAt(EntityAnchorArgument.Anchor.EYES, aim.getCenterPos());
            stand.setYHeadRot(stand.getYRot());
            listen();
        }

        private void begin() {
            beganAt = ticks;
            stand.setFinisherMeter(uppercut ? 0.75F : 0.0F);
            action = press(power.getAbility("heavy_punch"), ATTACK, true);
            boolean started;
            if (magiciansRed) {
                started = uppercut
                        ? action instanceof MagiciansRedKickAbility.MagiciansRedKick
                                && action.ability instanceof ReworkedStandAbilities.ReworkedMagiciansRedKickAbility
                        : action.getClass() == ReworkedStandAbilities.ReworkedMagiciansRedHeavyPunch.class
                                && action.ability
                                        instanceof ReworkedStandAbilities.ReworkedMagiciansRedHeavyPunchAbility;
            }
            else {
                started = uppercut
                        ? action instanceof HeavyPunchUppercutAbility.Uppercut
                                && action.ability instanceof ReworkedStandAbilities.ReworkedStarPlatinumUppercutAbility
                        : action.getClass() == StandEntityHeavyPunchAbility.StandEntityHeavyPunch.class
                                && action.ability instanceof ReworkedStandAbilities.ReworkedStarPlatinumHeavyPunchAbility;
            }
            helper.assertTrue(started, "Setup: the right click did not start the Reworked " + id()
                    + ": " + action.getClass().getName());
            firstFinisher = ((StandEntityHeavyPunchAbility.StandEntityHeavyPunch) action).finisherValue;
            helper.assertTrue(near(firstFinisher, uppercut ? 0.75F : 0.0F),
                    "Setup: the first press did not snapshot the finisher meter: " + firstFinisher);
            if (!blink) {
                checkPerform();
            }
            if (!magiciansRed) {
                float stored = heavyRecovery(16.0F, firstFinisher);
                helper.assertTrue(near(action.phasesLength.getFloat(ActionPhase.RECOVERY), stored),
                        "Setup: the " + (uppercut ? "uppercut" : "heavy punch") + " did not store the recovery "
                                + stored + " of speed 16: " + action.phasesLength.getFloat(ActionPhase.RECOVERY));
            }
        }

        private void listen() {
            Consumer<LivingKnockBackEvent> knockback = event -> {
                if (event.getEntity() == target) {
                    pulses.add(event.getOriginalStrength());
                }
            };
            Consumer<LivingIncomingDamageEvent> incoming = event -> {
                if (event.getEntity() == target && event.getSource().getDirectEntity() == stand) {
                    impactStrength = (float) stand.getAttackDamage();
                    impactFinisher = stand.getLastHeavyFinisherValue();
                }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (event.getEntity() == stand && !closed && eventFailure == null) {
                    try {
                        afterStandTick();
                    } catch (RuntimeException | Error failure) {
                        eventFailure = failure;
                    }
                }
            };
            listeners.add(knockback);
            listeners.add(incoming);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void tick() {
            if (closed) {
                return;
            }
            try {
                if (eventFailure instanceof RuntimeException failure) {
                    throw failure;
                }
                if (eventFailure instanceof Error failure) {
                    throw failure;
                }
                if (done) {
                    close();
                    helper.succeed();
                    return;
                }
                ticks++;
                if (action == null) {
                    if (ticks >= 3 && level.isPositionEntityTicking(stand.blockPosition())
                            && level.isPositionEntityTicking(target.blockPosition())) {
                        begin();
                    }
                }
                else if (recoveryRow == 1) {
                    pressHeavyKeyInRecovery();
                }
                else if (recoveryRow == 2) {
                    pressPunchInRecovery();
                }
                else if (ticks > beganAt + 1 && action.getPhase() == ActionPhase.WINDUP) {
                    if (blink && !repeated) {
                        repeatWithBlinkModifier();
                        if (stepScene) {
                            backAway();
                        }
                    }
                    else if (stepScene) {
                        checkStep();
                    }
                    else if (!speedRaised) {
                        raiseSpeed();
                    }
                }
                else if (stepScene && action != null && repeated) {
                    helper.fail("Setup: the " + id() + " left its windup before the step was checked: "
                            + action.getPhase());
                }
                helper.assertTrue(ticks < 100, action == null
                        ? "Setup: the fixture entities never ticked"
                        : "The Reworked " + id() + " did not strike, phase " + action.getPhase());
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        // 1.16 ReStarPlatinumHeavyPunch / ReStarPlatinumUppercut.replaceAction: Shift and the heavy key again in
        // the windup. StarPlatinumBlinkPunch was a modifier of the running task: its windup went on, its recovery
        // and finisher snapshot stayed, the meter was not debited again, and its cry, swing sound and hand were
        // not replayed.
        private void repeatWithBlinkModifier() {
            repeated = true;
            if (meterRises) {
                stand.setFinisherMeter(0.75F);
            }
            EntityActionInstance first = action;
            float windupTick = first.getPhaseTick();
            float windup = first.phasesLength.getFloat(ActionPhase.WINDUP);
            float recovery = first.phasesLength.getFloat(ActionPhase.RECOVERY);
            float meter = stand.getFinisherMeter();
            // By now a client of the running attack has played its cry, and its swing near the strike; the test
            // marks both on this side, where actionTick never plays them.
            StandEntityHeavyPunchAbility.StandEntityHeavyPunch running =
                    (StandEntityHeavyPunchAbility.StandEntityHeavyPunch) first;
            running.playedStandCrySound = true;
            running.playedSwingSound = true;
            InteractionHand hand = stand.getCurrentPunchingHand();
            user.setShiftKeyDown(true);
            // The press goes through the key's resolution, as ClAbilityInputPacket does it.
            Ability heavyKey = power.getAbility("heavy_punch");
            var resolved = power.updateAvailableMoves().getContextVariationContainer(heavyKey);
            if (uppercut) {
                helper.assertTrue(resolved != null
                                && resolved.ability instanceof ReworkedStandAbilities.ReworkedStarPlatinumBlinkPunchAbility
                                && resolved.conditionCheck.isPositive(),
                        "1.16 ReStarPlatinumUppercut.replaceAction gave the heavy key the blink uppercut while sneaking"
                                + " in the uppercut's windup; the port's key resolves to " + (resolved == null
                                ? "nothing" : resolved.ability.name()
                                        + (resolved.conditionCheck.isPositive() ? "" : ", refused")));
            }
            if (meterRises) {
                helper.assertTrue(resolved != null
                                && resolved.ability instanceof ReworkedStandAbilities.ReworkedStarPlatinumBlinkPunchAbility
                                && ReworkedStandAbilities.STAR_PLATINUM_BLINK_HEAVY_NAME.equals(resolved.ability.name())
                                && resolved.conditionCheck.isPositive(),
                        "1.16 ReStarPlatinumHeavyPunch.replaceAction gave the heavy key the blink heavy punch while"
                                + " sneaking in the heavy punch's windup, before the uppercut at a full meter; the port's"
                                + " key resolves to " + (resolved == null ? "nothing" : resolved.ability.name()
                                        + (resolved.conditionCheck.isPositive() ? "" : ", refused")));
            }
            action = press(heavyKey, REPEAT, true);
            user.setShiftKeyDown(false);
            long modifiers = first.getPunchModifiers().stream()
                    .filter(effect -> effect.effectType == (uppercut
                            ? ReworkedAddon.STAR_PLATINUM_UPPERCUT_BLINK_MODIFIER.get()
                            : ReworkedAddon.STAR_PLATINUM_BLINK_MODIFIER.get()))
                    .count();
            helper.assertTrue(action == first && modifiers == 1 && stand.getCurrentPunchingHand() == hand,
                    "1.16: the " + id() + " was a modifier of the running attack, which went on with its hand " + hand
                            + "; the Stand's action is " + action + " (the attack " + first + "), blink modifiers on the"
                            + " attack " + modifiers + ", hand " + stand.getCurrentPunchingHand());
            checkPerform();
            helper.assertTrue(windupTick >= 1.0F && action.getPhase() == ActionPhase.WINDUP
                            && near(action.getPhaseTick(), windupTick)
                            && near(action.phasesLength.getFloat(ActionPhase.WINDUP), windup),
                    "1.16: the " + id() + " goes on with the running windup at tick " + windupTick + " of " + windup
                            + "; the port starts a new one at tick " + action.getPhaseTick() + " of "
                            + action.phasesLength.getFloat(ActionPhase.WINDUP));
            helper.assertTrue(near(stand.getFinisherMeter(), meter)
                            && near(stand.getLastHeavyFinisherValue(), firstFinisher)
                            && near(((StandEntityHeavyPunchAbility.StandEntityHeavyPunch) action).finisherValue,
                                    firstFinisher)
                            && near(action.phasesLength.getFloat(ActionPhase.RECOVERY), recovery),
                    "1.16: the " + id() + " keeps the finisher " + firstFinisher + ", the meter " + meter
                            + " and the recovery " + recovery + " of the running attack; the port has finisher "
                            + ((StandEntityHeavyPunchAbility.StandEntityHeavyPunch) action).finisherValue
                            + " (snapshot " + stand.getLastHeavyFinisherValue() + "), meter "
                            + stand.getFinisherMeter() + ", recovery "
                            + action.phasesLength.getFloat(ActionPhase.RECOVERY));
        }

        // The cow backs away once the blink is on the running attack.
        private void backAway() {
            Vec3 offset = action.getSyncedStandOffset();
            helper.assertTrue(offset != null, "Setup: the " + id() + " set no Stand offset");
            offsetAtRepeat = offset.z;
            helper.assertTrue(offsetAtRepeat < stepOffset(STEP_FAR) - 0.5D,
                    "Setup: the Stand already stands " + offsetAtRepeat + " in front of its user");
            Vec3 origin = user.position();
            target.moveTo(origin.x, origin.y, origin.z + STEP_FAR, 0.0F, 0.0F);
            backedAwayAt = ticks;
        }

        private void checkStep() {
            if (ticks < backedAwayAt + 2) {
                return;
            }
            Vec3 offset = action.getSyncedStandOffset();
            double expected = stepOffset(STEP_FAR);
            helper.assertTrue(offset != null && Math.abs(offset.z - expected) < 0.05D,
                    "1.16 StandEntityHeavyAttack.getOffsetFromUser: through the rest of the windup the " + id()
                            + " keeps its distance from a target that backs away, " + expected
                            + " in front of the user; the port's Stand stays at "
                            + (offset != null ? offset.z : "no offset") + " (" + offsetAtRepeat + " at the press)");
            done = true;
        }

        private double stepOffset(double distance) {
            return distance - (1.0D + target.getBbWidth() / 2.0D);
        }

        // The recovery a stand of the raised speed takes, if the heavy punch samples it when it enters recovery.
        private void raiseSpeed() {
            speedRaised = true;
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(RAISED_SPEED);
            StandEntity.updateStandStatAttributes(stand, user);
            helper.assertTrue(near(stand.getAttackSpeed(), RAISED_SPEED),
                    "Setup: the stand speed did not rise to " + RAISED_SPEED + ": " + stand.getAttackSpeed());
        }

        private void checkPerform() {
            helper.assertTrue(near(action.phasesLength.getFloat(ActionPhase.PERFORM), 1.0F),
                    "1.16 Reworked's " + id() + " performs for the builder's one tick; the port stores PERFORM "
                            + action.phasesLength.getFloat(ActionPhase.PERFORM));
        }

        private void afterStandTick() {
            if (struck || done || action == null || action.punchedTarget == null) {
                return;
            }
            struck = true;
            // the blink rows' cow stands out of the attack's own reach: the attack misses, the blink strikes it
            helper.assertTrue(blink ? action.punchedTarget.isEmpty(level)
                            : action.punchedTarget.getMainEntity() == target,
                    "Setup: the " + id() + "'s own strike " + (blink ? "reached" : "did not strike") + " the cow");
            helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY,
                    "1.16: a one-tick perform ends on the impact tick, but the " + id() + " is still in "
                            + action.getPhase() + " after it");
            helper.assertTrue(!Float.isNaN(impactStrength), "Setup: the " + id() + " did not hurt the cow");
            helper.assertTrue(near(impactFinisher, firstFinisher),
                    "1.16: the " + id() + " strikes with the finisher " + firstFinisher
                            + " its attack snapshotted on the first press; the port struck with " + impactFinisher);
            if (magiciansRed) {
                // The kick's own push (x1.2) is the core MagiciansRedKick's; detached, it is two equal halves.
                helper.assertTrue(pulses.size() == 3 && near(pulses.get(0), 0.4F) && pulses.get(1) > 0.0F
                                && near(pulses.get(1), pulses.get(2)),
                        "1.16 StandEntityPunch pushes the " + id() + "'s target with the native 0.4, then half the"
                                + " extra knockback on the hit and the same half as the tail; the port pushed "
                                + pulses);
            }
            else {
                // The finisher of the first press: for the uppercut 0.5 * (0.5 + 15 / 16 * 0.75) * cos(-60) = 0.3008.
                float half = 0.5F * (uppercut
                        ? 0.5F + impactStrength / 16.0F * firstFinisher
                        : 0.5F + impactStrength / (8.0F - firstFinisher * 4.0F));
                float horizontal = uppercut ? half * Mth.cos(-60.0F * Mth.DEG_TO_RAD) : half;
                helper.assertTrue(pulses.size() == 3 && near(pulses.get(0), 0.4F)
                                && near(pulses.get(1), horizontal) && near(pulses.get(2), horizontal),
                        "1.16 StandEntityPunch pushes the " + id() + "'s target with the native 0.4, then "
                                + horizontal + " on the hit and " + horizontal + " as the tail; the port pushed "
                                + pulses);
            }
            // 1.16 StandEntityHeavyAttack.isFreeRecovery: the finisher's recovery can be cancelled, the heavy's not; the
            // heavy key in the uppercut's recovery is checked through the key presses below (pressHeavyKeyInRecovery)
            helper.assertTrue(action.canBeCancelledInto(action.ability) == uppercut,
                    "1.16: the " + id() + "'s recovery " + (uppercut ? "can" : "cannot")
                            + " be cancelled; the port's can be: " + action.canBeCancelledInto(action.ability));
            helper.assertTrue(speedRaised, "Setup: the stand speed was not raised in the windup");
            if (speedRaised) {
                float expected = heavyRecovery(RAISED_SPEED, firstFinisher);
                helper.assertTrue(near(action.phasesLength.getFloat(ActionPhase.RECOVERY), expected),
                        "1.16 StandEntityHeavyAttack takes the recovery of the speed it enters recovery with, "
                                + expected + "; the port keeps " + action.phasesLength.getFloat(ActionPhase.RECOVERY));
            }
            if (blink && uppercut && !stepScene) {
                recoveryRow = 1;
            }
            else {
                done = true;
            }
        }

        // 1.16: in the recovery of the uppercut the blink modified the heavy key resolved to that running uppercut
        // (getFinisherVariationIfPresent: the current task) and its click was refused, whatever the meter; the port may
        // keep such a press waiting in the input buffer (owner decision N2). Pressed as the server takes a press; the
        // keys are resolved once a tick, so the second press comes on the next one.
        private void pressHeavyKeyInRecovery() {
            recoveryRow = 2;
            helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.RECOVERY,
                    "Setup: the uppercut is not in its recovery: " + action.getPhase());
            stand.setFinisherMeter(0.0F);
            pressHeavyKey();
            stand.setFinisherMeter(0.75F);
        }

        // The next tick the heavy press still waits, the uppercut on the heavy key waits too, and a punch takes the
        // free recovery at once, as 1.16's other actions did.
        private void pressPunchInRecovery() {
            done = true;
            helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.RECOVERY,
                    "1.16: the uppercut the blink modified goes on through its recovery after the refused heavy key;"
                            + " the Stand has " + stand.getCurStandAction() + (stand.getCurStandAction() == action
                                    ? " in " + action.getPhase() : ""));
            pressHeavyKey();
            // The uppercut's recovery was free for other actions (StandEntityHeavyAttack.isFreeRecovery = isFinisher).
            Ability punch = power.getAbility("punch");
            var resolved = power.updateAvailableMoves().getContextVariationContainer(punch);
            helper.assertTrue(resolved != null && AbilityInput.withConditionCheck(resolved, user, InputMethod.CLICK),
                    "Setup: the punch was refused in the uppercut's recovery");
            BufferingState buffer = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(PUNCH_IN_RECOVERY, resolved.ability, user, null, InputMethod.CLICK, 0.0F, buffer,
                    punch.getAbilityId());
            EntityActionInstance now = stand.getCurStandAction();
            helper.assertTrue(buffer.isActionSuccess && now != null && now != action && now.ability == resolved.ability,
                    "1.16: a punch pressed in the free recovery of the uppercut the blink modified starts at once; the"
                            + " port " + (buffer.shouldBuffer ? "buffered it" : "did not start it") + ", the Stand has "
                            + now);
        }

        // 1.16 StandEntityAction.canClickDuringTask refused the running uppercut pressed again (not chainable)
        private void pressHeavyKey() {
            Ability heavyKey = power.getAbility("heavy_punch");
            var resolved = power.updateAvailableMoves().getContextVariationContainer(heavyKey);
            BufferingState buffer = BufferingState.clickCanBuffer();
            boolean usable = resolved != null && AbilityInput.withConditionCheck(resolved, user, InputMethod.CLICK);
            if (usable) {
                AbilityInput.keyPress(HEAVY_IN_RECOVERY, resolved.ability, user, null, InputMethod.CLICK, 0.0F,
                        buffer, heavyKey.getAbilityId());
            }
            EntityActionInstance now = stand.getCurStandAction();
            helper.assertTrue(!buffer.isActionSuccess && now == action && !action.isOver(),
                    "1.16 refused the heavy key in the recovery of the uppercut the blink modified; the key gave "
                            + (resolved == null ? "nothing" : resolved.ability.name()) + (usable ? "" : ", refused")
                            + ", the port " + (buffer.isActionSuccess ? "started" : "left") + " "
                            + (now == null ? "no action" : now.ability instanceof Ability nowAbility
                                    ? nowAbility.name() : now.getClass().getName())
                            + (buffer.shouldBuffer ? ", buffered" : ", not buffered"));
        }

        private EntityActionInstance press(Ability ability, short key, boolean contextVariation) {
            helper.assertTrue(ability != null, "Setup: Star Platinum has no heavy punch");
            Ability pressed = ability;
            if (contextVariation) {
                var selected = power.updateAvailableMoves().getContextVariationContainer(ability);
                helper.assertTrue(selected != null
                                && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                        "Setup: the heavy key was refused for the " + id());
                pressed = selected.ability;
            }
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(key, pressed, user, null, InputMethod.CLICK, 0.0F, buffer, ability.getAbilityId());
            EntityActionInstance started = stand.getCurStandAction();
            helper.assertTrue(buffer.isActionSuccess && started != null,
                    "Setup: the heavy key did not start an action for the " + id());
            return started;
        }

        private void unlock(String skill) {
            PowerSkillUnlocks.Result result = ReworkedAddon.POWER_SKILL_UNLOCKS.forceUnlock(power, skill);
            helper.assertTrue(result == PowerSkillUnlocks.Result.UNLOCKED
                            || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                    "Setup: could not unlock " + skill + ": " + result);
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Object listener : listeners) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            listeners.clear();
            if (user != null) {
                AbilityInput.keyRelease(ATTACK, user);
                AbilityInput.keyRelease(REPEAT, user);
                AbilityInput.keyRelease(HEAVY_IN_RECOVERY, user);
                AbilityInput.keyRelease(PUNCH_IN_RECOVERY, user);
                if (stand != null) {
                    LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
                }
                if (power != null && power.isSummoned()) {
                    standType().forceUnsummon(user, power);
                }
                if (stand != null && !stand.isRemoved()) {
                    stand.discard();
                }
                user.discard();
            }
            if (target != null) {
                target.discard();
            }
        }
    }

    /**
     * Star Platinum (Reworked) punches with nothing in reach, and in the punch's windup Shift and the heavy key go
     * through the key's resolution as the server takes them, twice. The cow stands beyond the punch's reach and within
     * the blink's, so only the blink's strike can hit it.
     */
    private static final class LightPunchBlinkScene {
        private static final short PUNCH = 171;
        private static final short BLINK = 172;
        private static final short BLINK_AGAIN = 173;
        // beyond the punch's reach, within the 5 blocks the blink strike's ray covers (speed 1, five ticks)
        private static final double COW_DISTANCE = BlinkFixture.FAR_SOUTH;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<Object> listeners = new ArrayList<>();
        private final List<Float> pulses = new ArrayList<>();
        private Player user;
        private StandPower power;
        private StandEntity stand;
        private Cow target;
        private EntityActionInstance punch;
        private StandEffectInstance modifier;
        private Vec3 userAtPress;
        private Throwable eventFailure;
        private int standHits;
        private int ticks;
        private boolean done;
        private boolean closed;

        private LightPunchBlinkScene(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
        }

        private void start() {
            try {
                setUp();
                helper.onEachTick(this::tick);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void setUp() {
            Vec3 origin = chunkMiddle(helper);
            user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "rejojo-sp-blink-jab")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Setup: the Star Platinum user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(ModStands.STAR_PLATINUM.get());
            power.setResolveLevel(2);
            helper.assertTrue(ModStands.STAR_PLATINUM.get().summon(user, power), "Could not summon the Stand.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.getUser() == user, "Setup: no summoned Stand");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15.0D);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16.0D);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12.0D);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8.0D);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8.0D);
            StandEntity.updateStandStatAttributes(stand, user);
            power.setStamina(power.getMaxStamina());
            stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            PowerSkillUnlocks.Result unlocked =
                    ReworkedAddon.POWER_SKILL_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
            helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED
                            || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                    "Setup: could not unlock the time stop: " + unlocked);
            // Five blink ticks at a movement speed of 1 reach about 10.8 blocks.
            user.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(1.0D);

            target = EntityType.COW.create(level);
            helper.assertTrue(target != null, "Setup: no cow");
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            target.setHealth(200.0F);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
            target.moveTo(origin.x, origin.y, origin.z + COW_DISTANCE, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(target), "Setup: the cow was not added");
            // The user looks at the cow, which the blink's ray then finds; the punch is pressed at nothing.
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            user.setYHeadRot(user.getYRot());
            listen();
        }

        private void listen() {
            Consumer<LivingKnockBackEvent> knockback = event -> {
                if (event.getEntity() == target) {
                    pulses.add(event.getOriginalStrength());
                }
            };
            Consumer<LivingIncomingDamageEvent> incoming = event -> {
                if (event.getEntity() == target && event.getSource().getDirectEntity() == stand) {
                    standHits++;
                }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (event.getEntity() == stand && !closed && eventFailure == null) {
                    try {
                        afterStandTick();
                    } catch (RuntimeException | Error failure) {
                        eventFailure = failure;
                    }
                }
            };
            listeners.add(knockback);
            listeners.add(incoming);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void tick() {
            if (closed) {
                return;
            }
            try {
                if (eventFailure instanceof RuntimeException failure) {
                    throw failure;
                }
                if (eventFailure instanceof Error failure) {
                    throw failure;
                }
                if (done) {
                    close();
                    helper.succeed();
                    return;
                }
                ticks++;
                if (punch == null && ticks >= 3 && level.isPositionEntityTicking(stand.blockPosition())
                        && level.isPositionEntityTicking(target.blockPosition())) {
                    punchAndBlink();
                }
                helper.assertTrue(ticks < 100, punch == null
                        ? "Setup: the fixture entities never ticked"
                        : "The light punch never reached its perform, phase " + punch.getPhase());
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void punchAndBlink() {
            Ability punchKey = power.getAbility("punch");
            helper.assertTrue(punchKey != null, "Setup: Star Platinum has no punch");
            var resolvedPunch = power.updateAvailableMoves().getContextVariationContainer(punchKey);
            helper.assertTrue(resolvedPunch != null
                            && AbilityInput.withConditionCheck(resolvedPunch, user, InputMethod.CLICK),
                    "Setup: the punch key was refused");
            BufferingState punchBuffer = BufferingState.clickOnly();
            AbilityInput.keyPress(PUNCH, resolvedPunch.ability, user, null, InputMethod.CLICK, 0.0F, punchBuffer,
                    punchKey.getAbilityId());
            punch = stand.getCurStandAction();
            helper.assertTrue(punchBuffer.isActionSuccess && punch != null && punch.ability == resolvedPunch.ability
                            && punch.getPhase() == ActionPhase.WINDUP,
                    "Setup: the punch key did not start a light punch in its windup: " + punch
                            + (punch != null ? " in " + punch.getPhase() : ""));

            user.setShiftKeyDown(true);
            boolean first = pressHeavyKey(BLINK);
            List<StandEffectInstance> attached = blinkModifiers();
            helper.assertTrue(first && stand.getCurStandAction() == punch && punch.getPhase() == ActionPhase.WINDUP
                            && attached.size() == 1
                            && power.userStandEffects.getEffects().contains(attached.get(0)),
                    "1.16 added the blink to the light punch in its windup (a StandEntityActionModifier of that task)"
                            + " and the punch went on: the press went off " + first + ", the Stand's action "
                            + stand.getCurStandAction() + " (the punch " + punch + "), blink modifiers on it "
                            + attached.size());
            modifier = attached.get(0);
            boolean again = pressHeavyKey(BLINK_AGAIN);
            helper.assertTrue(!again && stand.getCurStandAction() == punch && blinkModifiers().size() == 1,
                    "1.16 kept a task's modifiers in a set, so a second blink in the same windup added nothing: the"
                            + " second press went off " + again + ", blink modifiers on the punch "
                            + blinkModifiers().size() + ", the Stand's action " + stand.getCurStandAction());
            user.setShiftKeyDown(false);
            userAtPress = user.position();
        }

        // The press goes through the key's resolution, as ClAbilityInputPacket does it.
        private boolean pressHeavyKey(short key) {
            Ability heavyKey = power.getAbility("heavy_punch");
            var resolved = power.updateAvailableMoves().getContextVariationContainer(heavyKey);
            helper.assertTrue(resolved != null && AbilityInput.withConditionCheck(resolved, user, InputMethod.CLICK),
                    "Setup: the heavy key was refused in the light punch's windup: "
                            + (resolved == null ? "nothing" : resolved.ability.name()));
            BufferingState buffer = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(key, resolved.ability, user, null, InputMethod.CLICK, 0.0F, buffer,
                    heavyKey.getAbilityId());
            return buffer.isActionSuccess;
        }

        private List<StandEffectInstance> blinkModifiers() {
            return punch.getPunchModifiers().stream()
                    .filter(effect -> effect.effectType == ReworkedAddon.STAR_PLATINUM_BLINK_MODIFIER.get())
                    .toList();
        }

        private void afterStandTick() {
            if (done || modifier == null) {
                return;
            }
            if (punch.punchedTarget == null) {
                helper.assertTrue(!modifier.isStopped() && user.position().distanceTo(userAtPress) < 0.01D,
                        "1.16 ran the blink's standPerform right after the light punch's own perform, not before it: in "
                                + punch.getPhase() + " the blink stopped " + modifier.isStopped() + ", the user moved "
                                + user.position().distanceTo(userAtPress));
                return;
            }
            done = true;
            helper.assertTrue(punch.punchedTarget.getType() == ActionTarget.TargetType.EMPTY,
                    "Setup: the light punch itself struck " + punch.punchedTarget.getType());
            helper.assertTrue(stand.getCurStandAction() == punch && punch.getPhase() == ActionPhase.PERFORM
                            && modifier.isStopped(),
                    "1.16: the light punch goes on through its perform after the blink, which ran once; the Stand has "
                            + stand.getCurStandAction() + (stand.getCurStandAction() == punch
                                    ? " in " + punch.getPhase() : "") + ", the blink stopped " + modifier.isStopped());
            double horizontal = Math.hypot(target.getX() - user.getX(), target.getZ() - user.getZ());
            helper.assertTrue(horizontal < 3.0D
                            && power.isAbilityOnCooldown(ReworkedStandAbilities.STAR_PLATINUM_BLINK_HEAVY_NAME)
                            && power.isAbilityOnCooldown(TimeStopCooldowns.TIME_STOP_BLINK),
                    "1.16 StarPlatinumBlinkPunch.blink took the user to the cow and set the blink punch's and the"
                            + " time-stop blink's cooldowns right after the punch's perform: the user stands "
                            + horizontal + " from the cow, cooldowns "
                            + power.isAbilityOnCooldown(ReworkedStandAbilities.STAR_PLATINUM_BLINK_HEAVY_NAME) + " / "
                            + power.isAbilityOnCooldown(TimeStopCooldowns.TIME_STOP_BLINK));
            // RE_STAR_PLATINUM_HEAVY_PUNCH's punch with the Stand's last heavy finisher value (none pressed: 0).
            float half = 0.5F * (0.5F + (float) stand.getAttackDamage()
                    / (8.0F - stand.getLastHeavyFinisherValue() * 4.0F));
            helper.assertTrue(standHits == 1 && pulses.size() == 3 && near(pulses.get(0), 0.4F)
                            && near(pulses.get(1), half) && near(pulses.get(2), half),
                    "1.16 StandEntity.attackTarget struck the cow with the heavy attack's punch after the blink: one"
                            + " Stand hit pushing with the native 0.4, then " + half + " on the hit and " + half
                            + " as the tail; the cow took " + standHits + " Stand hits and the pushes " + pulses);
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Object listener : listeners) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            listeners.clear();
            if (user != null) {
                for (short key : new short[] {PUNCH, BLINK, BLINK_AGAIN}) {
                    AbilityInput.keyRelease(key, user);
                }
                if (stand != null) {
                    LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
                }
                if (power != null && power.isSummoned()) {
                    ModStands.STAR_PLATINUM.get().forceUnsummon(user, power);
                }
                if (stand != null && !stand.isRemoved()) {
                    stand.discard();
                }
                user.discard();
            }
            if (target != null) {
                target.discard();
            }
        }
    }

    /**
     * Star Platinum (Reworked) in the middle of the fixture's chunk with its Stand left where it is, a cow some blocks east
     * (1.4: within the Stand's reach) and one 4.5 blocks south, which the user looks at; the user's synced aim (its client's
     * pick) is on the east cow, so the attacks are pressed at it.
     */
    private static final class BlinkFixture implements AutoCloseable {
        static final double IN_REACH_EAST = 1.4D;
        static final double OUT_OF_REACH_EAST = 5.0D;
        // within the 5 blocks the strike's ray covers at speed 1 in five ticks (1.16 standPerform :119)
        static final double FAR_SOUTH = 4.5D;
        // beyond that ray, within the blink's 10.8
        static final double BEYOND_STRIKE_RAY = 7.0D;

        final GameTestHelper helper;
        final ServerLevel level;
        final Player user;
        final StandPower power;
        final StandEntity stand;
        final Cow near;
        final Cow far;
        int farHits;
        int nearHits;
        Ability lastResolved;
        boolean lastAdmitted;
        private final Consumer<LivingIncomingDamageEvent> hits;
        private final List<Short> keys = new ArrayList<>();

        BlinkFixture(GameTestHelper helper, String name, boolean timeStopUnlocked) {
            this(helper, name, timeStopUnlocked, IN_REACH_EAST);
        }

        BlinkFixture(GameTestHelper helper, String name, boolean timeStopUnlocked, double nearEast) {
            this(helper, name, timeStopUnlocked, nearEast, FAR_SOUTH);
        }

        BlinkFixture(GameTestHelper helper, String name, boolean timeStopUnlocked, double nearEast, double farSouth) {
            this(helper, name, timeStopUnlocked, nearEast, farSouth, 2);
        }

        BlinkFixture(GameTestHelper helper, String name, boolean timeStopUnlocked, double nearEast, double farSouth,
                int resolveLevel) {
            this.helper = helper;
            level = helper.getLevel();
            Vec3 origin = chunkMiddle(helper);
            user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), name)) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Setup: the Star Platinum user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(ModStands.STAR_PLATINUM.get());
            power.setResolveLevel(resolveLevel);
            helper.assertTrue(ModStands.STAR_PLATINUM.get().summon(user, power), "Could not summon the Stand.");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.getUser() == user, "Setup: no summoned Stand");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15.0D);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16.0D);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12.0D);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8.0D);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8.0D);
            StandEntity.updateStandStatAttributes(stand, user);
            power.setStamina(power.getMaxStamina());
            stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            if (timeStopUnlocked) {
                PowerSkillUnlocks.Result unlocked =
                        ReworkedAddon.POWER_SKILL_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
                helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED
                                || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                        "Setup: could not unlock the time stop: " + unlocked);
            }
            // five blink ticks at a movement speed of 1 reach about 10.8 blocks
            user.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(1.0D);
            near = cow(origin.add(nearEast, 0.0D, 0.0D));
            far = cow(origin.add(0.0D, 0.0D, farSouth));
            user.lookAt(EntityAnchorArgument.Anchor.EYES, far.getBoundingBox().getCenter());
            user.setYHeadRot(user.getYRot());
            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(near));
            hits = event -> {
                if (event.getEntity() == far && event.getSource().getDirectEntity() == stand) {
                    farHits++;
                }
                if (event.getEntity() == near && event.getSource().getDirectEntity() == stand) {
                    nearHits++;
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, hits);
        }

        private Cow cow(Vec3 position) {
            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Setup: no cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            cow.setHealth(200.0F);
            cow.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Setup: a cow was not added");
            return cow;
        }

        // A press through the key's resolution, as ClAbilityInputPacket does it; lastResolved is what the key gave
        BufferingState press(String keyName, short key) {
            Ability base = power.getAbility(keyName);
            helper.assertTrue(base != null, "Setup: Star Platinum has no " + keyName);
            var resolved = freshMoves().getContextVariationContainer(base);
            lastResolved = resolved != null ? resolved.ability : null;
            BufferingState state = BufferingState.clickCanBuffer();
            lastAdmitted = resolved != null && AbilityInput.withConditionCheck(resolved, user, InputMethod.CLICK);
            if (lastAdmitted) {
                AbilityInput.keyPress(key, resolved.ability, user, null, InputMethod.CLICK, 0.0F, state,
                        base.getAbilityId());
            }
            keys.add(key);
            return state;
        }

        Ability resolve(String keyName) {
            var resolved = freshMoves().getContextVariationContainer(power.getAbility(keyName));
            return resolved != null ? resolved.ability : null;
        }

        // the condition the HUD draws the key's slot by
        ConditionCheck drawn(String keyName) {
            var resolved = freshMoves().getContextVariationContainer(power.getAbility(keyName));
            return resolved != null ? resolved.drawnCondition(user) : null;
        }

        // the power caches its moves within a tick, and these rows press several keys in one
        private AvailableAbilities freshMoves() {
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            return available;
        }

        void unlock(String skill) {
            PowerSkillUnlocks.Result result = ReworkedAddon.POWER_SKILL_UNLOCKS.forceUnlock(power, skill);
            helper.assertTrue(result == PowerSkillUnlocks.Result.UNLOCKED
                            || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                    "Setup: could not unlock " + skill + ": " + result);
        }

        int blinkModifiers(EntityActionInstance action) {
            return (int) action.getPunchModifiers().stream()
                    .filter(effect -> effect.effectType == ReworkedAddon.STAR_PLATINUM_BLINK_MODIFIER.get()
                            || effect.effectType == ReworkedAddon.STAR_PLATINUM_UPPERCUT_BLINK_MODIFIER.get())
                    .count();
        }

        @Override
        public void close() {
            NeoForge.EVENT_BUS.unregister(hits);
            for (short key : keys) {
                AbilityInput.keyRelease(key, user);
            }
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            if (power.isSummoned()) {
                ModStands.STAR_PLATINUM.get().forceUnsummon(user, power);
            }
            if (!stand.isRemoved()) {
                stand.discard();
            }
            user.discard();
            near.discard();
            far.discard();
        }
    }

    private static final class BlinkTaskTargetScene {
        private final GameTestHelper helper;
        private final List<String> wrong = new ArrayList<>();
        private int ticks;
        private boolean done;

        private BlinkTaskTargetScene(GameTestHelper helper) {
            this.helper = helper;
        }

        private void start() {
            helper.onEachTick(() -> {
                if (done) {
                    return;
                }
                ticks++;
                helper.assertTrue(ticks < 40, "Setup: the fixture's chunk never ticked");
                if (ticks < 3 || !helper.getLevel().isPositionEntityTicking(BlockPos.containing(chunkMiddle(helper)))) {
                    return;
                }
                done = true;
                row(false, BlinkFixture.IN_REACH_EAST);
                row(true, BlinkFixture.IN_REACH_EAST);
                row(false, BlinkFixture.OUT_OF_REACH_EAST);
                row(true, BlinkFixture.OUT_OF_REACH_EAST);
                helper.assertTrue(wrong.isEmpty(), "1.16 StarPlatinumBlinkPunch blinked by the running task's target: "
                        + wrong);
                helper.succeed();
            });
        }

        private void row(boolean heavy, double nearEast) {
            boolean inReach = nearEast == BlinkFixture.IN_REACH_EAST;
            String what = (heavy ? "a heavy punch with the blink" : "a light punch with the blink")
                    + " pressed at a cow " + nearEast + " blocks east";
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-target", true, nearEast)) {
                BufferingState first = fixture.press(heavy ? "heavy_punch" : "punch", (short) 181);
                EntityActionInstance pressed = fixture.stand.getCurStandAction();
                helper.assertTrue(first.isActionSuccess && pressed != null && pressed.getPhase() == ActionPhase.WINDUP
                                && pressed.getTaskTarget(helper.getLevel()).getEntity() == fixture.near,
                        "Setup: " + what + " was not pressed at the near cow: " + pressed);
                fixture.user.setShiftKeyDown(true);
                BufferingState blink = fixture.press("heavy_punch", (short) 182);
                fixture.user.setShiftKeyDown(false);
                EntityActionInstance running = fixture.stand.getCurStandAction();
                helper.assertTrue(blink.isActionSuccess && running == pressed && fixture.blinkModifiers(running) == 1,
                        "Setup: Shift and the heavy key did not give " + what + ": " + running);
                Vec3 before = fixture.user.position();
                running.setPhaseStart(ActionPhase.PERFORM);
                running._tickAction();
                double moved = Math.hypot(fixture.user.getX() - before.x, fixture.user.getZ() - before.z);
                double toFar = Math.hypot(fixture.user.getX() - fixture.far.getX(),
                        fixture.user.getZ() - fixture.far.getZ());
                float yaw = Mth.wrapDegrees(fixture.user.getYRot());
                ActionTarget task = running.getTaskTarget(helper.getLevel());
                boolean blinked = inReach ? moved < 0.01D && Math.abs(yaw + 90.0F) < 1.0F
                        : toFar < 3.0D && Math.abs(yaw) < 1.0F;
                if (!blinked || fixture.farHits != 1 || task.getEntity() != fixture.far) {
                    wrong.add(what + ": 1.16 blinked by the task's target, " + (inReach
                            ? "the cow its own strike took in reach, so the user stayed beside it turned to it (yaw -90)"
                            : "none since its own strike found nothing in reach, so calcBlinkPos's ray took the user to"
                                    + " the cow 4.5 blocks south, its yaw unturned (0)")
                            + ", then the cow the ray found 4.5 blocks south became the task's and took the strike; the"
                            + " user moved " + moved + " (now " + toFar + " from the far cow), faces " + yaw
                            + ", the far cow took " + fixture.farHits + " Stand hits, the task holds " + task);
                }
            }
        }
    }

    private static final class HeavyTaskBlinkScene {
        private final GameTestHelper helper;
        private final List<String> wrong = new ArrayList<>();

        private HeavyTaskBlinkScene(GameTestHelper helper) {
            this.helper = helper;
        }

        private void start() {
            onceTicking(helper, () -> {
                row(false);
                row(true);
                helper.assertTrue(wrong.isEmpty(), "1.16 ran the blink as a modifier of the heavy task: " + wrong);
            });
        }

        // The attack pressed at the near cow, 2.2 blocks east (in the Stand's reach, and far enough from the user that the
        // blink by it moves the user and so cools down), then Shift and the heavy key with the user's aim on the far cow,
        // which the user looks at
        private void row(boolean uppercut) {
            String what = uppercut ? "the uppercut" : "the heavy punch";
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-heavy-task", true, 2.2D)) {
                fixture.unlock("uppercut");
                // a meter of 0.75 puts the uppercut on the heavy key
                fixture.stand.setFinisherMeter(uppercut ? 0.75F : 0.0F);
                BufferingState first = fixture.press("heavy_punch", (short) 221);
                EntityActionInstance attack = fixture.stand.getCurStandAction();
                helper.assertTrue(first.isActionSuccess && (uppercut
                                ? attack instanceof HeavyPunchUppercutAbility.Uppercut
                                : attack instanceof StandEntityHeavyPunchAbility.StandEntityHeavyPunch)
                                && attack.getTaskTarget(helper.getLevel()).getEntity() == fixture.near,
                        "Setup: the heavy key did not start " + what + " at the near cow: " + attack);
                LivingComponentAction.getComponent(fixture.user).entityAim.setTarget(new ActionTarget(fixture.far));
                fixture.user.setShiftKeyDown(true);
                BufferingState blink = fixture.press("heavy_punch", (short) 222);
                fixture.user.setShiftKeyDown(false);
                EntityActionInstance running = fixture.stand.getCurStandAction();
                ActionTarget kept = attack.getTaskTarget(helper.getLevel());
                if (!blink.isActionSuccess || running != attack || fixture.blinkModifiers(attack) != 1
                        || kept.getEntity() != fixture.near) {
                    wrong.add("Shift and the heavy key in " + what + "'s windup, aimed at the far cow: 1.16 added the"
                            + " blink to the running task, which kept the near cow it was pressed at; the press went off "
                            + blink.isActionSuccess + ", the Stand's action " + running + " (the attack " + attack
                            + "), blink modifiers on the attack " + fixture.blinkModifiers(attack) + ", its target "
                            + kept);
                    return;
                }
                attack.setPhaseStart(ActionPhase.PERFORM);
                attack._tickAction();
                String blinkName = uppercut ? ReworkedStandAbilities.STAR_PLATINUM_BLINK_UPPERCUT_NAME
                        : ReworkedStandAbilities.STAR_PLATINUM_BLINK_HEAVY_NAME;
                // the uppercut's punch sends its target up (knockback at -60 degrees), the heavy punch's flat
                double farLift = fixture.far.getDeltaMovement().y;
                boolean lifted = uppercut ? farLift > 0.05D : Math.abs(farLift) < 0.05D;
                if (fixture.nearHits != 1 || fixture.farHits != 1 || !lifted
                        || !fixture.power.isAbilityOnCooldown(blinkName)
                        || attack.getTaskTarget(helper.getLevel()).getEntity() != fixture.far) {
                    wrong.add(what + " with the blink at its perform: 1.16 struck the near cow with the attack's own"
                            + " strike and then the far cow with the blink's " + (uppercut ? "uppercut" : "heavy punch")
                            + ", which cooled down " + blinkName + " and became the task's; the near cow took "
                            + fixture.nearHits + " Stand hits, the far cow " + fixture.farHits + " (its vertical motion "
                            + farLift + "), " + blinkName + " cools down " + fixture.power.isAbilityOnCooldown(blinkName)
                            + ", the task holds " + attack.getTaskTarget(helper.getLevel()));
                }
            }
        }
    }

    private static final class BlinkStrikeRayScene {
        private final GameTestHelper helper;
        private final List<String> wrong = new ArrayList<>();

        private BlinkStrikeRayScene(GameTestHelper helper) {
            this.helper = helper;
        }

        private void start() {
            onceTicking(helper, () -> {
                beyondTheStrikeRay();
                fromTheUserUnderManualControl();
                helper.assertTrue(wrong.isEmpty(), "1.16 StarPlatinumBlinkPunch struck what its own ray from the user "
                        + "found: " + wrong);
            });
        }

        // A light punch pressed at nothing, the blink added in its windup; the user looks at a cow 7 blocks south,
        // which the blink's 10.8 reach takes the user to and the strike's 5 do not reach.
        private void beyondTheStrikeRay() {
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-ray", true,
                    BlinkFixture.OUT_OF_REACH_EAST, BlinkFixture.BEYOND_STRIKE_RAY)) {
                EntityActionInstance punch = punchWithBlink(fixture);
                punch.setPhaseStart(ActionPhase.PERFORM);
                punch._tickAction();
                double toFar = Math.hypot(fixture.user.getX() - fixture.far.getX(),
                        fixture.user.getZ() - fixture.far.getZ());
                ActionTarget task = punch.getTaskTarget(helper.getLevel());
                if (toFar > 3.0D || fixture.farHits != 0 || !task.isEmpty(helper.getLevel())) {
                    wrong.add("the cow 7 blocks south: 1.16's blink took the user beside it (calcBlinkPos's ray, 10.8"
                            + " blocks) while the strike's ray from the user reached 5 blocks and found nothing, so the"
                            + " task's target was cleared and nothing was struck; the user stands " + toFar
                            + " from the cow, which took " + fixture.farHits + " Stand hits, the task holds " + task);
                }
            }
        }

        // The Stand manually controlled 3 blocks east of its user, both looking south, a cow 4.5 blocks south of the
        // user and none on the Stand's line: the Stand blinks, and the strike's ray from the user finds the cow.
        private void fromTheUserUnderManualControl() {
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-ray-manual", true,
                    -BlinkFixture.OUT_OF_REACH_EAST)) {
                EntityActionInstance punch = punchWithBlink(fixture);
                // the user still looks at the cow 4.5 blocks south
                Vec3 standAt = fixture.user.position().add(3.0D, 0.0D, 0.0D);
                // a floor where the Stand lands 5 blocks south, so that it lands level with the cow whatever lies below
                BlockPos floor = BlockPos.containing(standAt.add(0.0D, -1.0D, 5.0D));
                BlockState floorWas = helper.getLevel().getBlockState(floor);
                helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                try {
                    blinkTheManuallyControlledStand(fixture, punch, standAt);
                }
                finally {
                    helper.getLevel().setBlockAndUpdate(floor, floorWas);
                }
            }
        }

        private void blinkTheManuallyControlledStand(BlinkFixture fixture, EntityActionInstance punch, Vec3 standAt) {
            fixture.stand.setManuallyControlled(true);
            fixture.stand.moveTo(standAt.x, standAt.y, standAt.z, 0.0F, 0.0F);
            fixture.stand.setYHeadRot(0.0F);
            helper.assertTrue(fixture.stand.isManuallyControlled() && fixture.stand.getCurStandAction() == punch,
                    "Setup: the Stand is not manually controlled with the punch running");
            Vec3 userBefore = fixture.user.position();
            ActionTarget userRay = HitResultUtil.clip(fixture.user.getEyePosition(), fixture.user.getLookAngle(),
                    5.0D, 5.0D, helper.getLevel(), entity -> entity instanceof LivingEntity
                            && entity != fixture.user && entity != fixture.stand, fixture.user, 0.0D);
            punch.setPhaseStart(ActionPhase.PERFORM);
            punch._tickAction();
            double userMoved = fixture.user.position().distanceTo(userBefore);
            double standMoved = Math.hypot(fixture.stand.getX() - standAt.x, fixture.stand.getZ() - standAt.z);
            ActionTarget task = punch.getTaskTarget(helper.getLevel());
            if (userMoved > 0.01D || standMoved < 3.0D || task.getEntity() != fixture.far) {
                wrong.add("the Stand manually controlled 3 blocks east: 1.16 blinked the Stand and cast the strike's"
                        + " ray from the user, which found the cow 4.5 blocks south and made it the task's; the user"
                        + " moved " + userMoved + ", the Stand " + standMoved + ", the task holds " + task
                        + " (the user's 5-block ray meets " + userRay + ", the Stand stands at "
                        + fixture.stand.position().subtract(userBefore) + ", the cow at "
                        + fixture.far.position().subtract(userBefore) + ", the punch struck " + punch.punchedTarget
                        + ")");
            }
        }

        // The punch pressed at nothing; the blink added in its windup through the key's resolution
        private EntityActionInstance punchWithBlink(BlinkFixture fixture) {
            LivingComponentAction.getComponent(fixture.user).entityAim.setTarget(ActionTarget.EMPTY);
            BufferingState first = fixture.press("punch", (short) 211);
            EntityActionInstance punch = fixture.stand.getCurStandAction();
            helper.assertTrue(first.isActionSuccess && punch != null && punch.getPhase() == ActionPhase.WINDUP,
                    "Setup: the punch did not start: " + punch);
            fixture.user.setShiftKeyDown(true);
            BufferingState blink = fixture.press("heavy_punch", (short) 212);
            fixture.user.setShiftKeyDown(false);
            helper.assertTrue(blink.isActionSuccess && fixture.stand.getCurStandAction() == punch
                            && fixture.blinkModifiers(punch) == 1,
                    "Setup: Shift and the heavy key did not add the blink to the punch: "
                            + fixture.stand.getCurStandAction());
            return punch;
        }
    }

    private static final class BlinkKeptTargetScene {
        private final GameTestHelper helper;
        private final List<String> wrong = new ArrayList<>();

        private BlinkKeptTargetScene(GameTestHelper helper) {
            this.helper = helper;
        }

        private void start() {
            onceTicking(helper, () -> {
                keptByTheRangeFromWhereTheStandStood();
                refusedLeavesTheTasksOwnTargetToBeStruck();
                helper.assertTrue(wrong.isEmpty(), "1.16 StarPlatinumBlinkPunch offered its ray's target to the task "
                        + "and struck what the task held: " + wrong);
            });
        }

        // The Stand stands 7 blocks west and 3 south of its user, a heavy punch pressed at the near cow 2 blocks west of
        // the Stand (in its reach), the user looking at the far cow 4.5 blocks south: the blink takes the user beside the
        // near cow; the far cow is 7.2 blocks from where the Stand stood, over 9 from beside the user it follows.
        private void keptByTheRangeFromWhereTheStandStood() {
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-kept", true)) {
                Vec3 origin = fixture.user.position();
                fixture.stand.moveTo(origin.x - 7.0D, origin.y, origin.z + 3.0D, 0.0F, 0.0F);
                fixture.near.moveTo(origin.x - 9.0D, origin.y, origin.z + 3.0D, 0.0F, 0.0F);
                EntityActionInstance heavy = heavyWithBlink(fixture, "the near cow 2 blocks west of the Stand");
                if (heavy == null) {
                    return;
                }
                // the Stand follows its user again, as in play
                fixture.stand.setCanFollowUser(true);
                Vec3 stoodAt = fixture.stand.position();
                heavy.setPhaseStart(ActionPhase.PERFORM);
                heavy._tickAction();
                if (fixture.stand.position().distanceToSqr(stoodAt) > 1.0E-6D) {
                    wrong.add("1.16's blink struck from where the Stand stood, which followed its user on its next tick;"
                            + " the Stand was at " + stoodAt.subtract(origin) + " and is at "
                            + fixture.stand.position().subtract(origin) + " after the blink's strike");
                }
                ActionTarget task = heavy.getTaskTarget(helper.getLevel());
                if (task.getEntity() != fixture.far || fixture.farHits != 1 || fixture.nearHits != 1) {
                    wrong.add("the Stand 7 blocks west of its user: 1.16 struck the near cow with the heavy punch's own"
                            + " strike, then the task kept the far cow its ray found, 7.2 blocks from where the Stand"
                            + " stood, and the blink struck it; the task holds " + task + ", the far cow took "
                            + fixture.farHits + " Stand hits, the near cow " + fixture.nearHits + " (the Stand stands at "
                            + fixture.stand.position().subtract(origin) + ", the user at "
                            + fixture.user.position().subtract(origin) + ")");
                }
            }
        }

        // The Stand stands 7 blocks west and 3 north of its user, a heavy punch pressed at the near cow 2 blocks west of
        // the Stand, the user looking at the far cow 4.5 blocks south: the ray finds the far cow, 10 blocks from the
        // Stand, which the heavy punch's task does not keep, so the blink strikes the task's own near cow (which its
        // Stand immunity from the punch's own strike leaves unhurt) and never the far cow.
        private void refusedLeavesTheTasksOwnTargetToBeStruck() {
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-refused", true)) {
                Vec3 origin = fixture.user.position();
                fixture.stand.moveTo(origin.x - 7.0D, origin.y, origin.z - 3.0D, 0.0F, 0.0F);
                fixture.near.moveTo(origin.x - 9.0D, origin.y, origin.z - 3.0D, 0.0F, 0.0F);
                EntityActionInstance heavy = heavyWithBlink(fixture, "the near cow 2 blocks west of the Stand");
                if (heavy == null) {
                    return;
                }
                heavy.setPhaseStart(ActionPhase.PERFORM);
                heavy._tickAction();
                ActionTarget task = heavy.getTaskTarget(helper.getLevel());
                if (task.getEntity() != fixture.near || fixture.farHits != 0 || fixture.nearHits < 1) {
                    wrong.add("the far cow 10 blocks from the Stand: 1.16's ray found it, the heavy punch's task did not"
                            + " keep it and held the near cow, and the blink struck the near cow, not the far one; the"
                            + " task holds " + task + ", the far cow took " + fixture.farHits + " Stand hits, the near cow "
                            + fixture.nearHits + " (the Stand stands at " + fixture.stand.position().subtract(origin)
                            + ")");
                }
            }
        }

        // The heavy punch pressed at the near cow, then Shift and the heavy key in its windup; null when that failed
        private EntityActionInstance heavyWithBlink(BlinkFixture fixture, String what) {
            BufferingState first = fixture.press("heavy_punch", (short) 231);
            EntityActionInstance heavy = fixture.stand.getCurStandAction();
            if (!first.isActionSuccess || !(heavy instanceof StandEntityHeavyPunchAbility.StandEntityHeavyPunch)
                    || heavy.getTaskTarget(helper.getLevel()).getEntity() != fixture.near) {
                wrong.add("Setup: the heavy key did not start the heavy punch at " + what + ": " + heavy);
                return null;
            }
            fixture.user.setShiftKeyDown(true);
            BufferingState blink = fixture.press("heavy_punch", (short) 232);
            fixture.user.setShiftKeyDown(false);
            if (!blink.isActionSuccess || fixture.stand.getCurStandAction() != heavy
                    || fixture.blinkModifiers(heavy) != 1) {
                wrong.add("Setup: Shift and the heavy key did not add the blink to the heavy punch at " + what + ": "
                        + fixture.stand.getCurStandAction());
                return null;
            }
            return heavy;
        }
    }

    private static final class BlinkGrassScene {
        private final GameTestHelper helper;

        private BlinkGrassScene(GameTestHelper helper) {
            this.helper = helper;
        }

        // A punch pressed at nothing with the blink, the user looking west past a short grass 3 blocks away at a stone
        // wall 6 blocks away: the blink ray stops at the grass, so the user lands in the grass's column.
        private void start() {
            onceTicking(helper, () -> {
                try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-grass", true)) {
                    // nothing for the Stand's punch or the blink's ray to find but the blocks
                    fixture.near.discard();
                    fixture.far.discard();
                    LivingComponentAction.getComponent(fixture.user).entityAim.setTarget(ActionTarget.EMPTY);
                    fixture.user.setYRot(90.0F);
                    fixture.user.setXRot(0.0F);
                    fixture.user.setYHeadRot(90.0F);
                    BufferingState first = fixture.press("punch", (short) 251);
                    EntityActionInstance punch = fixture.stand.getCurStandAction();
                    helper.assertTrue(first.isActionSuccess && punch != null && punch.getPhase() == ActionPhase.WINDUP,
                            "Setup: the punch did not start: " + punch);
                    fixture.user.setShiftKeyDown(true);
                    BufferingState blink = fixture.press("heavy_punch", (short) 252);
                    fixture.user.setShiftKeyDown(false);
                    helper.assertTrue(blink.isActionSuccess && fixture.stand.getCurStandAction() == punch
                                    && fixture.blinkModifiers(punch) == 1,
                            "Setup: Shift and the heavy key did not add the blink to the punch: "
                                    + fixture.stand.getCurStandAction());
                    Vec3 userAt = fixture.user.position();
                    BlockPos grass = BlockPos.containing(userAt.x - 3.0D, fixture.user.getEyeY(), userAt.z);
                    BlockPos wall = grass.west(3);
                    BlockState grassWas = helper.getLevel().getBlockState(grass);
                    BlockState wallWas = helper.getLevel().getBlockState(wall);
                    helper.getLevel().setBlockAndUpdate(grass, Blocks.SHORT_GRASS.defaultBlockState());
                    helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                    try {
                        punch.setPhaseStart(ActionPhase.PERFORM);
                        punch._tickAction();
                        double west = userAt.x - fixture.user.getX();
                        helper.assertTrue(west > 2.0D && west < 4.0D,
                                "1.16's OUTLINE blink ray met the short grass 3 blocks west and the user landed in its"
                                        + " column, not at the wall 6 blocks west; the user moved " + west
                                        + " blocks west, now at " + fixture.user.position().subtract(userAt));
                    }
                    finally {
                        helper.getLevel().setBlockAndUpdate(grass, grassWas);
                        helper.getLevel().setBlockAndUpdate(wall, wallWas);
                    }
                }
            });
        }
    }

    private static final class BlinkManualRayScene {
        private final GameTestHelper helper;

        private BlinkManualRayScene(GameTestHelper helper) {
            this.helper = helper;
        }

        // A light punch pressed at nothing with the blink, the Stand manually controlled 3 blocks east of its user and
        // looking west at it: the Stand's own ray meets the user, so the Stand lands beside the user, east of it.
        private void start() {
            onceTicking(helper, () -> {
                try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-manual-user", true, -6.0D)) {
                    LivingComponentAction.getComponent(fixture.user).entityAim.setTarget(ActionTarget.EMPTY);
                    BufferingState first = fixture.press("punch", (short) 241);
                    EntityActionInstance punch = fixture.stand.getCurStandAction();
                    helper.assertTrue(first.isActionSuccess && punch != null && punch.getPhase() == ActionPhase.WINDUP,
                            "Setup: the punch did not start: " + punch);
                    fixture.user.setShiftKeyDown(true);
                    BufferingState blink = fixture.press("heavy_punch", (short) 242);
                    fixture.user.setShiftKeyDown(false);
                    helper.assertTrue(blink.isActionSuccess && fixture.stand.getCurStandAction() == punch
                                    && fixture.blinkModifiers(punch) == 1,
                            "Setup: Shift and the heavy key did not add the blink to the punch: "
                                    + fixture.stand.getCurStandAction());
                    Vec3 userAt = fixture.user.position();
                    fixture.stand.setManuallyControlled(true);
                    fixture.stand.moveTo(userAt.x + 3.0D, userAt.y, userAt.z, 90.0F, 0.0F);
                    fixture.stand.setYHeadRot(90.0F);
                    helper.assertTrue(fixture.stand.isManuallyControlled() && fixture.stand.getCurStandAction() == punch
                                    && punch.getTaskTarget(helper.getLevel()).isEmpty(helper.getLevel()),
                            "Setup: the Stand is not manually controlled with the punch running at nothing");
                    // a floor where the Stand lands beside its user, whatever lies below
                    BlockPos floor = BlockPos.containing(userAt.add(1.0D, -1.0D, 0.0D));
                    BlockState floorWas = helper.getLevel().getBlockState(floor);
                    helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                    try {
                        punch.setPhaseStart(ActionPhase.PERFORM);
                        punch._tickAction();
                        double east = fixture.stand.getX() - userAt.x;
                        double across = Math.abs(fixture.stand.getZ() - userAt.z);
                        double userMoved = fixture.user.position().distanceTo(userAt);
                        helper.assertTrue(east > 0.3D && east < 2.0D && across < 0.5D && userMoved < 0.01D,
                                "1.16's blink ray from the manually controlled Stand met its user 3 blocks west, so the"
                                        + " Stand blinked beside the user, east of it; the Stand stands "
                                        + fixture.stand.position().subtract(userAt) + " from the user, who moved "
                                        + userMoved);
                    }
                    finally {
                        helper.getLevel().setBlockAndUpdate(floor, floorWas);
                    }
                }
            });
        }
    }

    // Runs the rows once the fixture's chunk ticks, then succeeds
    private static void onceTicking(GameTestHelper helper, Runnable rows) {
        int[] ticks = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            ticks[0]++;
            helper.assertTrue(ticks[0] < 40, "Setup: the fixture's chunk never ticked");
            if (ticks[0] < 3 || !helper.getLevel().isPositionEntityTicking(BlockPos.containing(chunkMiddle(helper)))) {
                return;
            }
            done[0] = true;
            rows.run();
            helper.succeed();
        });
    }

    private static final class BlinkLockedScene {
        private final GameTestHelper helper;
        private final List<String> wrong = new ArrayList<>();

        private BlinkLockedScene(GameTestHelper helper) {
            this.helper = helper;
        }

        private void start() {
            onceTicking(helper, () -> {
                row("punch", 0.0F);
                row("heavy_punch", 0.0F);
                row("heavy_punch", 0.75F);
                helper.assertTrue(wrong.isEmpty(), "1.16 kept the blink off the heavy key until its time stop was "
                        + "unlocked: " + wrong);
            });
        }

        // The attack pressed at the near cow, the meter set, then Shift and the heavy key in the attack's windup
        private void row(String attack, float meter) {
            String what = "Shift and the heavy key in the " + attack + "'s windup at a finisher meter of " + meter
                    + ", the time stop locked";
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-locked", false)) {
                // the uppercut is the heavy key's finisher variation at a meter of a half or more
                fixture.unlock("uppercut");
                BufferingState first = fixture.press(attack, (short) 191);
                EntityActionInstance pressed = fixture.stand.getCurStandAction();
                helper.assertTrue(first.isActionSuccess && pressed != null && pressed.getPhase() == ActionPhase.WINDUP,
                        "Setup: the " + attack + " did not start: " + pressed);
                fixture.stand.setFinisherMeter(meter);
                if (meter >= 0.5F) {
                    Ability resolvedKey = fixture.resolve("heavy_punch");
                    helper.assertTrue(resolvedKey != null && "finisher_uppercut".equals(resolvedKey.name()),
                            "Setup: without Shift the heavy key does not reach the uppercut at a meter of " + meter
                                    + ": " + (resolvedKey != null ? resolvedKey.name() : "nothing"));
                }
                Vec3 before = fixture.user.position();
                fixture.user.setShiftKeyDown(true);
                BufferingState again = fixture.press("heavy_punch", (short) 192);
                fixture.user.setShiftKeyDown(false);
                EntityActionInstance running = fixture.stand.getCurStandAction();
                String resolved = fixture.lastResolved != null ? fixture.lastResolved.name() : "nothing";
                boolean blinkStarted = running instanceof ReworkedStandAbilities.BlinkHeavyPunch
                        || running instanceof ReworkedStandAbilities.BlinkUppercut
                        || running != null && fixture.blinkModifiers(running) > 0;
                // 1.16 StandEntityAction.canClickDuringTask (:495-497, asked by checkTaskCancelling :127-132): the
                // heavy punch pressed in its own windup was refused, silently and unqueued (a heavy attack is not
                // chainable); the key was the heavy punch, not the blink, with the time stop locked
                boolean heavyOnHeavy = "heavy_punch".equals(attack);
                if (heavyOnHeavy) {
                    if (!"heavy_punch".equals(resolved) || blinkStarted || fixture.lastAdmitted
                            || again.isActionSuccess || again.shouldBuffer || running != pressed || running.isOver()) {
                        wrong.add(what + ": 1.16 refused the heavy punch pressed in its own windup, silently and"
                                + " unbuffered; the key gave " + resolved + ", the press was admitted "
                                + fixture.lastAdmitted + ", went off " + again.isActionSuccess + ", buffered "
                                + again.shouldBuffer + ", the Stand's action " + running + " (the heavy punch "
                                + pressed + ")" + (running != null
                                        ? " with " + fixture.blinkModifiers(running) + " blink modifiers" : ""));
                    }
                }
                else if (!"heavy_punch".equals(resolved) || blinkStarted
                        || !(again.isActionSuccess || again.shouldBuffer)) {
                    wrong.add(what + ": 1.16 gave the key the heavy punch, which went on as a heavy punch; the key "
                            + "gave " + resolved + ", the press went off " + again.isActionSuccess + ", buffered "
                            + again.shouldBuffer + ", the Stand's action " + running + (running != null
                                    ? " with " + fixture.blinkModifiers(running) + " blink modifiers" : ""));
                }
                if (running != null) {
                    running.setPhaseStart(ActionPhase.PERFORM);
                    running._tickAction();
                }
                if (heavyOnHeavy) {
                    // nothing waits behind the punch: once it is over the input buffer starts no second heavy punch
                    LivingComponentAction.getComponent(fixture.stand).setAction(null, SyncType.NO_SYNC);
                    fixture.user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).tick();
                    EntityActionInstance after = fixture.stand.getCurStandAction();
                    if (after != null) {
                        wrong.add(what + ": 1.16 queued nothing behind the refused heavy punch; after the punch the"
                                + " Stand started " + after);
                    }
                }
                double moved = Math.hypot(fixture.user.getX() - before.x, fixture.user.getZ() - before.z);
                if (moved > 0.01D || fixture.power.isAbilityOnCooldown(TimeStopCooldowns.TIME_STOP_BLINK)) {
                    wrong.add(what + ": 1.16 did not blink; the user moved " + moved + ", the time-stop blink cools "
                            + "down " + fixture.power.isAbilityOnCooldown(TimeStopCooldowns.TIME_STOP_BLINK));
                }
            }
        }
    }

    private static final class BlinkClientSentScene {
        private final GameTestHelper helper;
        private final List<String> wrong = new ArrayList<>();

        private BlinkClientSentScene(GameTestHelper helper) {
            this.helper = helper;
        }

        private void start() {
            onceTicking(helper, () -> {
                row("heavy_punch");
                row("grab");
                helper.assertTrue(wrong.isEmpty(), "1.16 gave Shift and the heavy key the blink whatever ability the "
                        + "client requested: " + wrong);
            });
        }

        private void row(String sent) {
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-sent", true)) {
                BufferingState first = fixture.press("heavy_punch", (short) 211);
                EntityActionInstance attack = fixture.stand.getCurStandAction();
                helper.assertTrue(first.isActionSuccess
                                && attack instanceof StandEntityHeavyPunchAbility.StandEntityHeavyPunch
                                && attack.getPhase() == ActionPhase.WINDUP,
                        "Setup: the heavy key did not start a heavy punch in its windup: " + attack);
                fixture.user.setShiftKeyDown(true);
                Ability resolved = fixture.resolve("heavy_punch");
                helper.assertTrue(resolved != null
                                && ReworkedStandAbilities.STAR_PLATINUM_BLINK_HEAVY_NAME.equals(resolved.name()),
                        "Setup: the server did not resolve the heavy key to the blink: "
                                + (resolved != null ? resolved.name() : "nothing"));
                Ability requested = fixture.power.getAbility(sent);
                helper.assertTrue(requested != null, "Setup: Star Platinum has no " + sent);
                // ClAbilityInputPacket.resolveServerInputAbility: the requested ability, checked on its own
                var container = fixture.power.updateAvailableMoves().getCheckedContainerFor(fixture.power, requested);
                boolean admitted = AbilityInput.withConditionCheck(container, fixture.user, InputMethod.CLICK);
                BufferingState state = BufferingState.clickCanBuffer();
                if (admitted) {
                    AbilityInput.keyPress((short) 212, container.ability, fixture.user, null, InputMethod.CLICK, 0.0F,
                            state, requested.getAbilityId());
                }
                fixture.user.setShiftKeyDown(false);
                EntityActionInstance now = fixture.stand.getCurStandAction();
                if (!admitted || !state.isActionSuccess || now != attack || fixture.blinkModifiers(attack) != 1) {
                    wrong.add("the client requested " + sent + " in the heavy punch's windup with Shift held: 1.16 gave "
                            + "the blink; the press was admitted " + admitted + ", went off " + state.isActionSuccess
                            + ", the Stand's action " + now + " (the heavy punch " + attack + ") carries "
                            + fixture.blinkModifiers(attack) + " blink modifiers");
                }
                AbilityInput.keyRelease((short) 212, fixture.user);
            }
        }
    }

    private static final class BlinkGrabSlotScene {
        private final GameTestHelper helper;
        private final List<String> wrong = new ArrayList<>();

        private BlinkGrabSlotScene(GameTestHelper helper) {
            this.helper = helper;
        }

        private void start() {
            onceTicking(helper, () -> {
                for (boolean clientCopy : new boolean[] {false, true}) {
                    row("heavy_punch", clientCopy, true, 2);
                    // before the uppercut unlocks only the heavy key's own resolution reaches the blink
                    row("heavy_punch", clientCopy, true, 0);
                    row("punch", clientCopy, true, 2);
                    row("finisher_uppercut", clientCopy, true, 2);
                    row("heavy_punch", clientCopy, false, 2);
                }
                pressGrabInTheWindup();
                helper.assertTrue(wrong.isEmpty(), "1.16 made Shift and the heavy key the blink in a running task's "
                        + "windup; the port's Shift+RMB grab click slot: " + wrong);
            });
        }

        private static String name(Ability ability) {
            return ability != null ? ability.name() : "nothing";
        }

        private static String drawnName(ConditionCheck check) {
            return check == null ? "nothing" : check == ConditionCheck.NEGATIVE_QUEUED ? "queued (dimmed)"
                    : check.isPositive() ? "usable" : "refused";
        }

        // The attack set on the Stand as the server holds it (the user's own ability) or as its user's client holds it
        // (the power type's base-moveset one), then the grab click's slot resolved with Shift in its windup and out of it
        private void row(String attack, boolean clientCopy, boolean timeStopUnlocked, int resolveLevel) {
            String what = "the " + attack + " as " + (clientCopy ? "its user's client" : "the server") + " holds it"
                    + " at Resolve " + resolveLevel + (timeStopUnlocked ? "" : ", the time stop locked");
            boolean uppercut = "finisher_uppercut".equals(attack);
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-grab", timeStopUnlocked,
                    BlinkFixture.IN_REACH_EAST, BlinkFixture.FAR_SOUTH, resolveLevel)) {
                // the uppercut is the heavy key's finisher variation at a meter of a half or more
                if (uppercut) {
                    fixture.unlock("uppercut");
                }
                Ability finisher = fixture.power.getAbility("finisher_uppercut");
                helper.assertTrue(resolveLevel > 0 || finisher == null || !finisher.isAbilityAvailable(fixture.power),
                        "Setup: the uppercut is available at Resolve " + resolveLevel);
                helper.assertTrue("grab".equals(name(fixture.resolve("grab"))),
                        "Setup: the idle Stand's grab click resolves to " + name(fixture.resolve("grab")));
                Ability own = fixture.power.getAbility(attack);
                Ability of = clientCopy ? fixture.power.getPowerType().getBaseMoveset().getAbility(attack) : own;
                helper.assertTrue(own instanceof EntityActionAbility && of instanceof EntityActionAbility
                                && (!clientCopy || of != own && of.getAbilityId().equals(own.getAbilityId())),
                        "Setup: the power type's base-moveset " + attack + " is not another instance of the user's own: "
                                + of);
                EntityActionInstance action = ((EntityActionAbility) of).initActionOnAbilityUse(
                        helper.getLevel(), fixture.user, fixture.stand, null);
                LivingComponentAction.getComponent(fixture.stand).setAction(action, fixture.user, SyncType.NO_SYNC);
                helper.assertTrue(fixture.stand.getCurStandAction() == action && action.ability == of
                                && action.getPhase() == ActionPhase.WINDUP,
                        "Setup: " + what + " is not the Stand's action in its windup: " + action);
                fixture.stand.setFinisherMeter(uppercut ? 0.75F : 0.0F);
                String blink = uppercut
                        ? ReworkedStandAbilities.STAR_PLATINUM_BLINK_UPPERCUT_NAME
                        : ReworkedStandAbilities.STAR_PLATINUM_BLINK_HEAVY_NAME;
                fixture.user.setShiftKeyDown(true);
                String grab = name(fixture.resolve("grab"));
                String heavy = name(fixture.resolve("heavy_punch"));
                ConditionCheck grabDrawn = fixture.drawn("grab");
                ConditionCheck heavyDrawn = fixture.drawn("heavy_punch");
                fixture.user.setShiftKeyDown(false);
                String unshifted = name(fixture.resolve("grab"));
                // 1.16's blink was a StandEntityActionModifier (a StandAction, never queued behind the task): its
                // slot was drawn by its own requirements, not dimmed as queued
                if (timeStopUnlocked && (grabDrawn != ConditionCheck.POSITIVE
                        || heavyDrawn != ConditionCheck.POSITIVE)) {
                    wrong.add(what + ", Shift held in its windup: 1.16 drew the blink's slot usable, not queued; the "
                            + "grab click's slot is drawn " + drawnName(grabDrawn) + ", the heavy key's "
                            + drawnName(heavyDrawn));
                }
                if (timeStopUnlocked ? !blink.equals(grab) || !blink.equals(heavy) : !"grab".equals(grab)) {
                    wrong.add(what + ", Shift held in its windup: " + (timeStopUnlocked
                            ? "the server took the grab click as the blink " + blink + ", so the slot shows it"
                            : "1.16 kept a locked blink off the key, the grab click stays the grab")
                            + "; the grab click resolves to " + grab + ", the heavy key to " + heavy);
                }
                if (!"grab".equals(unshifted)) {
                    wrong.add(what + ", no Shift in its windup: the grab click resolves to " + unshifted);
                }
                fixture.user.setShiftKeyDown(true);
                action.setPhaseStart(ActionPhase.RECOVERY);
                String recovery = name(fixture.resolve("grab"));
                LivingComponentAction.getComponent(fixture.stand).setAction(null, fixture.user, SyncType.NO_SYNC);
                String idle = name(fixture.resolve("grab"));
                fixture.user.setShiftKeyDown(false);
                if (!"grab".equals(recovery) || !"grab".equals(idle)) {
                    wrong.add(what + ", Shift held outside its windup: 1.16 gave the blink only in a windup; the grab "
                            + "click resolves to " + recovery + " in its recovery and to " + idle + " with no action");
                }
            }
        }

        // A real press of the grab click in a heavy punch's windup, through the key's resolution
        private void pressGrabInTheWindup() {
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-grab-press", true)) {
                BufferingState first = fixture.press("heavy_punch", (short) 221);
                EntityActionInstance attack = fixture.stand.getCurStandAction();
                helper.assertTrue(first.isActionSuccess
                                && attack instanceof StandEntityHeavyPunchAbility.StandEntityHeavyPunch
                                && attack.getPhase() == ActionPhase.WINDUP,
                        "Setup: the heavy key did not start a heavy punch in its windup: " + attack);
                fixture.user.setShiftKeyDown(true);
                BufferingState grab = fixture.press("grab", (short) 222);
                fixture.user.setShiftKeyDown(false);
                String resolved = name(fixture.lastResolved);
                if (!ReworkedStandAbilities.STAR_PLATINUM_BLINK_HEAVY_NAME.equals(resolved) || !fixture.lastAdmitted
                        || !grab.isActionSuccess || fixture.stand.getCurStandAction() != attack
                        || fixture.blinkModifiers(attack) != 1) {
                    wrong.add("the grab click pressed with Shift in the heavy punch's windup: the key resolved to "
                            + resolved + ", admitted " + fixture.lastAdmitted + ", went off " + grab.isActionSuccess
                            + ", the Stand's action " + fixture.stand.getCurStandAction() + " (the heavy punch "
                            + attack + ") carries " + fixture.blinkModifiers(attack) + " blink modifiers");
                }
            }
        }
    }

    private static final class SlowBurnScene {
        private static final short KEY = 231;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<String> wrong = new ArrayList<>();

        private SlowBurnScene(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
        }

        private void start() {
            onceTicking(helper, () -> {
                for (String attack : List.of("punch", "heavy_punch", "kick")) {
                    row(attack, true, -1);
                    row(attack, false, -1);
                }
                // a target already burning at 2: the punch adds 1, the heavy punch 4
                row("punch", true, 2);
                row("heavy_punch", true, 2);
                helper.assertTrue(wrong.isEmpty(), "1.16 gave the Reworked Magician's Red's Slow Burn to a living task "
                        + "target, hurt or not: " + wrong);
            });
        }

        // Magician's Red (Reworked) facing south at a player 1.5 blocks away with a raised shield, or at a boat with a
        // cow beside it; the attack pressed at it and struck at once
        private void row(String attack, boolean shielding, int burningAt) {
            String what = "the " + attack + " at " + (shielding ? "a player behind a raised shield" : "a boat")
                    + (burningAt >= 0 ? " with Slow Burn " + burningAt : "");
            Vec3 origin = chunkMiddle(helper);
            List<Entity> spawned = new ArrayList<>();
            Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "rejojo-mr-burn")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            StandPower power = PowerClass.STAND.attachGet(user);
            try {
                user.setNoGravity(true);
                user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
                helper.assertTrue(level.addFreshEntity(user), "Setup: the Magician's Red user was not added");
                spawned.add(user);
                power.setStand(ModStands.MAGICIANS_RED.get());
                helper.assertTrue(ModStands.MAGICIANS_RED.get().summon(user, power), "Setup: could not summon");
                StandEntity stand = power.getSummonedStandEntity();
                helper.assertTrue(stand != null && stand.getUser() == user, "Setup: no summoned Stand");
                stand.summonLockTicks = 0;
                stand.gradualSummonWeaknessTicks = 0;
                stand.setCanFollowUser(false);
                stand.setNoGravity(true);
                stand.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
                stand.setYHeadRot(0.0F);
                user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15.0D);
                user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16.0D);
                user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8.0D);
                user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8.0D);
                StandEntity.updateStandStatAttributes(stand, user);
                power.setStamina(power.getMaxStamina());
                stand.updateStrengthMultipliers();
                user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;

                Vec3 at = origin.add(0.0D, 0.0D, 1.5D);
                Entity target;
                Cow bystander = null;
                if (shielding) {
                    Player blocker = new Player(level, BlockPos.ZERO, 0.0F,
                            new GameProfile(UUID.randomUUID(), "rejojo-mr-burn-shield")) {
                        @Override
                        public boolean isSpectator() {
                            return false;
                        }

                        @Override
                        public boolean isCreative() {
                            return false;
                        }

                        // the shield is up past its first 5 ticks of use, which nothing ticks here
                        @Override
                        public boolean isBlocking() {
                            return isUsingItem();
                        }
                    };
                    blocker.setNoGravity(true);
                    blocker.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
                    blocker.setYHeadRot(180.0F);
                    blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                    helper.assertTrue(level.addFreshEntity(blocker), "Setup: the shielding player was not added");
                    spawned.add(blocker);
                    blocker.startUsingItem(InteractionHand.OFF_HAND);
                    if (burningAt >= 0) {
                        blocker.addEffect(new MobEffectInstance(ReworkedAddon.SLOW_BURN, 100, burningAt, false, false,
                                true));
                    }
                    target = blocker;
                }
                else {
                    Boat boat = EntityType.BOAT.create(level);
                    helper.assertTrue(boat != null, "Setup: no boat");
                    boat.setNoGravity(true);
                    boat.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                    helper.assertTrue(level.addFreshEntity(boat), "Setup: the boat was not added");
                    spawned.add(boat);
                    target = boat;
                    bystander = EntityType.COW.create(level);
                    helper.assertTrue(bystander != null, "Setup: no cow");
                    bystander.setNoAi(true);
                    bystander.setNoGravity(true);
                    bystander.moveTo(at.x + 2.0D, at.y, at.z, 0.0F, 0.0F);
                    helper.assertTrue(level.addFreshEntity(bystander), "Setup: the cow was not added");
                    spawned.add(bystander);
                }
                ActionTarget aim = new ActionTarget(target);
                LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
                LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);
                user.lookAt(EntityAnchorArgument.Anchor.EYES, aim.getCenterPos());
                user.setYHeadRot(user.getYRot());

                // the kick is the heavy key's finisher variation at a meter of a half or more
                boolean kick = "kick".equals(attack);
                stand.setFinisherMeter(kick ? 0.75F : 0.0F);
                Ability base = power.getAbility(kick ? "heavy_punch" : attack);
                AvailableAbilities moves = new AvailableAbilities();
                moves.update(power, power.getMoveset());
                var resolved = moves.getContextVariationContainer(base);
                helper.assertTrue(resolved != null && attack.equals(resolved.ability.name())
                                && AbilityInput.withConditionCheck(resolved, user, InputMethod.CLICK),
                        "Setup: the key did not give the Reworked " + attack + ": "
                                + (resolved != null ? resolved.ability.name() : "nothing"));
                BufferingState state = BufferingState.clickCanBuffer();
                AbilityInput.keyPress(KEY, resolved.ability, user, null, InputMethod.CLICK, 0.0F, state,
                        base.getAbilityId());
                EntityActionInstance action = stand.getCurStandAction();
                helper.assertTrue(state.isActionSuccess && action != null && action.getPhase() == ActionPhase.WINDUP
                                && (!shielding || action.getTaskTarget(level).getEntity() == target),
                        "Setup: " + what + " did not start at it: " + action
                                + (action != null ? ", task target " + action.getTaskTarget(level) : ""));
                float health = target instanceof LivingEntity living ? living.getHealth() : 0.0F;
                action.setPhaseStart(ActionPhase.PERFORM);
                action._tickAction();
                AbilityInput.keyRelease(KEY, user);

                if (target instanceof LivingEntity living) {
                    helper.assertTrue(living.getHealth() == health && living.isBlocking(),
                            "Setup: the shield did not take " + what + ": health " + health + " -> "
                                    + living.getHealth() + ", blocking " + living.isBlocking());
                    MobEffectInstance burn = living.getEffect(ReworkedAddon.SLOW_BURN);
                    // the amplifier a target without Slow Burn gets: 1.16 ReMagiciansRedLightAttack :52 (0),
                    // ReMagiciansRedHeavyPunch :51 (4), ReMagiciansRedKick :64 (primed: 15 s at 6)
                    int stacked = burningAt >= 0 ? burningAt : 0;
                    boolean right = burn != null && switch (attack) {
                        case "punch" -> burn.getAmplifier() == (burningAt >= 0 ? stacked + 1 : 0)
                                && burn.getDuration() <= 200 && burn.getDuration() > 100;
                        case "heavy_punch" -> burn.getAmplifier() == stacked + 4
                                && burn.getDuration() <= 200 && burn.getDuration() > 100;
                        case "kick" -> burn.getAmplifier() == 6 && burn.getDuration() > 200;
                        default -> false;
                    };
                    if (!right) {
                        int expected = kick ? 6 : "punch".equals(attack) ? (burningAt >= 0 ? stacked + 1 : 0)
                                : stacked + 4;
                        wrong.add(what + ": 1.16 " + (kick ? "primed" : "added") + " Slow Burn at amplifier "
                                + expected + " though the hurt was blocked; the player has "
                                + (burn != null ? "amplifier " + burn.getAmplifier() + " for " + burn.getDuration()
                                        + " ticks" : "none"));
                    }
                }
                else {
                    Boat boat = (Boat) target;
                    helper.assertTrue(boat.getHurtTime() > 0 || boat.isRemoved(),
                            "Setup: " + what + " did not strike it");
                    List<String> burning = new ArrayList<>();
                    for (LivingEntity living : List.of(user, stand, bystander)) {
                        if (living.hasEffect(ReworkedAddon.SLOW_BURN)) {
                            burning.add(living.getName().getString());
                        }
                    }
                    if (!burning.isEmpty()) {
                        wrong.add(what + ": 1.16 gave Slow Burn only to a living task target; now burning "
                                + burning);
                    }
                }
            }
            finally {
                if (power.isSummoned()) {
                    ModStands.MAGICIANS_RED.get().forceUnsummon(user, power);
                }
                spawned.forEach(Entity::discard);
            }
        }
    }

    private static final class BlinkAgainScene {
        private final GameTestHelper helper;
        private final List<String> wrong = new ArrayList<>();

        private BlinkAgainScene(GameTestHelper helper) {
            this.helper = helper;
        }

        private void start() {
            onceTicking(helper, () -> {
                row(false);
                row(true);
                helper.assertTrue(wrong.isEmpty(), "1.16 did nothing for the blink pressed again in its own windup: "
                        + wrong);
            });
        }

        private void row(boolean uppercut) {
            String what = uppercut ? "the uppercut with its blink" : "the heavy punch with its blink";
            try (BlinkFixture fixture = new BlinkFixture(helper, "rejojo-sp-blink-again", true)) {
                fixture.unlock("uppercut");
                // a meter of 0.75 puts the uppercut on the heavy key
                fixture.stand.setFinisherMeter(uppercut ? 0.75F : 0.0F);
                BufferingState first = fixture.press("heavy_punch", (short) 201);
                EntityActionInstance attack = fixture.stand.getCurStandAction();
                helper.assertTrue(first.isActionSuccess && (uppercut
                                ? attack instanceof HeavyPunchUppercutAbility.Uppercut
                                : attack instanceof StandEntityHeavyPunchAbility.StandEntityHeavyPunch),
                        "Setup: the heavy key did not start the " + (uppercut ? "uppercut" : "heavy punch") + ": "
                                + attack);
                fixture.user.setShiftKeyDown(true);
                BufferingState blink = fixture.press("heavy_punch", (short) 202);
                EntityActionInstance running = fixture.stand.getCurStandAction();
                helper.assertTrue(blink.isActionSuccess && running == attack && running.getPhase() == ActionPhase.WINDUP
                                && fixture.blinkModifiers(running) == 1,
                        "Setup: Shift and the heavy key did not give " + what + ": " + running);
                BufferingState again = fixture.press("heavy_punch", (short) 203);
                fixture.user.setShiftKeyDown(false);
                EntityActionInstance now = fixture.stand.getCurStandAction();
                if (again.shouldBuffer || again.isActionSuccess || now != running || running.isOver()
                        || fixture.blinkModifiers(running) != 1) {
                    wrong.add("Shift and the heavy key again in " + what + "'s windup: 1.16 resolved it to the same "
                            + "blink, which its task's modifier set already held; the port's key gave "
                            + (fixture.lastResolved != null ? fixture.lastResolved.name() : "nothing")
                            + ", the press went off " + again.isActionSuccess + ", buffered " + again.shouldBuffer
                            + ", the Stand's action " + now + " (the blink " + running + ", over " + running.isOver()
                            + "), blink modifiers on it " + fixture.blinkModifiers(running));
                }
                running.setPhaseStart(ActionPhase.PERFORM);
                running._tickAction();
                if (fixture.farHits != 1) {
                    wrong.add(what + " pressed again: 1.16 blinked and struck once; the cow 4.5 blocks south took "
                            + fixture.farHits + " Stand hits");
                }
            }
        }
    }

    /**
     * A guarding Magician's Red gets its counter from a second Magician's Red's heavy punch, as the core's
     * LightCounterPulseGameTests prime it, then punches a cow with the counter still up.
     */
    private static final class LightCounterScene {
        private static final short CONTROL = 161;
        private static final short GUARD = 162;
        private static final short PUNCH = 163;

        private record Actor(Player user, StandPower power, StandEntity stand) {}

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<Object> listeners = new ArrayList<>();
        private final List<Actor> actors = new ArrayList<>();
        private final List<Float> nativePulses = new ArrayList<>();
        private final List<Float> detachedPulses = new ArrayList<>();
        private Actor caster;
        private Actor primer;
        private Cow target;
        private EntityActionInstance punch;
        private Throwable eventFailure;
        private float counter;
        private int primeHits;
        private int targetHurts;
        private int ticks;
        private boolean hurtLanded;
        private boolean observing;
        private boolean done;
        private boolean closed;

        private LightCounterScene(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
        }

        private void start() {
            try {
                listen();
                setUp();
                helper.onEachTick(this::tick);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void setUp() {
            Vec3 origin = chunkMiddle(helper);
            caster = actor(origin, 0.0F, "rejojo-mr-counter");
            press(caster, "manual_control", InputMethod.CLICK, CONTROL);
            helper.assertTrue(caster.stand().isManuallyControlled(),
                    "Setup: manual control did not start, so the Stand would guard by itself");
            press(caster, "guard", InputMethod.HOLD, GUARD);
            primer = actor(origin.add(0.0D, 0.0D, 1.5D), 180.0F, "rejojo-mr-primer");
            helper.assertTrue(caster.stand().isStandBlocking()
                            && caster.stand().canBlockFromAngle(primer.stand().position()),
                    "Setup: Magician's Red does not guard towards the primer");
            aim(primer, new ActionTarget(caster.stand()));
            press(primer, "heavy_punch", InputMethod.CLICK, PUNCH);
        }

        private void listen() {
            Consumer<LivingIncomingDamageEvent> incoming = event -> {
                if (caster != null && primer != null && event.getEntity() == caster.stand()
                        && event.getSource().getDirectEntity() == primer.stand()) {
                    primeHits++;
                }
            };
            Consumer<LivingKnockBackEvent> knockback = event -> {
                if (observing && event.getEntity() == target) {
                    (DamageSourceModified.currentKnockbackSource(target) != null ? nativePulses : detachedPulses)
                            .add(event.getStrength());
                }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (caster != null && event.getEntity() == caster.stand() && !closed && eventFailure == null) {
                    try {
                        afterCasterTick();
                    } catch (RuntimeException | Error failure) {
                        eventFailure = failure;
                    }
                }
            };
            listeners.add(incoming);
            listeners.add(knockback);
            listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }

        private void tick() {
            if (closed) {
                return;
            }
            try {
                if (eventFailure instanceof RuntimeException failure) {
                    throw failure;
                }
                if (eventFailure instanceof Error failure) {
                    throw failure;
                }
                if (done) {
                    close();
                    helper.succeed();
                    return;
                }
                ticks++;
                if (punch == null && primeHits > 0) {
                    startPunch();
                }
                helper.assertTrue(ticks < 100, punch == null
                        ? "Setup: the primer's heavy punch never reached the guard"
                        : "The Reworked Magician's Red punch did not strike, phase " + punch.getPhase());
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private void startPunch() {
            counter = caster.stand().guardCounter();
            helper.assertTrue(caster.stand().isStandBlocking() && counter > 0.0F && counter <= 1.0F,
                    "Setup: the heavy punch into the guard left the counter at " + counter);
            target = new CounterCow(this);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            target.setHealth(200.0F);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
            Vec3 at = caster.user().position().add(0.0D, 0.0D, 1.5D);
            target.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(target), "Setup: the cow was not added");
            aim(caster, new ActionTarget(target));
            caster.power().setStamina(caster.power().getMaxStamina());
            observing = true;
            punch = press(caster, "punch", InputMethod.CLICK, PUNCH);
            helper.assertTrue(punch.ability instanceof ReworkedStandAbilities.ReworkedMagiciansRedPunchAbility,
                    "Setup: the punch key did not start Reworked's Magician's Red punch: " + punch.ability);
            helper.assertTrue(near(caster.stand().guardCounter(), counter),
                    "Setup: the punch dropped the guard counter " + counter + " to " + caster.stand().guardCounter());
            afterCasterTick();
        }

        private void afterCasterTick() {
            if (done || punch == null || punch.punchedTarget == null) {
                return;
            }
            observing = false;
            done = true;
            helper.assertTrue(punch.punchedTarget.getMainEntity() == target && targetHurts == 1 && hurtLanded,
                    "Setup: the punch did not land one hurt on the cow: hurts " + targetHurts + ", landed "
                            + hurtLanded);
            float half = counter * 0.5F;
            helper.assertTrue(nativePulses.size() <= 1 && nativePulses.stream().allMatch(pulse -> near(pulse, 0.4F))
                            && detachedPulses.size() == 2
                            && near(detachedPulses.get(0), half) && near(detachedPulses.get(1), half),
                    "1.16 StandEntityPunch pushes the target of Magician's Red's punch with the native 0.4, then half"
                            + " the guard counter " + counter + " on the hit and the same half as the tail, " + half
                            + " each; the port pushed " + nativePulses + " with the hurt and " + detachedPulses
                            + " apart from it");
        }

        private Actor actor(Vec3 position, float yaw, String name) {
            Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), name)) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            user.setNoGravity(true);
            user.moveTo(position.x, position.y, position.z, yaw, 0.0F);
            user.setYHeadRot(yaw);
            user.getAbilities().invulnerable = false;
            user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            user.setHealth(200.0F);
            user.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            helper.assertTrue(level.addFreshEntity(user), "Setup: the Magician's Red user was not added");
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(ModStands.MAGICIANS_RED.get());
            power.setResolveLevel(4);
            helper.assertTrue(ModStands.MAGICIANS_RED.get().summon(user, power), "Could not summon Magician's Red.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.getUser() == user && !stand.isArmsOnlyMode(),
                    "Setup: no full Magician's Red body");
            Actor actor = new Actor(user, power, stand);
            actors.add(actor);
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.moveTo(position.x, position.y, position.z, yaw, 0.0F);
            stand.setYHeadRot(yaw);
            stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(16.0D);
            stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            power.setStamina(power.getMaxStamina());
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            return actor;
        }

        private static void aim(Actor actor, ActionTarget aim) {
            LivingComponentAction.getComponent(actor.user()).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(actor.stand()).entityAim.setTarget(aim);
        }

        private EntityActionInstance press(Actor actor, String name, InputMethod method, short key) {
            Ability base = actor.power().getAbility(name);
            helper.assertTrue(base != null, "Setup: Magician's Red has no " + name);
            var selected = actor.power().updateAvailableMoves().getContextVariationContainer(base);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, actor.user(), method),
                    "Setup: the " + name + " key was refused");
            BufferingState buffer = BufferingState.clickOnly();
            var held = AbilityInput.keyPress(key, selected.ability, actor.user(), null, method, 0.0F, buffer,
                    base.getAbilityId());
            helper.assertTrue(buffer.isActionSuccess, "Setup: the " + name + " key did not succeed");
            return held != null && held.action instanceof EntityActionInstance action
                    ? action
                    : actor.stand().getCurStandAction();
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Object listener : listeners) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            listeners.clear();
            for (Actor actor : actors) {
                for (short key : new short[] {CONTROL, GUARD, PUNCH}) {
                    AbilityInput.keyRelease(key, actor.user());
                }
                if (actor.user().hasData(ModDataAttachmentTypes.CONTROLLER)) {
                    actor.user().getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
                }
                LivingComponentAction.getComponent(actor.stand()).setAction(null, SyncType.NO_SYNC);
                if (actor.power().isSummoned()) {
                    ModStands.MAGICIANS_RED.get().forceUnsummon(actor.user(), actor.power());
                }
                if (!actor.stand().isRemoved()) {
                    actor.stand().discard();
                }
                actor.user().discard();
            }
            if (target != null) {
                target.discard();
            }
        }

        private static final class CounterCow extends Cow {
            private final LightCounterScene scene;

            private CounterCow(LightCounterScene scene) {
                super(EntityType.COW, scene.level);
                this.scene = scene;
            }

            @Override
            public boolean hurt(DamageSource source, float amount) {
                boolean result = super.hurt(source, amount);
                if (scene.observing) {
                    scene.targetHurts++;
                    scene.hurtLanded = result;
                }
                return result;
            }
        }
    }

    // 1.16 Reworked magicians_red_flame_burst standOffsetFront(): (left, up, forward) (0, 0.2, 0.5)
    // from the user's feet in full mode, turned by the view yaw; the arms keep their arms-only default.
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "rejojoFlameBurstOffset")
    public static void flameBurstPutsMagiciansRedWhereTheDonorDid(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "rejojo-flame-burst")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandPower power = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            Vec3 position = chunkMiddle(helper);
            user.setNoGravity(true);
            user.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user), "Setup: the Magician's Red user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(ModStands.MAGICIANS_RED.get());
            power.skipProgression();
            helper.assertTrue(ModStands.MAGICIANS_RED.get().summon(user, power), "Could not summon Magician's Red.");
            StandEntity stand = power.getSummonedStandEntity();
            stand.updatePosition(user);
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                    "Setup: Magician's Red is not a full Stand following its user");
            helper.assertTrue(power.getAbility("flame_burst")
                            instanceof ReworkedStandAbilities.ReworkedMagiciansRedFlameBurstAbility,
                    "Setup: the Reworked flame burst does not replace the core one");
            EntityActionType type = (EntityActionType) power.getAbility("flame_burst");

            startFlameBurst(helper, user, stand, type);
            // the body is turned away from the view: a body-yaw offset misses
            turnFlameBurstUser(user, 0.0F, 50.0F);
            assertFlameBurstAt(helper, user, stand, 0.0D, 0.2D, 0.5D, "view yaw 0, body yaw 50");
            // facing west the forward is -x
            turnFlameBurstUser(user, 90.0F, 0.0F);
            assertFlameBurstAt(helper, user, stand, -0.5D, 0.2D, 0.0D, "view yaw 90, body yaw 0");

            // a Stand left far from its user is retracted when its action ends
            stand.setPos(user.position());
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            stand.setArmsOnlyMode();
            startFlameBurst(helper, user, stand, type);
            turnFlameBurstUser(user, 0.0F, 50.0F);
            assertFlameBurstAt(helper, user, stand, 0.0D, 0.0D, 0.15D,
                    "the arms only (their arms-only default), view yaw 0");
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                ModStands.MAGICIANS_RED.get().forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    private static void startFlameBurst(GameTestHelper helper, Player user, StandEntity stand, EntityActionType type) {
        EntityActionInstance action = type.createActionObj();
        type.initActionFromConfig(action, helper.getLevel(), user, stand);
        action.setStartingPhase();
        LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
        // the core's own shot, which the Reworked ability no longer replaces
        helper.assertTrue(stand.getCurStandAction() == action
                        && action.getClass() == MagiciansRedFlameBurstAbility.FlameBurstShot.class,
                "Setup: the core flame burst shot did not become the Stand's action: " + action);
    }

    private static void turnFlameBurstUser(Player user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertFlameBurstAt(GameTestHelper helper, Player user, StandEntity stand,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < 1.0E-4D && Math.abs(actual.y - dy) < 1.0E-4D
                        && Math.abs(actual.z - dz) < 1.0E-4D,
                "1.16 put Magician's Red bursting flames at its user's feet + (" + dx + ", " + dy + ", " + dz
                        + ") with " + when + ", got " + actual + " (offset "
                        + stand.offsetFromUser.getRelativeOffset() + " " + stand.offsetFromUser.getRotations() + ")");
    }

    /**
     * 1.16 Reworked's MRFlameEntity.hurtTarget overwrite added Slow Burn (140 ticks; its added event lit the target
     * for the longer of its fire and 140 + ticks back) and the heat, then ran dealDamageAndSetOnFire(target, 7 s,
     * stand fire), which never raised that fire and put it back when the hurt failed. A Stand and any other target
     * end up with the longer of their fire and 141 ticks at Slow Burn 0, never with the core flame's 10 s.
     */
    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "rejojoFlameFire")
    public static void flameLeavesTheDonorFireOnStandsAndOtherTargetsAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = chunkMiddle(helper);
        List<Runnable> cleanups = new ArrayList<>();
        try {
            StandEntity magiciansRed = flameStand(helper, origin, "rejojo-flame-caster",
                    ModStands.MAGICIANS_RED.get(), cleanups);
            StandEntity starPlatinum = flameStand(helper, origin.add(0.0D, 0.0D, 3.0D), "rejojo-flame-stand",
                    ModStands.STAR_PLATINUM.get(), cleanups);
            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Setup: no cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            cow.moveTo(origin.x, origin.y, origin.z + 1.5D, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Setup: the cow was not added");
            cleanups.add(cow::discard);
            int slowBurnFire = 140 + 140 / 20 / 7;
            for (int fireBefore : new int[] {0, 180, 200, 300}) {
                checkFlameFire(helper, magiciansRed, cow, fireBefore, true,
                        Math.max(fireBefore, slowBurnFire), "a cow");
                checkFlameFire(helper, magiciansRed, starPlatinum, fireBefore, true,
                        Math.max(fireBefore, slowBurnFire), "Star Platinum");
            }
            cow.setInvulnerable(true);
            checkFlameFire(helper, magiciansRed, cow, 0, false, slowBurnFire, "an invulnerable cow");
        }
        finally {
            for (int i = cleanups.size() - 1; i >= 0; i--) {
                cleanups.get(i).run();
            }
        }
        helper.succeed();
    }

    private static StandEntity flameStand(GameTestHelper helper, Vec3 position, String name, EntityStandType type,
            List<Runnable> cleanups) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        user.setNoGravity(true);
        user.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        user.getAbilities().invulnerable = false;
        user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        user.setHealth(200.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "Setup: the " + name + " user was not added");
        cleanups.add(user::discard);
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(type);
        helper.assertTrue(type.summon(user, power), "Could not summon the " + name + " Stand.");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(stand != null && stand.getUser() == user, "Setup: no summoned " + name + " Stand");
        cleanups.add(() -> {
            if (power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            if (!stand.isRemoved()) {
                stand.discard();
            }
        });
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        stand.setCanFollowUser(false);
        stand.setNoGravity(true);
        stand.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        return stand;
    }

    private static void checkFlameFire(GameTestHelper helper, StandEntity shooter, LivingEntity target,
            int fireBefore, boolean hurts, int expectedFire, String what) {
        target.removeEffect(ReworkedAddon.SLOW_BURN);
        ReworkedAddon.state(target).setHeat(0.0F);
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        target.setRemainingFireTicks(fireBefore);
        HitFlame flame = new HitFlame(shooter, helper.getLevel());
        flame.hitNow(target);
        helper.assertTrue(flame.hurt != null && flame.hurt == hurts,
                "Setup: the flame's hurt on " + what + " returned " + flame.hurt + ", expected " + hurts);
        MobEffectInstance slowBurn = target.getEffect(ReworkedAddon.SLOW_BURN);
        helper.assertTrue(slowBurn != null && slowBurn.getAmplifier() == 0 && slowBurn.getDuration() == 140,
                "1.16 gave " + what + " Slow Burn 0 for 140 ticks when the flame hit"
                        + (hurts ? "" : ", though the hurt failed") + "; the port gave " + slowBurn);
        helper.assertTrue(flame.fireAfterHit == expectedFire,
                "1.16 left " + what + " burning " + expectedFire + " ticks after a flame hit at " + fireBefore
                        + " (Slow Burn's fire, then 7 s of Stand fire" + (hurts ? "" : ", put back after the failed"
                        + " hurt") + "); the port left " + flame.fireAfterHit);
    }

    // A flame that hits the given entity at once, through the impact event and onHit as DamagingEntity.checkHit
    // does, and reads the target's fire once the hit is over.
    private static final class HitFlame extends MRFlameEntity {
        private Boolean hurt;
        private int fireAfterHit = Integer.MIN_VALUE;

        private HitFlame(LivingEntity shooter, Level level) {
            super(shooter, level);
        }

        private void hitNow(Entity target) {
            EntityHitResult result = new EntityHitResult(target);
            if (!EventHooks.onProjectileImpact(this, result)) {
                onHit(result);
            }
        }

        @Override
        protected void afterEntityHit(EntityHitResult result, boolean entityHurt) {
            super.afterEntityHit(result, entityHurt);
            hurt = entityHurt;
            fireAfterHit = result.getEntity().getRemainingFireTicks();
        }
    }

    // The middle of the structure's own chunk, which the test runner keeps loaded; an offset from the structure's
    // corner can land in a neighbouring chunk that does not tick entities. The runner places a test every 6 blocks
    // and rings each one with barriers one and two blocks above its structure block, so the fixture floats above
    // them: a ring crossing the chunk middle would block the blink's ray.
    private static Vec3 chunkMiddle(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(base);
        return new Vec3(chunk.getMiddleBlockX() + 0.5D, base.getY() + 5.0D, chunk.getMiddleBlockZ() + 0.5D);
    }

    private static float heavyRecovery(float speed, float finisher) {
        float max = (40.0F - speed * 1.25F) * 0.75F;
        return (float) Math.floor(max + finisher * (max / 2.0F - max));
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 0.001D;
    }

    // Three to five handler ticks fit in a window, whichever side of the level tick the test steps run on.
    private static boolean paidEveryTick(float gained) {
        return gained > (WINDOW_TICKS - 1.5F) * RESOLVE_PER_TICK
                && gained < (WINDOW_TICKS + 1.5F) * RESOLVE_PER_TICK;
    }

    private static void pose(ServerPlayer player, boolean onGround, boolean sneaking, boolean sprinting) {
        player.setOnGround(onGround);
        player.setShiftKeyDown(sneaking);
        player.setSprinting(sprinting);
    }

    private static float resolve(StandPower power) {
        return power.getResolveCounter().getResolveValue();
    }

    private static TimeStopState state(ServerPlayer player) {
        return player.serverLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
    }

    // A failed step ends the test, so its later steps never run: clean up here.
    private static void guarded(Fixture fixture, Runnable step) {
        guarded(() -> cleanup(fixture), step);
    }

    private static void guarded(Runnable cleanup, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static Fixture user(GameTestHelper helper, String name) {
        return user(helper, name, ModStands.THE_WORLD.get());
    }

    private static Fixture user(GameTestHelper helper, String name, EntityStandType standType) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        Vec3 position = chunkMiddle(helper);
        player.moveTo(position.x, position.y, position.z);
        level.addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(standType);
        helper.assertTrue(power.hasPower() && power.usesResolve()
                        && !power.isAbilityUnlocked(TimeStopLearning.TIME_STOP),
                "Setup: a new " + standType.getId() + " user must use resolve and start with the time stop locked");
        return new Fixture(player, power);
    }

    private static void cleanup(Fixture fixture) {
        ServerPlayer player = fixture.player();
        TimeStopState state = state(player);
        for (TimeStopState.Instance instance : List.copyOf(state.getInstances())) {
            if (instance.userId() == player.getId()) {
                state.removeInstance(instance.id());
            }
        }
        player.removeAllEffects();
        player.discard();
    }

    private record Fixture(ServerPlayer player, StandPower power) {}

    private static final class SilentListener extends ServerGamePacketListenerImpl {
        private SilentListener(
                MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener sendListener) {}
    }
}
