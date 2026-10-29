package rotp.addons.hermitpurple.ability;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
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
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.hermitpurple.HermitHamon;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public final class HermitBarrierAbility
        extends NoPoseStandEntityAbility {
    private final boolean remove;

    public HermitBarrierAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                type -> new BarrierAction(
                        type,
                        HermitPurpleAddon.HP_UNBARRIER_NAME.equals(
                                abilityId.nameInMoveset())));
        remove = HermitPurpleAddon.HP_UNBARRIER_NAME.equals(
                abilityId.nameInMoveset());
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 hp_barrier and hp_unbarrier (061 InitStands:78-79) set no
        // standRecoveryTicks
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    /**
     * 1.16's {@code HPAttachBarrier} put the string budget in the ability's own name - "Hermit Purple String
     * (3/15)" - by overriding {@code getTranslatedName} with the placed count and the maximum. The port dropped
     * the override, so the only way to learn the limit is to hit it and read the refusal.
     *
     * <p>Both numbers are already here and the maximum is the same 15 the donor hard-coded;
     * {@code getPlacedBarrierCount} reads the synced value on the client. The placing ability carries the
     * counter, the removing one does not, exactly as 1.16 had it (only {@code HPAttachBarrier} overrode the
     * name). The wording stays the port's.
     *
     * <p>The argument is always supplied, including with no Stand out: an unfilled placeholder does not throw,
     * it prints a literal "%s".
     */
    @Override
    public Component getName(Power<?> context) {
        if (remove) {
            return super.getName(context);
        }
        HermitPurpleStandEntity stand =
                HermitPurpleAddon.controller(context.getUser());
        return abilityName(
                context,
                "",
                String.valueOf(
                        stand == null ? 0 : stand.getPlacedBarrierCount()),
                String.valueOf(HermitPurpleSpec.BARRIER_MAX_PLACED));
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
        HermitPurpleStandEntity stand =
                HermitPurpleAddon.controller(user);
        if (stand == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (remove) {
            return stand.getPlacedBarrierCount() > 0
                    ? ConditionCheck.POSITIVE
                    : ConditionCheck.NEGATIVE;
        }
        if (!HermitHamon.hasSkill(
                user, ModHamonSkills.ROPE_TRAP.get())) {
            return ConditionCheck.createNegative(
                    "strings_learn");
        }
        if (!stand.canPlaceBarrier()) {
            return ConditionCheck.createNegative("barrier");
        }
        return checkPressedBlock(user, stand);
    }

    // 1.16 HPAttachBarrier.getTargetRequirement BLOCK: PowerBaseImpl.checkTarget
    // refused the press, silently, unless it was at a block that is not air,
    // and with "target_too_far" beyond 10 blocks of the Stand
    // (Action.checkRangeAndTarget).
    private static ConditionCheck checkPressedBlock(
            LivingEntity user, HermitPurpleStandEntity stand) {
        Level level = stand.level();
        ActionTarget target = pressedTarget(user, stand);
        if (target.getType() != TargetType.BLOCK) {
            return ConditionCheck.NEGATIVE;
        }
        BlockPos pos = target.getBlockPos();
        if (level.getBlockState(pos).is(Blocks.AIR)) {
            return ConditionCheck.NEGATIVE;
        }
        if (!ActionTargetRange.isBlockWithinRange(stand, level, pos,
                ActionTargetRange.DEFAULT_BLOCK_RANGE_SQ)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        return ConditionCheck.POSITIVE;
    }

    // The Stand's aim the action captures; the server puts the user's aim in
    // an empty Stand aim of a press (ClAbilityInputPacket). The aims go only
    // from the user's client to the server, so that client reads the ones it
    // sends.
    private static ActionTarget pressedTarget(
            LivingEntity user, HermitPurpleStandEntity stand) {
        Level level = stand.level();
        ActionTarget target = aimOf(stand, user, level);
        return target.isEmpty(level) ? aimOf(user, user, level) : target;
    }

    private static ActionTarget aimOf(
            LivingEntity aiming, LivingEntity user, Level level) {
        ActionTargetAim aim = level.isClientSide()
                        && user == ClientProxy.getClientPlayer()
                ? aiming == user
                        ? ClientsideAim.playerAim
                        : ClientsideAim.standAim
                : LivingComponentAction.getAim(aiming);
        ActionTarget target = aim != null ? aim.getTarget() : null;
        return target != null
                ? target.resolveEntityId(level)
                : ActionTarget.EMPTY;
    }

    // 1.16 HPAttachBarrier getTargetRequirement BLOCK: its task kept the
    // block it was pressed at
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return !remove && target.getType() == TargetType.BLOCK;
    }

    // 1.16 PowerBaseImpl.checkTarget emptied any other pressed target: hp_barrier
    // (TargetRequirement BLOCK) took only a block, hp_unbarrier (no
    // standKeepsTarget) nothing, so the Stand never turned to a mob.
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return keepsTaskTargetType(standEntity, target);
    }

    // 1.16 StandEntityAction.overrideVanillaMouseTarget let a pressed block
    // without a collision shape give way only to an action that takes an
    // entity: HPAttachBarrier (BLOCK) kept a pressed torch or grass,
    // HPUnattachBarrier (NONE) let it give way but kept no target.
    @Override
    public boolean pressedBlockGivesWayToEntity() {
        return remove;
    }

    public static final class BarrierAction
            extends EntityActionInstance {
        private final boolean remove;

        public BarrierAction(
                EntityActionType ability, boolean remove) {
            super(ability);
            this.remove = remove;
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            if (performer instanceof HermitPurpleStandEntity stand) {
                keepStandAimedAtTarget(
                        captureActionTargetFromAim(stand));
            }
            aimAs = AimingEntity.STAND;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(performer
                            instanceof HermitPurpleStandEntity stand)) {
                return;
            }
            if (remove) {
                stand.discardBarriers();
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            ActionTarget target =
                    getActionTargetSnapshot(level());
            if (user == null
                    || target.getType() != TargetType.BLOCK
                    || !level().hasChunkAt(target.getBlockPos())) {
                startRecovery();
                return;
            }
            stand.attachBarrier(target.getBlockPos());
            StandUtil.playStandEntitySound(
                    stand,
                    HermitPurpleAddon.HP_GRAB_CATCH.get(),
                    1.0F,
                    1.0F);
            // 1.16 StandEntityTask paid the cost after the perform and ignored
            // the result: a short bar only drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    HermitPurpleSpec.BARRIER_STAMINA,
                    false);
        }
    }
}
