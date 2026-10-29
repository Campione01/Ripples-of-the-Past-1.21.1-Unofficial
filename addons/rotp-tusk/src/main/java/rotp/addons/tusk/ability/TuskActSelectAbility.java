package rotp.addons.tusk.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.tusk.TuskAddon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class TuskActSelectAbility extends EntityActionAbility {
    public static final int RECOVERY_TICKS = 8;

    public TuskActSelectAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, SelectActAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, RECOVERY_TICKS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        if (!(context.getUser() instanceof Player player)
                || !player.isAlive()) {
            return ConditionCheck.NEGATIVE;
        }
        StandPower power = StandPower.get(player);
        int currentAct = power != null
                ? TuskAddon.actIndex(power.getPowerType())
                : -1;
        if (currentAct < TuskAddon.ACT_1
                || currentAct > TuskAddon.ACT_4) {
            return ConditionCheck.NEGATIVE;
        }
        int targetAct = targetAct(currentAct, power.getResolveLevel());
        if (targetAct == currentAct
                || !TuskActTransition.isUnlocked(
                        currentAct, targetAct, power.getResolveLevel())) {
            int lockedAct = currentAct + 1;
            return ConditionCheck.createNegative(Component.translatable(
                    "rotp_t.message.action_condition."
                            + switch (lockedAct) {
                                case TuskAddon.ACT_4 ->
                                        "act_4_locked";
                                case TuskAddon.ACT_3 ->
                                        "act_3_locked";
                                default -> "act_2_locked";
                            }));
        }
        return ConditionCheck.POSITIVE;
    }

    /**
     * The 1.16 {@code ChangeActScreen} let the user pick any act up to the resolve level; the port cycles
     * instead, so when the next act is still locked the cycle wraps to Act 1 rather than stalling at the
     * highest unlocked act (a lower act is always allowed by {@link TuskActRules#isActUnlocked}).
     */
    private static int targetAct(int currentAct, int resolveLevel) {
        int next = currentAct == TuskAddon.ACT_4
                ? TuskAddon.ACT_1
                : currentAct + 1;
        if (next > currentAct
                && !TuskActTransition.isUnlocked(currentAct, next, resolveLevel)) {
            return TuskAddon.ACT_1;
        }
        return next;
    }

    public static final class SelectActAction
            extends EntityActionInstance {
        public SelectActAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(getPerformer() instanceof ServerPlayer player)) {
                return;
            }
            StandPower power = StandPower.get(player);
            if (power == null) {
                return;
            }
            int currentAct = TuskAddon.actIndex(power.getPowerType());
            if (currentAct >= TuskAddon.ACT_1
                    && currentAct <= TuskAddon.ACT_4) {
                if (!TuskActTransition.replace(
                        player,
                        targetAct(currentAct, power.getResolveLevel()))) {
                    player.displayClientMessage(
                            Component.translatable(
                                    "rotp_t.message.action_condition."
                                            + "act_transition_failed"),
                            true);
                }
            }
        }
    }
}
