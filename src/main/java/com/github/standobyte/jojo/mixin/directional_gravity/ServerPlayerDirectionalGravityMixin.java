package com.github.standobyte.jojo.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDirectionalGravityMixin {
	@Unique
	private double jojo_ripples$directionalGravityServerFallY;

	@Inject(method = "doCheckFallDamage", at = @At("HEAD"))
	private void jojo_ripples$captureDirectionalGravityServerFall(
			double movementX, double movementY, double movementZ,
			boolean onGround, CallbackInfo callbackInfo) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(player);
		jojo_ripples$directionalGravityServerFallY =
				direction == Direction.DOWN ? movementY
						: DirectionalGravityTransforms.toLocal(
								direction,
								new Vec3(movementX, movementY,
										movementZ)).y;
	}

	@ModifyArg(method = "doCheckFallDamage",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/"
							+ "Player;checkFallDamage"
							+ "(DZLnet/minecraft/world/level/block/"
							+ "state/BlockState;"
							+ "Lnet/minecraft/core/BlockPos;)V"),
			index = 0)
	private double jojo_ripples$directionalGravityServerFall(
			double vanillaY) {
		return jojo_ripples$directionalGravityServerFallY;
	}
}
