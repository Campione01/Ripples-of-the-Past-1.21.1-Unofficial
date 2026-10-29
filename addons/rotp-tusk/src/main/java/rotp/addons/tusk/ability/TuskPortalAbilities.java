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
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.state.TuskPortalOperations;
import rotp.addons.tusk.state.TuskPortalOperations.OperationKind;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class TuskPortalAbilities {
    public static final float CREATE_PORTAL_STAMINA = 150.0F;
    public static final int CREATE_RECOVERY_TICKS = 8;
    public static final int MOVE_RECOVERY_TICKS = 4;
    public static final int REMOVE_RECOVERY_TICKS = 4;

    private TuskPortalAbilities() {}

    public static final class CreatePortal
            extends EntityActionAbility {
        public CreatePortal(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId, CreatePortalAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(
                    ActionPhase.RECOVERY, CREATE_RECOVERY_TICKS);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            return checkCreate(
                    super.checkSpecificConditions(context), context);
        }
    }

    public static final class CreatePortalAction
            extends EntityActionInstance {
        public CreatePortalAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(getPerformer() instanceof ServerPlayer player)
                    || !isPortalActReady(player)) {
                return;
            }
            TuskPortalOperations operations =
                    TuskPortalOperations.get(player.server);
            if (!operations.canCreate(player)) {
                creationFailed(player);
                return;
            }
            // 1.16 StandAction.onPerform: the cost is paid with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(player),
                    CREATE_PORTAL_STAMINA,
                    false);
            if (!operations.createPortalPair(player)) {
                creationFailed(player);
            }
        }
    }

    public static final class MovePortal extends EntityActionAbility {
        public MovePortal(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId, MovePortalAction::new);
            setButtonHoldPhase(ActionPhase.PERFORM);
            setDefaultPhaseLength(
                    ActionPhase.RECOVERY, MOVE_RECOVERY_TICKS);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            return checkExisting(
                    super.checkSpecificConditions(context),
                    context,
                    OperationKind.PORTAL_PAIR);
        }
    }

    public static final class MovePortalAction
            extends EntityActionInstance {
        public MovePortalAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() == ActionPhase.PERFORM
                    && getPerformer() instanceof ServerPlayer player
                    && !TuskPortalOperations.get(player.server)
                            .moveOwned(
                                    player,
                                    OperationKind.PORTAL_PAIR)) {
                startRecovery();
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }
    }

    public static final class RemovePortal
            extends EntityActionAbility {
        public RemovePortal(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId, RemovePortalAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(
                    ActionPhase.RECOVERY, REMOVE_RECOVERY_TICKS);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            return checkExisting(
                    super.checkSpecificConditions(context),
                    context,
                    OperationKind.PORTAL_PAIR);
        }
    }

    public static final class RemovePortalAction
            extends EntityActionInstance {
        public RemovePortalAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (getPerformer() instanceof ServerPlayer player) {
                TuskPortalOperations.get(player.server).closeOwned(
                        player.getUUID(),
                        OperationKind.PORTAL_PAIR);
            }
        }
    }

    public static final class CreateArm extends EntityActionAbility {
        public CreateArm(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId, CreateArmAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(
                    ActionPhase.RECOVERY, CREATE_RECOVERY_TICKS);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            return checkCreate(
                    super.checkSpecificConditions(context), context);
        }
    }

    public static final class CreateArmAction
            extends EntityActionInstance {
        public CreateArmAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (getPerformer() instanceof ServerPlayer player
                    && isPortalActReady(player)
                    && !TuskPortalOperations.get(player.server)
                            .createRemoteArm(player)) {
                creationFailed(player);
            }
        }
    }

    public static final class MoveArm extends EntityActionAbility {
        public MoveArm(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId, MoveArmAction::new);
            setButtonHoldPhase(ActionPhase.PERFORM);
            setDefaultPhaseLength(
                    ActionPhase.RECOVERY, MOVE_RECOVERY_TICKS);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            return checkExisting(
                    super.checkSpecificConditions(context),
                    context,
                    OperationKind.REMOTE_ARM);
        }
    }

    public static final class MoveArmAction
            extends EntityActionInstance {
        public MoveArmAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            if (getPhase() == ActionPhase.PERFORM
                    && getPerformer() instanceof ServerPlayer player
                    && !TuskPortalOperations.get(player.server)
                            .moveOwned(
                                    player,
                                    OperationKind.REMOTE_ARM)) {
                startRecovery();
            }
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }
    }

    public static final class RemoveArm
            extends EntityActionAbility {
        public RemoveArm(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId, RemoveArmAction::new);
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(
                    ActionPhase.RECOVERY, REMOVE_RECOVERY_TICKS);
        }

        @Override
        public ConditionCheck checkSpecificConditions(
                Power<?> context) {
            return checkExisting(
                    super.checkSpecificConditions(context),
                    context,
                    OperationKind.REMOTE_ARM);
        }
    }

    public static final class RemoveArmAction
            extends EntityActionInstance {
        public RemoveArmAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (getPerformer() instanceof ServerPlayer player) {
                TuskPortalOperations.get(player.server).closeOwned(
                        player.getUUID(),
                        OperationKind.REMOTE_ARM);
            }
        }
    }

    private static ConditionCheck checkCreate(
            ConditionCheck base, Power<?> context) {
        ConditionCheck ready = checkPortalAct(base, context);
        if (!ready.isPositive()) {
            return ready;
        }
        Player player = (Player) context.getUser();
        if (TuskAddon.nailState(player).nails() <= 0) {
            return ConditionCheck.createNegative(Component.translatable(
                    "rotp_t.message.action_condition.no_nails"));
        }
        if (player instanceof ServerPlayer serverPlayer
                && !TuskPortalOperations.get(serverPlayer.server)
                        .canCreate(serverPlayer)) {
            return ConditionCheck.createNegative(Component.translatable(
                    "rotp_t.message.action_condition.portal_source"));
        }
        return ConditionCheck.POSITIVE;
    }

    private static ConditionCheck checkExisting(
            ConditionCheck base,
            Power<?> context,
            OperationKind expectedKind) {
        ConditionCheck ready = checkPortalAct(base, context);
        if (!ready.isPositive()) {
            return ready;
        }
        if (context.getUser() instanceof ServerPlayer player
                && !TuskPortalOperations.get(player.server)
                        .hasOwnedKind(player, expectedKind)) {
            return ConditionCheck.createNegative(Component.translatable(
                    "rotp_t.message.action_condition.portal_missing"));
        }
        return ConditionCheck.POSITIVE;
    }

    private static ConditionCheck checkPortalAct(
            ConditionCheck base, Power<?> context) {
        if (!base.isPositive()) {
            return base;
        }
        if (!(context.getUser() instanceof Player player)
                || !player.isAlive()
                || !TuskAddon.isPortalActUser(player)) {
            return ConditionCheck.NEGATIVE;
        }
        StandPower power = StandPower.get(player);
        return power != null && power.isSummoned()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    private static boolean isPortalActReady(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        return player.isAlive()
                && !player.isRemoved()
                && TuskAddon.isPortalActUser(player)
                && power != null
                && power.isSummoned();
    }

    private static void creationFailed(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable(
                        "rotp_t.message.action_condition."
                                + "portal_create_failed"),
                true);
    }
}
