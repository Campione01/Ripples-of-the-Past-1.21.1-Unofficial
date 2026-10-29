package rotp.addons.stickyfingers.storage;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record BlockZipperStorageKey(
        ResourceLocation dimension,
        BlockPos position) {
    private static final String DIMENSION_TAG = "Dimension";
    private static final String POSITION_TAG = "Position";

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIMENSION_TAG, dimension.toString());
        tag.putLong(POSITION_TAG, position.asLong());
        return tag;
    }

    @Nullable
    public static BlockZipperStorageKey fromNbt(
            CompoundTag tag) {
        ResourceLocation dimension =
                ResourceLocation.tryParse(
                        tag.getString(DIMENSION_TAG));
        return dimension != null
                ? new BlockZipperStorageKey(
                        dimension,
                        BlockPos.of(tag.getLong(POSITION_TAG)))
                : null;
    }
}
