package rotp.addons.tickettoride.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.item.TearsBladeItem;
import net.minecraft.world.entity.player.Player;

public final class RefillAmmoAbility
        extends NoPoseStandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 50;

    public RefillAmmoAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, RefillAmmoAction::new);
        setDefaultPhaseLength(
                ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
    }

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return HOLD_TO_FIRE_TICKS;
    }

    // The charging windup is the 1.16 hold, so a release from any bind
    // still cancels or fires it.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.WINDUP
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick), and the Stand queued none
    // behind its task (canBeQueued).
    @Override
    public boolean isHoldAction() {
        return true;
    }

    // 1.16 stopHeldAction(false): a hold stopped by anything but the
    // release never fires, a full charge included.
    @Override
    public void stopHeldActionOnGettingAttacked(
            EntityActionInstance action) {
        action.forceStop();
        action.syncPhaseChanges();
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player)
                || (!player.level().isClientSide()
                        && TicketToRideAddon.controller(player)
                                == null)) {
            return ConditionCheck.createNegative(
                    "ticket_to_ride_not_summoned");
        }
        return checkHeldBlade(player);
    }

    // 1.16 tickHeldAction and stopHeldAction(true) re-ran checkConditions:
    // a charge whose blade leaves the hands, or fills up, stops.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        return context.getUser() instanceof Player player
                ? checkHeldBlade(player)
                : ConditionCheck.NEGATIVE;
    }

    // What 1.16 checkSpecificConditions checked: a Tears Blade in hand
    // that is not full.
    private static ConditionCheck checkHeldBlade(Player player) {
        if (!TearsBladeItem.hasTearsBladeInHand(player)) {
            return ConditionCheck.createNegative(
                    "no_tears_blade");
        }
        boolean refillable = player.level().isClientSide()
                ? TearsBladeItem.hasAmmoBelowMaxInHand(player)
                : TearsBladeItem.hasRefillableInHand(player);
        return refillable
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("ammo_full");
    }

    public static final class RefillAmmoAction
            extends EntityActionInstance {
        public RefillAmmoAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 item_fill is holdType() + holdToFire(50, false): a full
        // charge waits for the key to be let go and fires then; letting
        // go earlier cancels it.
        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return getPhase() == ActionPhase.WINDUP;
        }

        // 1.16 stopHeldAction(true) checked the requirements once more
        // before the released charge fired.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                if (getPhaseTick() >= getCurPhaseLength()
                        && ability instanceof RefillAmmoAbility held
                        && held.canFireReleasedHold(this)) {
                    setPhaseStart(ActionPhase.PERFORM);
                }
                else {
                    forceStop();
                }
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPowerUser() instanceof Player player
                    && TicketToRideAddon.controller(player) != null) {
                TearsBladeItem.refillOwnedInHands(player);
            }
        }
    }
}
