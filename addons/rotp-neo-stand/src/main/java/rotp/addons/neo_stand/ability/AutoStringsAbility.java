package rotp.addons.neo_stand.ability;

import java.util.Random;

import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands.hierophant.HierophantGreenEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class AutoStringsAbility extends StandEntityAbility {
    public static final int MAX_PLACEMENTS = 51;
    public static final int PLACEMENTS_PER_TICK = 8;
    public static final float STAMINA_COST = 1200.0F;

    public AutoStringsAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(abilityType, abilityId, AutoStringsAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 30);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 15);
        cooldown(450, 900);
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.MAIN_BODY);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action, Level level,
            LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (!level.isClientSide() && performer instanceof StandEntity stand) {
            action.phasesLength.put(ActionPhase.PERFORM,
                    Mth.ceil(30.0D / Math.max(
                            stand.getAttackSpeed() / 8.0D, 0.125D)));
        }
    }

    @Override
    public boolean noAdheringToUserOffset(
            StandPower power, StandEntity stand) {
        return true;
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(StandEntity stand) {
        return true;
    }

    // 1.16 ab.standKeepsTarget (:201-203): the task keeps any target
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() != ActionTarget.TargetType.EMPTY;
    }

    public static final class AutoStringsAction
            extends EntityActionInstance {
        private Vec3 center;
        private double radius;
        private double angleStep;
        private double startAngle;
        private int attemptedPlacements;

        public AutoStringsAction(EntityActionType ability) {
            super(ability);
        }

        // Server: 1.16 kept the press's target as its task's, so Hierophant
        // Green faced it while it circled
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (!level().isClientSide()
                    && getPerformer() instanceof StandEntity stand) {
                keepStandAimedAtTarget(captureActionTargetFromAim(stand));
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(getPerformer()
                            instanceof HierophantGreenEntity stand)
                    || level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                forceStop();
                return;
            }

            center = user.position();
            radius = Math.min(6.0D, stand.getEffectiveRange() * 0.5D);
            angleStep = Math.PI * 2.0D
                    / Math.max(1.0D, getCurPhaseLength());
            startAngle = Math.atan2(
                    stand.getZ() - center.z,
                    stand.getX() - center.x);
            attemptedPlacements = 0;
            stand.createUserString();
            StandUtil.playStandEntitySound(
                    stand,
                    ModSoundEvents.HIEROPHANT_GREEN_TENTACLES,
                    1.0F,
                    1.0F);
            // 1.16 StandEntityTask: the cost is paid at perform start with
            // the result ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, StandPower.get(user), STAMINA_COST, false);
            performStep(stand);
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM
                    || center == null
                    || !(getPerformer()
                            instanceof HierophantGreenEntity stand)) {
                return;
            }

            performStep(stand);
        }

        private void performStep(HierophantGreenEntity stand) {
            placeBarrierBatch(stand);
            double angle =
                    startAngle + angleStep * getPhaseTick();
            Vec3 target = new Vec3(
                    center.x + Math.cos(angle) * radius,
                    stand.getY(),
                    center.z + Math.sin(angle) * radius);
            Vec3 delta = target.subtract(stand.position());
            stand.setDeltaMovement(
                    Mth.clamp(delta.x, -1.2D, 1.2D),
                    0.0D,
                    Mth.clamp(delta.z, -1.2D, 1.2D));

            if (getPhaseTicksLeft() <= 1.0F
                    && !stand.isManuallyControlled()
                    && stand.followingUserIsEnabled()) {
                stand.retract();
            }
        }

        private void placeBarrierBatch(
                HierophantGreenEntity stand) {
            int count = Math.min(
                    PLACEMENTS_PER_TICK,
                    MAX_PLACEMENTS - attemptedPlacements);
            if (count <= 0) {
                return;
            }
            LivingEntity user = getPowerUser();
            BlockPos origin = user != null
                    ? user.blockPosition()
                    : stand.blockPosition();
            for (int index = 0; index < count; index++) {
                int placement = attemptedPlacements + index;
                int pair = placement / 2;
                boolean firstOfPair = placement % 2 == 0;
                int seed = origin.getX() * 73856093
                        ^ origin.getZ() * 19349663
                        ^ pair * 83492791;
                Random random = new Random(seed);
                double baseAngle =
                        random.nextDouble() * Math.PI * 2.0D;
                double pairOffset =
                        (random.nextDouble() - 0.5D) * 0.35D;
                double angle = firstOfPair
                        ? baseAngle
                        : baseAngle + Math.PI + pairOffset;
                double choice = random.nextDouble();
                double distance = choice < 0.7D
                        ? 22.0D + random.nextDouble() * 8.0D
                        : 14.0D + random.nextDouble() * 8.0D;
                BlockPos candidate = origin.offset(
                        (int) Math.round(
                                Math.cos(angle) * distance),
                        0,
                        (int) Math.round(
                                Math.sin(angle) * distance));
                boolean preferHighest = firstOfPair
                        || random.nextDouble() < 0.9D;
                BlockPos surface = preferHighest
                        ? findHighestSurface(level(), candidate, origin.getY() - 3, 3)
                        : findDownwardSurface(level(), candidate, 3);
                if (surface == null) {
                    surface = preferHighest
                            ? findDownwardSurface(level(), candidate, 3)
                            : findHighestSurface(level(), candidate, origin.getY() - 3, 3);
                }
                for (int retry = 0;
                        surface == null && retry < 3;
                        retry++) {
                    double retryAngle = angle
                            + (random.nextDouble() - 0.5D) * 0.6D;
                    double retryDistance = distance
                            * (0.8D + random.nextDouble() * 0.4D);
                    BlockPos retryPos = origin.offset(
                            (int) Math.round(
                                    Math.cos(retryAngle)
                                            * retryDistance),
                            0,
                            (int) Math.round(
                                    Math.sin(retryAngle)
                                            * retryDistance));
                    surface = findHighestSurface(
                            level(),
                            retryPos,
                            origin.getY() - 3,
                            3);
                    if (surface == null) {
                        surface = findDownwardSurface(level(), retryPos, 3);
                    }
                }
                if (surface != null) {
                    stand.attachBarrier(surface);
                }
            }
            attemptedPlacements += count;
        }

        private static BlockPos findHighestSurface(
                Level level,
                BlockPos center,
                int minimumY,
                int radius) {
            int top = Math.min(
                    level.getMaxBuildHeight() - 1,
                    Math.max(
                            minimumY + 48,
                            center.getY() + 24));
            int bottom = Math.max(
                    level.getMinBuildHeight(),
                    minimumY);
            BlockPos best = null;
            for (int xOffset = -radius;
                    xOffset <= radius;
                    xOffset++) {
                for (int zOffset = -radius;
                        zOffset <= radius;
                        zOffset++) {
                    for (int y = top; y >= bottom; y--) {
                        BlockPos floor = new BlockPos(
                                center.getX() + xOffset,
                                y,
                                center.getZ() + zOffset);
                        if (!level.getBlockState(floor).isAir()
                                && level.getBlockState(floor).isSolid()
                                && level.isEmptyBlock(
                                        floor.above())) {
                            BlockPos surface = floor.above();
                            if (best == null
                                    || surface.getY()
                                            > best.getY()) {
                                best = surface;
                            }
                            break;
                        }
                    }
                }
            }
            return best;
        }

        private static BlockPos findDownwardSurface(
                Level level, BlockPos center, int radius) {
            BlockPos best = null;
            int startY = center.getY();
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    for (int y = startY, step = 0;
                            step < 64 && y >= level.getMinBuildHeight();
                            y--, step++) {
                        BlockPos floor = new BlockPos(
                                center.getX() + xOffset, y, center.getZ() + zOffset);
                        if (!level.getBlockState(floor).isAir()
                                && level.getBlockState(floor).isSolid()
                                && level.isEmptyBlock(floor.above())) {
                            BlockPos surface = floor.above();
                            if (best == null || surface.getY() > best.getY()) {
                                best = surface;
                            }
                            break;
                        }
                    }
                }
            }
            return best;
        }
    }
}
