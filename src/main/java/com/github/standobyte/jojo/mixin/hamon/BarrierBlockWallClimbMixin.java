package com.github.standobyte.jojo.mixin.hamon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojoimpl.powers.hamon.HamonWallClimbingHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@Mixin(BlockBehaviour.class)
public abstract class BarrierBlockWallClimbMixin {
	@Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
	private void jojo$wallClimbingIgnoreBarriers(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context, CallbackInfoReturnable<VoxelShape> ci) {
		if (state.is(Blocks.BARRIER) && HamonWallClimbingHelper.disableBlockCollisionShape(context)) {
			ci.setReturnValue(Shapes.empty());
		}
	}
}
