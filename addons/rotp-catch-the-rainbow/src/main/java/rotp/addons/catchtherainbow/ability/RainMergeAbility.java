package rotp.addons.catchtherainbow.ability;

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
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;

public final class RainMergeAbility extends StandEntityAbility {
    public static final float ACTIVATION_STAMINA_COST = 50.0F;
    public static final int HOLD_TICKS = 5;
    public static final int COOLDOWN_TICKS = 50;

    public RainMergeAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, MergeAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 InitStands :62-65: a StandAction, no Stand task after it fired
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(COOLDOWN_TICKS);
        // 1.16 builder: ignoresPerformerStun()
        setIgnoresPerformerStun();
    }

    // 1.16 InitStands :63: a StandAction, held and performed by its user's
    // power (its Stand while out), never a task of the Stand
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 holdToFire(5, false) set the cooldown when the hold fired,
    // so a cancelled hold leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 rain_merge was a StandAction its user's power performed in the
    // tick the hold fired, the 5th from the press, not the Stand's next one.
    @Override
    public boolean chargeFiresAsItsLengthEnds(EntityActionInstance action) {
        return true;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user =
                context != null ? context.getUser() : null;
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(user);
        if (user == null
                || controller == null
                        && !CatchTheRainbowAddon.summonsOnPress(user)) {
            return ConditionCheck.createNegative(
                    "catch_the_rainbow_not_summoned");
        }
        // 1.16 CatchTheRainbowRainMerge checked the same things to end
        // Merge as to start it, and charged its 50 stamina both ways.
        if (!CatchTheRainbowAddon.isCreative(user)
                && !RainExposure.isExposedToRain(user)) {
            return ConditionCheck.createNegative("needs_rain");
        }
        if (!CatchTheRainbowAddon.isCreative(user)
                && (controller != null && controller.isTempHealthActive()
                        || TempHealthAbility.hasOwnedAbsorption(user)
                        || user.hasEffect(MobEffects.ABSORPTION)
                        || user.hasEffect(
                                CatchTheRainbowAddon
                                        .RAIN_REDEMPTION_EFFECT))) {
            return ConditionCheck.createNegative(
                    "dont_have_redemption_or_temp_health");
        }
        // 1.16 refused at 50 stamina or less, Resolve or not.
        if (!hasMoreThanActivationStamina(user)) {
            return ConditionCheck.createNegative(
                    "not_enough_stamina");
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkSpecificConditions(context);
    }

    private static boolean hasMoreThanActivationStamina(
            LivingEntity user) {
        if (CatchTheRainbowAddon.isCreative(user)) {
            return true;
        }
        StandPower power = StandPower.get(user);
        return power != null
                && power.getStamina() > ACTIVATION_STAMINA_COST;
    }

    @Override
    public String getSpriteName(Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(user);
        return controller != null && controller.isMergeActive()
                ? CatchTheRainbowAddon.RAIN_MERGE_NAME
                        + "_disable"
                : CatchTheRainbowAddon.RAIN_MERGE_NAME;
    }

    public static final class MergeAction
            extends EntityActionInstance {
        public MergeAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 InitStands :58-65: a StandAction its user's power performed,
        // which a new Stand's summon lock never held back
        @Override
        public boolean isHeldBackBySummonLock() {
            return false;
        }

        // 1.16 holdToFire(5, false): letting go before the charge
        // completes cancels the merge.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            // 1.16 PowerBaseImpl kept the held merge through its Stand's
            // unsummon; CatchTheRainbowRainMerge.perform then toggled
            // nothing but still took the cost and the cooldown and played
            // the sound at its user.
            CatchTheRainbowEntity stand =
                    getPerformer() instanceof CatchTheRainbowEntity performer
                            ? performer
                            : null;
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            boolean creative =
                    CatchTheRainbowAddon.isCreative(user);
            if (user == null
                    || power == null
                    || stand == null
                            && !CatchTheRainbowAddon.summonsOnPress(user)) {
                startRecovery();
                return;
            }
            if (!creative
                            && (!RainExposure.isExposedToRain(user)
                                    || stand != null
                                            && stand.isTempHealthActive()
                                    || TempHealthAbility
                                            .hasOwnedAbsorption(user)
                                    || user.hasEffect(MobEffects.ABSORPTION)
                                    || user.hasEffect(
                                            CatchTheRainbowAddon
                                                    .RAIN_REDEMPTION_EFFECT)
                                    || !hasMoreThanActivationStamina(user))) {
                startRecovery();
                return;
            }
            // 1.16 StandAction.onPerform: the cost is paid with the result
            // ignored.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    creative ? 0.0F : ACTIVATION_STAMINA_COST,
                    false);
            if (ability instanceof RainMergeAbility merge) {
                merge.setCooldownOnUse(power);
            }
            if (stand == null) {
                level().playSound(
                        null,
                        user.blockPosition(),
                        CatchTheRainbowAddon.MERGE_SOUND.get(),
                        user.getSoundSource(),
                        1.0F,
                        1.0F);
                return;
            }
            boolean enable = !stand.isMergeActive();
            stand.setMergeActive(enable);
            if (!enable) {
                user.removeEffect(
                        CatchTheRainbowAddon
                                .RAIN_MERGE_EFFECT);
            }
            StandUtil.playStandEntitySound(
                    stand,
                    CatchTheRainbowAddon.MERGE_SOUND,
                    1.0F,
                    1.0F);
        }
    }
}
