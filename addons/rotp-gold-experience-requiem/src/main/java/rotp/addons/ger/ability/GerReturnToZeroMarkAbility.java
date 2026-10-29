package rotp.addons.ger.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.ger.state.GerRtzRewindService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class GerReturnToZeroMarkAbility
        extends StandEntityAbility {
    public static final int MARK_CHUNK_RADIUS = 8;

    public GerReturnToZeroMarkAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, MarkAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        standAutoSummonMode(AutoSummonMode.FULL);
    }

    public static final class MarkAction
            extends EntityActionInstance {
        public MarkAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (!(user instanceof ServerPlayer player)) {
                return;
            }
            GerRtzRewindService.Result result =
                    GerRtzRewindService.setMark(
                            serverLevel,
                            player,
                            MARK_CHUNK_RADIUS);
            if (result != GerRtzRewindService.Result.SUCCESS) {
                player.displayClientMessage(
                        Component.translatable(
                                result.translationKey()),
                        true);
            }
        }
    }
}
