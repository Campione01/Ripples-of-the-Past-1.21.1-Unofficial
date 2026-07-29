package com.github.standobyte.jojo.mixin.block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.api.block.BlockSignalQuery;
import com.github.standobyte.jojo.api.block.BlockSignalSuppressors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;

@Mixin(WeightedPressurePlateBlock.class)
public abstract class WeightedPressurePlateBlockSignalMixin {
	@Inject(
			method = "getSignalStrength",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo$applySignalSuppressors(
			Level level,
			BlockPos position,
			CallbackInfoReturnable<Integer> callback) {
		if (BlockSignalSuppressors.shouldSuppress(
				level,
				position,
				level.getBlockState(position),
				BlockSignalQuery.Kind.PRESSURE_PLATE_OUTPUT)) {
			callback.setReturnValue(0);
		}
	}
}
