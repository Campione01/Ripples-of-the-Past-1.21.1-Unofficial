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
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class RainRedemptionAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST = 380.0F;
    public static final int HOLD_TICKS = 20;
    public static final int DURATION_TICKS = 12000;
    public static final int COOLDOWN_TICKS = 12000;
    public static final ResourceLocation HEALTH_MODIFIER =
            CatchTheRainbowAddon.id("rain_redemption_health");

    public RainRedemptionAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                RainRedemptionAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 InitStands :62-65: a StandAction, no Stand task after it fired
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        resolveLevelToUnlock(1);
        cooldown(COOLDOWN_TICKS);
        // 1.16 builder: ignoresPerformerStun()
        setIgnoresPerformerStun();
    }

    // 1.16 InitStands :65: a StandAction, held and performed by its user's
    // power (its Stand while out), never a task of the Stand
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 holdToFire(20, true) set the cooldown when the hold fired,
    // so a cancelled hold leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
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
        if (!CatchTheRainbowAddon.isCreative(user)
                && !RainExposure.isExposedToRain(user)) {
            return ConditionCheck.createNegative("needs_rain");
        }
        if (!CatchTheRainbowAddon.isCreative(user)
                && user.getHealth() > user.getMaxHealth() * 0.3F) {
            return ConditionCheck.createNegative(
                    "not_enough_damage");
        }
        // 1.16 CatchTheRainbowRainRedemption refused at getStamina() < 380,
        // with no Resolve exemption.
        StandPower power = StandPower.get(user);
        if (!CatchTheRainbowAddon.isCreative(user)
                && (power == null
                        || power.getStamina() < STAMINA_COST)) {
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

    public static final class RainRedemptionAction
            extends EntityActionInstance {
        public RainRedemptionAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 InitStands :58-65: a StandAction its user's power performed,
        // which a new Stand's summon lock never held back
        @Override
        public boolean isHeldBackBySummonLock() {
            return false;
        }

        // 1.16 holdToFire(20, true): a full charge waits until the key
        // is let go and fires then; letting go earlier cancels it.
        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return getPhase() == ActionPhase.BUTTON_CHARGE;
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                if (getPhaseTick() >= getCurPhaseLength()) {
                    setPhaseStart(ActionPhase.WINDUP);
                }
                else {
                    forceStop();
                }
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            // 1.16 PowerBaseImpl kept the held charge through its Stand's
            // unsummon; CatchTheRainbowRainRedemption.perform needs no Stand.
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
                            && !CatchTheRainbowAddon.summonsOnPress(user)
                    || !creative
                            && (!RainExposure
                                            .isExposedToRain(user)
                                    || user.getHealth()
                                            > user.getMaxHealth()
                                                    * 0.3F
                                    || power.getStamina() < STAMINA_COST)) {
                startRecovery();
                return;
            }
            // 1.16 StandAction.onPerform: the cost is paid with the result
            // ignored.
            StandAbilityStamina.consume(
                    ability, power, creative ? 0.0F : STAMINA_COST, false);
            if (ability instanceof RainRedemptionAbility redemption) {
                redemption.setCooldownOnUse(power);
            }
            if (stand != null) {
                stand.setMergeActive(false);
            }
            user.removeEffect(
                    CatchTheRainbowAddon.RAIN_MERGE_EFFECT);
            // 1.16 ended the Temp Health cut only while Absorption remained;
            // curing that effect kept the cut in Redemption's calculation.
            if (user.hasEffect(MobEffects.ABSORPTION)) {
                TempHealthAbility.endMaxHealthCut(user);
            }
            user.addEffect(new MobEffectInstance(
                    CatchTheRainbowAddon
                            .RAIN_REDEMPTION_EFFECT,
                    DURATION_TICKS,
                    0,
                    false,
                    false,
                    true));
            // 1.16 gave Saturation 255 for a tick (a full food bar) and
            // played the merge sound it was registered with.
            user.addEffect(new MobEffectInstance(
                    MobEffects.SATURATION,
                    1,
                    255,
                    false,
                    false,
                    false));
            level().playSound(
                    null,
                    user.blockPosition(),
                    CatchTheRainbowAddon.MERGE_SOUND.get(),
                    user.getSoundSource(),
                    1.0F,
                    1.0F);
        }
    }
}
