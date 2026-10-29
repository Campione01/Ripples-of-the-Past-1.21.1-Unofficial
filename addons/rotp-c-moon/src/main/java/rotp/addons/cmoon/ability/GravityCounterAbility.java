package rotp.addons.cmoon.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.cmoon.entity.CMoonStandEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class GravityCounterAbility
        extends StandEntityAbility {
    public static final float REQUIRED_STAMINA = 75.0F;
    public static final float STAMINA_PER_TICK = 1.0F;
    public static final int PERFORM_TICKS = 20;
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate("block", false);

    public GravityCounterAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CounterAction::new);
        // 1.16 standPerformDuration(20), no recovery. It was a click
        // action, so letting go of the key does not end the counter.
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        standAutoSummonMode(AutoSummonMode.FULL);
        // 1.16 cooldown(100, 75): Resolve drops the 75.
        cooldown(100, 75);
    }

    // 1.16 GravityCounter.checkStandConditions: below 75 stamina a plain
    // NEGATIVE, so no message.
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        return !base.isPositive()
                ? base
                : ConditionCheck.noMessage(
                        context instanceof StandPower power
                                && power.getStamina() >= REQUIRED_STAMINA);
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (level.isClientSide()) {
            return null;
        }
        StandPower power = StandPower.get(user);
        if (power == null || power.getStamina() < REQUIRED_STAMINA) {
            return null;
        }
        return super.onKeyPress(level, user, extraClientInput,
                inputMethod, clickHoldResolveTime, bufferingState);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIMATION;
    }

    public static final class CounterAction
            extends EntityActionInstance {
        public CounterAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            setCounter(true);
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                setCounter(false);
                startRecovery();
                return;
            }
            // 1.16 StandEntityTask took staminaCostTick(1) every perform
            // tick and the counter went on whatever it returned.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    STAMINA_PER_TICK,
                    true);
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase != ActionPhase.PERFORM) {
                setCounter(false);
            }
        }

        @Override
        public void onActionCleared(
                EntityActionInstance newAction) {
            setCounter(false);
        }

        private void setCounter(boolean active) {
            if (!level().isClientSide()
                    && getPerformer()
                            instanceof CMoonStandEntity stand) {
                stand.setCounterActive(active);
            }
        }
    }
}
