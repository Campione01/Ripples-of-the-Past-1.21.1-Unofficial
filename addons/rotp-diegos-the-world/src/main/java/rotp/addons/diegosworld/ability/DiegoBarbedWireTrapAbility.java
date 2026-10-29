package rotp.addons.diegosworld.ability;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import rotp.addons.diegosworld.block.BarbedWireBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class DiegoBarbedWireTrapAbility
        extends StandEntityAbility {
    public static final int WIRE_COST = 6;
    public static final float STAMINA_COST = 50.0F;

    public DiegoBarbedWireTrapAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, BarbedWireTrapAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 1);
        cooldown(200);
        resolveLevelToUnlock(1);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context.getUser();
        if (!hasOwnTimeStop(user)) {
            return ConditionCheck.createNegative(
                    "only_in_time_stop");
        }
        if (!(user instanceof Player player)
                || countWire(player) < WIRE_COST) {
            return ConditionCheck.createNegative(
                    "need_barbed_wire");
        }
        return checkPressedTarget(context);
    }

    // 1.16 BarbedWireTrap.getTargetRequirement ENTITY: PowerBaseImpl.checkTarget
    // refused the press unless it was at an entity within 8 blocks of the
    // performer (4 out of sight, "target_too_far") that checkStandTarget took,
    // a living one the Stand may attack; nothing was paid. The refusals 1.16
    // left silent keep the port's target_entity message. Before any of it
    // StandEntityAction.overrideVanillaMouseTarget let a pressed torch or
    // grass give way to what the performer's collision ray met.
    private ConditionCheck checkPressedTarget(Power<?> context) {
        LivingEntity user = context.getUser();
        StandEntity stand = context instanceof StandPower power
                ? power.getSummonedStandEntity()
                : null;
        LivingEntity performer = stand != null ? stand : user;
        ActionTarget pressed = pressedTarget(user, stand);
        Entity target = (user != null
                ? overridePressedTarget(user, pressed)
                : pressed).getMainEntity();
        if (target == null) {
            return ConditionCheck.createNegative("target_entity");
        }
        if (!ActionTargetRange.isEntityWithinRange(performer, target,
                ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (!(target instanceof LivingEntity living)
                || !standMayAttack(user, stand, living)) {
            return ConditionCheck.createNegative("target_entity");
        }
        return ConditionCheck.POSITIVE;
    }

    // What the press is at: the Stand's aim while it is out, the user's
    // otherwise (1.16 StandPower.clientHitResult); the server puts the user's
    // aim in an empty Stand aim of a press (ClAbilityInputPacket).
    static ActionTarget pressedTarget(
            LivingEntity user, StandEntity stand) {
        if (user == null) {
            return ActionTarget.EMPTY;
        }
        Level level = user.level();
        ActionTarget target = stand != null
                ? aimOf(stand, user, level)
                : ActionTarget.EMPTY;
        return target.isEmpty(level) ? aimOf(user, user, level) : target;
    }

    // The aims go only from the user's client to the server, so that client
    // reads the ones it sends.
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

    // 1.16 StandEntity.canAttack, which the server asked of the Stand its
    // press had just summoned; with the Stand away, its rules for the user.
    private static boolean standMayAttack(
            LivingEntity user, StandEntity stand, LivingEntity target) {
        if (stand != null) {
            return stand.canAttack(target);
        }
        if (user == null || target == user
                || !DamageUtil.isNotFriendlyFire(
                        user, StandUtil.getStandUser(target))) {
            return false;
        }
        if (target instanceof Animal) {
            if (target.isPassengerOfSameVehicle(user)) {
                return false;
            }
            return !(target instanceof TamableAnimal tameable
                    && tameable.getOwner() == user);
        }
        return true;
    }

    private static boolean hasOwnTimeStop(LivingEntity user) {
        if (user == null) {
            return false;
        }
        if (user.level().isClientSide()) {
            return TimeStopState.getClientInstance(user.getId())
                    .filter(TimeStopState.Instance::isActive)
                    .isPresent();
        }
        if (user.level() instanceof ServerLevel level) {
            TimeStopState state = level.getData(
                    ModDataAttachmentTypes.TIME_STOP.get());
            return state.isTimeStopped(user)
                    && state.getInstance(user.getId())
                            .filter(TimeStopState.Instance::isActive)
                            .isPresent();
        }
        return false;
    }

    private static int countWire(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(DiegosTheWorldAddon.BARBED_WIRE_ITEM.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean consumeWire(Player player, int amount) {
        if (countWire(player) < amount) {
            return false;
        }
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.is(
                    DiegosTheWorldAddon.BARBED_WIRE_ITEM.get())) {
                continue;
            }
            int consumed = Math.min(stack.getCount(), remaining);
            stack.shrink(consumed);
            remaining -= consumed;
            if (remaining == 0) {
                return true;
            }
        }
        return false;
    }

    public static final class BarbedWireTrapAction
            extends EntityActionInstance {
        public BarbedWireTrapAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (getPerformer() instanceof StandEntity stand
                    && !level().isClientSide()) {
                // a Stand the press summoned has no aim yet: the user's
                LivingEntity user = stand.getUser();
                LivingEntity aiming = aimOf(stand, user, level())
                        .isEmpty(level()) ? user : stand;
                if (aiming != null) {
                    captureActionTargetFromAim(aiming);
                }
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPowerUser() instanceof Player player)) {
                return;
            }
            placeTrap(level, player);
            // 1.16 StandEntityTask paid staminaCost(50) after standPerform
            // whatever it placed; a short bar just drops to 0
            StandAbilityStamina.consume(
                    ability, StandPower.get(player), STAMINA_COST, false);
        }

        private void placeTrap(ServerLevel level, Player player) {
            if (!hasOwnTimeStop(player)) {
                return;
            }
            ActionTarget target =
                    getActionTargetSnapshot(level);
            Entity targetEntity = target.getMainEntity();
            if (targetEntity == null) {
                player.displayClientMessage(
                        Component.translatable(
                                "jojo.message.action_condition.target_entity"),
                        true);
                return;
            }
            BlockPos ground = findGroundBelow(
                    level, targetEntity.blockPosition());
            if (ground == null
                    || targetEntity.getY() - ground.getY() > 2.0D) {
                player.displayClientMessage(
                        Component.translatable(
                                "jojo.message.action_condition.too_high"),
                        true);
                return;
            }
            if (!consumeWire(player, WIRE_COST)) {
                return;
            }
            placeBarbedWireAround(level, player, ground);
            JojoModUtil.sayVoiceLine(
                    player,
                    DiegosTheWorldAddon
                            .DIEGO_AND_NOW_THE_FUN_BEGINS,
                    1.0F,
                    1.0F,
                    0,
                    true);
        }

        private static BlockPos findGroundBelow(
                ServerLevel level, BlockPos start) {
            for (int down = 0; down <= 3; down++) {
                BlockPos pos = start.below(down);
                BlockState state = level.getBlockState(pos);
                if (state.isFaceSturdy(
                                level, pos, Direction.UP)
                        || !state.getFluidState().isEmpty()) {
                    return pos;
                }
            }
            return null;
        }

        private static void placeBarbedWireAround(
                ServerLevel level,
                Player player,
                BlockPos ground) {
            placeWall(level, player, ground.north(2), Direction.SOUTH,
                    Direction.EAST);
            placeWall(level, player, ground.south(2), Direction.NORTH,
                    Direction.EAST);
            placeWall(level, player, ground.west(2), Direction.EAST,
                    Direction.SOUTH);
            placeWall(level, player, ground.east(2), Direction.WEST,
                    Direction.SOUTH);
        }

        private static void placeWall(
                ServerLevel level,
                Player player,
                BlockPos center,
                Direction facing,
                Direction lateral) {
            for (int side = -1; side <= 1; side++) {
                for (int up = 1; up <= 3; up++) {
                    BlockPos pos = center.relative(lateral, side)
                            .above(up);
                    BlockState old = level.getBlockState(pos);
                    if (!level.mayInteract(player, pos)
                            || !canReplace(old)) {
                        continue;
                    }
                    BlockState wire = DiegosTheWorldAddon
                            .BARBED_WIRE.get()
                            .defaultBlockState()
                            .setValue(
                                    BarbedWireBlock.FACING, facing)
                            .setValue(
                                    BarbedWireBlock.PART,
                                    BarbedWireBlock.CONTROLLER_PART);
                    level.setBlock(pos, wire, 3);
                }
            }
        }

        private static boolean canReplace(BlockState state) {
            return state.canBeReplaced()
                    || state.is(BlockTags.LEAVES)
                    || !state.getFluidState().isEmpty();
        }
    }
}
