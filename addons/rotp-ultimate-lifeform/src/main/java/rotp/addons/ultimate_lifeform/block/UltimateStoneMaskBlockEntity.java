package rotp.addons.ultimate_lifeform.block;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.core.init.ModSoundEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class UltimateStoneMaskBlockEntity extends BlockEntity {
    private ItemStack maskStack = ItemStack.EMPTY;
    private int activationTicks;

    public UltimateStoneMaskBlockEntity(
            BlockPos pos, BlockState state) {
        super(
                UltimateLifeformAddon
                        .UL_STONE_MASK_BLOCK_ENTITY.get(),
                pos,
                state);
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!maskStack.isEmpty()) {
            tag.put("Item", maskStack.save(registries));
        }
        tag.putInt("ActivationTicks", activationTicks);
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        maskStack = tag.contains("Item", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(
                        registries, tag.getCompound("Item"))
                : ItemStack.EMPTY;
        activationTicks = Math.max(
                tag.getInt("ActivationTicks"), 0);
    }

    public static void tick(
            Level level,
            BlockPos pos,
            BlockState state,
            UltimateStoneMaskBlockEntity mask) {
        if (level.isClientSide() || mask.activationTicks <= 0) {
            return;
        }
        mask.activationTicks--;
        mask.setChanged();
        if (mask.activationTicks == 0
                && state.hasProperty(
                        UltimateStoneMaskBlock.BLOOD_ACTIVATION)) {
            level.setBlockAndUpdate(
                    pos,
                    state.setValue(
                            UltimateStoneMaskBlock.BLOOD_ACTIVATION,
                            false));
        }
    }

    public void activate() {
        activationTicks = 100;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(
                    UltimateStoneMaskBlock.BLOOD_ACTIVATION)) {
                level.setBlockAndUpdate(
                        worldPosition,
                        state.setValue(
                                UltimateStoneMaskBlock
                                        .BLOOD_ACTIVATION,
                                true));
            }
            level.playSound(
                    null,
                    worldPosition,
                    ModSoundEvents
                            .STONE_MASK_ACTIVATION.get(),
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F);
        }
    }

    public boolean isActivated() {
        return activationTicks > 0;
    }

    public void setStack(ItemStack stack) {
        maskStack = stack.copyWithCount(1);
        setChanged();
    }

    public ItemStack getStack() {
        if (!maskStack.isEmpty()) {
            return maskStack.copy();
        }
        boolean withStone =
                getBlockState().getBlock()
                        == UltimateLifeformAddon
                                .UL_STONE_MASK_WITHSTONE.get();
        return new ItemStack(
                withStone
                        ? UltimateLifeformAddon
                                .STONE_MASK_UL_WITH_STONE.get()
                        : UltimateLifeformAddon
                                .STONE_MASK_UL_NOSTONE.get());
    }
}
