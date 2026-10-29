package rotp.addons.speedking.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.speedking.SpeedKingAddon;
import rotp.addons.speedking.SpeedKingService;
import net.minecraft.server.level.ServerPlayer;

public final class SpeedKingAbility extends StandEntityAbility {
    public enum Mode {
        // Cooldown and stamina follow donor 099 InitStands and the 1.16 builder
        // defaults: only heat vision called cooldown(100). The punch, barrage,
        // heavy and finisher punches are the core's (SpeedKingPunchAbility,
        // SpeedKingBarrageAbility, SpeedKingHeavyAbility).
        THROW_PICKAXE(20, 1, 20, 0, false, 75.0F, 0),
        HEAT_INJECTION(0, 80, 15, 0, false, 60.0F, 2),
        HEAT_VISION(0, 1, 0, 100, false, 40.0F, 3),
        HEAT_COOK(40, 1, 10, 0, false, 25.0F, 1),
        HEAT_GRAB(0, 60, 15, 0, false, 80.0F, 4),
        HEAT_TRAP_TRIGGER(0, 1, 5, 0, false, 0.0F, 0),
        HEAT_TRAP(20, 1, 10, 0, false, 50.0F, 0);

        final int windup;
        final int perform;
        final int recovery;
        final int cooldown;
        final boolean held;
        final float stamina;
        final int resolveLevel;

        Mode(
                int windup,
                int perform,
                int recovery,
                int cooldown,
                boolean held,
                float stamina,
                int resolveLevel) {
            this.windup = windup;
            this.perform = perform;
            this.recovery = recovery;
            this.cooldown = cooldown;
            this.held = held;
            this.stamina = stamina;
            this.resolveLevel = resolveLevel;
        }

        boolean hasChargePhase() {
            return this == THROW_PICKAXE || this == HEAT_TRAP
                    || this == HEAT_COOK;
        }

        boolean releasesToFire() {
            return this == THROW_PICKAXE || this == HEAT_TRAP;
        }
    }

    private final Mode mode;

    public SpeedKingAbility(
            AbilityType<?> type, AbilityId id, Mode mode) {
        super(type, id, action -> new SpeedKingAction(action, mode));
        this.mode = mode;
        usageGroup = switch (mode) {
            case HEAT_VISION, HEAT_COOK,
                    HEAT_TRAP, HEAT_TRAP_TRIGGER ->
                    AbilityUsageGroup.UTILITY;
            default -> AbilityUsageGroup.COMBAT;
        };
        standAutoSummonMode(AutoSummonMode.FULL);
        // Donor 099 InitStands: no partsRequired on heat vision, heat cook and
        // the trap trigger, which stay usable (and held) without the arms.
        if (mode != Mode.HEAT_VISION && mode != Mode.HEAT_COOK
                && mode != Mode.HEAT_TRAP_TRIGGER) {
            partsRequired(StandPart.ARMS);
        }
        setDefaultPhaseLength(mode.hasChargePhase()
                ? ActionPhase.BUTTON_CHARGE : ActionPhase.WINDUP,
                mode.windup);
        setDefaultPhaseLength(ActionPhase.PERFORM, mode.perform);
        setDefaultPhaseLength(ActionPhase.RECOVERY, mode.recovery);
        if (mode.held) {
            setButtonHoldPhase(ActionPhase.PERFORM);
        }
        if (mode.cooldown > 0) {
            cooldown(mode.cooldown);
        }
        if (mode.resolveLevel > 0) {
            resolveLevelToUnlock(mode.resolveLevel);
        }
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || !SpeedKingAddon.isUser(power.getUser())) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        if (mode == Mode.HEAT_TRAP_TRIGGER
                && !SpeedKingAddon.state(power.getUser()).hasHeatTrap()) {
            return ConditionCheck.NEGATIVE;
        }
        return base;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        String animation = switch (mode) {
            case THROW_PICKAXE -> "pickaxeThrow";
            case HEAT_INJECTION -> "heat_injection";
            case HEAT_VISION -> "heat_vision";
            case HEAT_COOK -> "heat_cook";
            case HEAT_GRAB -> "heat_grab";
            case HEAT_TRAP, HEAT_TRAP_TRIGGER -> "heat_trap";
        };
        return ActionAnimIdentifier.getOrCreate(animation, false);
    }

    // Donor 099 heat_trap and heat_grab standMovesByItself: the Stand takes no
    // offset from its user, is not moved by hand, and stays where the action
    // put it until the action ends.
    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return standMovesByItself();
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return standMovesByItself();
    }

    @Override
    public boolean lockStandManualMovement(
            StandPower standPower, StandEntity standEntity) {
        return standMovesByItself();
    }

    private boolean standMovesByItself() {
        return mode == Mode.HEAT_TRAP || mode == Mode.HEAT_GRAB;
    }

    // Donor 099 SpeedKingHeatTrap.standKeepsTarget: the block it was pressed
    // at, which the self-moving Stand turns to for as long as the task holds it.
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return mode == Mode.HEAT_TRAP
                && target.getType() == ActionTarget.TargetType.BLOCK;
    }

    private static final class SpeedKingAction
            extends EntityActionInstance {
        private final Mode mode;

        private SpeedKingAction(EntityActionType type, Mode mode) {
            super(type);
            this.mode = mode;
        }

        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return mode.releasesToFire()
                    && getPhase() == ActionPhase.BUTTON_CHARGE;
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (mode == Mode.HEAT_TRAP
                    && getPerformer() instanceof StandEntity stand) {
                ActionTarget pressed = captureActionTargetFromAim(stand);
                if (pressed.getType() == ActionTarget.TargetType.BLOCK) {
                    keepStandAimedAtTarget(pressed);
                }
            }
            if (mode == Mode.THROW_PICKAXE
                    && getPowerUser() instanceof ServerPlayer player) {
                SpeedKingService.holdPickaxe(player, true);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            StandPower power = StandPower.get(player);
            if (power == null) {
                startRecovery();
                return;
            }
            // 1.16 StandEntityTask never refused a Speed King action for
            // stamina: it spent what was left.
            StandAbilityStamina.consume(
                    ability, power, mode.stamina, false);
            switch (mode) {
                case THROW_PICKAXE -> SpeedKingService.throwPickaxe(player, this);
                case HEAT_VISION -> SpeedKingService.heatVision(player);
                case HEAT_COOK -> SpeedKingService.heatCook(player);
                case HEAT_TRAP_TRIGGER ->
                        SpeedKingService.triggerHeatTrap(player);
                case HEAT_TRAP -> SpeedKingService.placeHeatTrap(player);
                case HEAT_INJECTION, HEAT_GRAB -> {
                }
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            int tick = Math.max(0, (int) getPhaseTick());
            switch (mode) {
                case HEAT_INJECTION ->
                        SpeedKingService.heatInjectionTick(player, tick);
                case HEAT_GRAB ->
                        SpeedKingService.heatGrabTick(player, tick);
                default -> {
                }
            }
        }

        @Override
        public void onButtonStopHold() {
            if (mode.hasChargePhase()
                    && getPhase() == ActionPhase.BUTTON_CHARGE) {
                // 1.16 stopHeldAction(true) checked the requirements once
                // more before the released charge fired.
                if (mode.releasesToFire()
                        && getPhaseTick() >= getCurPhaseLength()
                        && ability instanceof SpeedKingAbility held
                        && held.canFireReleasedHold(this)) {
                    setPhaseStart(ActionPhase.PERFORM);
                }
                else {
                    forceStop();
                }
                syncPhaseChanges();
            }
            else if (mode.held) {
                startRecovery();
            }
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            if (getPowerUser() instanceof ServerPlayer player) {
                if (mode == Mode.HEAT_GRAB) {
                    SpeedKingService.clearGrab(player);
                }
                if (mode == Mode.THROW_PICKAXE) {
                    SpeedKingService.holdPickaxe(player, false);
                }
            }
        }
    }
}
