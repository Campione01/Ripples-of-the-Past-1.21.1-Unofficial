package com.github.standobyte.jojo.mixin.directional_gravity;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Mixin(Entity.class)
public abstract class EntityDirectionalGravityMixin {
	@Shadow
	public boolean horizontalCollision;
	@Shadow
	public boolean verticalCollision;
	@Shadow
	public boolean verticalCollisionBelow;
	@Shadow
	public boolean minorHorizontalCollision;
	@Shadow
	public Optional<BlockPos> mainSupportingBlockPos;
	@Shadow
	private boolean onGroundNoBlocks;

	@Shadow
	protected abstract float getBlockSpeedFactor();

	@Unique
	private Direction jojo_ripples$moveGravity = Direction.DOWN;
	@Unique
	private Vec3 jojo_ripples$actualMovement = Vec3.ZERO;

	@Inject(method = "makeBoundingBox", at = @At("RETURN"),
			cancellable = true)
	private void jojo_ripples$directionalGravityBoundingBox(
			CallbackInfoReturnable<AABB> cir) {
		Entity entity = (Entity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction != Direction.DOWN) {
			cir.setReturnValue(DirectionalGravityTransforms.rotateBox(
					direction, cir.getReturnValue(), entity.position()));
		}
	}

	@Inject(method = "calculateViewVector", at = @At("RETURN"),
			cancellable = true)
	private void jojo_ripples$directionalGravityViewVector(
			float xRot, float yRot, CallbackInfoReturnable<Vec3> cir) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(
						(Entity) (Object) this);
		if (direction != Direction.DOWN) {
			cir.setReturnValue(DirectionalGravityTransforms.toWorld(
					direction, cir.getReturnValue()));
		}
	}

	@Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;",
			at = @At("RETURN"), cancellable = true)
	private void jojo_ripples$directionalGravityEyePosition(
			CallbackInfoReturnable<Vec3> cir) {
		Entity entity = (Entity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction != Direction.DOWN) {
			cir.setReturnValue(DirectionalGravityTransforms.eyePosition(
					direction, entity.position(), entity.getEyeHeight()));
		}
	}

	@Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
			at = @At("RETURN"), cancellable = true)
	private void jojo_ripples$directionalGravityInterpolatedEyePosition(
			float partialTicks, CallbackInfoReturnable<Vec3> cir) {
		Entity entity = (Entity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction != Direction.DOWN) {
			cir.setReturnValue(DirectionalGravityTransforms.eyePosition(
					direction, entity.getPosition(partialTicks),
					entity.getEyeHeight()));
		}
	}

	@Inject(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;",
			at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$directionalGravityOnPos(
			float offset,
			CallbackInfoReturnable<BlockPos> cir) {
		Entity entity = (Entity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction != Direction.DOWN) {
			cir.setReturnValue(mainSupportingBlockPos.orElseGet(
					() -> DirectionalGravityTransforms.floorBlockPos(
							direction, entity.position(), offset)));
		}
	}

	@Inject(method = "move", at = @At("HEAD"))
	private void jojo_ripples$startDirectionalGravityMove(
			MoverType type, Vec3 requested, CallbackInfo ci) {
		jojo_ripples$moveGravity =
				DirectionalGravityApi.getEffectiveDirection(
						(Entity) (Object) this);
		jojo_ripples$actualMovement = Vec3.ZERO;
	}

	@Redirect(method = "move",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;"
							+ "setOnGroundWithMovement"
							+ "(ZLnet/minecraft/world/phys/Vec3;)V"))
	private void jojo_ripples$directionalGravityCollisionState(
			Entity entity, boolean vanillaOnGround, Vec3 actualMovement,
			MoverType type, Vec3 requestedMovement) {
		jojo_ripples$actualMovement = actualMovement;
		if (jojo_ripples$moveGravity == Direction.DOWN) {
			entity.setOnGroundWithMovement(
					vanillaOnGround, actualMovement);
			return;
		}

		DirectionalGravityTransforms.RelativeCollision collision =
				DirectionalGravityTransforms.relativeCollision(
						jojo_ripples$moveGravity,
						requestedMovement, actualMovement);
		verticalCollision = collision.gravityAxisCollision();
		verticalCollisionBelow = collision.groundCollision();
		horizontalCollision = collision.transverseCollision();
		minorHorizontalCollision = false;
		if (collision.xCollision() || collision.yCollision()
				|| collision.zCollision()) {
			entity.setDeltaMovement(
					DirectionalGravityTransforms.stopCollidedVelocity(
							entity.getDeltaMovement(), collision));
		}
		entity.setOnGroundWithMovement(
				collision.groundCollision(), actualMovement);
	}

	@Inject(method = "checkSupportingBlock", at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$directionalGravitySupportingBlock(
			boolean onGround, Vec3 movement, CallbackInfo ci) {
		Entity entity = (Entity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction == Direction.DOWN) {
			return;
		}

		if (onGround) {
			AABB supportBox =
					DirectionalGravityTransforms.supportingBox(
							direction, entity.getBoundingBox(), 1.0E-6);
			Optional<BlockPos> support =
					entity.level().findSupportingBlock(entity, supportBox);
			if (support.isPresent() || onGroundNoBlocks) {
				mainSupportingBlockPos = support;
			}
			else if (movement != null) {
				Vec3 transverse =
						DirectionalGravityTransforms.transverseMovement(
								direction, movement);
				support = entity.level().findSupportingBlock(
						entity, supportBox.move(transverse.scale(-1.0)));
				mainSupportingBlockPos = support;
			}
			onGroundNoBlocks = support.isEmpty();
		}
		else {
			onGroundNoBlocks = false;
			mainSupportingBlockPos = Optional.empty();
		}
		ci.cancel();
	}

	@ModifyArg(method = "move",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;"
							+ "checkFallDamage"
							+ "(DZLnet/minecraft/world/level/block/state/"
							+ "BlockState;Lnet/minecraft/core/BlockPos;)V"),
			index = 0)
	private double jojo_ripples$directionalGravityFallMovement(
			double vanillaYMovement) {
		if (jojo_ripples$moveGravity == Direction.DOWN) {
			return vanillaYMovement;
		}
		return DirectionalGravityTransforms.toLocal(
				jojo_ripples$moveGravity,
				jojo_ripples$actualMovement).y;
	}

	@Redirect(method = "move",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;"
							+ "setDeltaMovement"
							+ "(Lnet/minecraft/world/phys/Vec3;)V",
					ordinal = 1))
	private void jojo_ripples$directionalGravityBlockSpeed(
			Entity entity, Vec3 vanillaScaledMovement) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction == Direction.DOWN) {
			entity.setDeltaMovement(vanillaScaledMovement);
			return;
		}
		entity.setDeltaMovement(
				DirectionalGravityTransforms.applyBlockSpeedFactor(
						direction, entity.getDeltaMovement(),
						getBlockSpeedFactor()));
	}

	@Inject(method = "move", at = @At("RETURN"))
	private void jojo_ripples$finishDirectionalGravityMove(
			MoverType type, Vec3 requested, CallbackInfo ci) {
		jojo_ripples$moveGravity = Direction.DOWN;
		jojo_ripples$actualMovement = Vec3.ZERO;
	}
}
