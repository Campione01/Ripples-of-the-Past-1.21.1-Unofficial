package rotp.addons.cmoon.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

public final class GravityShiftAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST = 200.0F;
    public static final int HOLD_TO_FIRE_TICKS = 25;
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate(
                    "gravity_change", false);

    public GravityShiftAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ShiftAction::new);
        // 1.16 holdToFire(25, false), no recovery.
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        standAutoSummonMode(AutoSummonMode.FULL);
        resolveLevelToUnlock(2);
        // 1.16 cooldown(10, 5): Resolve drops the 5.
        cooldown(10, 5);
    }

    // 1.16 set the cooldown when the hold fired, so a cancelled hold
    // leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 GravitationalShift.checkStandConditions: below 200 stamina a
    // plain NEGATIVE, so no message. 1.16 asked it again on every tick of
    // the 25-tick hold.
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        return !base.isPositive()
                ? base
                : checkStamina(context);
    }

    private static ConditionCheck checkStamina(Power<?> context) {
        return ConditionCheck.noMessage(
                context instanceof StandPower power
                        && power.getStamina() >= STAMINA_COST);
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return action.getPhase() == ActionPhase.BUTTON_CHARGE
                ? checkStamina(context)
                : ConditionCheck.POSITIVE;
    }

    // 1.16 renamed the slot to its ".param" name while the shift is on.
    @Override
    public Component getName(Power<?> context) {
        if (context instanceof StandPower power
                && power.getSummonedStandEntity()
                        instanceof CMoonStandEntity stand
                && stand.isShiftActive()) {
            return abilityName(context, ".param");
        }
        return super.getName(context);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIMATION;
    }

    public static final class ShiftAction
            extends EntityActionInstance {
        public ShiftAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdToFire(25, false): letting go before the charge
        // completes cancels the shift.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        // 1.16 PowerBaseImpl.performAction set the cooldown as the hold
        // fired, in the charge's last tick; the Stand's task performed in
        // its next tick.
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            LivingEntity user = getPowerUser();
            if (newPhase == ActionPhase.PERFORM
                    && !level().isClientSide()
                    && getPerformer() instanceof CMoonStandEntity
                    && user != null
                    && ability instanceof GravityShiftAbility shift) {
                shift.setCooldownOnUse(StandPower.get(user));
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer()
                            instanceof CMoonStandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                startRecovery();
                return;
            }
            stand.setShiftActive(!stand.isShiftActive());
            StandUtil.playStandEntitySound(
                    stand,
                    CMoonAddon.C_MOON_GRAVITY_SHIFT_SOUND,
                    1.0F,
                    1.0F);
            // 1.16 StandEntityTask took staminaCost(200) after standPerform
            // and ignored the result: a short bar is drained to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
        }
    }
}
