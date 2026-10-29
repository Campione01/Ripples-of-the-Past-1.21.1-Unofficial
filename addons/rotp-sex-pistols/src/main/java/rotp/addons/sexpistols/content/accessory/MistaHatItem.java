package rotp.addons.sexpistols.content.accessory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.addons.sexpistols.registry.SexPistolsArmorMaterials;
import rotp.addons.sexpistols.registry.SexPistolsDataComponents;
import rotp.addons.sexpistols.registry.SexPistolsItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class MistaHatItem extends ArmorItem {
    public static final int MAX_STORED_AMMO = 256;
    private static final int AMMO_BAR_COLOR = 0x7088FF;

    public MistaHatItem(Properties properties) {
        super(
                SexPistolsArmorMaterials.MISTA_HAT,
                ArmorItem.Type.HELMET,
                properties);
    }

    public static boolean isMistaHat(ItemStack stack) {
        return stack != null
                && stack.is(SexPistolsItems.MISTA_SUIT_HAT.get());
    }

    public static boolean isAmmoItem(ItemStack stack) {
        return stack != null
                && stack.is(SexPistolsItems.REVOLVER_AMMO.get());
    }

    public static int storedAmmo(ItemStack stack) {
        if (!isMistaHat(stack)) {
            return 0;
        }
        return Mth.clamp(
                stack.getOrDefault(
                        SexPistolsDataComponents.MISTA_HAT_AMMO.get(),
                        0),
                0,
                MAX_STORED_AMMO);
    }

    public static int remainingCapacity(ItemStack stack) {
        return Math.max(0, MAX_STORED_AMMO - storedAmmo(stack));
    }

    public static int insertAmmo(
            ItemStack hatStack, ItemStack ammoStack) {
        if (!isMistaHat(hatStack) || !isAmmoItem(ammoStack)) {
            return 0;
        }
        int moved = Math.min(
                ammoStack.getCount(),
                remainingCapacity(hatStack));
        if (moved <= 0) {
            return 0;
        }
        setStoredAmmo(hatStack, storedAmmo(hatStack) + moved);
        ammoStack.shrink(moved);
        return moved;
    }

    public static boolean consumeOneAmmo(ItemStack hatStack) {
        int stored = storedAmmo(hatStack);
        if (stored <= 0) {
            return false;
        }
        setStoredAmmo(hatStack, stored - 1);
        return true;
    }

    public static ItemStack removeAmmoStack(ItemStack hatStack) {
        int stored = storedAmmo(hatStack);
        if (stored <= 0) {
            return ItemStack.EMPTY;
        }
        int removed = Math.min(
                stored,
                SexPistolsItems.REVOLVER_AMMO.get()
                        .getDefaultMaxStackSize());
        setStoredAmmo(hatStack, stored - removed);
        return new ItemStack(
                SexPistolsItems.REVOLVER_AMMO.get(),
                removed);
    }

    public static List<ItemStack> storedAmmoStacks(
            ItemStack hatStack) {
        int stored = storedAmmo(hatStack);
        if (stored <= 0) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>(4);
        int maxStack = SexPistolsItems.REVOLVER_AMMO.get()
                .getDefaultMaxStackSize();
        while (stored > 0) {
            int count = Math.min(stored, maxStack);
            stacks.add(new ItemStack(
                    SexPistolsItems.REVOLVER_AMMO.get(),
                    count));
            stored -= count;
        }
        return List.copyOf(stacks);
    }

    public static void dropStoredAmmo(
            ItemStack hatStack,
            Level level,
            double x,
            double y,
            double z) {
        if (level.isClientSide()) {
            return;
        }
        List<ItemStack> stored = storedAmmoStacks(hatStack);
        setStoredAmmo(hatStack, 0);
        for (ItemStack ammo : stored) {
            level.addFreshEntity(new ItemEntity(
                    level, x, y, z, ammo));
        }
    }

    private static void setStoredAmmo(
            ItemStack hatStack, int stored) {
        if (isMistaHat(hatStack)) {
            hatStack.set(
                    SexPistolsDataComponents.MISTA_HAT_AMMO.get(),
                    Mth.clamp(stored, 0, MAX_STORED_AMMO));
        }
    }

    @Override
    public <T extends LivingEntity> int damageItem(
            ItemStack stack,
            int amount,
            @Nullable T entity,
            Consumer<Item> onBroken) {
        if (entity != null
                && amount > 0
                && stack.isDamageableItem()
                && stack.getDamageValue() + amount
                        >= stack.getMaxDamage()) {
            dropStoredAmmo(
                    stack,
                    entity.level(),
                    entity.getX(),
                    entity.getY() + 0.5D,
                    entity.getZ());
        }
        return amount;
    }

    @Override
    public void onDestroyed(
            ItemEntity itemEntity,
            DamageSource damageSource) {
        ItemStack stack = itemEntity.getItem();
        dropStoredAmmo(
                stack,
                itemEntity.level(),
                itemEntity.getX(),
                itemEntity.getY(),
                itemEntity.getZ());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return storedAmmo(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(
                13.0F * storedAmmo(stack) / MAX_STORED_AMMO);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return AMMO_BAR_COLOR;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.mista_suit_hat.ammo",
                        storedAmmo(stack),
                        MAX_STORED_AMMO)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.mista_suit_hat.transfer")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.mista_suit_hat.extract")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.mista_suit_hat.capacity")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.mista_suit_hat.supply")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.mista_suit_hat.protection")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_sex_pistols.mista_suit_hat.reference_quote")
                .withStyle(
                        ChatFormatting.GRAY,
                        ChatFormatting.ITALIC));
    }
}
