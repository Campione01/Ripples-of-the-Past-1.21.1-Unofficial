package rotp.addons.whitesnake.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.blindness.BlindnessState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class WhitesnakeBlindnessAbility
        extends NoPoseStandEntityAbility {
    public static final float STAMINA_COST = 50.0F;
    public static final int HOLD_TO_FIRE_TICKS = 5;
    private static final String ABILITY_NAME = "blindness";

    public WhitesnakeBlindnessAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, BlindnessAction::new);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(
                ActionPhase.WINDUP, HOLD_TO_FIRE_TICKS);
    }

    @Override
    protected float getWindupHoldToFireIndicatorLength() {
        return HOLD_TO_FIRE_TICKS;
    }

    // The hold charges in the windup, as 1.16 held it before it fired.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.WINDUP
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick): the press waits in the
    // input buffer until the held guard or barrage is let go.
    @Override
    public boolean isHoldAction() {
        return true;
    }

    // 1.16 stopHeldAction(false): the stopped hold never fires.
    @Override
    public void stopHeldActionOnGettingAttacked(
            EntityActionInstance action) {
        action.forceStop();
        action.syncPhaseChanges();
    }

    @Override
    public boolean canContinueAction(EntityActionInstance action) {
        return WhitesnakeAddon.isActiveWhitesnakeUser(
                action.getPowerUser(), ABILITY_NAME);
    }

    @Override
    public Component getName(Power<?> context) {
        return hasBlindness(context)
                ? abilityName(context, "_disable")
                : super.getName(context);
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return hasBlindness(context)
                ? "blindness_disable"
                : super.getSpriteName(context);
    }

    private static boolean hasBlindness(Power<?> context) {
        LivingEntity user = context != null
                ? context.getUser()
                : null;
        return user != null && user.hasEffect(MobEffects.BLINDNESS);
    }

    public static final class BlindnessAction
            extends EntityActionInstance {
        public BlindnessAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdType() + holdToFire(5, false): a full charge waits for
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
                        && ability instanceof WhitesnakeBlindnessAbility held
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
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                forceStop();
                return;
            }

            BlindnessState state =
                    WhitesnakeAddon.blindnessState(user);
            if (user.hasEffect(MobEffects.BLINDNESS)) {
                user.removeEffect(MobEffects.BLINDNESS);
                state.clear(user, false);
            }
            else {
                state.activate(user);
            }
            // 1.16 StandEntityTask: the cost is paid after the toggle; a
            // short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, StandPower.get(user), STAMINA_COST, false);
        }
    }
}
