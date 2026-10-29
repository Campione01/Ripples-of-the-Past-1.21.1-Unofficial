package rotp.addons.memory_discs.item.dispenser;

import rotp.core.util.functions.ItemUtil;
import rotp.addons.memory_discs.RotpMemoryDiscsAddon;
import rotp.addons.memory_discs.item.MemoryDiscItem;
import rotp.addons.memory_discs.item.PowerDiscData;
import rotp.addons.memory_discs.service.PlayerPowerTransitionService;
import rotp.addons.memory_discs.service.PlayerPowerTransitionService.Result;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

public final class MemoryDiscDispenserBehaviors {
    private MemoryDiscDispenserBehaviors() {}

    public static void register() {
        DispenserBlock.registerBehavior(
                RotpMemoryDiscsAddon.MEMORY_DISC.get(),
                new PowerToolBehavior(Operation.APPLY_DISC, true));
        DispenserBlock.registerBehavior(
                RotpMemoryDiscsAddon.MEMORY_REMOVER.get(),
                new PowerToolBehavior(Operation.REMOVE, false));
        DispenserBlock.registerBehavior(
                RotpMemoryDiscsAddon.MEMORY_REMOVER_ONE_TIME.get(),
                new PowerToolBehavior(Operation.REMOVE, true));
        DispenserBlock.registerBehavior(
                RotpMemoryDiscsAddon.MEMORY_EJECT.get(),
                new PowerToolBehavior(Operation.EJECT, false));
        DispenserBlock.registerBehavior(
                RotpMemoryDiscsAddon.MEMORY_EJECT_ONE_TIME.get(),
                new PowerToolBehavior(Operation.EJECT, true));
    }

    private static final class PowerToolBehavior
            extends DefaultDispenseItemBehavior {
        private final Operation operation;
        private final boolean consumeOnSuccess;

        private PowerToolBehavior(
                Operation operation,
                boolean consumeOnSuccess) {
            this.operation = operation;
            this.consumeOnSuccess = consumeOnSuccess;
        }

        @Override
        protected ItemStack execute(
                BlockSource source, ItemStack stack) {
            if (operation == Operation.APPLY_DISC
                    && !MemoryDiscItem.hasPower(stack)) {
                return super.execute(source, stack);
            }

            Direction direction =
                    source.state().getValue(DispenserBlock.FACING);
            BlockPos targetPos =
                    source.pos().relative(direction);
            ServerLevel level = source.level();

            for (LivingEntity target
                    : level.getEntitiesOfClass(
                            LivingEntity.class,
                            new AABB(targetPos),
                            entity -> !entity.isSpectator())) {
                Result result =
                        operation == Operation.APPLY_DISC
                        ? PlayerPowerTransitionService.applyDisc(
                                target, stack, false)
                        : PlayerPowerTransitionService.clearPower(
                                target);
                if (!result.applied()) {
                    continue;
                }

                if (operation == Operation.EJECT
                        && result.previousType() != null) {
                    ItemUtil.giveItemTo(
                            result.target(),
                            PowerDiscData.newFilledDisc(
                                    result.previousType()),
                            true);
                }
                if (consumeOnSuccess) {
                    stack.shrink(1);
                }
                return stack;
            }
            return super.execute(source, stack);
        }
    }

    private enum Operation {
        APPLY_DISC,
        REMOVE,
        EJECT
    }
}
