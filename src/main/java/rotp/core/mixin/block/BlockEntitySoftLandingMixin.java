package rotp.core.mixin.block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.api.block.EntitySoftLandingDecision;
import rotp.core.api.block.EntitySoftLandingProviders;
import rotp.core.api.block.EntitySoftLandingQuery;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(Block.class)
public abstract class BlockEntitySoftLandingMixin {
	@Inject(
			method = "fallOn",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo$applySoftLandingFallDamage(
			Level level,
			BlockState state,
			BlockPos position,
			Entity entity,
			float fallDistance,
			CallbackInfo callback) {
		if (!(level instanceof ServerLevel serverLevel)
				|| entity.level() != serverLevel
				|| entity.isSuppressingBounce()) {
			return;
		}
		EntitySoftLandingDecision decision =
				EntitySoftLandingProviders.resolve(
						new EntitySoftLandingQuery(
								serverLevel,
								position,
								state,
								entity));
		if (decision.isHandled()) {
			entity.causeFallDamage(
					fallDistance,
					decision.fallDamageMultiplier(),
					entity.damageSources().fall());
			callback.cancel();
		}
	}
}
