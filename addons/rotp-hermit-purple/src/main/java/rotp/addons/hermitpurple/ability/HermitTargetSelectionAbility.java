package rotp.addons.hermitpurple.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.hermitpurple.network.HermitTargetPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HermitTargetSelectionAbility
        extends NoPoseStandEntityAbility {
    public HermitTargetSelectionAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, TargetAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    public static final class TargetAction
            extends EntityActionInstance {
        public TargetAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPowerUser()
                            instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(
                        player,
                        HermitTargetPayloads
                                .createOpenPayload(player));
            }
        }
    }
}
