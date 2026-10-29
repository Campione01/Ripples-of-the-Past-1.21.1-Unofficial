package rotp.addons.greenday.ability;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.util.functions.StatusEffectUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.greenday.GreenDayAddon;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class GreenDayHealAbility
        extends StandEntityAbility {
    public static final float STAMINA_PER_TICK = 6.0F;
    public static final int HEAL_INTERVAL_TICKS = 60;
    public static final float HEAL_FRACTION = 0.25F;

    public GreenDayHealAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, HealAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        standAutoSummonMode(AutoSummonMode.FULL);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 gd_heal (072 InitStands:67) set no standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        setButtonHoldPhase(ActionPhase.PERFORM);
        resolveLevelToUnlock(4);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck parent =
                super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        if (!(context instanceof StandPower power)
                || power.getPowerType()
                        != GreenDayAddon.GREEN_DAY.get()) {
            return ConditionCheck.NEGATIVE;
        }
        LivingEntity user = power.getUser();
        if (user == null
                || (!user.hasEffect(ModStatusEffects.BLEEDING)
                        && user.getHealth()
                                >= user.getMaxHealth())) {
            return ConditionCheck.NEGATIVE;
        }
        return parent;
    }

    // 1.16 PowerBaseImpl.tickHeldAction reran HealMold.checkSpecificConditions
    // on every held tick: a healed user who no longer bleeds ends the heal,
    // silently, before it pays another tick of stamina.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkSpecificConditions(context);
    }

    public static final class HealAction
            extends EntityActionInstance {
        public HealAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || power == null) {
                startRecovery();
                return;
            }
            // 1.16 StandEntityTask paid staminaCostTick(6) each perform tick
            // and ignored the result: the heal holds on an empty bar
            StandAbilityStamina.consume(
                    ability, power, STAMINA_PER_TICK, true);
            if ((int) getPhaseTick()
                            % HEAL_INTERVAL_TICKS
                    != HEAL_INTERVAL_TICKS - 1) {
                return;
            }

            MobEffectInstance bleeding =
                    user.getEffect(ModStatusEffects.BLEEDING);
            if (bleeding != null) {
                StatusEffectUtil.reduceEffect(
                        user,
                        ModStatusEffects.BLEEDING,
                        bleeding.getDuration() / 2,
                        1);
                user.playSound(
                        GreenDayAddon.GD_MOLD.get(),
                        1.0F,
                        1.0F);
            }
            user.heal(user.getMaxHealth() * HEAL_FRACTION);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.PERFORM) {
                startRecovery();
            }
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return getPhase() == ActionPhase.PERFORM
                    || super.canBeCancelledInto(
                            cancellingAbility);
        }

        // 1.16 gd_heal (HealMold) was a holdType Stand action
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // 1.16 StandEntityAction.canClickDuringTask :495-497: not chainable
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
