package rotp.addons.memory_discs.item;

import java.util.Optional;

import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.addons.memory_discs.RotpMemoryDiscsAddon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class PowerDiscData {
    public static final String LEGACY_POWER_TAG = "NonStandPower";

    private PowerDiscData() {}

    public static boolean hasStoredPowerId(ItemStack stack) {
        return payload(stack).contains(
                LEGACY_POWER_TAG, Tag.TAG_STRING);
    }

    public static Optional<ResourceLocation> getPowerId(
            ItemStack stack) {
        CompoundTag tag = payload(stack);
        if (!tag.contains(LEGACY_POWER_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(
                tag.getString(LEGACY_POWER_TAG)));
    }

    public static Optional<PlayerPowerType<?>> getPowerType(
            ItemStack stack) {
        return getPowerId(stack)
                .map(PlayerPowerType::fromId);
    }

    public static ItemStack withPower(
            ItemStack stack, PlayerPowerType<?> powerType) {
        if (stack == null
                || stack.isEmpty()
                || powerType == null
                || !(stack.getItem() instanceof MemoryDiscItem)) {
            return ItemStack.EMPTY;
        }
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putString(
                        LEGACY_POWER_TAG,
                        powerType.getId().toString()));
        return stack;
    }

    public static ItemStack newFilledDisc(
            PlayerPowerType<?> powerType) {
        return withPower(
                new ItemStack(
                        RotpMemoryDiscsAddon.MEMORY_DISC.get()),
                powerType);
    }

    private static CompoundTag payload(ItemStack stack) {
        if (stack == null
                || stack.isEmpty()
                || !(stack.getItem() instanceof MemoryDiscItem)) {
            return new CompoundTag();
        }
        return stack.getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY)
                .copyTag();
    }
}
