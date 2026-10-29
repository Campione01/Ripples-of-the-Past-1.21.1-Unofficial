package rotp.addons.yellowtemperance.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class YellowTemperanceToggleDollAbility
        extends NoPoseStandEntityAbility {
    public YellowTemperanceToggleDollAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ToggleAction::new);
        resolveLevelToUnlock(2);
    }

    private static final class ToggleAction
            extends EntityActionInstance {
        private ToggleAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof YellowTemperanceControllerEntity
                            controller) {
                boolean enabled = !controller.isDollEnabled();
                controller.setDollEnabled(enabled);
                if (controller.getUser() instanceof Player player) {
                    player.displayClientMessage(
                            Component.translatable(enabled
                                    ? "action.rotp_yellowtemperance.toggle_doll.on"
                                    : "action.rotp_yellowtemperance.toggle_doll.off"),
                            true);
                }
            }
        }
    }
}
