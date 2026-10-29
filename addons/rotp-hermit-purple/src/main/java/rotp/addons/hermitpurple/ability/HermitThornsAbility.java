package rotp.addons.hermitpurple.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.hermitpurple.HermitPurpleSpec;
import net.minecraft.world.entity.LivingEntity;

public final class HermitThornsAbility
        extends StandEntityAbility {
    private static final ActionAnimIdentifier BLOCK_ANIM =
            ActionAnimIdentifier.getOrCreate("block", false);

    public HermitThornsAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ThornsAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 hp_block (061 InitStands:75) set no standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        setButtonHoldPhase(ActionPhase.PERFORM);
        // Owner rule of 2026-10-15: a Stand's own guard halves its user's
        // walk speed.
        standUserWalkSpeed(0.5F);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        StandPower power = PowerClass.STAND.cast(context);
        StandEntity stand =
                power != null
                        ? power.getSummonedStandEntity()
                        : null;
        if (stand != null
                && !(stand.canStartBlocking()
                        || stand.isStandBlocking())) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck check =
                super.checkSpecificConditions(context);
        return check.isPositive()
                ? hasStaminaLeft(power)
                : check;
    }

    // 1.16 HPThorns.checkSpecificConditions, which also ran on every held
    // tick: any stamina above 0, refused without a message.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return hasStaminaLeft(PowerClass.STAND.cast(context));
    }

    private static ConditionCheck hasStaminaLeft(StandPower power) {
        return power != null && power.getStamina() > 0.0F
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return BLOCK_ANIM;
    }

    public static final class ThornsAction
            extends EntityActionInstance {
        public ThornsAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                startRecovery();
                return;
            }
            // 1.16 staminaCostTick(1), result ignored: the held check ends
            // the block once the bar is empty.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(user),
                    HermitPurpleSpec.THORNS_STAMINA_PER_TICK,
                    true);
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        // A client's copy holds the base-moveset ability, so identity would
        // let the block's own press replace it there.
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return !isSameAbility(cancellingAbility, ability);
        }

        // 1.16 hp_block (HPThorns) was a holdType Stand action
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // 1.16 StandEntityAction.canClickDuringTask :495-497: not chainable
        // (HPThorns overrides only heldAllowsOtherAction, 061 :40)
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
