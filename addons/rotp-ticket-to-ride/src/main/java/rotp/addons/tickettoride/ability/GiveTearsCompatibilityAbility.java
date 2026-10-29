package rotp.addons.tickettoride.ability;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.world.entity.LivingEntity;

/**
 * Preserves the donor's hidden, behavior-free {@code givetears} action.
 */
public final class GiveTearsCompatibilityAbility
        extends NoPoseStandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 20;
    public static final int RECOVERY_TICKS = 20;
    public static final float STAMINA_COST = 75.0F;

    public GiveTearsCompatibilityAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                GiveTearsCompatibilityAction::new);
        setDefaultPhaseLength(
                ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY, RECOVERY_TICKS);
        partsRequired(StandPart.ARMS);
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

    public static final class GiveTearsCompatibilityAction
            extends EntityActionInstance {
        public GiveTearsCompatibilityAction(
                EntityActionType ability) {
            super(ability);
        }

        // 1.16 givetears is holdToFire(20, true): a full charge waits for
        // the key to be let go and fires then; letting go earlier cancels.
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
                        && ability instanceof GiveTearsCompatibilityAbility held
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
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (!level().isClientSide()
                    && ability
                            instanceof GiveTearsCompatibilityAbility
                            current
                    && user != null
                    && power != null) {
                // 1.16 StandEntityTask paid staminaCost(75) and ignored the result
                StandAbilityStamina.consume(
                        (Ability) current, power, STAMINA_COST, false);
            }
        }
    }
}
