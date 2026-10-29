package rotp.addons.yellowtemperance.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;

public final class YellowTemperanceDisguiseDisableAbility
        extends NoPoseStandEntityAbility {
    public YellowTemperanceDisguiseDisableAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            int resolveLevel) {
        super(abilityType, abilityId, DisableAction::new);
        resolveLevelToUnlock(resolveLevel);
    }

    private static final class DisableAction
            extends EntityActionInstance {
        private DisableAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof YellowTemperanceControllerEntity
                            controller) {
                controller.clearDisguise();
            }
        }
    }
}
