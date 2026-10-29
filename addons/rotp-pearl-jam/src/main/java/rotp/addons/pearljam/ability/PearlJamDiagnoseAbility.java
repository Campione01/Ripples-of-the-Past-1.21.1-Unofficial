package rotp.addons.pearljam.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.pearljam.PearlJamAddon;
import rotp.addons.pearljam.food.PearlJamFoodService;
import rotp.addons.pearljam.food.PearlJamTreatment;
import rotp.addons.pearljam.state.PearlJamPlayerState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class PearlJamDiagnoseAbility
        extends NoPoseStandEntityAbility {
    public static final double TARGET_RANGE = 4.0D;
    public static final float STAMINA_COST = 20.0F;
    public static final int WINDUP_TICKS = 5;
    public static final int COOLDOWN_TICKS = 3;

    public PearlJamDiagnoseAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DiagnoseAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(COOLDOWN_TICKS);
    }

    // 1.16 set the cooldown when the hold fired, so a cancelled hold
    // leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // Held in the windup like the pj_* holds, so a release from any
    // bind still cancels it.
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
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (user == null || PearlJamAddon.controller(user) == null) {
            return ConditionCheck.createNegative(
                    "pearl_jam_not_summoned");
        }
        return check;
    }

    // The diagnosis is a kept port addition: it snapshots whatever living
    // thing the user aims at, an ally or the user's own pet too, which the
    // Stand's attack check would refuse.
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return true;
    }

    public static final class DiagnoseAction
            extends EntityActionInstance {
        public DiagnoseAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            LivingEntity user = getPowerUser();
            if (user != null) {
                captureActionTargetFromAim(user);
            }
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                forceStop();
            }
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer owner
                    && ability instanceof PearlJamDiagnoseAbility held) {
                held.setCooldownOnUse(StandPower.get(owner));
            }
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer player)) {
                return;
            }
            if (PearlJamAddon.controller(player) == null) {
                return;
            }

            LivingEntity target = targetFromSnapshot(player);
            PearlJamTreatment treatment =
                    PearlJamFoodService.diagnose(target);
            PearlJamPlayerState state = PearlJamAddon.state(player);
            if (treatment.isActive()) {
                state.diagnose(
                        treatment,
                        target.getUUID(),
                        level().getGameTime()
                                + PearlJamFoodService
                                        .DIAGNOSIS_TTL_TICKS);
            }
            else {
                state.clearDiagnosis();
            }
            PearlJamAddon.syncState(player);
            player.displayClientMessage(
                    Component.translatable(
                            "rotp_pj.message.diagnosis",
                            target.getDisplayName(),
                            Component.translatable(
                                    treatment.translationKey())),
                    false);
            // like the 1.16 Pearl Jam actions and StandAction.onPerform: paid after the action, result ignored
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(player),
                    STAMINA_COST,
                    false);
        }

        private LivingEntity targetFromSnapshot(
                LivingEntity user) {
            ActionTarget snapshot =
                    getActionTargetSnapshot(level());
            LivingEntity target =
                    PearlJamTargeting.resolveLiving(
                            user,
                            snapshot,
                            level(),
                            TARGET_RANGE);
            return target != null
                    ? target
                    : PearlJamTargeting.findTargetOrSelf(
                            user, level(), TARGET_RANGE);
        }
    }
}
