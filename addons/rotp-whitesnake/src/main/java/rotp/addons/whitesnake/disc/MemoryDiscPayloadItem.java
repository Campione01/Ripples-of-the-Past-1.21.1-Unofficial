package rotp.addons.whitesnake.disc;

import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Transport boundary implemented by the independent memory-disc item slice.
 */
public interface MemoryDiscPayloadItem {
    Optional<CompoundTag> copyMemoryPayload(ItemStack stack);

    boolean restoreMemory(
            ItemStack stack, LivingEntity target, CompoundTag payload);
}
