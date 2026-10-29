package rotp.addons.tusk.ability;

import java.util.ArrayList;
import java.util.List;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.tusk.state.TuskOpenSpaceLeaseData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class TuskOpenSpaceAbility
        extends NoPoseStandEntityAbility {
    public static final float STAMINA_COST = 225.0F;
    public static final double TARGET_RANGE = 6.0D;
    public static final int MAX_BLOCKS = 12;

    public TuskOpenSpaceAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, OpenSpaceAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
        standAutoSummonMode(AutoSummonMode.FULL);
    }

    // 1.16 TuskOpenSpace.getTargetRequirement BLOCK: PowerBaseImpl.checkTarget
    // refused the press without a message, before any cost, unless the
    // client's target was a block.
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        ActionTarget target =
                pressTarget(user, StandUtil.getSummonedStand(context));
        return target.getType() == ActionTarget.TargetType.BLOCK
                        && target.getBlockPos() != null
                        && !user.level().getBlockState(
                                target.getBlockPos()).isAir()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    // 1.16 TuskOpenSpace.getTargetRequirement BLOCK: the task kept the
    // block it was pressed at
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.BLOCK;
    }

    // 1.16 getTargetRequirement BLOCK: PowerBaseImpl.checkTarget emptied a
    // pressed target of any other kind
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.BLOCK;
    }

    // 1.16 StandEntityAction.overrideVanillaMouseTarget ran only for an
    // action that takes an entity: TuskOpenSpace (BLOCK) kept a pressed torch
    // or grass instead of the mob behind it.
    @Override
    public boolean pressedBlockGivesWayToEntity() {
        return false;
    }

    // 1.16 EntityStandType.clientHitResult: the vanilla pick, which is the
    // user's synced aim, or when that missed the Stand's precision ray at
    // the user's 5-block reach.
    private static ActionTarget pressTarget(
            LivingEntity user, StandEntity stand) {
        Level level = user.level();
        LivingEntity aiming = stand != null && stand.isManuallyControlled()
                ? stand : user;
        // The aims only go from the user's client to the server, so that
        // client reads the aims it sends.
        ActionTarget target;
        if (level.isClientSide() && user == ClientProxy.getClientPlayer()) {
            target = (aiming == user
                    ? ClientsideAim.playerAim
                    : ClientsideAim.standAim).getTarget();
        } else {
            var aim = LivingComponentAction.getAim(aiming);
            target = aim != null && aim.getTarget() != null
                    ? aim.getTarget() : ActionTarget.EMPTY;
        }
        if (target.isEmpty(level) && aiming == user && stand != null) {
            target = StandPrecisionRay.trace(user, stand);
        }
        return target;
    }

    public static final class OpenSpaceAction
            extends EntityActionInstance {
        public OpenSpaceAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (getPerformer() instanceof StandEntity stand) {
                ActionTarget target = captureActionTargetFromAim(stand);
                keepStandAimedAtTarget(target);
                aimAs = AimingEntity.STAND;
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null || !user.isAlive()) {
                return;
            }

            ActionTarget target = aimWithThisOrUser(
                    stand, user, getActionTargetSnapshot(level));
            setActionTargetSnapshot(target);
            if (target.getType() != ActionTarget.TargetType.BLOCK
                    || target.getBlockPos() == null
                    || target.getFace() == null) {
                return;
            }

            TuskOpenSpaceLeaseData leases =
                    TuskOpenSpaceLeaseData.get(level);
            List<BlockPos> positions = collectPositions(
                    level,
                    leases,
                    user,
                    target.getBlockPos(),
                    target.getFace().getOpposite());
            if (positions.isEmpty()) {
                return;
            }

            // 1.16 StandEntityTask: the cost is paid with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability, StandPower.get(user), STAMINA_COST, false);
            leases.open(level, user, positions);
        }

        // 1.16 StandEntity.aimWithThisOrUser(TARGET_RANGE, task target,
        // false): the task's target while the Stand reaches it, else the
        // precision ray from the user's eyes, else from the Stand's.
        private static ActionTarget aimWithThisOrUser(
                StandEntity stand,
                LivingEntity user,
                ActionTarget current) {
            ActionTarget target =
                    StandEntityPunchAbility.validatePunchTarget(
                            stand, current);
            if (!target.isEmpty(stand.level())) {
                return target;
            }
            if (!stand.isManuallyControlled()) {
                target = StandPrecisionRay.trace(
                        user, stand, TARGET_RANGE, 0.0D, false, true);
                if (!target.isEmpty(stand.level())) {
                    return target;
                }
            }
            return StandPrecisionRay.trace(
                    stand, stand, TARGET_RANGE, 0.0D, false, true);
        }

        private static List<BlockPos> collectPositions(
                ServerLevel level,
                TuskOpenSpaceLeaseData leases,
                LivingEntity owner,
                BlockPos start,
                Direction direction) {
            List<BlockPos> positions =
                    new ArrayList<>(MAX_BLOCKS);
            for (int index = 0; index < MAX_BLOCKS; index++) {
                BlockPos position = start.relative(direction, index);
                if (!leases.canOpen(level, owner, position)) {
                    break;
                }
                positions.add(position);

                BlockPos next = position.relative(direction);
                BlockState nextState = level.getBlockState(next);
                if (nextState.is(Blocks.BEDROCK)
                        || !nextState.isSolidRender(level, next)) {
                    break;
                }
            }
            return positions;
        }
    }
}
