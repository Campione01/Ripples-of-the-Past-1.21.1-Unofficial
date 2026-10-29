package rotp.addons.catchtherainbow.item;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class CatchTheRainbowMaskItem extends ArmorItem {
    public CatchTheRainbowMaskItem(Properties properties) {
        super(
                CatchTheRainbowAddon.MASK_MATERIAL,
                ArmorItem.Type.HELMET,
                properties);
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
                        || CatchTheRainbowAddon.controller(living) == null
                        || living.getItemBySlot(
                                EquipmentSlot.HEAD) != stack)) {
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

    public static void enforceEquipped(LivingEntity user) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        ItemStack head = user.getItemBySlot(EquipmentSlot.HEAD);
        if (head.is(CatchTheRainbowAddon.MASK_ITEM.get())) {
            return;
        }
        if (!head.isEmpty()) {
            user.spawnAtLocation(head.copy());
        }
        user.setItemSlot(
                EquipmentSlot.HEAD,
                new ItemStack(CatchTheRainbowAddon.MASK_ITEM.get()));
    }

    public static void purgeAll(LivingEntity holder) {
        if (holder == null) {
            return;
        }
        if (holder instanceof Player player) {
            for (int slot = 0;
                    slot < player.getInventory().getContainerSize();
                    slot++) {
                ItemStack stack =
                        player.getInventory().getItem(slot);
                if (stack.is(CatchTheRainbowAddon.MASK_ITEM.get())) {
                    player.getInventory().setItem(
                            slot, ItemStack.EMPTY);
                }
            }
            purgeMenu(player);
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (holder.getItemBySlot(slot).is(
                    CatchTheRainbowAddon.MASK_ITEM.get())) {
                holder.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    public static boolean containsAny(LivingEntity holder) {
        if (holder == null) {
            return false;
        }
        if (holder instanceof Player player) {
            for (int slot = 0;
                    slot < player.getInventory().getContainerSize();
                    slot++) {
                if (player.getInventory().getItem(slot).is(
                        CatchTheRainbowAddon.MASK_ITEM.get())) {
                    return true;
                }
            }
            if (player.containerMenu != null) {
                for (Slot slot : player.containerMenu.slots) {
                    if (slot.getItem().is(
                            CatchTheRainbowAddon.MASK_ITEM.get())) {
                        return true;
                    }
                }
                if (player.containerMenu.getCarried().is(
                        CatchTheRainbowAddon.MASK_ITEM.get())) {
                    return true;
                }
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (holder.getItemBySlot(slot).is(
                    CatchTheRainbowAddon.MASK_ITEM.get())) {
                return true;
            }
        }
        return false;
    }

    private static void purgeMenu(Player player) {
        if (player.containerMenu == null) {
            return;
        }
        for (Slot slot : player.containerMenu.slots) {
            if (slot.getItem().is(
                    CatchTheRainbowAddon.MASK_ITEM.get())) {
                slot.set(ItemStack.EMPTY);
            }
        }
        if (player.containerMenu.getCarried().is(
                CatchTheRainbowAddon.MASK_ITEM.get())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }
}
