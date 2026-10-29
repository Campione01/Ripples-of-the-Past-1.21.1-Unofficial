package rotp.addons.madeinheaven.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.MadeInHeavenClientConfig;
import rotp.addons.madeinheaven.MadeInHeavenTimeSystemIcons;
import rotp.addons.madeinheaven.state.TimeAccelerationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public final class TimeSystemAbility
        extends StandEntityAbility {
    public static final int WINDUP_TICKS = 25;
    public static final int START_COOLDOWN_TICKS = 10;
    public static final float REQUIRED_STAMINA = 300.0F;

    private final boolean clear;

    public TimeSystemAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            boolean clear) {
        super(
                abilityType,
                abilityId,
                type -> new TimeSystemAction(type, clear));
        this.clear = clear;
        // 1.16 skipped this by accident (TimeSystem.checkConditions replaced
        // the check); the owner's rule for the dash's legs applies here too.
        partsRequired(StandPart.MAIN_BODY);
        // 1.16 mih_time_system was one holdToFire(25, false) action for
        // both starting and clearing the acceleration.
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return WINDUP_TICKS;
    }

    // The 25-tick windup is the 1.16 hold, so a release from any bind
    // still cancels it.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.WINDUP
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick): the press waits in the
    // input buffer until that hold ends.
    @Override
    public boolean isHoldAction() {
        return true;
    }

    @Override
    public String getSpriteName(Power<?> context) {
        boolean clearIcon = clear
                || context != null
                        && TimeAccelerationService.isActiveOwner(
                                context.getUser());
        return MadeInHeavenTimeSystemIcons.spriteName(
                MadeInHeavenClientConfig
                        .ENABLE_NEW_TIME_SYSTEM_ICONS.get(),
                clearIcon);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context,
            AvailableAbilities abilities) {
        if (!clear
                && context != null
                && TimeAccelerationService.isActiveOwner(
                        context.getUser())) {
            Ability variation =
                    abilities.getContextVariation(
                            MadeInHeavenAddon
                                    .TIME_SYSTEM_CLEAR_NAME);
            return variation != null ? variation : this;
        }
        return this;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base =
                super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context.getUser();
        if (clear) {
            return TimeAccelerationService.isActiveOwner(user)
                    ? checkStamina(context)
                    : ConditionCheck.NEGATIVE;
        }
        if (!(user instanceof ServerPlayer)
                && !user.level().isClientSide()) {
            return ConditionCheck.NEGATIVE;
        }
        if (user.level().dimension() != Level.OVERWORLD
                || !user.level()
                        .getGameRules()
                        .getBoolean(GameRules.RULE_DAYLIGHT)
                || MadeInHeavenAddon
                        .accelerationState(user.level())
                        .active()) {
            return ConditionCheck.createNegative(
                    "already_time_manipulation");
        }
        return checkStamina(context);
    }

    // 1.16 TimeSystem.checkConditions, for starting and clearing alike:
    // below 300 stamina a plain NEGATIVE, so no message. 1.16 asked it
    // again on every tick of the 25-tick hold.
    private static ConditionCheck checkStamina(Power<?> context) {
        return ConditionCheck.noMessage(
                context instanceof StandPower power
                        && power.getStamina() >= REQUIRED_STAMINA);
    }

    // 1.16 asked all of TimeSystem.checkConditions on every held tick:
    // TimeUtil.checkConditions (the Overworld, the daylight cycle, and no
    // time manipulation but the user's own) with its message, then the
    // stamina without one.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        if (action.getPhase() != ActionPhase.WINDUP) {
            return ConditionCheck.POSITIVE;
        }
        LivingEntity user = context.getUser();
        if (user.level().dimension() != Level.OVERWORLD
                || !user.level()
                        .getGameRules()
                        .getBoolean(GameRules.RULE_DAYLIGHT)
                || MadeInHeavenAddon
                        .accelerationState(user.level())
                        .active()
                        && !TimeAccelerationService.isActiveOwner(user)) {
            return ConditionCheck.createNegative(
                    "already_time_manipulation");
        }
        return checkStamina(context);
    }

    private static final class TimeSystemAction
            extends EntityActionInstance {
        private final boolean clear;

        private TimeSystemAction(
                EntityActionType ability, boolean clear) {
            super(ability);
            this.clear = clear;
        }

        // 1.16 holdToFire(25, false): letting go before the hold
        // completes cancels it.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                forceStop();
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            LivingEntity user = getPowerUser();
            if (level().isClientSide()
                    || !(user instanceof ServerPlayer player)) {
                return;
            }
            if (clear) {
                TimeAccelerationService.clearIfOwner(
                        player,
                        TimeAccelerationService
                                .ClearReason.MANUAL);
            }
            else if (TimeAccelerationService.start(player)) {
                StandPower power = StandPower.get(player);
                if (power != null) {
                    power.setAbilityCooldown(
                            MadeInHeavenAddon
                                    .TIME_SYSTEM_NAME,
                            START_COOLDOWN_TICKS);
                }
            }
        }
    }
}
