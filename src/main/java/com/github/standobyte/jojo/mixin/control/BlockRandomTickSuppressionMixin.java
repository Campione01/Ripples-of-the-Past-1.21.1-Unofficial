package com.github.standobyte.jojo.mixin.control;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.block.BlockRandomTickSuppressionProviders;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockRandomTickSuppressionMixin {
	@Inject(
			method = "randomTick",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$suppressAddonRandomTick(
			ServerLevel level,
			BlockPos position,
			RandomSource random,
			CallbackInfo ci) {
		if (BlockRandomTickSuppressionProviders.shouldSuppress(
				level,
				position,
				(BlockState) (Object) this)) {
			ci.cancel();
		}
	}
}
