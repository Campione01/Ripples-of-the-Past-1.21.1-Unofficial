package rotp.addons.speedwagon.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class RunAbility extends SpeedwagonActionAbility {
    public RunAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                0.0F,
                30.0F,
                0,
                RunAction::new);
        setButtonHoldPhase(ActionPhase.PERFORM);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    // 1.16 tickHeldAction reran NonStandAction.checkEnergy every held
    // tick: too little energy ended the run with no_energy_speedwagon.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkSpecificConditions(context);
    }

    // While its key holds the run, the held recheck above ends it with
    // the message; this silent stop is left for a run no key holds.
    @Override
    public boolean canContinueAction(
            EntityActionInstance action) {
        return AbilityInput.isHeldByKey(action.getPowerUser(), action)
                || super.canContinueAction(action);
    }

    public static final class RunAction
            extends EntityActionInstance {
        private int ticksHeld;

        public RunAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null
                    || !(ability instanceof RunAbility runAbility)) {
                return;
            }
            if (!level().isClientSide()) {
                if (!runAbility.consumeHeldEnergy(user)) {
                    forceStop();
                    syncPhaseChanges();
                    return;
                }
                user.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        3,
                        0,
                        false,
                        true,
                        true));
                if (ticksHeld % 5 == 0) {
                    runAbility.syncData(user);
                }
            }
            ticksHeld++;
        }

        @Override
        public void onButtonStopHold() {
            forceStop();
            syncPhaseChanges();
        }

        @Override
        public void onActionCleared(
                EntityActionInstance newAction) {
            LivingEntity user = getPowerUser();
            if (!level().isClientSide()
                    && user != null
                    && ability instanceof RunAbility runAbility) {
                runAbility.syncData(user);
            }
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }
    }
}
