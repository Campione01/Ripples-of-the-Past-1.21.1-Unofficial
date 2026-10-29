package rotp.addons.lovers.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.lovers.LoversAddon;
import rotp.addons.lovers.state.LoversLinkService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class RetractAbility
        extends NoPoseStandEntityAbility {
    public static final int COOLDOWN_TICKS = 6;

    public RetractAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, RetractAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(COOLDOWN_TICKS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        return LoversAddon.activeStand(user) != null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    public static final class RetractAction
            extends EntityActionInstance {
        public RetractAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPowerUser()
                            instanceof ServerPlayer owner) {
                boolean cleared =
                        LoversLinkService.clearOwner(owner);
                owner.displayClientMessage(
                        Component.translatable(
                                cleared
                                        ? "rotp_lovers.message.retracted"
                                        : "rotp_lovers.message.no_link"),
                        false);
            }
        }
    }
}
