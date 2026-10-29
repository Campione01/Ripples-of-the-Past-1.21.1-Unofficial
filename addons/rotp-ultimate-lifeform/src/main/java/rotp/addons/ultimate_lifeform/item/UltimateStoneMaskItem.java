package rotp.addons.ultimate_lifeform.item;

import rotp.core.init.ModItems;
import rotp.core.item.StoneMaskItem;
import rotp.addons.ultimate_lifeform.block.UltimateStoneMaskBlock;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class UltimateStoneMaskItem extends StoneMaskItem {
    private static final String AJA_CONVERSION_TICKS =
            "UltimateAjaConversionTicks";
    private final boolean withStone;

    public UltimateStoneMaskItem(
            Properties properties,
            UltimateStoneMaskBlock block,
            boolean withStone) {
        super(properties, block);
        this.withStone = withStone;
    }

    @Override
    public MaskActivationResult tryBloodActivation(
            LivingEntity wearer,
            ItemStack stack) {
        // The Aja variant converts through sunlight, not ordinary vampirism.
        return withStone
                ? MaskActivationResult.REJECTED
                : MaskActivationResult.PASS;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int inventorySlot,
            boolean isSelected) {
        super.inventoryTick(
                stack, level, entity, inventorySlot, isSelected);
        if (!withStone
                || level.isClientSide()
                || !(entity instanceof LivingEntity living)
                || living.getItemBySlot(EquipmentSlot.HEAD) != stack) {
            return;
        }

        CustomData data = stack.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        net.minecraft.nbt.CompoundTag tag = data.copyTag();
        if (!tag.contains(AJA_CONVERSION_TICKS)) {
            return;
        }
        int ticks = tag.getInt(AJA_CONVERSION_TICKS);
        ticks--;
        int nextTicks = ticks;
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                mutableTag -> {
                    if (nextTicks > 0) {
                        mutableTag.putInt(
                                AJA_CONVERSION_TICKS, nextTicks);
                    }
                    else {
                        mutableTag.remove(AJA_CONVERSION_TICKS);
                    }
                });
        if (nextTicks == 0) {
            living.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            ItemStack ajaStone = new ItemStack(
                    ModItems.SUPER_AJA_STONE.get());
            if (!(living instanceof net.minecraft.world.entity.player.Player
                            player)
                    || !player.addItem(ajaStone)) {
                living.spawnAtLocation(
                        new ItemStack(
                                ModItems.SUPER_AJA_STONE.get()));
            }
        }
    }

    public static void beginAjaConversion(ItemStack stack) {
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putInt(AJA_CONVERSION_TICKS, 101));
    }

    public static boolean isConverting(ItemStack stack) {
        return stack.getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY)
                .copyTag()
                .contains(AJA_CONVERSION_TICKS);
    }
}
