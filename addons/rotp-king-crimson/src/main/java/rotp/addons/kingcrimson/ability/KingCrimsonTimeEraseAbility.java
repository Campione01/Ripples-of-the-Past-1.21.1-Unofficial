package rotp.addons.kingcrimson.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
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
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class KingCrimsonTimeEraseAbility
        extends NoPoseStandEntityAbility {
    public KingCrimsonTimeEraseAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, TimeEraseAction::new);
        partsRequired(StandPart.ARMS);
        standAutoSummonMode(AutoSummonMode.ARMS);
        setDefaultPhaseLength(
                ActionPhase.PERFORM,
                KingCrimsonPhase5Spec.TIME_ERASE_DURATION_TICKS);
        setButtonHoldPhase(ActionPhase.PERFORM);
        cooldown(KingCrimsonPhase5Spec.TIME_ERASE_FIXED_COOLDOWN_TICKS);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        // 1.16 kingcrimson_timeerase was a plain StandAction cooldown(timeEraseCooldown) (062 InitStands :81): none in
        // Creative or Resolve, kept with Stand stamina off. Its onTaskStopped computed the dynamic cooldown into a
        // local it never applied (KingCrimsonTimeErase :245-252), so the hold's length never changed it.
        return KingCrimsonConfig.configuredCooldown(
                super.getCooldown(context, ticksHeld),
                KingCrimsonConfig.timeEraseCooldownTicks());
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player)) {
            return negative("player_only");
        }
        if (player instanceof ServerPlayer serverPlayer
                && !KingCrimsonTemporalStateService
                        .canStartTimeErase(serverPlayer)) {
            return negative("temporal_state_active");
        }
        if (player.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                && !KingCrimsonAddon.temporalState(player).isIdle()) {
            return negative("temporal_state_active");
        }
        return ConditionCheck.POSITIVE;
    }

    // Full addon key; the String overload would add the jojo prefix.
    private static ConditionCheck negative(String key) {
        return ConditionCheck.createNegative(
                Component.translatable(
                        "rotp_kingcrimson.message." + key));
    }

    public static final class TimeEraseAction
            extends EntityActionInstance {
        private boolean performStarted;
        private boolean handedOffToAttack;
        private long eraseStartedAt = -1L;

        public TimeEraseAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (getPerformer() instanceof StandEntity stand
                    && !stand.level().isClientSide()) {
                stand.summonLockTicks = 0;
            }
        }

        @Override
        public void actionPerformStart() {
            if (performStarted
                    || level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer player)
                    || !(ability
                            instanceof KingCrimsonTimeEraseAbility
                            eraseAbility)) {
                return;
            }
            performStarted = true;
            boolean began = KingCrimsonTemporalStateService
                    .beginTimeErase(player);
            // 1.16 StandEntityTask pays once PERFORM starts, after standPerform, and ignores the result: a short
            // bar drops to 0 and the per-tick upkeep then ends the erase.
            StandAbilityStamina.consume(
                    (Ability) eraseAbility,
                    StandPower.get(player),
                    KingCrimsonPhase5Spec
                            .TIME_ERASE_ACTIVATION_STAMINA,
                    false);
            if (!began) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            sayShoutOf(
                    ability,
                    player,
                    KingCrimsonAddon.KINGCRIMSON_TIMEERASE);
            eraseStartedAt = KingCrimsonAddon.temporalState(player).startedAt();
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM
                    || !(getPowerUser()
                            instanceof ServerPlayer player)
                    || !(ability
                             instanceof KingCrimsonTimeEraseAbility
                             eraseAbility)) {
                return;
            }
            if (!performStarted) {
                actionPerformStart();
                return;
            }
            if (!KingCrimsonAddon.temporalState(player)
                    .isErasing(level().getGameTime())) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            // The temporal service owns upkeep even after this action hands off to a windup.
        }

        // 1.16 kingcrimson_timeerase was holdType() with no recovery: another
        // Stand action pressed during the erase replaced it at once
        // (isCancelable), and any other action of the power ended the hold
        // (stopHeldAction(false)); onTaskStopped then ended the erase
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // The port's punches and barrages keep the erase until they reach
        // their perform, where TimeEraseAttackPhaseMixin ends it.
        private boolean handsOffTo(EntityActionType next) {
            return getPhase() == ActionPhase.PERFORM
                    && (next instanceof StandEntityPunchAbility
                            || next instanceof StandEntityBarrageAbility);
        }

        @Override
        public void onKeyRelease(LivingEntity user) {
            if (handedOffToAttack && user instanceof ServerPlayer player
                    && KingCrimsonAddon.temporalState(player).startedAt() == eraseStartedAt) {
                KingCrimsonTemporalStateService.terminateOnInteraction(player);
            }
            super.onKeyRelease(user);
        }

        @Override
        public void onButtonStopHold() {
            if (!level().isClientSide()
                    && getPowerUser()
                            instanceof ServerPlayer player) {
                KingCrimsonTemporalStateService.terminate(player);
            }
            forceStop();
            syncPhaseChanges();
        }

        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            if (newAction != null && handsOffTo(newAction.ability)) {
                handedOffToAttack = true;
                return;
            }
            if (!level().isClientSide()
                    && getPowerUser()
                            instanceof ServerPlayer player) {
                KingCrimsonTemporalStateService.terminate(player);
            }
        }
    }
}
