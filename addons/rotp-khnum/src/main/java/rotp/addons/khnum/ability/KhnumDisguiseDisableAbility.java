package rotp.addons.khnum.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.khnum.entity.KhnumControllerEntity;

public final class KhnumDisguiseDisableAbility
        extends NoPoseStandEntityAbility {
    public KhnumDisguiseDisableAbility(
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
                            instanceof KhnumControllerEntity
                            controller) {
                controller.clearDisguise();
            }
        }
    }
}
