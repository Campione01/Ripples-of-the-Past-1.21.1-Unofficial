package com.github.standobyte.jojo.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDirectionalGravityMixin {
	@Unique
	private Vec3 jojo_ripples$jumpStartMovement;
	@Unique
	private Direction jojo_ripples$jumpDirection = Direction.DOWN;

	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$directionalGravityTravel(
			Vec3 travelVector, CallbackInfo ci) {
		LivingEntity living = (LivingEntity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(living);
		if (direction == Direction.DOWN) {
			return;
		}

		if (living.isControlledByLocalInstance()) {
			double gravity = living.getGravity();
			boolean falling =
					DirectionalGravityTransforms.isMovingWithGravity(
							direction, living.getDeltaMovement());
			if (falling && living.hasEffect(MobEffects.SLOW_FALLING)) {
				gravity = Math.min(gravity, 0.01);
			}
			if (living instanceof Player player) {
				gravity = TimeStopState.travelGravityForTimeStopFloat(
						player, gravity);
			}

			BlockPos floor =
					living.getBlockPosBelowThatAffectsMyMovement();
			float blockFriction = living.level().getBlockState(floor)
					.getFriction(living.level(), floor, living);
			float transverseFriction =
					living.onGround()
							? blockFriction * 0.91F
							: 0.91F;
			Vec3 movement =
					living.handleRelativeFrictionAndCalculateMovement(
							travelVector, blockFriction);
			double gravityFriction =
					living instanceof FlyingAnimal
							? transverseFriction
							: 0.98F;
			if (living.shouldDiscardFriction()) {
				transverseFriction = 1.0F;
				gravityFriction = 1.0;
			}
			living.setDeltaMovement(
					DirectionalGravityTransforms
							.applyGravityAndFriction(
									direction,
									movement,
									gravity,
									transverseFriction,
									gravityFriction));
		}

		living.calculateEntityAnimation(
				living instanceof FlyingAnimal);
		ci.cancel();
	}

	@Redirect(method = "handleRelativeFrictionAndCalculateMovement",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;"
							+ "moveRelative"
							+ "(FLnet/minecraft/world/phys/Vec3;)V"))
	private void jojo_ripples$directionalGravityMovementInput(
			LivingEntity living, float speed, Vec3 input) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(living);
		if (direction == Direction.DOWN) {
			living.moveRelative(speed, input);
			return;
		}

		Vec3 before = living.getDeltaMovement();
		living.moveRelative(speed, input);
		Vec3 vanillaAcceleration =
				living.getDeltaMovement().subtract(before);
		living.setDeltaMovement(before.add(
				DirectionalGravityTransforms.toWorld(
						direction, vanillaAcceleration)));
	}

	@Inject(method = "jumpFromGround", at = @At("HEAD"))
	private void jojo_ripples$captureDirectionalGravityJump(
			CallbackInfo ci) {
		LivingEntity living = (LivingEntity) (Object) this;
		jojo_ripples$jumpDirection =
				DirectionalGravityApi.getEffectiveDirection(living);
		jojo_ripples$jumpStartMovement =
				jojo_ripples$jumpDirection != Direction.DOWN
						? living.getDeltaMovement() : null;
	}

	@Inject(method = "jumpFromGround", at = @At("RETURN"))
	private void jojo_ripples$applyDirectionalGravityJump(
			CallbackInfo ci) {
		if (jojo_ripples$jumpStartMovement == null) {
			return;
		}
		LivingEntity living = (LivingEntity) (Object) this;
		Vec3 vanillaJump = living.getDeltaMovement()
				.subtract(jojo_ripples$jumpStartMovement);
		living.setDeltaMovement(jojo_ripples$jumpStartMovement.add(
				DirectionalGravityTransforms.toWorld(
						jojo_ripples$jumpDirection, vanillaJump)));
		jojo_ripples$jumpStartMovement = null;
		jojo_ripples$jumpDirection = Direction.DOWN;
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
