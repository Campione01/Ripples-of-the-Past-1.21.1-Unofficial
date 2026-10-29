package rotp.addons.catchtherainbow.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class FallingRainBladesAbility extends Ability {
    public static final float ACTIVATION_STAMINA_COST = 100.0F;
    public static final int COOLDOWN_TICKS = 10;

    public FallingRainBladesAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        cooldown(COOLDOWN_TICKS);
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
        // 1.16 CatchTheRainbowFallingRainBlades checked rain and >100
        // stamina to end blade rain as to start it.
        if (!CatchTheRainbowAddon.isCreative(user)
                && !RainExposure.isExposedToRain(user)) {
            return ConditionCheck.createNegative("needs_rain");
        }
        StandPower power = StandPower.get(user);
        if (!CatchTheRainbowAddon.isCreative(user)
                && (power == null
                        || power.getStamina()
                                <= ACTIVATION_STAMINA_COST)) {
            return ConditionCheck.createNegative(
                    "not_enough_stamina");
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public String getSpriteName(Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(user);
        return controller != null
                        && controller.isBladeRainActive()
                ? CatchTheRainbowAddon.RAIN_BLADES_NAME
                        + "_disable"
                : CatchTheRainbowAddon.RAIN_BLADES_NAME;
    }

    @Override
    public void onClick(
            Level level,
            LivingEntity user,
            FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            return;
        }
        StandPower power = StandPower.get(user);
        if (power == null) {
            return;
        }
        autoSummonStand(user, power);
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(user);
        if (controller == null) {
            return;
        }
        boolean enable = !controller.isBladeRainActive();
        // 1.16 StandAction.onPerform charged staminaCost(100) on every
        // press, turning blade rain off included, and ignored the result.
        if (!CatchTheRainbowAddon.isCreative(user)
                && (!RainExposure.isExposedToRain(user)
                        || power.getStamina()
                                <= ACTIVATION_STAMINA_COST)) {
            return;
        }
        StandAbilityStamina.consume(
                this,
                power,
                CatchTheRainbowAddon.isCreative(user)
                        ? 0.0F
                        : ACTIVATION_STAMINA_COST,
                false);
        controller.setBladeRainActive(enable);
    }

    // 1.16 StandAction.onClick with autoSummonStand(): the press summons
    // the Stand before the toggle, as the core's Magician's Red
    // Detector does for a non-entity ability.
    private static void autoSummonStand(
            LivingEntity user, StandPower power) {
        if (!(power.getPowerType()
                instanceof EntityStandType entityStandType)) {
            return;
        }
        StandEntity standEntity = power.getSummonedStandEntity();
        if (standEntity == null) {
            entityStandType.summon(user, power, entity -> {}, true);
        }
        else if (standEntity.isArmsOnlyMode()) {
            standEntity.fullSummonFromArms();
            entityStandType.triggerFullSummonAdvancement(
                    user, standEntity);
        }
    }
}
