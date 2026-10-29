package rotp.addons.reworked.ability;

import javax.annotation.Nullable;

import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;

import rotp.addons.reworked.network.ReworkedManualInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class ReworkedTimeSkip {
    // 1.16 TheWorldTSBlinkRecovery: 3x the TS punch's getCooldown
    private static final int THE_WORLD_RECOVERY_COOLDOWN_MULTIPLIER = 3;
    // 1.16 TheWorldTSBlinkRecovery read the time-stop ticks of the recovery
    // action itself, which is never trained: floor(-1) + 5 = 4.
    static final int RECOVERY_TIME_SKIP_TICKS = 4;
    // 1.16 the_world_ts_blink_recovery builder: no staminaCost and no
    // staminaCostTick.
    private static final float RECOVERY_STAMINA_COST = 0.0F;
    private static final float RECOVERY_STAMINA_COST_TICKING = 0.0F;
    // 1.16 TimeStopInstant.getMaxDistance
    private static final double MAX_BLINK_DISTANCE = 192.0D;
    // 1.16 StarPlatinumBlinkPunch: 3x the blink's 1/6 of the time-stop
    // cooldown for the skipped ticks.
    static final float BLINK_PUNCH_COOLDOWN_MULTIPLIER = 3.0F;

    private ReworkedTimeSkip() {}

    // 1.16 priced these from the moveset's Reworked blink (ReTimeStopInstant:
    // 0.8 of the Reworked time stop's 300 / 8.875).
    @Nullable
    private static TimeStopBlinkAbility getMovesetBlink(StandPower power) {
        return power.getAbility(TimeStopCooldowns.TIME_STOP_BLINK)
                instanceof TimeStopBlinkAbility blink ? blink : null;
    }

    private static String getBlinkLearningName(StandPower power) {
        TimeStopBlinkAbility blink = getMovesetBlink(power);
        return blink != null
                ? TimeStopLearning.getLearningName(
                        power, blink.getTimeStopAbilityName())
                : TimeStopLearning.getDefaultLearningName(power);
    }

    static float getBlinkStaminaCost(StandPower power) {
        TimeStopBlinkAbility blink = getMovesetBlink(power);
        return blink != null
                ? blink.getBlinkStaminaCost(power)
                : TimeStopLearning.getTimeStopBlinkStaminaCost(
                        power,
                        ReworkedStandAbilities.TIME_STOP_STAMINA_COST);
    }

    static float getBlinkStaminaCostTicking(StandPower power) {
        TimeStopBlinkAbility blink = getMovesetBlink(power);
        String learningName = getBlinkLearningName(power);
        return blink != null
                ? blink.getBlinkStaminaCostTicking(power, learningName)
                : TimeStopLearning.getTimeStopBlinkStaminaCostTicking(
                        power,
                        learningName,
                        ReworkedStandAbilities
                                .TIME_STOP_STAMINA_COST_TICK);
    }

    // 1.16 StarPlatinumBlinkPunch read the ReTimeStopInstant's
    // getMaxImpliedTicks, which add-ons rewrote at RETURN (Next Album
    // aging); the moveset blink applies those modifiers, 0 = no reach.
    static int getBlinkMaxImpliedTicks(StandPower power) {
        TimeStopBlinkAbility blink = getMovesetBlink(power);
        if (blink != null) {
            return blink.getMaxImpliedTicks(power);
        }
        return getStaminaCappedBlinkTicks(power);
    }

    // 1.16 TimeStopInstant.getMaxImpliedTicks: what the stamina left after
    // the blink's start cost pays for, at least 5 and at most the trained
    // ticks.
    private static int getStaminaCappedBlinkTicks(StandPower power) {
        int timeStopTicks = TimeStopLearning.getTimeStopTicks(
                power, getBlinkLearningName(power));
        if (StandUtil.standIgnoresStaminaDebuff(power)) {
            return timeStopTicks;
        }
        float multiplier = PlayerClientBroadcastedSettings
                .getTimeStopStaminaCostMultiplier(power);
        float tickingCost = getBlinkStaminaCostTicking(power) * multiplier;
        if (tickingCost <= 0.0F) {
            return timeStopTicks;
        }
        int affordableTicks = Mth.floor(
                (power.getStamina()
                        - getBlinkStaminaCost(power) * multiplier)
                        / tickingCost);
        return Mth.clamp(affordableTicks,
                TimeStopLearning.MIN_TIME_STOP_TICKS, timeStopTicks);
    }

    // 1.16 StarPlatinumBlinkPunch.blink, run where 1.16 ran it, with the
    // punch: the user blinks (the Stand when it is manually controlled),
    // and the skip is what that distance takes at the user's speed. The
    // blink went by the running task's target (standPerform :120), which
    // TimeStopInstant.calcBlinkPos replaced by its own ray only when the task
    // held none, and faced only that task target (:143-147). The target
    // returned, which 1.16 then offered the task (:121), is what
    // standPerform's own ray found before the blink (:119): from the user
    // even under manual control, as far as the user's bare speed (getSpeed,
    // no 2.1585) covers in the blink's ticks.
    static ActionTarget blinkBeforePunch(
            StandEntity stand, String punchAbilityName,
            ActionTarget taskTarget) {
        LivingEntity user = stand.getUser();
        StandPower power = user != null ? StandPower.get(user) : null;
        if (power == null
                || !(stand.level() instanceof ServerLevel level)) {
            return ActionTarget.EMPTY;
        }
        LivingEntity performer =
                stand.isManuallyControlled() ? stand : user;
        playBlinkSound(
                level,
                performer,
                power,
                ModSoundEvents.STAR_PLATINUM_TIME_STOP_BLINK.value());

        // the blink's getMaxImpliedTicks and getMaxDistance
        int timeStopTicks = getBlinkMaxImpliedTicks(power);
        double speed = distancePerTick(user);
        double distance = Math.min(
                speed * timeStopTicks, MAX_BLINK_DISTANCE);
        if (performer == stand) {
            distance = Math.min(distance, Math.max(
                    stand.getMaxRange() - stand.distanceTo(user),
                    0.0D));
        }
        ActionTarget target = rayTraceBlinkTarget(
                user,
                Math.min(user.getSpeed() * timeStopTicks,
                        MAX_BLINK_DISTANCE));
        ActionTarget held = taskTarget != null
                ? taskTarget.resolveEntityId(level)
                : ActionTarget.EMPTY;
        boolean holds = !held.isEmpty(level);
        Vec3 blinkPos = calcBlinkPos(
                level, performer,
                holds ? held : rayTraceBlinkTarget(
                        performer, distance),
                distance);
        double blinkDistance =
                performer.position().subtract(blinkPos).length();
        Float facingYaw = holds
                ? TimeStopBlinkAbility.getFacingYaw(held, blinkPos)
                : null;
        makeNearbyMobsLoseTarget(performer, blinkPos);
        int impliedTicks = speed > 0.0D
                ? Mth.clamp(Mth.ceil(blinkDistance / speed),
                        0, timeStopTicks)
                : 0;

        TimeStopBlinkAbility.teleportFacing(
                performer,
                groundedPosition(level, blinkPos, blinkPos.y),
                facingYaw);
        skipTicksForStandAndUser(power, stand, impliedTicks);
        // 1.16 paid only the skipped ticks at the Reworked blink's
        // per-tick cost (8.875 * 100 / ticks * 0.8), with no start cost.
        power.consumeStamina(
                impliedTicks
                        * getBlinkStaminaCostTicking(power)
                        * PlayerClientBroadcastedSettings
                                .getTimeStopStaminaCostMultiplier(
                                        power));
        setBlinkPunchCooldowns(power, punchAbilityName, impliedTicks);
        // 1.16 StarPlatinumBlinkPunch trained in fractions, unlike the
        // TS punch's whole points.
        TimeStopLearning.onBlinkPunchTimeSkip(power, impliedTicks);
        TimeStopLearning.markUsedTimeStopToday(power);
        return target;
    }

    // 1.16 set the same cooldown on the punch, the blink and, unless it
    // was already cooling down, the time stop.
    static void setBlinkPunchCooldowns(
            StandPower power, String punchAbilityName, int impliedTicks) {
        int cooldown = (int) (TimeStopCooldowns.timeStopCooldown(
                        power, impliedTicks)
                * TimeStopCooldowns.TIME_STOP_BLINK_COOLDOWN_RATIO
                * BLINK_PUNCH_COOLDOWN_MULTIPLIER);
        if (cooldown <= 0) {
            return;
        }
        power.setAbilityCooldown(punchAbilityName, cooldown);
        power.setAbilityCooldown(
                TimeStopCooldowns.TIME_STOP_BLINK, cooldown);
        if (!power.isAbilityOnCooldown(TimeStopCooldowns.TIME_STOP)) {
            power.setAbilityCooldown(
                    TimeStopCooldowns.TIME_STOP, cooldown);
        }
    }

    // 1.16 attacked the blink's target outright (StandEntity.attackTarget,
    // no reach check) unless the Stand was manually controlled; null keeps
    // the normal punch target.
    @Nullable
    static ActionTarget getBlinkPunchTarget(
            StandEntity stand, @Nullable ActionTarget blinkTarget) {
        if (blinkTarget == null || stand.isManuallyControlled()) {
            return null;
        }
        ActionTarget target = blinkTarget.resolveEntityId(stand.level());
        if (target.getType() == ActionTarget.TargetType.ENTITY) {
            Entity entity = target.getMainEntity();
            return entity != null
                            && StandEntityPunchAbility.canStandHit(
                                    stand, entity)
                    ? target
                    : ActionTarget.EMPTY;
        }
        return target;
    }

    static boolean performTheWorldRecovery(
            ServerLevel level,
            LivingEntity user,
            StandPower power,
            StandEntity stand) {
        LivingComponentAction.getComponent(stand)
                .setAction(null, SyncType.TRACKING_AND_SELF);

        int impliedTicks = RECOVERY_TIME_SKIP_TICKS;
        if (!StandUtil.standIgnoresStaminaDebuff(power)) {
            impliedTicks = getStaminaCappedRecoveryTicks(
                    power, impliedTicks);
        }
        float multiplier =
                PlayerClientBroadcastedSettings
                        .getTimeStopStaminaCostMultiplier(power);
        // 1.16: the Reworked blink's start cost plus the skipped ticks
        float staminaCost = multiplier
                * (getBlinkStaminaCost(power)
                        + impliedTicks
                                * getBlinkStaminaCostTicking(power));
        // 1.16 never refused the recovery for stamina and ignored the
        // result of the payment.
        power.consumeStamina(staminaCost);
        skipTicksForStandAndUser(
                power, stand, impliedTicks);
        power.setAbilityCooldown(
                ReworkedStandAbilities.THE_WORLD_TS_PUNCH_NAME,
                getRecoveryPunchCooldown(power, impliedTicks));
        power.setAbilityCooldown(
                TimeStopCooldowns.TIME_STOP_BLINK,
                getRecoveryBlinkCooldown(power, impliedTicks));

        // 1.16 played the blink where the one that moves stood; the user
        // moves with the blink modifier (1.16 Shift), manual control too
        if (!ReworkedManualInput.isBlinkModifierHeld(user, stand)) {
            playBlinkSound(
                    level,
                    stand,
                    power,
                    ModSoundEvents.THE_WORLD_TIME_STOP_BLINK.value());
            stand.offsetFromUser.resetToIdle();
            Vec3 target = stand.collideNextPos(
                    stand.offsetFromUser.getPosition(user));
            stand.moveTo(target.x, target.y, target.z);
        }
        else {
            playBlinkSound(
                    level,
                    user,
                    power,
                    ModSoundEvents.THE_WORLD_TIME_STOP_BLINK.value());
            Vec3 target = groundedPosition(
                    level, stand.position(), user.position().y);
            user.teleportTo(target.x, target.y, target.z);
        }
        TimeStopLearning.markUsedTimeStopToday(power);
        return true;
    }

    // 1.16 TheWorldTSBlinkRecovery.perform capped the ticks by the stamina
    // over the recovery action's own costs, which its builder left at 0:
    // any stamina divides to +Infinity, an empty bar to NaN, which (int)
    // makes 0. 1.16 MathHelper.clamp tested the minimum (5) before the
    // maximum (4), so an empty bar skips 5 ticks and any other 4.
    static int getStaminaCappedRecoveryTicks(
            StandPower power, int timeStopTicks) {
        int affordableTicks = Mth.floor(
                (power.getStamina() - RECOVERY_STAMINA_COST)
                        / RECOVERY_STAMINA_COST_TICKING);
        return affordableTicks < TimeStopLearning.MIN_TIME_STOP_TICKS
                ? TimeStopLearning.MIN_TIME_STOP_TICKS
                : Math.min(affordableTicks, timeStopTicks);
    }

    // 1.16 TheWorldTSBlinkRecovery: the TS punch's own getCooldown for the
    // skipped ticks, a plain cooldown(50) that Resolve and Creative empty,
    // three times.
    static int getRecoveryPunchCooldown(StandPower power, int impliedTicks) {
        Ability punch = power.getAbility(
                ReworkedStandAbilities.THE_WORLD_TS_PUNCH_NAME);
        return punch != null
                ? punch.getCooldown(power, impliedTicks)
                        * THE_WORLD_RECOVERY_COOLDOWN_MULTIPLIER
                : 0;
    }

    // 1.16 TheWorldTSBlinkRecovery: 1/6 of the time-stop cooldown for the
    // skipped ticks (The World's is not cut by the Resolve effect) times
    // (105 - 90 * resolve ratio^2) * 9 / 22.5, on the blink only.
    static int getRecoveryBlinkCooldown(StandPower power, int impliedTicks) {
        float cooldown = power.isUserCreative()
                ? 0.0F
                : TimeStopCooldowns.TIME_STOP_COOLDOWN_PER_TICK
                        * Math.max(impliedTicks, 0);
        double resolveRatio =
                power.getResolveCounter().getResolveRatio(power);
        return (int) ((double) (cooldown
                        * TimeStopCooldowns.TIME_STOP_BLINK_COOLDOWN_RATIO)
                * (Math.pow(resolveRatio, 2.0D) * -90.0D + 105.0D)
                * 9.0D / 22.5D);
    }

    static boolean isTimeStopped(Level level, LivingEntity user) {
        ChunkPos chunk = new ChunkPos(user.blockPosition());
        if (level.isClientSide()) {
            return TimeStopState.getClientDisplayInstance(chunk)
                    .isPresent();
        }
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel
                    .getData(ModDataAttachmentTypes.TIME_STOP.get())
                    .isTimeStopped(chunk);
        }
        return false;
    }

    // 1.16 JojoModUtil.rayTrace from the given entity: living entities but
    // that entity and its own Stands (standPerform :119 from the user;
    // TimeStopInstant.calcBlinkPos :143-144 from the one that blinks, so a
    // manually controlled Stand's ray can meet its user). The block mode is
    // OUTLINE: grass, a flower or a torch stops the ray.
    private static ActionTarget rayTraceBlinkTarget(
            LivingEntity source,
            double distance) {
        return HitResultUtil.clip(
                source.getEyePosition(),
                source.getLookAngle(),
                distance,
                distance,
                source.level(),
                entity -> entity instanceof LivingEntity
                        && !(entity instanceof StandEntity standEntity
                                && standEntity.getUser() == source),
                source,
                0.0D,
                ClipContext.Block.OUTLINE);
    }

    // 1.16 TimeStopInstant.calcBlinkPos of Star Platinum's blink (not
    // behind the target).
    private static Vec3 calcBlinkPos(
            ServerLevel level,
            LivingEntity performer,
            ActionTarget target,
            double distance) {
        if (target.isEmpty(level)) {
            Vec3 pos = performer.getEyePosition().add(
                    performer.getLookAngle().scale(distance));
            BlockPos blockPos = BlockPos.containing(pos);
            while (blockPos.getY() > level.getMinBuildHeight()
                    && level.isEmptyBlock(blockPos.below())) {
                blockPos = blockPos.below();
            }
            double y = blockPos.getY() > level.getMinBuildHeight()
                    ? blockPos.getY()
                    : performer.getY();
            return new Vec3(pos.x, y, pos.z);
        }
        return switch (target.getType()) {
            case ENTITY -> {
                Entity targetEntity = target.getEntity();
                double offset = targetEntity.getBbWidth()
                        + performer.getBbWidth();
                yield performer.distanceToSqr(targetEntity)
                                > offset * offset
                        ? targetEntity.position().subtract(
                                performer.getLookAngle().scale(offset))
                        : performer.position();
            }
            case BLOCK -> {
                BlockPos blockPos = target.getBlockPos();
                yield Vec3.atBottomCenterOf(
                        level.isEmptyBlock(blockPos.above())
                                ? blockPos.above()
                                : blockPos.relative(target.getFace()));
            }
            default -> performer.position();
        };
    }

    // 1.16: mobs chasing the performer that face away from where it lands
    // lose it.
    private static void makeNearbyMobsLoseTarget(
            LivingEntity performer, Vec3 blinkPos) {
        performer.level().getEntitiesOfClass(
                        Mob.class,
                        performer.getBoundingBox().inflate(8.0D),
                        mob -> mob.getTarget() == performer
                                && mob.getLookAngle().dot(
                                                mob.getEyePosition(1.0F)
                                                        .subtract(blinkPos))
                                        >= 0.0D)
                .forEach(mob -> {
                    mob.setTarget(null);
                    for (WrappedGoal goal
                            : mob.targetSelector.getAvailableGoals()) {
                        if (goal.isRunning()) {
                            goal.stop();
                        }
                    }
                });
    }

    // 1.16 StarPlatinumBlinkPunch / TheWorldTSBlinkRecovery: up out of any
    // block that is not air (water too), then down to the floor. The blink
    // punch also cut x and z to the block corner; both keep them here.
    private static Vec3 groundedPosition(
            ServerLevel level,
            Vec3 source,
            double fallbackY) {
        BlockPos pos = BlockPos.containing(source);
        int maxY = level.getMaxBuildHeight() - 1;
        while (pos.getY() < maxY && !level.isEmptyBlock(pos)) {
            pos = pos.above();
        }
        while (pos.getY() > level.getMinBuildHeight()
                && level.isEmptyBlock(pos.below())) {
            pos = pos.below();
        }
        double y = pos.getY() > level.getMinBuildHeight()
                ? pos.getY()
                : fallbackY;
        return new Vec3(source.x, y, source.z);
    }

    private static double distancePerTick(LivingEntity entity) {
        return entity.getAttributeValue(Attributes.MOVEMENT_SPEED)
                * 2.1585D;
    }

    private static void skipTicksForStandAndUser(
            StandPower power,
            StandEntity stand,
            int ticks) {
        if (power != null && power.getUser() != null) {
            LivingEntity user = power.getUser();
            syncNoLerpPosition(user, user.position());
            skipTicks(user, ticks);
        }
        syncNoLerpPosition(stand, stand.position());
        skipTicks(stand, ticks);
        if (ticks > 0) {
            stand.overlayTickCount += ticks;
        }
    }

    private static void skipTicks(
            LivingEntity entity, int ticks) {
        if (ticks > 0) {
            entity.tickCount += ticks;
        }
    }

    private static void syncNoLerpPosition(
            LivingEntity entity, Vec3 position) {
        if (!entity.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    entity,
                    new TrDirectEntityPosPacket(
                            entity.getId(), position));
        }
    }

    private static void playBlinkSound(
            ServerLevel level,
            Entity source,
            StandPower power,
            net.minecraft.sounds.SoundEvent sound) {
        Vec3 position = source.position();
        double radius = 80.0D;
        StandUtil.broadcastSoundWithCondition(
                level,
                position,
                net.minecraft.core.Holder.direct(sound),
                false,
                power,
                SoundSource.AMBIENT,
                5.0F,
                1.0F,
                player -> TimeStopState.canPlayerSeeInStoppedTime(
                                player)
                        && player.position().distanceToSqr(position)
                                < radius * radius);
    }
}
