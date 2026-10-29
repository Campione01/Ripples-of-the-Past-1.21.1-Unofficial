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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class TempHealthAbility extends StandEntityAbility {
    public static final float STAMINA_COST = 80.0F;
    public static final float LEGACY_MINIMUM_STAMINA = 100.0F;
    public static final int HOLD_TICKS = 10;
    public static final int COOLDOWN_TICKS = 20;
    public static final int ABSORPTION_TICKS = 37000;
    // 1.16 charged Hunger 240 for 20 ticks, about six food points.
    public static final int HUNGER_TICKS = 20;
    public static final int HUNGER_AMPLIFIER = 240;
    public static final ResourceLocation HEALTH_MODIFIER =
            CatchTheRainbowAddon.id("temp_health");
    // Marks the Absorption this ability granted; saved with the
    // player, so the mark survives a relog along with the effect.
    private static final String ABSORPTION_TAG =
            "rotp_ctr:temp_health_absorption";
    // Marks a cut made while no Stand was out: 1.16 kept it on the user,
    // since only a Stand's removal (onRemovedFromWorld) or dry weather
    // ended one.
    private static final String STANDLESS_CUT_TAG =
            "rotp_ctr:temp_health_standless_cut";

    public TempHealthAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, TempHealthAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 InitStands :62-65: a StandAction, no Stand task after it fired
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(COOLDOWN_TICKS);
        // 1.16 builder: ignoresPerformerStun()
        setIgnoresPerformerStun();
    }

    // 1.16 InitStands :64: a StandAction, held and performed by its user's
    // power (its Stand while out), never a task of the Stand
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 holdToFire(10, false) set the cooldown when the hold fired,
    // so a cancelled hold leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 temp_health was a StandAction its user's power performed in the
    // tick the hold fired, the 10th from the press, not the Stand's next one.
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
        boolean creative =
                CatchTheRainbowAddon.isCreative(user);
        boolean active = controller != null
                ? controller.isTempHealthActive()
                : hasOwnedState(user);
        if (!creative && !RainExposure.isExposedToRain(user)) {
            return ConditionCheck.createNegative("needs_rain");
        }
        if (!creative
                && (controller != null && controller.isMergeActive()
                        || user.hasEffect(
                                CatchTheRainbowAddon
                                        .RAIN_REDEMPTION_EFFECT))) {
            return ConditionCheck.createNegative(
                    "dont_have_merge_or_redemption");
        }
        if (!creative
                && !active
                && user.getHealth() > user.getMaxHealth() * 0.9F) {
            return ConditionCheck.createNegative(
                    "little_bit_not_enough_damage");
        }
        StandPower power = StandPower.get(user);
        if (!creative
                && (power == null
                        || power.getStamina()
                                <= LEGACY_MINIMUM_STAMINA)) {
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

    @Override
    public String getSpriteName(Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(user);
        boolean active = controller != null
                ? controller.isTempHealthActive()
                : hasOwnedState(user);
        return active
                ? CatchTheRainbowAddon.TEMP_HEALTH_NAME
                        + "_disable"
                : CatchTheRainbowAddon.TEMP_HEALTH_NAME;
    }

    // 1.16 CatchTheRainbowTempHealth.perform:
    // (int)((max / 2 - (int)health / 2) / 2 - 1).
    public static int absorptionAmplifier(
            float health, float maxHealth) {
        return (int) ((maxHealth / 2.0D - (double) ((int) health / 2))
                / 2.0D - 1.0D);
    }

    public static void clear(LivingEntity user) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        endMaxHealthCut(user);
        CompoundTag data = user.getPersistentData();
        if (data.getBoolean(ABSORPTION_TAG)) {
            data.remove(ABSORPTION_TAG);
            user.removeEffect(MobEffects.ABSORPTION);
        }
    }

    // Ends the max-health cut but leaves the Absorption: 1.16 did so
    // on a Rain Redemption cast, and a logout saved the effect but not
    // the transient modifier.
    public static void endMaxHealthCut(LivingEntity user) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        AttributeInstance health =
                user.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(HEALTH_MODIFIER);
        }
        user.getPersistentData().remove(STANDLESS_CUT_TAG);
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(user);
        if (controller != null) {
            controller.setTempHealthActive(false);
        }
    }

    // A cut left behind by a vanished Stand ends in full, as 1.16
    // onRemovedFromWorld did; Absorption kept over a relog stays
    // while unsummoned in the rain.
    public static void clearIfStranded(LivingEntity user) {
        if (hasOwnedState(user)
                && !user.getPersistentData().getBoolean(
                        STANDLESS_CUT_TAG)) {
            clear(user);
        }
    }

    // The next Stand takes a cut made without one, so its removal and the
    // next Temp Health end it, as 1.16's did by the modifier.
    public static void adoptStandlessCut(
            LivingEntity user, CatchTheRainbowEntity controller) {
        CompoundTag data = user.getPersistentData();
        if (data.getBoolean(STANDLESS_CUT_TAG)) {
            data.remove(STANDLESS_CUT_TAG);
            if (hasOwnedState(user)) {
                controller.setTempHealthActive(true);
            }
        }
    }

    // 1.16 blocked Rain Merge while this Absorption lasted.
    public static boolean hasOwnedAbsorption(LivingEntity user) {
        return user != null
                && user.getPersistentData().getBoolean(ABSORPTION_TAG)
                && user.hasEffect(MobEffects.ABSORPTION);
    }

    // The mark only follows its own Absorption; once that is used up
    // or cleared, a later one is not Temp Health's.
    public static void pruneAbsorptionMark(LivingEntity user) {
        CompoundTag data = user.getPersistentData();
        if (data.getBoolean(ABSORPTION_TAG)
                && !user.hasEffect(MobEffects.ABSORPTION)) {
            data.remove(ABSORPTION_TAG);
        }
    }

    public static boolean hasOwnedState(LivingEntity user) {
        AttributeInstance health = user != null
                ? user.getAttribute(Attributes.MAX_HEALTH) : null;
        return health != null && health.hasModifier(HEALTH_MODIFIER);
    }

    public static final class TempHealthAction
            extends EntityActionInstance {
        public TempHealthAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 InitStands :58-65: a StandAction its user's power performed,
        // which a new Stand's summon lock never held back
        @Override
        public boolean isHeldBackBySummonLock() {
            return false;
        }

        // 1.16 holdToFire(10, false): letting go before the charge
        // completes cancels the heal.
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
            // 1.16 PowerBaseImpl kept the held charge through its Stand's
            // unsummon; CatchTheRainbowTempHealth.perform then skipped only
            // the Stand's stored max health and toggled the cut by the
            // modifier on its user.
            CatchTheRainbowEntity stand =
                    getPerformer() instanceof CatchTheRainbowEntity performer
                            ? performer
                            : null;
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            boolean creative =
                    CatchTheRainbowAddon.isCreative(user);
            boolean active = stand != null
                    ? stand.isTempHealthActive()
                    : hasOwnedState(user);
            if (user == null
                    || power == null
                    || stand == null
                            && !CatchTheRainbowAddon.summonsOnPress(user)
                    || !creative
                            && (!RainExposure
                                            .isExposedToRain(user)
                                    || stand != null
                                            && stand.isMergeActive()
                                    || user.hasEffect(
                                            CatchTheRainbowAddon
                                                    .RAIN_REDEMPTION_EFFECT)
                                    || !active
                                            && user.getHealth()
                                                    > user.getMaxHealth()
                                                            * 0.9F
                                    || power.getStamina()
                                            <= LEGACY_MINIMUM_STAMINA)) {
                startRecovery();
                return;
            }
            // 1.16 CatchTheRainbowTempHealth refused at getStamina() <= 100
            // both ways; StandAction.onPerform then paid the 80 with the
            // result ignored.
            StandAbilityStamina.consume(
                    ability, power, creative ? 0.0F : STAMINA_COST, false);
            if (ability instanceof TempHealthAbility tempHealth) {
                tempHealth.setCooldownOnUse(power);
            }

            float health = user.getHealth();
            float maxHealth = user.getMaxHealth();
            if (stand != null) {
                stand.setStoredMaxHealth(maxHealth);
            }
            AttributeInstance attribute =
                    user.getAttribute(Attributes.MAX_HEALTH);
            // 1.16 CatchTheRainbowTempHealth.perform: turning it off drops
            // the cut, Absorption and Hunger; turning it on cuts max
            // health to the current health, replaces any Absorption with
            // its own and charges Hunger. The heal sound plays both ways.
            if (attribute != null && active) {
                clear(user);
                user.removeEffect(MobEffects.ABSORPTION);
                user.removeEffect(MobEffects.HUNGER);
            }
            else if (attribute != null) {
                clear(user);
                user.removeEffect(MobEffects.ABSORPTION);
                user.removeEffect(MobEffects.HUNGER);
                attribute.addOrUpdateTransientModifier(
                        new AttributeModifier(
                                HEALTH_MODIFIER,
                                -Math.floor(maxHealth - health),
                                AttributeModifier.Operation.ADD_VALUE));
                user.setHealth(Math.min(health, user.getMaxHealth()));
                int amplifier = absorptionAmplifier(health, maxHealth);
                // Only a creative user at full health gets -1, which
                // 1.16 kept as no Absorption; 1.21 would clamp it to 0.
                if (amplifier >= 0) {
                    user.addEffect(new MobEffectInstance(
                            MobEffects.ABSORPTION,
                            ABSORPTION_TICKS,
                            amplifier,
                            false,
                            false,
                            false));
                    user.getPersistentData().putBoolean(
                            ABSORPTION_TAG, true);
                }
                user.addEffect(new MobEffectInstance(
                        MobEffects.HUNGER,
                        HUNGER_TICKS,
                        HUNGER_AMPLIFIER,
                        false,
                        false,
                        false));
                if (stand != null) {
                    stand.setTempHealthActive(true);
                }
                else {
                    user.getPersistentData().putBoolean(
                            STANDLESS_CUT_TAG, true);
                }
            }
            level().playSound(
                    null,
                    user.blockPosition(),
                    CatchTheRainbowAddon.RAIN_HEAL_SOUND.get(),
                    user.getSoundSource(),
                    1.0F,
                    1.0F);
        }
    }
}
