package rotp.addons.killerqueen.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class SheerHeartRecallAbility
        extends StandEntityAbility {
    public static final int WINDUP_TICKS = 0;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 0;

    public SheerHeartRecallAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, RecallAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return super.isAbilityAvailable(context)
                && context.getUser() instanceof Player player
                && KillerQueenAddon.state(player)
                        .hasSheerHeart();
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        // 1.16 SHABack: refused while Bites the Dust is armed
        if (check.isPositive()
                && BitesTheDustKitLock.locked(context)) {
            return ConditionCheck.NEGATIVE;
        }
        return check.isPositive()
                && context.getUser() instanceof Player player
                && KillerQueenAddon.state(player)
                        .hasSheerHeart()
                                ? check
                                : ConditionCheck.createNegative(
                                        "killer_queen_no_sheer_heart");
    }

    public static final class RecallAction
            extends EntityActionInstance {
        public RecallAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof StandEntity stand
                    && getPowerUser()
                            instanceof ServerPlayer user
                    && KillerQueenStateService
                            .recallSheerHeart(user)) {
                StandUtil.playStandEntitySound(
                        stand,
                        KillerQueenAddon.KILLER_QUEEN_UNSUMMON,
                        1.0F,
                        1.0F);
            }
        }
    }
}
