package rotp.addons.scr.ability;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.scr.SilverChariotRequiemAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class ChariotDarkAuraAbility
        extends StandEntityAbility {
    public static final double RANGE = 30.0D;
    public static final int MAX_HOLD_TICKS = 600;
    public static final int EFFECT_DURATION_TICKS = 100;
    public static final float START_STAMINA = 300.0F;
    public static final float STAMINA_COST_TICK = 3.0F;
    public static final int COOLDOWN_TICKS = 600;

    public ChariotDarkAuraAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, AuraAction::new);
        setDefaultPhaseLength(
                ActionPhase.PERFORM, MAX_HOLD_TICKS);
        cooldown(COOLDOWN_TICKS);
        resolveLevelToUnlock(1);
        // 1.16 cancelHeldOnGettingAttacked: a hit while the aura is held
        // ends it the way letting go does (recovery, then cooldown).
        setCancelHeldOnGettingAttacked();
    }

    // The aura is held for its whole PERFORM phase (1.16 holdType 600).
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.PERFORM;
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick): the press waits in the
    // input buffer until the held guard or barrage is let go.
    @Override
    public boolean isHoldAction() {
        return true;
    }

    // 1.16 checkStandConditions: below 300 stamina the aura neither
    // starts nor stays held, silently and whatever the Resolve.
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        return hasStartStamina(context)
                ? super.checkSpecificConditions(context)
                : ConditionCheck.NEGATIVE;
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        return base.isPositive() && !hasStartStamina(context)
                ? ConditionCheck.NEGATIVE
                : base;
    }

    private static boolean hasStartStamina(Power<?> context) {
        return !(context instanceof StandPower power)
                || power.getStamina() >= START_STAMINA;
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    public static final class AuraAction
            extends EntityActionInstance {
        private boolean cooldownSet;

        public AuraAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdType(600) with no recovery: another Stand action pressed
        // during the aura replaced it at once (isCancelable), and any other
        // action of the power ended the hold (stopHeldAction(false))
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // 1.16 stopHeldAction(false) set the cooldown of an aura replaced
        // before its recovery as well
        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            if (newAction != null) {
                setCooldown();
            }
            super.onActionCleared(newAction);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!level().isClientSide()
                    && performer instanceof StandEntity stand) {
                // 1.16 CHARIOT_REQUIEM_AURA is the scr_ability_1 event.
                StandUtil.playStandEntitySound(
                        stand,
                        SilverChariotRequiemAddon.SCR_ABILITY_1,
                        1.0F,
                        1.0F);
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(level()
                            instanceof ServerLevel serverLevel)
                    || !(performer instanceof StandEntity stand)
                    || !(ability
                            instanceof ChariotDarkAuraAbility)) {
                return;
            }
            // 1.16 PowerBaseImpl.tickHeldAction counted holdType(600) from the press, through the summon lock
            if (getFullTicksPassed() + getTicksHeldInSummonLock() >= MAX_HOLD_TICKS) {
                startRecovery();
                return;
            }

            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                startRecovery();
                return;
            }
            // 1.16 StandEntityTask paid staminaCostTick(3) and ignored the
            // result; the 300 recheck is what ends the aura
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST_TICK, true);

            int amplifier =
                    (serverLevel.getDifficulty().getId() - 1) * 3;
            double rangeSqr = RANGE * RANGE;
            for (LivingEntity target :
                    serverLevel.getEntitiesOfClass(
                            LivingEntity.class,
                            user.getBoundingBox().inflate(RANGE),
                            target -> target.isAlive()
                                    && target != user
                                    && user.distanceToSqr(target)
                                            <= rangeSqr
                                    && !target.getType().is(
                                            EntityTypeTags.UNDEAD)
                                    && !isUsersStand(
                                            target, user))) {
                applyAura(target, amplifier);
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.RECOVERY) {
                setCooldown();
            }
        }

        private void setCooldown() {
            if (cooldownSet || level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (power != null
                    && ability
                            instanceof ChariotDarkAuraAbility aura) {
                aura.setCooldownOnUse(power);
                cooldownSet = true;
            }
        }
    }

    private static boolean isUsersStand(
            LivingEntity target, LivingEntity user) {
        return target instanceof StandEntity stand
                && stand.getUser() == user;
    }

    private static void applyAura(
            LivingEntity target, int amplifier) {
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                EFFECT_DURATION_TICKS,
                amplifier));
        target.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                EFFECT_DURATION_TICKS,
                amplifier));
        target.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN,
                EFFECT_DURATION_TICKS,
                amplifier));
        target.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION,
                EFFECT_DURATION_TICKS,
                amplifier));
        target.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS,
                EFFECT_DURATION_TICKS,
                amplifier));
        target.addEffect(new MobEffectInstance(
                ModStatusEffects.STUN,
                EFFECT_DURATION_TICKS,
                amplifier));
    }
}
