package rotp.addons.diverdown.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class WallGlideAbility extends StandEntityAbility {
    public static final float STAMINA_COST_PER_TICK = 3.0F;
    // 1.16 InitStands:70 resolveLevelToUnlock(1)
    public static final int RESOLVE_LEVEL = 1;

    public WallGlideAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, WallGlideAction::new);
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 1);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null) {
            return ConditionCheck.createNegative("diver_down_wall");
        }
        if (insideTarget(user)) {
            return ConditionCheck.NEGATIVE_CONTINUE_HOLD;
        }
        return touchesWall(user) ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("diver_down_wall").setContinueHold();
    }

    // 1.16 DiverDownWallGlide.checkSpecificConditions: no glide while Diver
    // Down is inside a target (isInside), the hold going on
    private static boolean insideTarget(LivingEntity user) {
        DiverDownStandEntity stand = DiverDownAddon.activeStand(user);
        return stand != null && stand.isEmbedded();
    }

    private static boolean touchesWall(LivingEntity user) {
        // A player's server-side movement can be stationary against the wall.
        return user.horizontalCollision || user.level().getBlockCollisions(
                user, user.getBoundingBox().inflate(0.01D, 0.0D, 0.01D))
                .iterator().hasNext();
    }

    public static final class WallGlideAction
            extends EntityActionInstance {
        private boolean soundPlayed;

        public WallGlideAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()) {
                return;
            }

            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                startRecovery();
                return;
            }
            // 1.16 StandEntityTask took staminaCostTick(3) every perform
            // tick, off a wall too, and ignored the result: an empty bar does
            // not end the glide.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    STAMINA_COST_PER_TICK,
                    true);
            if (!touchesWall(user) || insideTarget(user)) {
                return;
            }

            if (!soundPlayed
                    && getPerformer() instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        DiverDownAddon.DIVER_DOWN_GLIDE.get(),
                        1.0F,
                        1.0F);
                soundPlayed = true;
            }

            Vec3 movement = user.isCrouching()
                    ? Vec3.ZERO
                    : user.getLookAngle().scale(1.0D / 3.0D);
            user.setDeltaMovement(movement);
            user.hurtMarked = true;
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 diver_down_glide was holdType: any other action of the power
        // that went off ended the glide (stopHeldAction(false))
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // 1.16 StandEntityAction.canClickDuringTask :495-497: not chainable
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
