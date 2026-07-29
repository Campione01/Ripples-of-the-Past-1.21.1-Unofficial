package com.github.standobyte.jojo.block;

import com.github.standobyte.jojo.init.ModBlockEntities;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModSoundEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StoneMaskBlockEntity extends BlockEntity {
	private ItemStack maskStack;
	private int activationTicks;

	public StoneMaskBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.STONE_MASK.get(), pos, state);
		ItemStack stateStack =
				new ItemStack(state.getBlock().asItem());
		maskStack = !stateStack.isEmpty()
				? stateStack
				: new ItemStack(ModItems.STONE_MASK.get());
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("Item", maskStack.save(registries));
		tag.putInt("ActivationTicks", activationTicks);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains("Item", Tag.TAG_COMPOUND)) {
			maskStack = ItemStack.parseOptional(registries, tag.getCompound("Item"));
		}
		activationTicks = tag.getInt("ActivationTicks");
	}

	public static void tick(Level level, BlockPos pos, BlockState state, StoneMaskBlockEntity stoneMask) {
		if (level.isClientSide() || stoneMask.activationTicks <= 0) {
			return;
		}

		stoneMask.activationTicks--;
		stoneMask.setChanged();
		if (stoneMask.activationTicks == 0) {
			level.playSound(null, pos, ModSoundEvents.STONE_MASK_DEACTIVATION.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
			level.setBlockAndUpdate(pos, state.setValue(StoneMaskBlock.BLOOD_ACTIVATION, false));
		}
	}

	public void activate() {
		activate(this);
	}

	private static void activate(StoneMaskBlockEntity stoneMask) {
		stoneMask.activationTicks = 100;
		stoneMask.setChanged();
		if (stoneMask.level != null) {
			BlockState state = stoneMask.getBlockState();
			stoneMask.level.setBlockAndUpdate(stoneMask.worldPosition,
					state.setValue(StoneMaskBlock.BLOOD_ACTIVATION, true));
		}
	}

	public boolean isActivated() {
		return activationTicks > 0;
	}

	public void setStack(ItemStack stack) {
		maskStack = stack.copy();
		setChanged();
	}

	public ItemStack getStack() {
		return maskStack.copy();
	}
}
