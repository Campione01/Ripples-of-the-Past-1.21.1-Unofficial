package rotp.addons.catchtherainbow.ability;

import java.util.Optional;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class RainBlinkAbility extends StandEntityAbility {
    public static final float STAMINA_COST = 60.0F;
    public static final float LEGACY_MINIMUM_STAMINA = 25.0F;
    public static final int COOLDOWN_TICKS = 10;
    public static final double MIN_DISTANCE = 5.0D;
    public static final double BONUS_DISTANCE = 15.0D;

    public RainBlinkAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, BlinkAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 InitStands :62-65: a StandAction, no Stand task after it fired
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(COOLDOWN_TICKS);
        // 1.16 builder: ignoresPerformerStun()
        setIgnoresPerformerStun();
    }

    // 1.16 InitStands :62: a StandAction, held and performed by its user's
    // power (its Stand while out), never a task of the Stand
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user =
                context != null ? context.getUser() : null;
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(user);
        if (user == null
                || controller == null
                        && !CatchTheRainbowAddon.summonsOnPress(user)) {
            return ConditionCheck.createNegative(
                    "catch_the_rainbow_not_summoned");
        }
        if (!CatchTheRainbowAddon.isCreative(user)
                && !RainExposure.isExposedToRain(user)) {
            return ConditionCheck.createNegative("needs_rain");
        }
        if (!CatchTheRainbowAddon.isCreative(user)
                && controller != null
                && controller.isMergeActive()) {
            return ConditionCheck.createNegative(
                    "dont_have_merge");
        }
        // 1.16 CatchTheRainbowTeleport refused at getStamina() <= 25, with
        // no Resolve exemption; the 60 cost itself was paid unchecked.
        StandPower power = StandPower.get(user);
        if (!CatchTheRainbowAddon.isCreative(user)
                && (power == null
                        || power.getStamina()
                                <= LEGACY_MINIMUM_STAMINA)) {
            return ConditionCheck.createNegative(
                    "not_enough_stamina");
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class BlinkAction
            extends EntityActionInstance {
        public BlinkAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 InitStands :58-65: a StandAction its user's power performed,
        // which a new Stand's summon lock never held back
        @Override
        public boolean isHeldBackBySummonLock() {
            return false;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            CatchTheRainbowEntity controller =
                    CatchTheRainbowAddon.controller(user);
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            boolean creative =
                    CatchTheRainbowAddon.isCreative(user);
            if (user == null
                    || controller == null
                    || power == null
                    || !creative
                            && (!RainExposure
                                            .isExposedToRain(user)
                                    || controller.isMergeActive()
                                    || power.getStamina()
                                            <= LEGACY_MINIMUM_STAMINA)) {
                startRecovery();
                return;
            }
            // 1.16 StandAction.onPerform paid the 60 before the blink with
            // the result ignored; the distance reads the bar after it.
            StandAbilityStamina.consume(
                    ability, power, creative ? 0.0F : STAMINA_COST, false);

            double ratio = power.getMaxStamina() > 0.0F
                    ? power.getStamina()
                            / power.getMaxStamina()
                    : 0.0D;
            double distance = user.hasEffect(
                    ModStatusEffects.RESOLVE)
                            ? MIN_DISTANCE + BONUS_DISTANCE
                            : MIN_DISTANCE
                                    + BONUS_DISTANCE * ratio;
            Vec3 destination = destination(
                    level(), user, distance);
            if (destination != null
                    && isSafe(level(), user, destination)) {
                user.teleportTo(
                        destination.x,
                        destination.y,
                        destination.z);
                user.resetFallDistance();
                level().playSound(
                        null,
                        user.blockPosition(),
                        CatchTheRainbowAddon
                                .RAIN_BLINK_SOUND.get(),
                        user.getSoundSource(),
                        1.0F,
                        1.0F);
            }
        }

        private static Vec3 destination(
                Level level,
                LivingEntity user,
                double distance) {
            Vec3 start = user.getEyePosition();
            Vec3 fullEnd = start.add(
                    user.getLookAngle().scale(distance));
            BlockHitResult blockHit = level.clip(
                    new ClipContext(
                            start,
                            fullEnd,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            user));
            Vec3 end = blockHit.getType()
                            == HitResult.Type.BLOCK
                    ? blockHit.getLocation()
                    : fullEnd;

            LivingEntity target = firstTarget(
                    level, user, start, end);
            if (target != null) {
                double gap = target.getBbWidth() + user.getBbWidth();
                if (user.distanceToSqr(target) <= gap * gap) {
                    return user.position();
                }
                return target.position().subtract(
                        user.getLookAngle().scale(gap));
            }
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockPos hit = blockHit.getBlockPos();
                BlockPos destination = level.getBlockState(
                                hit.above()).canBeReplaced()
                        ? hit.above()
                        : hit.relative(blockHit.getDirection());
                return Vec3.atBottomCenterOf(destination);
            }
            return fullEnd.subtract(
                    0.0D, user.getEyeHeight(), 0.0D);
        }

        private static LivingEntity firstTarget(
                Level level,
                LivingEntity user,
                Vec3 start,
                Vec3 end) {
            AABB search = user.getBoundingBox()
                    .expandTowards(end.subtract(start))
                    .inflate(0.35D);
            LivingEntity best = null;
            double bestDistance = start.distanceToSqr(end);
            for (Entity candidate : level.getEntities(
                    user,
                    search,
                    entity -> entity instanceof LivingEntity
                            && entity.isAlive())) {
                LivingEntity living =
                        candidate instanceof StandEntity stand
                                && stand.getUser() != null
                                ? stand.getUser()
                                : (LivingEntity) candidate;
                if (living == user) {
                    continue;
                }
                AABB hitbox = living.getBoundingBox()
                        .inflate(
                                living.getPickRadius()
                                        + 0.35D);
                Optional<Vec3> intersection =
                        hitbox.contains(start)
                                ? Optional.of(start)
                                : hitbox.clip(start, end);
                if (intersection.isEmpty()) {
                    continue;
                }
                double hitDistance = start.distanceToSqr(
                        intersection.get());
                if (hitDistance < bestDistance) {
                    bestDistance = hitDistance;
                    best = living;
                }
            }
            return best;
        }

        private static boolean isSafe(
                Level level,
                LivingEntity user,
                Vec3 destination) {
            BlockPos position =
                    BlockPos.containing(destination);
            if (!level.hasChunkAt(position)) {
                return false;
            }
            AABB moved = user.getBoundingBox().move(
                    destination.subtract(user.position()));
            return level.noCollision(user, moved);
        }
    }
}
