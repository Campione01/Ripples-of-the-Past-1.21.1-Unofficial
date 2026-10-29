package rotp.addons.thelock.item;

import java.util.UUID;

import rotp.addons.thelock.TheLockAddon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TheLockItem extends Item {
    public TheLockItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createOwned(LivingEntity owner) {
        ItemStack stack =
                new ItemStack(TheLockAddon.THE_LOCK_ITEM.get());
        stack.set(
                TheLockAddon.LOCK_ITEM_OWNER.get(),
                new LockItemData(owner.getUUID()));
        return stack;
    }

    public static boolean isOwnedBy(
            ItemStack stack, LivingEntity owner) {
        LockItemData data = stack != null
                ? stack.get(TheLockAddon.LOCK_ITEM_OWNER.get())
                : null;
        return stack != null
                && stack.is(TheLockAddon.THE_LOCK_ITEM.get())
                && data != null
                && data.ownerUuid().equals(owner.getUUID());
    }

    public static boolean isActiveOwned(
            ItemStack stack, LivingEntity owner) {
        return isOwnedBy(stack, owner)
                && TheLockAddon.controller(owner) != null;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int itemSlot,
            boolean isSelected) {
        if (!level.isClientSide()
                && (!(entity instanceof LivingEntity living)
                        || !isActiveOwned(stack, living))) {
            stack.setCount(0);
        }
    }

    @Override
    public boolean onEntityItemUpdate(
            ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide()) {
            entity.discard();
        }
        return true;
    }

    public static void enforceOwnedItem(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        if (TheLockAddon.controller(player) == null) {
            purgeAll(player);
            return;
        }

        ItemStack keep = ItemStack.EMPTY;
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack =
                    player.getInventory().getItem(slot);
            if (!stack.is(TheLockAddon.THE_LOCK_ITEM.get())) {
                continue;
            }
            if (keep.isEmpty() && isOwnedBy(stack, player)) {
                keep = stack;
            } else {
                player.getInventory().setItem(
                        slot, ItemStack.EMPTY);
            }
        }

        purgeMenuExcept(player, keep);
        if (keep.isEmpty()) {
            player.getInventory().add(createOwned(player));
        }
    }

    public static void purgeAll(LivingEntity holder) {
        if (holder instanceof Player player) {
            for (int slot = 0;
                    slot < player.getInventory().getContainerSize();
                    slot++) {
                ItemStack stack =
                        player.getInventory().getItem(slot);
                if (stack.is(TheLockAddon.THE_LOCK_ITEM.get())) {
                    player.getInventory().setItem(
                            slot, ItemStack.EMPTY);
                }
            }
            purgeMenuExcept(player, ItemStack.EMPTY);
            return;
        }

        if (holder.getMainHandItem().is(
                TheLockAddon.THE_LOCK_ITEM.get())) {
            holder.setItemInHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    ItemStack.EMPTY);
        }
        if (holder.getOffhandItem().is(
                TheLockAddon.THE_LOCK_ITEM.get())) {
            holder.setItemInHand(
                    net.minecraft.world.InteractionHand.OFF_HAND,
                    ItemStack.EMPTY);
        }
    }

    private static void purgeMenuExcept(
            Player player, ItemStack keep) {
        if (player.containerMenu == null) {
            return;
        }
        for (Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (stack != keep
                    && stack.is(TheLockAddon.THE_LOCK_ITEM.get())) {
                slot.set(ItemStack.EMPTY);
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried != keep
                && carried.is(TheLockAddon.THE_LOCK_ITEM.get())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    public static boolean ownerMatches(
            ItemStack stack, UUID ownerUuid) {
        LockItemData data = stack != null
                ? stack.get(TheLockAddon.LOCK_ITEM_OWNER.get())
                : null;
        return data != null && data.ownerUuid().equals(ownerUuid);
    }
}
