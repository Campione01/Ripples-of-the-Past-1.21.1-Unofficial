package rotp.addons.yellowtemperance.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.yellowtemperance.disguise.DisguiseClientBridge;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class YellowTemperanceDisguiseAbility
        extends NoPoseStandEntityAbility {
    private final DisguiseMode mode;
    private final String disableName;

    public YellowTemperanceDisguiseAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            DisguiseMode mode,
            String disableName,
            int resolveLevel) {
        super(
                abilityType,
                abilityId,
                type -> new DisguiseAction(type, mode));
        this.mode = mode;
        this.disableName = disableName;
        resolveLevelToUnlock(resolveLevel);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        var power = user != null
                ? rotp.core.powersystem
                        .standpower.StandPower.get(user)
                : null;
        if (power != null
                && power.getSummonedStandEntity()
                                instanceof YellowTemperanceControllerEntity
                                controller
                && controller.getDisguiseMode() == mode) {
            Ability disable =
                    abilities.getContextVariation(disableName);
            return disable != null ? disable : this;
        }
        return this;
    }

    private static final class DisguiseAction
            extends EntityActionInstance {
        private final DisguiseMode mode;

        private DisguiseAction(
                EntityActionType ability, DisguiseMode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void actionPerformStart() {
            if (!(getPerformer()
                    instanceof YellowTemperanceControllerEntity
                    controller)) {
                forceStop();
                return;
            }
            if (level().isClientSide()) {
                LivingEntity user = controller.getUser();
                if (user instanceof Player player && player.isLocalPlayer()) {
                    DisguiseClientBridge.open(mode);
                }
            }
            else {
                controller.beginSelection(mode);
            }
        }
    }
}
