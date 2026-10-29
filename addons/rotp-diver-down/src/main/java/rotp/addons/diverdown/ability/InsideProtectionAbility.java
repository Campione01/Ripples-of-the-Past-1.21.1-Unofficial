package rotp.addons.diverdown.ability;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;

public final class InsideProtectionAbility
        extends StandEntityAbility {
    public static final int RESOLVE_LEVEL = 3;

    public InsideProtectionAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ProtectionAction::new);
        resolveLevelToUnlock(RESOLVE_LEVEL);
        // 1.16 DiverDownInsideProtection: holdType() with no recovery, so a
        // pressed action replaced the hold at once (isCancelable)
        setButtonHoldPhase(ActionPhase.PERFORM);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        return checkInside(context);
    }

    // 1.16 tickHeldAction re-ran checkStandConditions every held tick: a
    // Stand no longer inside its target ends the hold with dd_notinside.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        return checkInside(context);
    }

    private static ConditionCheck checkInside(Power<?> context) {
        DiverDownStandEntity stand =
                DiverDownAddon.activeStand(context.getUser());
        return stand != null && stand.isEmbedded()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "diver_down_not_embedded");
    }

    public static final class ProtectionAction
            extends EntityActionInstance {
        private boolean loopStarted;

        public ProtectionAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(getPerformer()
                            instanceof DiverDownStandEntity stand)) {
                return;
            }
            if (!stand.isEmbedded()) {
                startRecovery();
                return;
            }
            if (level().isClientSide() && !loopStarted) {
                loopStarted = true;
                playLoopingSound(stand);
            }
        }

        // 1.16 DiverDownInsideProtection.phaseTransition :50-56: each client looped the protection sound on Diver Down
        // while the perform lasted (ClientTickingSoundsHelper.playStandEntityCancelableActionSound, looping)
        private void playLoopingSound(DiverDownStandEntity stand) {
            if (ClientGlobals.canHearStand(stand)) {
                ClientsideSoundsHelper.playLoopingActionSound(
                        ClientsideSoundsHelper.withStandSkin(
                                DiverDownAddon.DIVER_DOWN_INSIDE_PROTECTION.get(), stand),
                        stand, this, ActionPhase.PERFORM, 1.0F, 1.0F);
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        // 1.16 DiverDownInsideProtection.phaseTransition :191-199: Diver Down's summon sound as the perform ends
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            if (getPhase() == ActionPhase.PERFORM && newPhase != ActionPhase.PERFORM) {
                playPerformEndSound();
            }
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            if (getPhase() == ActionPhase.PERFORM) {
                playPerformEndSound();
            }
        }

        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        private void playPerformEndSound() {
            if (!level().isClientSide() && getPerformer() instanceof DiverDownStandEntity stand) {
                StandUtil.playStandEntitySound(stand, DiverDownAddon.DIVER_DOWN_SUMMON.get(), 1.0F, 1.0F);
            }
        }
    }
}
