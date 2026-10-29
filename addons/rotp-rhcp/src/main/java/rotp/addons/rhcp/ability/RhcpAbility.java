package rotp.addons.rhcp.ability;

import java.util.OptionalInt;

import javax.annotation.Nullable;

import rotp.core.api.client.render.AbilitySelectionVisualQuery;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.subsystems.entity_puppetcontrol.StandManualInput;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.RhcpService;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import rotp.addons.rhcp.state.RhcpState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RhcpAbility extends StandEntityAbility
        implements RhcpModeAbility {
    // The punches, barrage, heavy and combo (the heavy's finisher) are the
    // core-based RhcpPunchAbility, RhcpBarrageAbility and RhcpHeavyAbility;
    // their modes name them here.
    public enum Mode {
        PUNCH_OFF(3, 1, 6, 4, false, 0),
        PUNCH(3, 1, 6, 4, false, 0),
        BARRAGE(0, 1, 8, 0, true, 0),
        // RhcpHeavyAbility reads only its Resolve level (core heavy timing).
        COMBO_PUNCH(6, 1, 16, 20, false, 1),
        // 1.16 094 InitStands:75, 77: the chase and the electrocute were
        // StandEntityActionModifiers with no cooldown: a press adds them to
        // the running combo or heavy (onKeyPress), they start no task.
        CHASE_TARGET(0, 1, 0, 0, false, 3),
        HEAVY_PUNCH(8, 1, 18, 20, false, 0),
        ELECTROCUTE_TARGET(0, 1, 0, 0, false, 1),
        // 1.16 :79-80 standPerformDuration(2), no windup, recovery or
        // cooldown.
        ENTER_CONDUCTOR(0, 2, 0, 0, false, 0),
        EXIT_CONDUCTOR(0, 2, 0, 0, false, 0),
        // 1.16 RHCPRecharge.getStandRecoveryTicks :210-211 returns 3.
        RECHARGE(0, 1, 3, 0, true, 1),
        BOOST(0, 1, 3, 0, true, 2),
        // 1.16 :83 and :87 set no windup or standRecoveryTicks.
        REDUCE_CONSUMPTION(0, 1, 0, 5, false, 2),
        DODGE(0, 1, 3, 0, true, 3),
        COUNTERATTACK(3, 1, 5, 10, false, 0),
        BLITZ_ATTACK(6, 1, 8, 20, false, 0),
        ELECTRIC_DASH(0, 1, 0, 40, false, 3),
        DRAG_INTO_LINE(0, 1, 5, 2400, false, 3);

        final int windup;
        final int perform;
        final int recovery;
        final int cooldown;
        final boolean held;
        final int resolveLevel;

        Mode(
                int windup,
                int perform,
                int recovery,
                int cooldown,
                boolean held,
                int resolveLevel) {
            this.windup = windup;
            this.perform = perform;
            this.recovery = recovery;
            this.cooldown = cooldown;
            this.held = held;
            this.resolveLevel = resolveLevel;
        }
    }

    // The follow-ups (chase after the combo, electrocute after the heavy)
    // come from RhcpHeavyAbility.replaceWithSubAbility.
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        return this;
    }

    // 1.16 StandAction.replaceActionKostyl offered the follow-up for the
    // whole of the heavy's or the combo's task, windup and perform too,
    // while canSetFollowUp held: the modifier not yet on the task, and its
    // own checks (electrocute: potency, not attackAlt; chase: potency 4+).
    // The modifier then acted in the recovery.
    static boolean canFollowUp(
            RhcpStandEntity stand, RhcpState state, Mode followup) {
        if (stand == null || !state.powered()
                || !RhcpHeavyAbility.followUpOpen(stand.getCurStandAction())
                || !(stand.getCurStandAction()
                        instanceof RhcpHeavyAbility.RhcpHeavyPunch action)) {
            return false;
        }
        if (followup == Mode.CHASE_TARGET) {
            return action.mode == Mode.COMBO_PUNCH && state.potency() >= 4
                    && !action.isChaseRequested();
        }
        return followup == Mode.ELECTROCUTE_TARGET
                && action.mode == Mode.HEAVY_PUNCH
                && !stand.isAttackAlternate()
                && !action.isElectrocuteRequested();
    }

    @Override
    public Mode rhcpMode() {
        return mode;
    }

    private final Mode mode;

    @Nullable
    public static Mode modeOf(@Nullable EntityActionInstance action) {
        return action != null && action.ability instanceof RhcpModeAbility rhcp
                ? rhcp.rhcpMode() : null;
    }

    // 1.16 InitStands staminaCostTick of the held modes, paid every held tick.
    public static float staminaTick(Mode mode) {
        return switch (mode) {
            case RECHARGE -> 0.1F;
            case BOOST -> 8.0F;
            case DODGE -> 5.0F;
            default -> 0.0F;
        };
    }

    // 1.16 StandEntityHeavyAttacks: the heavy punch and the combo are both
    // RhcpHeavyAbility now, so no RhcpAbility mode is one.
    public boolean isHeavyAttack() {
        return mode == Mode.COMBO_PUNCH;
    }

    // 1.16 StandEntityActionModifier.greenSelection (094 InitStands :75, :77): a selected chase or electrocute was
    // framed green while its conditions held.
    public static OptionalInt followUpSelectionTint(AbilitySelectionVisualQuery query) {
        return query.ability() instanceof RhcpAbility rhcp
                && (rhcp.mode == Mode.CHASE_TARGET || rhcp.mode == Mode.ELECTROCUTE_TARGET)
                && query.conditionCheck().isPositive()
                ? OptionalInt.of(FOLLOW_UP_GREEN) : OptionalInt.empty();
    }

    private static final int FOLLOW_UP_GREEN = 0xFF00FF00;

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
        if (mode != Mode.CHASE_TARGET && mode != Mode.ELECTROCUTE_TARGET) {
            return super.onKeyPress(level, user, extraClientInput,
                    inputMethod, clickHoldResolveTime, bufferingState);
        }
        if (!level.isClientSide()) {
            RhcpStandEntity stand = RhcpService.activeStand(user);
            bufferingState.isActionSuccess = inputMethod == InputMethod.CLICK
                    && canFollowUp(stand, RhcpAddon.state(user), mode)
                    && stand.getCurStandAction() instanceof RhcpHeavyAbility.RhcpHeavyPunch action
                    && (mode == Mode.CHASE_TARGET ? action.queueChase() : action.queueElectrocute());
        }
        return null;
    }

    static void performQueuedChase(ServerPlayer player, RhcpStandEntity performer) {
        if (RhcpService.activeStand(player) == performer) {
            RhcpAction.chase(player, RhcpAddon.state(player));
        }
    }

    public RhcpAbility(
            AbilityType<?> type, AbilityId id, Mode mode) {
        super(type, id, action -> new RhcpAction(action, mode));
        this.mode = mode;
        usageGroup = switch (mode) {
            case ENTER_CONDUCTOR, EXIT_CONDUCTOR,
                    RECHARGE, BOOST, REDUCE_CONSUMPTION ->
                    AbilityUsageGroup.UTILITY;
            default -> AbilityUsageGroup.COMBAT;
        };
        standAutoSummonMode(AutoSummonMode.FULL);
        // 1.16 :75, :77 built the two modifiers with no partsRequired.
        if (mode != Mode.CHASE_TARGET && mode != Mode.ELECTROCUTE_TARGET) {
            partsRequired(StandPart.MAIN_BODY);
        }
        setDefaultPhaseLength(ActionPhase.WINDUP, mode.windup);
        setDefaultPhaseLength(ActionPhase.PERFORM, mode.perform);
        setDefaultPhaseLength(ActionPhase.RECOVERY, mode.recovery);
        if (mode.held) {
            setButtonHoldPhase(ActionPhase.PERFORM);
        }
        if (mode.cooldown > 0) {
            switch (mode) {
                // 1.16 cooldown(5, 5) and cooldown(10, 40): Resolve drops only the second part.
                case REDUCE_CONSUMPTION -> cooldown(5, mode.cooldown);
                case ELECTRIC_DASH -> cooldown(10, mode.cooldown);
                // 1.16 cooldown(2400, 2400): a miss costs 4800, or 2400 in Resolve;
                // a pulled target resets it to 2400 (actionPerformStart).
                case DRAG_INTO_LINE -> cooldown(mode.cooldown, mode.cooldown);
                default -> cooldown(mode.cooldown);
            }
        }
        if (mode.resolveLevel > 0) {
            resolveLevelToUnlock(mode.resolveLevel);
        }
    }

    @Override
    public String getSpriteName(Power<?> context) {
        if (context != null && context.getUser() != null) {
            // The combo's "_alt" sprite is on RhcpHeavyAbility.
            RhcpState state = RhcpAddon.state(context.getUser());
            if (mode == Mode.BOOST && state.potency() == 0) {
                return "rhcp_boost_off";
            }
            if (mode == Mode.REDUCE_CONSUMPTION
                    && state.potency() == 0) {
                return "rhcp_reduce_consumption_off";
            }
        }
        return super.getSpriteName(context);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || power.getPowerType() != RhcpAddon.RHCP.get()
                || !(power.getUser() instanceof LivingEntity user)) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        RhcpState state = RhcpAddon.state(user);
        if (mode == Mode.EXIT_CONDUCTOR) {
            // 1.16 RHCPExit.checkSpecificConditions :49-50: RHCP stays where
            // it leaves, so not from inside a full block.
            RhcpStandEntity stand = RhcpService.activeStand(user);
            if (stand != null && RhcpService.inFullBlock(stand)) {
                return ConditionCheck.createNegative("full_block_rhcp");
            }
        }
        if (mode == Mode.ENTER_CONDUCTOR && state.powered()
                && !state.inConductor()) {
            ConditionCheck pressed = checkConductorPress(user);
            if (!pressed.isPositive()) {
                return pressed;
            }
        }
        boolean allowed = switch (mode) {
            // RhcpPunchAbility, RhcpBarrageAbility and RhcpHeavyAbility.
            case PUNCH_OFF, PUNCH, BARRAGE, COMBO_PUNCH, HEAVY_PUNCH,
                    COUNTERATTACK, BLITZ_ATTACK -> false;
            case ELECTRIC_DASH -> {
                RhcpStandEntity stand = RhcpService.activeStand(user);
                yield dashAllowed(state.powered(), state.inConductor(),
                        user.level().getEntity(state.targetId()) != null,
                        state.potency(),
                        modeOf(stand != null ? stand.getCurStandAction() : null));
            }
            case DODGE -> state.powered() && !state.inConductor()
                    && canDodge(user);
            case CHASE_TARGET, ELECTROCUTE_TARGET ->
                    canFollowUp(RhcpService.activeStand(user), state, mode);
            case ENTER_CONDUCTOR -> state.powered()
                    && !state.inConductor();
            case EXIT_CONDUCTOR -> state.inConductor();
            case DRAG_INTO_LINE -> state.inConductor()
                    && RhcpService.findDragTarget(user) != null;
            case RECHARGE -> RhcpService.canRecharge(user);
            case BOOST -> state.energy() > 100.0F
                    && state.potency() < 5;
            case REDUCE_CONSUMPTION -> state.potency() > 1;
        };
        if (!allowed) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 had no stamina gate on the held modes.
        return ConditionCheck.POSITIVE;
    }

    // 1.16 PowerBaseImpl.checkTarget refused a press at no block silently and
    // one farther than 10 blocks from the performer with target_too_far;
    // RHCPGettingIntoTheMechanism.checkSpecificConditions :24-33 a block that
    // is no conductor, silently.
    private static ConditionCheck checkConductorPress(LivingEntity user) {
        BlockPos pressed = RhcpService.pressedBlock(user);
        if (pressed == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (!RhcpService.pressedBlockInRange(user, pressed)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        return RhcpService.isConductor(user.level().getBlockState(pressed))
                ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
    }

    // 1.16 RHCPGettingIntoTheMechanism was a TargetRequirement.BLOCK
    // StandEntityAction: its task kept the pressed block
    // (StandEntityTask.keepTarget, within 10 blocks) and rotateStand turned
    // a Stand not manually controlled to it through the perform.
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return mode == Mode.ENTER_CONDUCTOR
                && target.getType() == ActionTarget.TargetType.BLOCK;
    }

    // 1.16 PowerBaseImpl.tickHeldAction re-ran RHCPRecharge and RHCPDodge
    // checkSpecificConditions on every held tick; a NEGATIVE ended the hold
    // silently. The dodge's potency is left out: 1.16 RHCPEntity only
    // dropped it with no task running, the port drops it with the energy.
    // Boost's own check already ends it in actionTick.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        if (!(context.getUser() instanceof LivingEntity user)) {
            return ConditionCheck.POSITIVE;
        }
        boolean allowed = switch (mode) {
            case RECHARGE -> RhcpService.canRecharge(user);
            case DODGE -> !RhcpAddon.state(user).inConductor()
                    && canDodge(user);
            default -> true;
        };
        return allowed ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
    }

    // 1.16 RHCPElectricDash.checkSpecificConditions: no dash while a target
    // lock is live (getTargetHit) or while a potency 4+ combo runs.
    public static boolean dashAllowed(boolean powered, boolean inConductor,
            boolean targetLocked, int potency, @Nullable Mode running) {
        return powered && !inConductor && !targetLocked
                && (potency <= 3 || running != Mode.COMBO_PUNCH);
    }

    // 1.16 RHCPDodge.checkSpecificConditions: RHCP is out and manually
    // controlled or left at a fixed remote position, and neither it nor its
    // user has just been hurt.
    private static boolean canDodge(LivingEntity user) {
        RhcpStandEntity stand = RhcpService.activeStand(user);
        return stand != null
                && user.invulnerableTime < 1
                && stand.invulnerableTime < 1
                && (stand.isManuallyControlled()
                        || !stand.followingUserIsEnabled());
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        String animation = switch (mode) {
            case RECHARGE -> "recharge";
            case DODGE -> "taunt";
            case ELECTRIC_DASH -> "electric_dash";
            default -> "idle";
        };
        return ActionAnimIdentifier.getOrCreate(animation, false);
    }

    private static final class RhcpAction extends EntityActionInstance {
        private final Mode mode;
        // The dash target, taken when the dash starts.
        private ActionTarget dashAim;
        // 1.16 preTaskInit: the dash Shift, read at the press.
        private Boolean dashShift;

        private RhcpAction(EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (!level().isClientSide()
                    && mode == Mode.ENTER_CONDUCTOR
                    && getPowerUser() != null) {
                keepStandAimedAtTarget(
                        RhcpService.pressedBlockTarget(getPowerUser()));
            }
            // 1.16 RHCPElectricDash: aim first, then manual control on.
            if (!level().isClientSide()
                    && mode == Mode.ELECTRIC_DASH
                    && getPowerUser() instanceof ServerPlayer player) {
                dashAim = RhcpService.dashAim(player);
                RhcpStandEntity stand = RhcpService.activeStand(player);
                // Still the user's own sneak here; once manual control is
                // on, Shift waits for the client's key packet.
                dashShift = StandManualInput.isShiftHeld(player, stand);
                if (stand != null && !stand.isManuallyControlled()) {
                    StandEntityManualControlToggle.on(level(), stand);
                }
            }
        }

        // The combo's cancels and chase follow-up are on RhcpHeavyAbility.

        @Override
        public void actionPerformStart() {
            if (!(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            RhcpState state = RhcpAddon.state(player);
            switch (mode) {
                case CHASE_TARGET -> chase(player, state);
                case ENTER_CONDUCTOR -> RhcpService.enterConductor(player);
                case EXIT_CONDUCTOR -> RhcpService.leaveConductor(player);
                case DRAG_INTO_LINE -> {
                    // 1.16 RHCPDragIntoLine: setCooldownTimer(this, 2400) on a hit.
                    StandPower power = StandPower.get(player);
                    if (RhcpService.dragIntoLine(player)
                            && power != null
                            && ability instanceof RhcpAbility drag
                            && power.isAbilityOnCooldown(drag.name())) {
                        power.setAbilityCooldown(
                                drag.name(), mode.cooldown);
                    }
                }
                case REDUCE_CONSUMPTION -> {
                    state.setPotency(state.potency() - 1);
                    RhcpService.sync(player);
                }
                case ELECTRIC_DASH -> RhcpService.dash(player,
                        dashAim != null ? dashAim : RhcpService.dashAim(player),
                        dashShift != null ? dashShift
                                : StandManualInput.isShiftHeld(player,
                                        RhcpService.activeStand(player)));
                case DODGE -> {
                    state.setDodging(true);
                    RhcpService.sync(player);
                }
                case RECHARGE -> recharge(player);
                case BOOST -> {
                    state.setBoosting(true);
                    RhcpService.sync(player);
                }
                default -> {
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
                case RECHARGE -> recharge(player);
                case BOOST -> {
                    RhcpService.boost(player, tick);
                    if (RhcpAddon.state(player).potency() >= 5
                            || RhcpAddon.state(player).energy() <= 100.0F) {
                        startRecovery();
                    }
                }
                case DODGE -> {
                    // 1.16 RHCPDodge.standTickPerform: ++ticksEvaded, which
                    // sets the dodge's cooldown once it is over.
                    RhcpStandEntity stand = RhcpService.activeStand(player);
                    if (stand != null) {
                        stand.addTicksEvaded();
                    }
                    if (!RhcpService.consume(player, 1.0F, 0.0F)) {
                        startRecovery();
                    }
                }
                default -> {
                }
            }
            // 1.16 StandEntityTask: staminaCostTick after each held tick,
            // result ignored; a short user drops to 0 and keeps holding.
            float stamina = staminaTick(mode);
            StandPower staminaPower = StandPower.get(player);
            if (stamina > 0.0F && staminaPower != null) {
                staminaPower.consumeStamina(stamina, true);
            }
        }

        @Override
        public void onButtonStopHold() {
            if (mode.held) {
                startRecovery();
            }
        }

        // 1.16 RHCPRecharge, RHCPBoost and RHCPDodge were holdType Stand
        // actions with getStandRecoveryTicks 3
        @Override
        public boolean isHoldTypeStandAction() {
            return isHeldStandMode();
        }

        // 1.16 isCancelable "phase == RECOVERY || super": a press waits
        // through the hold (it has a recovery) and goes off in the recovery.
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return isHeldStandMode() && getPhase() == ActionPhase.RECOVERY
                    || super.canBeCancelledInto(cancellingAbility);
        }

        // 1.16 canClickDuringTask: these were not chainable
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return isHeldStandMode();
        }

        private boolean isHeldStandMode() {
            return mode == Mode.RECHARGE || mode == Mode.BOOST
                    || mode == Mode.DODGE;
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            if (getPowerUser() instanceof ServerPlayer player) {
                RhcpState state = RhcpAddon.state(player);
                if (mode == Mode.DODGE) {
                    state.setDodging(false);
                }
                if (mode == Mode.BOOST) {
                    state.setBoosting(false);
                }
                // 1.16 RHCPElectricDash.onTaskStopped: RHCP stays manually
                // controlled, keeps the lock 5 ticks and punches alternate.
                RhcpStandEntity stand = RhcpService.activeStand(player);
                if (mode == Mode.ELECTRIC_DASH && stand != null) {
                    if (!stand.isManuallyControlled()) {
                        StandEntityManualControlToggle.on(level(), stand);
                    }
                    if (state.potency() > 0) {
                        stand.setStandCount(5);
                        stand.setAttackAlternate(true);
                    }
                }
                RhcpService.sync(player);
            }
        }

        // 1.16 RHCPRecharge.standTickPerform: energyCharge(null, 3, false).
        private static void recharge(ServerPlayer player) {
            RhcpStandEntity stand = RhcpService.activeStand(player);
            if (stand != null) {
                RhcpService.energyCharge(player, stand, null, false);
            }
        }

        private static void chase(
                ServerPlayer player, RhcpState state) {
            RhcpStandEntity stand = RhcpService.activeStand(player);
            Entity target = player.level().getEntity(state.targetId());
            if (stand == null
                    || !state.powered() || state.inConductor() || state.potency() < 4
                    || !(target instanceof LivingEntity living)
                    || !living.isAlive()) {
                if (stand != null) {
                    stand.setChasing(false);
                    stand.setAttackAlternate(false);
                }
                state.setTargetId(-1);
                RhcpService.sync(player);
                return;
            }
            stand.setChasing(true);
            stand.setAttackAlternate(true);
        }
    }
}
