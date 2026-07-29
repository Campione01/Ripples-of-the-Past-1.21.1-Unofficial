package com.github.standobyte.jojo.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDirectionalGravityMixin {
	@ModifyArg(method = "doCheckFallDamage",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/"
							+ "Player;checkFallDamage"
							+ "(DZLnet/minecraft/world/level/block/"
							+ "state/BlockState;"
							+ "Lnet/minecraft/core/BlockPos;)V"),
			index = 0)
	private double jojo_ripples$directionalGravityServerFall(
			double vanillaY, double movementX, double movementY,
			double movementZ, boolean onGround) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(player);
		return direction == Direction.DOWN ? vanillaY
				: DirectionalGravityTransforms.toLocal(
						direction,
						new Vec3(movementX, movementY,
								movementZ)).y;
	}
}
