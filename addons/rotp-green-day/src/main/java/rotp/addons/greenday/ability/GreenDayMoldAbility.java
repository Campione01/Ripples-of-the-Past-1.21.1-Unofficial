package rotp.addons.greenday.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.greenday.GreenDayAddon;
import rotp.addons.greenday.state.GreenDayMoldState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public final class GreenDayMoldAbility extends StandEntityAbility {
    public static final int CHARGE_TICKS = 40;
    public static final int PERFORM_TICKS = 1;
    public static final int RECOVERY_TICKS = 2;
    // 1.16 InitStands:66 resolveLevelToUnlock(2)
    public static final int RESOLVE_LEVEL = 2;

    public GreenDayMoldAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, MoldAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, CHARGE_TICKS);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(
                ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY, RECOVERY_TICKS);
        standAutoSummonMode(AutoSummonMode.FULL);
        resolveLevelToUnlock(RESOLVE_LEVEL);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck parent =
                super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        return context instanceof StandPower power
                && power.getPowerType()
                        == GreenDayAddon.GREEN_DAY.get()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    @Override
    public Component getName(Power<?> context) {
        LivingEntity owner =
                context != null ? context.getUser() : null;
        boolean active = owner != null
                && owner.hasData(GreenDayAddon.MOLD_STATE)
                && GreenDayMoldState.get(owner).moldActive();
        return Component.translatable(
                getTranslationKey(
                        active ? ".deactivate" : ".activate"));
    }

    @Override
    public boolean retractsStandAfterAction(
            StandPower standPower,
            StandEntity standEntity,
            EntityActionInstance action) {
        return false;
    }

    public static final class MoldAction
            extends EntityActionInstance {
        public MoldAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity owner = getPowerUser();
            if (owner == null) {
                return;
            }
            GreenDayMoldState state =
                    GreenDayMoldState.get(owner);
            boolean activate = !state.moldActive();
            state.setMoldActive(owner, activate);
            if (!activate
                    && owner.level()
                            instanceof ServerLevel serverLevel) {
                GreenDayMoldState.removeSourceFromLevel(
                        serverLevel, owner);
            }
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                LivingEntity performer = getPerformer();
                if (performer != null) {
                    LivingComponentAction.getComponent(performer)
                            .setAction(
                                    null,
                                    SyncType.TRACKING_AND_SELF);
                }
            }
        }
    }
}
