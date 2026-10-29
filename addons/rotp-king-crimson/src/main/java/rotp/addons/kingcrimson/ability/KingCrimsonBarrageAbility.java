package rotp.addons.kingcrimson.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonDonorGameplaySpec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class KingCrimsonBarrageAbility
        extends StandEntityBarrageAbility {
    private static final String DOPPIO_NAME =
            "kingcrimson_doppio_barrage";
    private static final ActionAnimIdentifier BARRAGE_ANIMATION =
            ActionAnimIdentifier.getOrCreate("barrage", false);

    private final boolean doppio;

    public KingCrimsonBarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        this(abilityType, abilityId, false);
    }

    public static KingCrimsonBarrageAbility doppio(
            AbilityType<?> abilityType, AbilityId abilityId) {
        return new KingCrimsonBarrageAbility(
                abilityType, abilityId, true);
    }

    private KingCrimsonBarrageAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            boolean doppio) {
        super(abilityType, abilityId, KingCrimsonBarrage::new);
        this.doppio = doppio;
        cooldown(
                KingCrimsonDonorGameplaySpec
                        .BARRAGE_COOLDOWN_TICKS);
        if (doppio) {
            partsRequired(StandPart.ARMS);
            standAutoSummonMode(AutoSummonMode.ARMS);
            isSubAbility = true;
        }
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return BARRAGE_ANIMATION;
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        Ability shared =
                super.replaceWithSubAbility(context, abilities);
        if (doppio || shared != this || abilities == null) {
            return shared;
        }
        StandPower power = PowerClass.STAND.cast(context);
        if (usesDoppioBarrage(power)) {
            return abilities.getContextVariationOrDisable(
                    name(), DOPPIO_NAME);
        }
        return this;
    }

    // 1.16 PowerBaseImpl.stopHeldAction: a hold-only action gets its cooldown when the hold ends.
    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck shared =
                super.checkSpecificConditions(context);
        return shared.isPositive()
                ? checkHealthGate(context)
                : shared;
    }

    // 1.16 PowerBaseImpl.tickHeldAction ran checkStandConditions again on every held tick.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck shared =
                super.checkHeldSpecificConditions(action, context);
        return shared.isPositive()
                ? checkHealthGate(context)
                : shared;
    }

    private ConditionCheck checkHealthGate(Power<?> context) {
        StandPower power = PowerClass.STAND.cast(context);
        LivingEntity user = context != null
                ? context.getUser()
                : null;
        if (!doppio && usesDoppioBarrage(power)) {
            return ConditionCheck.POSITIVE;
        }
        // 1.16 KCConfig barrageHealthThreshold(Doppio)
        float threshold =
                KingCrimsonConfig.barrageHealthThreshold(doppio);
        if (user instanceof Player player
                && !player.getAbilities().instabuild
                && power != null
                && power.getResolveLevel()
                        >= KingCrimsonDonorGameplaySpec
                                .HIGH_RESOLVE_LEVEL
                && user.getMaxHealth() > 0.0F
                && user.getHealth() / user.getMaxHealth()
                        > threshold) {
            return ConditionCheck.createNegative(
                    "rotp_kingcrimson.message."
                            + (doppio
                                    ? "barrage_health_doppio"
                                    : "barrage_health"));
        }
        return ConditionCheck.POSITIVE;
    }

    private static boolean usesDoppioBarrage(
            StandPower power) {
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        return stand == null || stand.isArmsOnlyMode();
    }

    /**
     * Sets the cooldown once, on the server, when the hold ends: RECOVERY (release, a failed held check,
     * the maximum duration, a heavy hit) or the action being cleared before it.
     */
    private static final class KingCrimsonBarrage
            extends StandEntityBarrage {
        private boolean cooldownSet;
        private boolean cooldownSetOnClear;
        private int cooldownBeforeClear;
        private int cooldownTotalBeforeClear;

        private KingCrimsonBarrage(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            // Input rollback reinstalls this same action after its removal callback ran.
            if (cooldownSetOnClear && !level().isClientSide()) {
                cooldownSetOnClear = false;
                cooldownSet = false;
                StandPower power = userPower();
                if (power != null && ability instanceof Ability barrage) {
                    power.setAbilityCooldown(barrage.name(),
                            cooldownBeforeClear, cooldownTotalBeforeClear);
                }
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.RECOVERY) {
                setHoldEndCooldown();
            }
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            super.onActionCleared(newAction);
            if (newAction == this || cooldownSet || level().isClientSide()) {
                return;
            }
            StandPower power = userPower();
            if (power != null && ability instanceof Ability barrage) {
                cooldownBeforeClear = power.getAbilityCooldown(barrage.name());
                cooldownTotalBeforeClear = power.getAbilityCooldownTotal(barrage.name());
                cooldownSetOnClear = true;
                setHoldEndCooldown();
            }
        }

        private StandPower userPower() {
            LivingEntity user = getPowerUser();
            return user != null ? StandPower.get(user) : null;
        }

        private void setHoldEndCooldown() {
            if (cooldownSet || level().isClientSide()) {
                return;
            }
            StandPower power = userPower();
            if (power != null && ability instanceof Ability barrage) {
                barrage.setCooldownOnUse(power);
                cooldownSet = true;
            }
        }
    }
}
