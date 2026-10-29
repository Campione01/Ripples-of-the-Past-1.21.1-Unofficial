package rotp.addons.diegosworld.block;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BarbedWireBlockEntity extends BlockEntity {
    private int absorbedLife;

    public BarbedWireBlockEntity(
            BlockPos pos, BlockState state) {
        super(
                DiegosTheWorldAddon
                        .BARBED_WIRE_BLOCK_ENTITY.get(),
                pos,
                state);
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("AbsorbedLife", absorbedLife);
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        absorbedLife = tag.getInt("AbsorbedLife");
    }

    public void incrementAbsorbedLife() {
        absorbedLife++;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(
                    worldPosition, state, state, 2);
        }
    }

    public int getAbsorbedLife() {
        return absorbedLife;
    }
}
