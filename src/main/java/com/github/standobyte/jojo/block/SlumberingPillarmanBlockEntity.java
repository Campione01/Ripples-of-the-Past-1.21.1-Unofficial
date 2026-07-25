package com.github.standobyte.jojo.block;

import com.github.standobyte.jojo.init.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SlumberingPillarmanBlockEntity extends BlockEntity {
	private int absorbedLife;

	public SlumberingPillarmanBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SLUMBERING_PILLARMAN.get(), pos, state);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("AbsorbedLife", absorbedLife);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		absorbedLife = tag.getInt("AbsorbedLife");
	}

	public void incAbsorbed() {
		absorbedLife++;
		setChanged();
		if (level != null) {
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, 2);
		}
	}

	public int getAbsorbedLife() {
		return absorbedLife;
	}
}
