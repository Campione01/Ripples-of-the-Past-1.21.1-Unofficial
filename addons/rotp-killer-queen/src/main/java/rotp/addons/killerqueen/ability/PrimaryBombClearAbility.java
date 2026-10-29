package rotp.addons.killerqueen.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.KillerQueenPhase1Spec;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.server.level.ServerPlayer;

public final class PrimaryBombClearAbility
        extends StandEntityAbility {
    // 1.16 kq_entity_quit: standWindupDuration(5), default perform 1 and recovery 0, no partsRequired
    public static final int WINDUP_TICKS = 5;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 0;

    public PrimaryBombClearAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ClearAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
        resolveLevelToUnlock(1);
    }

    // 1.16 EntityQuitBomb.checkConditions asked only that Bites the Dust was
    // not armed: no bomb needed, a press with none set the bomb to the user
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (BitesTheDustKitLock.locked(context)) {
            return ConditionCheck.NEGATIVE;
        }
        return ConditionCheck.POSITIVE;
    }

    public static final class ClearAction
            extends EntityActionInstance {
        public ClearAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)
                    || !(getPowerUser()
                            instanceof ServerPlayer user)) {
                return;
            }
            KillerQueenStateService.clearPrimaryBomb(user);
            StandUtil.playStandEntitySound(
                    stand,
                    KillerQueenAddon.KILLER_QUEEN_UNBOOM,
                    1.0F,
                    1.0F);
            // 1.16 EntityQuitBomb had no stamina condition: the Stand task
            // pays the cost after standPerform and a short bar drops to 0
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    KillerQueenPhase1Spec.PRIMARY_BOMB_CLEAR_STAMINA,
                    false);
        }
    }
}
