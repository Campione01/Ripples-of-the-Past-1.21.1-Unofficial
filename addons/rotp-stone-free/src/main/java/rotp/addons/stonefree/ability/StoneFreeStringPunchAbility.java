package rotp.addons.stonefree.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import rotp.addons.stonefree.state.StoneFreeState;
import javax.annotation.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class StoneFreeStringPunchAbility
        extends StandEntityHeavyPunchAbility {
    private static final double SLIDE_DISTANCE = 1.5D;
    private static final ActionAnimIdentifier FINISHER_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "finisher_punch", false);

    public StoneFreeStringPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = StringPunchAction::new;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return FINISHER_ANIM;
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        return withCaptureFollowUp(
                this,
                super.replaceWithSubAbility(context, abilities),
                context,
                abilities);
    }

    // 1.16 StoneFreeStringPunch.standMovesByItself: from the last two windup
    // ticks the Stand slides on its own and stays where it punched.
    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return movesByItself(standEntity);
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return movesByItself(standEntity);
    }

    @Override
    public boolean lockStandManualMovement(
            StandPower standPower, StandEntity standEntity) {
        return movesByItself(standEntity);
    }

    private static boolean movesByItself(StandEntity standEntity) {
        EntityActionInstance action = standEntity.getCurStandAction();
        if (!(action instanceof StringPunchAction)) {
            return false;
        }
        ActionPhase phase = action.getPhase();
        return phase == ActionPhase.WINDUP && action.getPhaseTicksLeft() <= 2
                || phase == ActionPhase.PERFORM
                || phase == ActionPhase.RECOVERY;
    }

    /**
     * 1.16 StandAction.replaceActionKostyl with
     * StoneFreeStringPunch.getRecoveryFollowup: the heavy punch pressed
     * again while the Stand's string punch runs is the capture, once per
     * punch, when the capture's own conditions pass (canSetFollowUp).
     */
    public static Ability captureFollowUp(
            Power<?> context, AvailableAbilities abilities) {
        if (abilities == null) {
            return null;
        }
        StandPower power = PowerClass.STAND.cast(context);
        StandEntity stand =
                power != null ? power.getSummonedStandEntity() : null;
        if (stand == null
                || !(stand.getCurStandAction()
                        instanceof StringPunchAction punch)
                || !punch.canQueueCapture()) {
            return null;
        }
        Ability capture = abilities.getContextVariation(
                StoneFreeAddon.STRING_CAPTURE_NAME);
        return capture != null
                        && abilities.getConditionCheck(
                                StoneFreeAddon.STRING_CAPTURE_NAME)
                                .isPositive()
                ? capture
                : null;
    }

    private static Ability withCaptureFollowUp(
            Ability self,
            Ability shared,
            Power<?> context,
            AvailableAbilities abilities) {
        if (shared != self) {
            return shared;
        }
        Ability capture = captureFollowUp(context, abilities);
        return capture != null ? capture : self;
    }

    /**
     * 1.16 StoneFreeStringCapture was a StandEntityActionModifier: it joined
     * the running string punch (stamina and cooldown on use) and never ran
     * as a task of its own. The strings go off in the punch's recovery.
     */
    public static void queueCapture(
            Ability capture,
            float staminaCost,
            Level level,
            LivingEntity user,
            BufferingState bufferingState) {
        if (level.isClientSide()) {
            return;
        }
        StandPower power = StandPower.get(user);
        StandEntity stand =
                power != null ? power.getSummonedStandEntity() : null;
        if (stand != null
                && stand.getCurStandAction()
                        instanceof StringPunchAction punch
                && punch.canQueueCapture()) {
            // 1.16 StandAction.onPerform paid with the result ignored.
            StandAbilityStamina.consume(
                    capture, power, staminaCost, false);
            punch.queueCapture();
            bufferingState.setActionSuccess();
        }
    }

    /** Stone Free's heavy punch: pressed again during the string punch, it gives the capture. */
    public static final class HeavyPunch
            extends StandEntityHeavyPunchAbility {
        public HeavyPunch(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
        }

        @Override
        public Ability replaceWithSubAbility(
                Power<?> context, AvailableAbilities abilities) {
            return withCaptureFollowUp(
                    this,
                    super.replaceWithSubAbility(context, abilities),
                    context,
                    abilities);
        }
    }

    public static final class StringPunchAction
            extends StandEntityHeavyPunch {
        private boolean captureQueued;

        public StringPunchAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 StoneFreeStringPunch.getOffsetFromUser: noYOffset(0, 0.5) in
        // either mode, at that mode's default y and turned by the view yaw,
        // where the heavy punch it extends put the Stand in front of its
        // target; the heavy punch places it through here.
        @Override
        public boolean setStandFrontOffsetFromTarget(
                StandEntity stand,
                ActionTarget target,
                double minOffset,
                double maxOffset) {
            boolean armsOnly = stand.isArmsOnlyMode();
            double y = armsOnly
                    ? stand.offsetFromUser.armsOnlyIdleOffset.y
                    : stand.Y_OFFSET;
            setStandOffset(
                    new Vec3(0.0D, y, 0.5D),
                    StandOffsetFromUser.Rotations.HEAD,
                    armsOnly);
            return true;
        }

        @Override
        public void actionTick() {
            super.actionTick();
            if (getPhase() == ActionPhase.WINDUP
                    && performer instanceof StandEntity stand) {
                slideInWindup(stand);
            }
            trySpawnCapture(getPhase());
        }

        // 1.16 StoneFreeStringPunch.standTickWindup: one slide at 2 ticks
        // left, toward the task target by its distance less the Stand's
        // width (at most 1.5), along the look without one; at 1 tick left
        // the Stand stops.
        private void slideInWindup(StandEntity stand) {
            float ticksLeft = getPhaseTicksLeft();
            if (ticksLeft <= 1.0F) {
                stand.setDeltaMovement(Vec3.ZERO);
            }
            else if (ticksLeft <= 2.0F) {
                Vec3 targetPos = taskTargetPos(getTaskTarget(level()));
                Vec3 slide;
                if (targetPos != null) {
                    Vec3 toTarget = targetPos.subtract(
                            stand.getEyePosition());
                    slide = toTarget.normalize().scale(
                            Mth.clamp(
                                    toTarget.length() - stand.getBbWidth(),
                                    0.0D,
                                    SLIDE_DISTANCE));
                }
                else {
                    slide = stand.getLookAngle().scale(SLIDE_DISTANCE);
                }
                stand.setDeltaMovement(slide);
            }
        }

        // 1.16 ActionTarget.getTargetPos(true): an entity's eyes, a block's centre
        @Nullable
        private static Vec3 taskTargetPos(ActionTarget target) {
            return switch (target.getType()) {
                case ENTITY -> target.getEntity() != null
                        ? target.getEntity().getEyePosition()
                        : null;
                case BLOCK -> Vec3.atCenterOf(target.getBlockPos());
                default -> null;
            };
        }

        // 1.16 StoneFreeStringPunch.standPerform: the capture may go off.
        @Override
        public void actionPerformStart() {
            super.actionPerformStart();
            if (!level().isClientSide()
                    && performer instanceof StoneFreeStandEntity stand) {
                stand.setCaptureActive(true);
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            trySpawnCapture(newPhase);
        }

        // 1.16 canSetFollowUp: the capture joins this punch only once.
        public boolean canQueueCapture() {
            return !captureQueued;
        }

        void queueCapture() {
            captureQueued = true;
            trySpawnCapture(getPhase());
        }

        // 1.16 StoneFreeStringCapture.standTickRecovery.
        private void trySpawnCapture(ActionPhase phase) {
            if (captureQueued
                    && phase == ActionPhase.RECOVERY
                    && !level().isClientSide()
                    && performer instanceof StoneFreeStandEntity stand
                    && stand.isCaptureActive()) {
                spawnCaptureBurst(stand);
                stand.setCaptureActive(false);
            }
        }

        @Override
        public void onActionCleared(
                EntityActionInstance newAction) {
            if (performer instanceof StoneFreeStandEntity stand) {
                stand.setCaptureActive(false);
            }
            super.onActionCleared(newAction);
        }

        @Override
        protected float getAdditionalHeavyPunchKnockback(
                StandEntity stand) {
            return super.getAdditionalHeavyPunchKnockback(stand)
                    + 2.0F;
        }

        // 1.16: 3 strings round the aim (fewer from 97 placed strings on),
        // then one straight ahead.
        private void spawnCaptureBurst(
                StoneFreeStandEntity stand) {
            LivingEntity user = getPowerUser();
            int strings = user != null
                    ? StoneFreeState.get(user).placedStrings()
                    : 0;
            int n = strings >= 97 ? 99 - strings : 3;
            stand.playSound(
                    StoneFreeAddon.STONE_FREE_STRING.get(),
                    1.0F,
                    1.0F);
            for (int index = 0; index < n; index++) {
                float[] offsets = xRotYRotOffsets(
                        (double) index / (double) n * Math.PI * 2.0D,
                        10.0D);
                spawnCaptureString(stand, offsets[1], offsets[0]);
            }
            spawnCaptureString(stand, 0.0F, 0.0F);
        }

        private void spawnCaptureString(
                StoneFreeStandEntity stand,
                float yRotDelta,
                float xRotDelta) {
            StoneFreeStringEntity string =
                    new StoneFreeStringEntity(
                            StoneFreeStringEntity.Kind.STAND_STRING,
                            stand,
                            level());
            string.setBinding(true);
            string.setCapture();
            string.setShootingPosOf(stand);
            // 1.16 StoneFreeStringCapture.addProjectile: no shot; the string
            // moves itself out at its angle and back (moveBoundToOwner).
            string.setRotOffsets(yRotDelta, xRotDelta);
            addProjectileWithStandStats(string);
        }

        // 1.16 MathUtil.xRotYRotOffsets: {xRot, yRot} in degrees.
        static float[] xRotYRotOffsets(double angleXYRad, double z) {
            double xSq = -Math.cos(angleXYRad);
            double ySq = Math.sin(angleXYRad);
            xSq *= xSq;
            ySq *= ySq;
            double zSq = z * z;
            double angleXZ = Math.acos(
                    Math.sqrt((xSq + zSq) / (xSq + ySq + zSq)));
            double angleYZ = Math.acos(
                    Math.sqrt((ySq + zSq) / (xSq + ySq + zSq)));
            if (angleXYRad > Math.PI) {
                angleXZ *= -1;
            }
            if (angleXYRad > Math.PI / 2
                    && angleXYRad < Math.PI * 3 / 2) {
                angleYZ *= -1;
            }
            return new float[] {
                    (float) Math.toDegrees(angleYZ),
                    (float) Math.toDegrees(angleXZ)};
        }
    }
}
