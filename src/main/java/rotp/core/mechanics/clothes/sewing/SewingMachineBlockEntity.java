package rotp.core.mechanics.clothes.sewing;

import rotp.core.init.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SewingMachineBlockEntity extends BlockEntity {

	public SewingMachineBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.SEWING_MACHINE.get(), pos, blockState);
	}

}
