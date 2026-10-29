package rotp.addons.echoes.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.echoes.init.EchoesStands;
import net.minecraft.server.level.ServerPlayer;

public final class EchoesTransitionAbility extends StandEntityAbility {
    public enum Mode {
        EVOLVE_TO_ACT2,
        EVOLVE_TO_ACT3,
        DEGRADE_TO_ACT2,
        DEGRADE_TO_ACT1
    }

    private final Mode mode;

    public EchoesTransitionAbility(
            AbilityType<?> type, AbilityId id, Mode mode) {
        super(type, id, action -> new TransitionAction(action, mode));
        this.mode = mode;
        this.spriteName = id.nameInMoveset();
        setDefaultPhaseLength(ActionPhase.WINDUP, 40);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive() || !(context instanceof StandPower power)) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        return power.getPowerType() == sourceType(mode)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate("idle", false);
    }

    private static EntityStandType sourceType(Mode mode) {
        return switch (mode) {
            case EVOLVE_TO_ACT2 -> EchoesStands.ECHOES_ACT1.get();
            case EVOLVE_TO_ACT3, DEGRADE_TO_ACT1 -> EchoesStands.ECHOES_ACT2.get();
            case DEGRADE_TO_ACT2 -> EchoesStands.ECHOES_ACT3.get();
        };
    }

    private static EntityStandType targetType(Mode mode) {
        return switch (mode) {
            case DEGRADE_TO_ACT1 -> EchoesStands.ECHOES_ACT1.get();
            case EVOLVE_TO_ACT2, DEGRADE_TO_ACT2 -> EchoesStands.ECHOES_ACT2.get();
            case EVOLVE_TO_ACT3 -> EchoesStands.ECHOES_ACT3.get();
        };
    }

    private static final class TransitionAction extends EntityActionInstance {
        private final Mode mode;

        private TransitionAction(EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void actionPerformStart() {
            if (!(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            StandPower power = StandPower.get(player);
            if (power == null || power.getPowerType() != sourceType(mode)) {
                return;
            }
            EntityStandType target = targetType(mode);
            power.setStand(target);
            target.summon(player, power);
        }
    }
}
