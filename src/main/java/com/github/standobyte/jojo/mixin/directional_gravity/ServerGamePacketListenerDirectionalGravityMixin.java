package com.github.standobyte.jojo.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;

import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerDirectionalGravityMixin {
	@Shadow
	public ServerPlayer player;

	@Unique
	private double jojo_ripples$packetResidualLocalVertical;

	@Inject(method = "handleMovePlayer", at = @At("HEAD"))
	private void jojo_ripples$beginDirectionalGravityPacket(
			ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		jojo_ripples$packetResidualLocalVertical = 0.0;
	}

	@Redirect(method = "handleMovePlayer",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/"
							+ "ServerPlayer;jumpFromGround()V"))
	private void jojo_ripples$deferDirectionalGravityPacketJump(
			ServerPlayer player) {
		if (DirectionalGravityApi.getEffectiveDirection(player)
				== Direction.DOWN) {
			player.jumpFromGround();
		}
	}

	@Redirect(method = "handleMovePlayer",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/"
							+ "ServerPlayer;move"
							+ "(Lnet/minecraft/world/entity/MoverType;"
							+ "Lnet/minecraft/world/phys/Vec3;)V"))
	private void jojo_ripples$directionalGravityPacketMove(
			ServerPlayer player, MoverType mover, Vec3 worldMovement,
			ServerboundMovePlayerPacket packet) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(player);
		if (direction == Direction.DOWN) {
			player.move(mover, worldMovement);
			return;
		}

		Vec3 localMovement =
				DirectionalGravityTransforms.toLocal(
						direction, worldMovement);
		if (player.onGround() && !packet.isOnGround()
				&& localMovement.y > 0.0) {
			player.jumpFromGround();
		}
		Vec3 start = player.position();
		player.move(mover, worldMovement);
		Vec3 actual = player.position().subtract(start);
		double localResidual =
				DirectionalGravityTransforms.toLocal(
						direction,
						worldMovement.subtract(actual)).y;
		jojo_ripples$packetResidualLocalVertical =
				localResidual > -0.5 && localResidual < 0.5
						? 0.0 : localResidual;
		if (localMovement.y > 0.0) {
			player.resetFallDistance();
		}
	}

	@ModifyConstant(method = "handleMovePlayer",
			constant = @Constant(doubleValue = -0.03125))
	private double jojo_ripples$directionalGravityFloatingThreshold(
			double vanillaThreshold) {
		return DirectionalGravityApi.getEffectiveDirection(player)
				== Direction.DOWN
				? vanillaThreshold : Double.NEGATIVE_INFINITY;
	}

	@Redirect(method = "handleMovePlayer",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/network/"
							+ "ServerGamePacketListenerImpl;"
							+ "noBlocksAround"
							+ "(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean jojo_ripples$directionalGravityFloatingProbe(
			ServerGamePacketListenerImpl listener, Entity entity) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		Vec3 probe = direction == Direction.DOWN
				? new Vec3(0.0, -0.55, 0.0)
				: Vec3.atLowerCornerOf(
						direction.getNormal()).scale(0.55);
		AABB box = entity.getBoundingBox()
				.inflate(0.0625).expandTowards(probe);
		boolean noBlocks = entity.level().getBlockStates(box)
				.allMatch(state -> state.isAir());
		return noBlocks && (direction == Direction.DOWN
				|| jojo_ripples$packetResidualLocalVertical
						>= -0.03125);
	}

	@Redirect(method = "handleMovePlayer",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/"
							+ "ServerPlayer;resetFallDistance()V"))
	private void jojo_ripples$directionalGravityPacketFallReset(
			ServerPlayer player) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(player);
		if (direction == Direction.DOWN) {
			player.resetFallDistance();
		}
	}
}
