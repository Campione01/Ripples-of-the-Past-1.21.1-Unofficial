package rotp.addons.badcompany.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.badcompany.entity.BadCompanyStandEntity;
import net.minecraft.world.entity.LivingEntity;

public final class ToMeAbility extends StandEntityAbility {
    public static final int VOLLEY_INTERVAL_TICKS = 5;
    public static final float STAMINA_PER_VOLLEY = 20.0F;

    public ToMeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ToMeAction::new);
        partsRequired(StandPart.MAIN_BODY);
        setButtonHoldPhase(ActionPhase.PERFORM);
        // 1.16 to_me (055 InitStands:56) set no standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    public static final class ToMeAction
            extends EntityActionInstance {
        public ToMeAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof BadCompanyStandEntity
                                    coordinator) {
                coordinator.setVolleyMode(true);
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()
                    || (int) getPhaseTick()
                            % VOLLEY_INTERVAL_TICKS != 0
                    || !(getPerformer()
                            instanceof BadCompanyStandEntity
                                    coordinator)) {
                return;
            }

            LivingEntity owner = getPowerUser();
            StandPower power =
                    owner != null ? StandPower.get(owner) : null;
            if (owner == null
                    || power == null
                    || !coordinator.hasVolleyUnits(owner)) {
                return;
            }
            // 1.16 SoldierFrontLineAction paid a non-ticking consumeStamina(20) after the volley, at once, and
            // ignored the result: an empty bar stays at 0
            coordinator.commandVolley(owner);
            StandAbilityStamina.consume(
                    ability,
                    power,
                    STAMINA_PER_VOLLEY,
                    false);
        }

        @Override
        public void onButtonStopHold() {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof BadCompanyStandEntity
                                    coordinator) {
                coordinator.setVolleyMode(false);
            }
            startRecovery();
        }

        @Override
        public void onActionCleared(
                EntityActionInstance newAction) {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof BadCompanyStandEntity
                                    coordinator) {
                coordinator.setVolleyMode(false);
            }
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 to_me (SoldierFrontLineAction) was a holdType Stand action
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
