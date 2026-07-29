package com.github.standobyte.jojo.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityRuntime;
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityRuntime.LocalFrame;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDirectionalGravityMixin {
	@WrapMethod(method = "travel")
	private void jojo_ripples$directionalGravityTravelFrame(
			Vec3 travelVector, Operation<Void> original) {
		LivingEntity living = (LivingEntity) (Object) this;
		try (LocalFrame ignored =
				DirectionalGravityRuntime.enterLocalFrame(living)) {
			original.call(travelVector);
		}
	}

	@WrapOperation(method = "travel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/"
							+ "LivingEntity;isFree(DDD)Z"))
	private boolean jojo_ripples$directionalFluidExitProbe(
			LivingEntity living, double x, double y, double z,
			Operation<Boolean> original) {
		Direction direction =
				DirectionalGravityRuntime.localFrameDirection(living);
		if (direction == Direction.DOWN) {
			return original.call(living, x, y, z);
		}
		Vec3 localMovement = living.getDeltaMovement();
		Vec3 lastMove =
				DirectionalGravityRuntime.lastLocalMovement(living);
		Vec3 localProbe = new Vec3(
				localMovement.x,
				localMovement.y + 0.6 - lastMove.y,
				localMovement.z);
		Vec3 worldProbe = DirectionalGravityTransforms.toWorld(
				direction, localProbe);
		return original.call(living,
				worldProbe.x, worldProbe.y, worldProbe.z);
	}

	@WrapMethod(method = "jumpFromGround")
	private void jojo_ripples$directionalGravityJump(
			Operation<Void> original) {
		LivingEntity living = (LivingEntity) (Object) this;
		try (LocalFrame ignored =
				DirectionalGravityRuntime.enterLocalFrame(living)) {
			original.call();
		}
	}

	@WrapOperation(method = "aiStep",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/"
							+ "LivingEntity;jumpInFluid"
							+ "(Lnet/neoforged/neoforge/fluids/"
							+ "FluidType;)V"))
	private void jojo_ripples$directionalGravityFluidJump(
			LivingEntity living, FluidType fluid,
			Operation<Void> original) {
		try (LocalFrame ignored =
				DirectionalGravityRuntime.enterLocalFrame(living)) {
			original.call(living, fluid);
		}
	}

	@Inject(method = "getLocalBoundsForPose", at = @At("RETURN"),
			cancellable = true)
	private void jojo_ripples$directionalGravityPoseBounds(
			Pose pose, CallbackInfoReturnable<AABB> cir) {
		LivingEntity living = (LivingEntity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(living);
		if (direction != Direction.DOWN) {
			cir.setReturnValue(DirectionalGravityTransforms.rotateBox(
					direction, cir.getReturnValue(), Vec3.ZERO));
		}
	}

	@Inject(method = "wouldNotSuffocateAtTargetPose",
			at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$directionalGravityPoseCollision(
			Pose pose, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity living = (LivingEntity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(living);
		if (direction == Direction.DOWN) {
			return;
		}
		AABB vanillaBox =
				living.getDimensions(pose).makeBoundingBox(
						living.position());
		AABB rotatedBox = DirectionalGravityTransforms.rotateBox(
				direction, vanillaBox, living.position());
		cir.setReturnValue(
				living.level().noBlockCollision(living, rotatedBox));
	}
}
