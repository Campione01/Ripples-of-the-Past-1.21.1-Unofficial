package rotp.addons.cream.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.cream.CreamAddon;
import rotp.addons.cream.CreamConfig;
import rotp.addons.cream.CreamSpec;
import rotp.addons.cream.entity.CreamEntity;
import rotp.addons.cream.entity.CreamEntity.VoidMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CreamVoidAbility extends StandEntityAbility {
    private final Kind kind;

    public CreamVoidAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Kind kind) {
        super(abilityType, abilityId, VoidAction::new);
        this.kind = kind;
        usageGroup = AbilityUsageGroup.SPECIAL;
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
        noFinisherBarDecay = true;

        switch (kind) {
            case BALL -> {
                setDefaultPhaseLength(
                        ActionPhase.BUTTON_CHARGE,
                        CreamSpec.VOID_BALL_CHARGE_TICKS);
                // 1.16 InitStands :75, :80 and :77: no standRecoveryTicks
                setDefaultPhaseLength(
                        ActionPhase.PERFORM, Integer.MAX_VALUE);
                resolveLevelToUnlock(2);
            }
            case SEMI -> {
                setDefaultPhaseLength(
                        ActionPhase.BUTTON_CHARGE,
                        CreamSpec.SEMI_VOID_CHARGE_TICKS);
                setDefaultPhaseLength(
                        ActionPhase.PERFORM, Integer.MAX_VALUE);
                resolveLevelToUnlock(3);
            }
            case DASH -> {
                setDefaultPhaseLength(
                        ActionPhase.PERFORM,
                        CreamSpec.VOID_DASH_TICKS);
                resolveLevelToUnlock(3);
            }
            case CANCEL_BALL -> {
                setDefaultPhaseLength(ActionPhase.PERFORM, 1);
                cooldown(80);
                resolveLevelToUnlock(1);
            }
            case CANCEL_SEMI -> {
                setDefaultPhaseLength(ActionPhase.PERFORM, 1);
                cooldown(40);
                resolveLevelToUnlock(3);
            }
        }
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        LivingEntity user = context.getUser();
        CreamEntity stand = CreamEntity.activeFor(user);
        ConditionCheck standCheck = checkCreamConditions(stand);
        if (!standCheck.isPositive()) {
            return standCheck;
        }
        VoidMode mode = stand != null
                ? stand.getVoidMode()
                : VoidMode.NONE;
        boolean allowed = switch (kind) {
            case BALL, SEMI -> mode == VoidMode.NONE;
            case DASH -> CreamConfig.HAVE_VOID_DASH.get()
                    && (mode == VoidMode.BALL
                            || mode == VoidMode.SEMI);
            case CANCEL_BALL -> mode == VoidMode.BALL;
            case CANCEL_SEMI -> mode == VoidMode.SEMI;
        };
        if (!allowed) {
            return ConditionCheck.NEGATIVE;
        }
        return super.checkSpecificConditions(context);
    }

    // 1.16 CreamVoidBall and CreamSemiVoidState.checkStandConditions, asked
    // on the press and on every held tick of their charge.
    private ConditionCheck checkCreamConditions(CreamEntity stand) {
        if (stand == null || kind != Kind.BALL && kind != Kind.SEMI) {
            return ConditionCheck.POSITIVE;
        }
        if (stand.isManuallyControlled()) {
            return ConditionCheck.createNegative(
                    Component.translatable("message.cream.nu_uh"));
        }
        if (kind == Kind.BALL && stand.getVoidMode() == VoidMode.SEMI) {
            return ConditionCheck.createNegative(
                    Component.translatable("message.cream.nu_uh2"));
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        return base.isPositive()
                ? checkCreamConditions(
                        CreamEntity.activeFor(context.getUser()))
                : base;
    }

    // 1.16 cancelHeldOnGettingAttacked: a hit during the charge drops
    // Enter the Void and Enter Cream when their config options say so.
    @Override
    public boolean cancelHeldOnGettingAttacked(
            EntityActionInstance action,
            DamageSource dmgSource,
            float dmgAmount) {
        return switch (kind) {
            case BALL -> CreamConfig.VOID_BALL_CANCEL_ON_DAMAGE.get();
            case SEMI -> CreamConfig.SEMI_VOID_CANCEL_ON_DAMAGE.get();
            default -> false;
        };
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        Ability shared =
                super.replaceWithSubAbility(context, abilities);
        if (shared != this || abilities == null) {
            return shared;
        }

        LivingEntity user = context != null
                ? context.getUser()
                : null;
        CreamEntity stand = CreamEntity.activeFor(user);
        VoidMode mode = stand != null
                ? stand.getVoidMode()
                : VoidMode.NONE;
        String variationName = switch (kind) {
            case BALL -> mode == VoidMode.BALL
                    ? CreamAddon.CREAM_VOID_BALL_CANCEL_NAME
                    : mode == VoidMode.SEMI
                            && CreamConfig.HAVE_VOID_DASH.get()
                                    ? CreamAddon.CREAM_VOID_DASH_NAME
                                    : null;
            case SEMI -> mode == VoidMode.SEMI
                    ? CreamAddon.CREAM_SEMI_VOID_CANCEL_NAME
                    : null;
            default -> null;
        };
        if (variationName == null) {
            return this;
        }
        Ability variation =
                abilities.getContextVariation(variationName);
        return variation != null
                        && variation.isAbilityAvailable(context)
                ? variation
                : this;
    }

    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return standEntity instanceof CreamEntity cream
                && cream.isInVoidState();
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return standEntity instanceof CreamEntity cream
                && cream.isInVoidState();
    }

    @Override
    public boolean lockStandManualMovement(
            StandPower standPower, StandEntity standEntity) {
        return standEntity instanceof CreamEntity cream
                && cream.isInVoidState();
    }

    private float activationStamina() {
        return switch (kind) {
            case BALL -> CreamConfig.VOID_BALL_STAMINA.get()
                    .floatValue();
            case SEMI -> CreamConfig.SEMI_VOID_STAMINA.get()
                    .floatValue();
            case DASH -> CreamConfig.VOID_DASH_STAMINA.get()
                    .floatValue();
            case CANCEL_BALL -> 80.0F;
            case CANCEL_SEMI -> 40.0F;
        };
    }

    public enum Kind {
        BALL,
        DASH,
        SEMI,
        CANCEL_BALL,
        CANCEL_SEMI
    }

    public static final class VoidAction extends EntityActionInstance {
        private boolean started;
        private boolean roared;

        public VoidAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 standOffsetFromUser(0, 0, 0): the user's feet through the
        // charge too, full mode only, turned by the view yaw
        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            if (ability instanceof CreamVoidAbility voidAbility
                    && (voidAbility.kind == Kind.BALL
                            || voidAbility.kind == Kind.SEMI)) {
                setStandOffset(Vec3.ZERO,
                        StandOffsetFromUser.Rotations.HEAD, false);
            }
        }

        // 1.16 standSound(Phase.BUTTON_HOLD, CREAM_ROAR): Enter the Void
        // and Enter Cream roar once when their charge starts.
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (roared
                    || newPhase != ActionPhase.BUTTON_CHARGE
                    || !(ability instanceof CreamVoidAbility voidAbility)
                    || voidAbility.kind != Kind.BALL
                            && voidAbility.kind != Kind.SEMI
                    || !(getPerformer() instanceof CreamEntity stand)
                    || stand.level().isClientSide()) {
                return;
            }
            roared = true;
            StandUtil.playStandEntitySound(
                    stand, CreamAddon.CREAM_ROAR, 1.0F, 1.0F);
        }

        @Override
        public void actionPerformStart() {
            if (started
                    || level().isClientSide()
                    || !(ability instanceof CreamVoidAbility voidAbility)
                    || !(getPerformer() instanceof CreamEntity stand)) {
                return;
            }
            started = true;
            LivingEntity user = getPowerUser();
            StandPower power = user != null
                    ? StandPower.get(user)
                    : null;
            if (user == null || power == null) {
                startRecovery();
                return;
            }

            switch (voidAbility.kind) {
                case BALL -> {
                    stand.beginVoidMode(VoidMode.BALL);
                    lockCancel(power, CreamAddon.CREAM_VOID_BALL_CANCEL_NAME);
                    StandUtil.playStandEntitySound(
                            stand,
                            CreamAddon.CREAM_VOID_FORM,
                            1.0F,
                            1.0F);
                }
                case SEMI -> {
                    stand.beginVoidMode(VoidMode.SEMI);
                    lockCancel(power, CreamAddon.CREAM_SEMI_VOID_CANCEL_NAME);
                    StandUtil.playStandEntitySound(
                            stand,
                            CreamAddon.CREAM_VOID_FORM_SHORT,
                            1.0F,
                            1.0F);
                }
                case DASH -> stand.beginVoidMode(VoidMode.DASH);
                case CANCEL_BALL, CANCEL_SEMI ->
                        stand.endVoidMode(true);
            }
            // 1.16 paid these start costs with the result ignored; an empty bar
            // ends Enter the Void and Enter Cream on their next tick instead
            StandAbilityStamina.consume(
                    ability, power, voidAbility.activationStamina(), false);
            // 1.16 CreamVoidDash.standTickPerform ran in the tick its perform
            // started too: 20 dash ticks in the perform of 20 that ends it
            if (voidAbility.kind == Kind.DASH) {
                stand.tickVoidDashStart();
            }
        }

        // 1.16 CreamVoidBall and CreamSemiVoidState.standPerform: setCooldownTimer(cancel, 20), a plain timer that
        // Creative does not skip, so the cancel is refused for the first 20 ticks of the void
        private static void lockCancel(StandPower power, String cancelName) {
            power.setAbilityCooldown(cancelName, CreamSpec.VOID_CANCEL_LOCK_TICKS);
        }

        @Override
        public void onButtonStopHold() {
            if (!(ability instanceof CreamVoidAbility voidAbility)
                    || voidAbility.kind != Kind.BALL
                            && voidAbility.kind != Kind.SEMI) {
                return;
            }
            LivingEntity performer = getPerformer();
            if (getPhase() == ActionPhase.BUTTON_CHARGE
                    && performer != null) {
                LivingComponentAction.getComponent(performer)
                        .setAction(null, SyncType.TRACKING_AND_SELF);
            }
        }

        @Override
        public void onActionCleared(
                EntityActionInstance newAction) {
            if (level().isClientSide()
                    || !(ability instanceof CreamVoidAbility current)
                    || !(getPerformer() instanceof CreamEntity stand)) {
                return;
            }
            boolean transitioningToDash = newAction != null
                    && newAction.ability
                            instanceof CreamVoidAbility next
                    && next.kind == Kind.DASH;
            if (!transitioningToDash
                    && (current.kind == Kind.BALL
                            || current.kind == Kind.SEMI
                            || current.kind == Kind.DASH)
                    && stand.isInVoidState()) {
                stand.endVoidMode(true);
            }
        }

        // 1.16 StandEntityAction.canClickDuringTask refused the running action's own ability (isChainable was
        // false for the void actions)
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            if (!(ability instanceof CreamVoidAbility current)
                    || !(cancellingAbility
                            instanceof CreamVoidAbility next)) {
                return false;
            }
            return next.kind == Kind.DASH
                    || current.kind == Kind.BALL
                            && next.kind == Kind.CANCEL_BALL
                    || current.kind == Kind.SEMI
                            && next.kind == Kind.CANCEL_SEMI;
        }
    }
}
