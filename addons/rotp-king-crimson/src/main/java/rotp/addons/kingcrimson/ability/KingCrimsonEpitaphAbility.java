package rotp.addons.kingcrimson.ability;

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
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase3Spec;
import rotp.addons.kingcrimson.state.EpitaphState;
import rotp.addons.kingcrimson.state.EpitaphState.PredictionKind;
import rotp.addons.kingcrimson.state.EpitaphStateService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class KingCrimsonEpitaphAbility
        extends NoPoseStandEntityAbility {
    public KingCrimsonEpitaphAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, EpitaphAction::new);
        partsRequired(StandPart.MAIN_BODY);
        standAutoSummonMode(AutoSummonMode.FULL);
        setDefaultPhaseLength(
                ActionPhase.PERFORM,
                KingCrimsonPhase3Spec.EPITAPH_WINDOW_TICKS);
        setButtonHoldPhase(ActionPhase.PERFORM);
        cooldown(KingCrimsonPhase3Spec.EPITAPH_COOLDOWN_TICKS);
    }

    // 1.16 KCConfig epitaphCooldown
    @Override
    public int getCooldown(Power<?> context, int ticksHeld) {
        return KingCrimsonConfig.configuredCooldown(
                super.getCooldown(context, ticksHeld),
                KingCrimsonConfig.epitaphCooldownTicks());
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player)) {
            return negative("player_only");
        }
        EpitaphState state =
                KingCrimsonAddon.epitaphState(player);
        if (state.isPresentationActive(
                player.level().getGameTime())) {
            return negative("epitaph_already_active");
        }
        return ConditionCheck.POSITIVE;
    }

    // Full addon key; the String overload would add the jojo prefix.
    private static ConditionCheck negative(String key) {
        return ConditionCheck.createNegative(
                Component.translatable(
                        "rotp_kingcrimson.message." + key));
    }

    public static final class EpitaphAction
            extends EntityActionInstance {
        private boolean released;

        public EpitaphAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer player)
                    || !(ability
                            instanceof KingCrimsonEpitaphAbility
                            epitaphAbility)) {
                return;
            }
            StandPower power = StandPower.get(player);
            // 1.16 never charged Epitaph's staminaCost(175): a hold-only StandAction was never performed.
            PredictionKind prediction =
                    power.getResolveLevel()
                                    >= KingCrimsonPhase3Spec
                                            .HIGH_RESOLVE_LEVEL
                            ? PredictionKind.ANY_NON_BYPASSING
                            : PredictionKind.DIRECT_ATTACK;
            if (!EpitaphStateService.arm(player, prediction)) {
                forceStop();
                syncPhaseChanges();
                return;
            }

            epitaphAbility.setCooldownOnUse(power);
            sayShoutOf(
                    ability, player, KingCrimsonAddon.DIAVOLO_EPITAPH);
            level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    KingCrimsonAddon.KINGCRIMSON_EPITAPH.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM
                    || getPhaseTick() <= 0
                    || !(getPowerUser()
                            instanceof ServerPlayer player)) {
                return;
            }
            if (!KingCrimsonAddon.epitaphState(player)
                    .isArmed(level().getGameTime())) {
                forceStop();
                syncPhaseChanges();
                return;
            }
        }

        /**
         * Letting go of the key does not close the vision: what Epitaph has shown stays for the rest of its window, so
         * King Crimson can be told to erase that time, as Diavolo does in the manga.
         */
        @Override
        public void onButtonStopHold() {
            released = true;
            forceStop();
            syncPhaseChanges();
        }

        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user instanceof ServerPlayer player
                    && !released
                    && !continuesIntoErasedTime(newAction)) {
                EpitaphStateService.terminateIfArmed(player);
            }
        }

        /**
         * In the manga Epitaph and King Crimson work as one: Diavolo foresees the coming seconds, then erases them.
         * Erasing time does not close the vision, which stays armed for the rest of its window.
         */
        private static boolean continuesIntoErasedTime(
                @Nullable EntityActionInstance newAction) {
            return newAction != null
                    && newAction.ability
                            instanceof KingCrimsonTimeEraseAbility;
        }
    }
}
