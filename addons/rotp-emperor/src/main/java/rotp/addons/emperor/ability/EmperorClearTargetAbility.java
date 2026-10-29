package rotp.addons.emperor.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;

import rotp.addons.emperor.EmperorAddon;
import rotp.addons.emperor.entity.EmperorStandEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class EmperorClearTargetAbility extends Ability {
    public static final int RESOLVE_LEVEL = 3;

    public EmperorClearTargetAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        resolveLevelToUnlock(RESOLVE_LEVEL);
        isSubAbility = true;
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        if (!super.isAbilityAvailable(context)) {
            return false;
        }
        Player player = EmperorTargetModeAbility.activePlayer(context);
        EmperorStandEntity controller =
                player != null ? EmperorAddon.controller(player) : null;
        return controller != null && controller.hasTarget();
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        // 1.16 no_stand_target did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        return super.checkSpecificConditions(context);
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput) {
        if (!level.isClientSide()
                && user instanceof Player player) {
            EmperorStandEntity controller =
                    EmperorAddon.controller(player);
            if (controller != null) {
                controller.clearTarget();
            }
        }
    }
}
