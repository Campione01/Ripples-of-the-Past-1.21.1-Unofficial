package com.github.standobyte.jojo.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityRuntime;
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityRuntime.LocalFrame;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Mixin(Player.class)
public abstract class PlayerDirectionalGravityMixin {
	@Shadow
	protected abstract boolean isStayingOnGroundSurface();

	@WrapMethod(method = "travel")
	private void jojo_ripples$directionalGravityPlayerTravelFrame(
			Vec3 travelVector, Operation<Void> original) {
		Player player = (Player) (Object) this;
		try (LocalFrame ignored =
				DirectionalGravityRuntime.enterLocalFrame(player)) {
			original.call(travelVector);
		}
	}

	@Redirect(method = "travel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/core/BlockPos;"
							+ "containing(DDD)"
							+ "Lnet/minecraft/core/BlockPos;"))
	private BlockPos jojo_ripples$directionalSwimmingHeadProbe(
			double x, double y, double z) {
		Player player = (Player) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(player);
		if (direction == Direction.DOWN) {
			return BlockPos.containing(x, y, z);
		}
		double localUpOffset = y - player.getY();
		Vec3 world = player.position().add(
				DirectionalGravityTransforms.toWorld(
						direction,
						new Vec3(0.0, localUpOffset, 0.0)));
		return BlockPos.containing(world);
	}

	@Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$directionalGravityEdgeBackoff(
			Vec3 requested, MoverType mover,
			CallbackInfoReturnable<Vec3> cir) {
		Player player = (Player) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(player);
		if (direction == Direction.DOWN) {
			return;
		}
		Vec3 local = DirectionalGravityTransforms.toLocal(
				direction, requested);
		float maxStep = player.maxUpStep();
		if (player.getAbilities().flying || local.y > 0.0
				|| (mover != MoverType.SELF
						&& mover != MoverType.PLAYER)
				|| !isStayingOnGroundSurface()
				|| !jojo_ripples$isAboveGround(
						player, direction, maxStep)) {
			cir.setReturnValue(requested);
			return;
		}

		double x = local.x;
		double z = local.z;
		double xStep = Math.signum(x) * 0.05;
		double zStep = Math.signum(z) * 0.05;
		while (x != 0.0 && jojo_ripples$canFallAtLeast(
				player, direction, x, 0.0, maxStep)) {
			if (Math.abs(x) <= 0.05) {
				x = 0.0;
				break;
			}
			x -= xStep;
		}
		while (z != 0.0 && jojo_ripples$canFallAtLeast(
				player, direction, 0.0, z, maxStep)) {
			if (Math.abs(z) <= 0.05) {
				z = 0.0;
				break;
			}
			z -= zStep;
		}
		while (x != 0.0 && z != 0.0
				&& jojo_ripples$canFallAtLeast(
						player, direction, x, z, maxStep)) {
			x = Math.abs(x) <= 0.05 ? 0.0 : x - xStep;
			z = Math.abs(z) <= 0.05 ? 0.0 : z - zStep;
		}
		cir.setReturnValue(DirectionalGravityTransforms.toWorld(
				direction, new Vec3(x, local.y, z)));
	}

	@Unique
	private static boolean jojo_ripples$isAboveGround(
			Player player, Direction direction, float maxStep) {
		return player.onGround()
				|| player.fallDistance < maxStep
				&& !jojo_ripples$canFallAtLeast(
						player, direction, 0.0, 0.0,
						maxStep - player.fallDistance);
	}

	@Unique
	private static boolean jojo_ripples$canFallAtLeast(
			Player player, Direction direction,
			double localX, double localZ, float distance) {
		Vec3 transverse = DirectionalGravityTransforms.toWorld(
				direction, new Vec3(localX, 0.0, localZ));
		AABB moved = player.getBoundingBox().move(transverse);
		AABB supportProbe =
				DirectionalGravityTransforms.supportingBox(
						direction, moved,
						(double) distance + 1.0E-5F);
		return player.level().noCollision(player, supportProbe);
	}
}
