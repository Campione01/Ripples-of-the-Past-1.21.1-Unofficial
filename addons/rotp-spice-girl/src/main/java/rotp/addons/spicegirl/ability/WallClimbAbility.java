package rotp.addons.spicegirl.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.spicegirl.SpiceGirlAddon;
import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class WallClimbAbility
        extends StandEntityAbility {
    // 1.16 SpiceGirlWallClimb set no stamina cost: the climb is free.
    public static final double MOVEMENT_SCALE = 1.0D / 3.0D;

    public WallClimbAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, WallClimbAction::new);
        partsRequired(StandPart.ARMS);
        setButtonHoldPhase(ActionPhase.PERFORM);
        // 1.16 spice_girl_wall_climb (067 InitStands:70) set no
        // standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        // 1.16 returned a plain NEGATIVE here: no press message.
        return user != null && user.horizontalCollision
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    public static final class WallClimbAction
            extends EntityActionInstance {
        private boolean playedVoice;

        public WallClimbAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            SpiceGirlStandEntity stand =
                    getPerformer() instanceof SpiceGirlStandEntity spiceGirl
                            ? spiceGirl
                            : null;
            if (user == null
                    || stand == null
                    || SpiceGirlAddon.activeStand(user) != stand
                    || !user.horizontalCollision) {
                startRecovery();
                return;
            }
            if (!playedVoice) {
                // 1.16 wall_climb .shout(SPICE_GIRL_HEAVY_PUNCH_VOICE)
                sayShoutOf(ability, user,
                        SpiceGirlAddon.SG_PUNCH_HEAVY_VOICE);
                playedVoice = true;
            }
            Vec3 movement = user.isCrouching()
                    ? Vec3.ZERO
                    : user.getLookAngle().scale(
                            MOVEMENT_SCALE);
            user.setDeltaMovement(movement);
            user.hurtMarked = true;
            user.fallDistance = 0.0F;
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

        // 1.16 SpiceGirlWallClimb (StandEntityAction, no isChainable) refused its own press via canClickDuringTask
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }

        // 1.16 SpiceGirlWallClimb was a holdType Stand action
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }
    }
}
