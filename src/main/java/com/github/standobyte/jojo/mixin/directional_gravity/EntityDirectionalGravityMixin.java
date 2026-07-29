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
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityCollision;
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityFrameAccess;
import com.github.standobyte.jojo.subsystems.directional_gravity.DirectionalGravityRuntime;
import com.github.standobyte.jojo.subsystems.entity_soft_landing.EntitySoftLandingRuntime;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Mixin(Entity.class)
public abstract class EntityDirectionalGravityMixin
		implements DirectionalGravityFrameAccess {
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
	@Unique
	private DirectionalGravityTransforms.RelativeCollision
			jojo_ripples$relativeCollision;
	@Unique
	private int jojo_ripples$localFrameDepth;
	@Unique
	private Direction jojo_ripples$localFrameDirection =
			Direction.DOWN;
	@Unique
	private Vec3 jojo_ripples$lastLocalMovement = Vec3.ZERO;
	@Unique
	private boolean jojo_ripples$worldMoveAdapterActive;

	@Override
	public int jojo_ripples$localFrameDepth() {
		return jojo_ripples$localFrameDepth;
	}

	@Override
	public void jojo_ripples$setLocalFrameDepth(int depth) {
		jojo_ripples$localFrameDepth = depth;
	}

	@Override
	public Direction jojo_ripples$localFrameDirection() {
		return jojo_ripples$localFrameDirection;
	}

	@Override
	public void jojo_ripples$setLocalFrameDirection(
			Direction direction) {
		jojo_ripples$localFrameDirection = direction;
	}

	@Override
	public Vec3 jojo_ripples$lastLocalMovement() {
		return jojo_ripples$lastLocalMovement;
	}

	@Override
	public void jojo_ripples$setLastLocalMovement(Vec3 movement) {
		jojo_ripples$lastLocalMovement = movement;
	}

	@Override
	public boolean jojo_ripples$isWorldMoveAdapterActive() {
		return jojo_ripples$worldMoveAdapterActive;
	}

	@Override
	public void jojo_ripples$setWorldMoveAdapterActive(
			boolean active) {
		jojo_ripples$worldMoveAdapterActive = active;
	}

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
		if (direction != Direction.DOWN
				&& !DirectionalGravityRuntime.isLocalFrame(
						(Entity) (Object) this)) {
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

	@Inject(method = "getVehicleAttachmentPoint",
			at = @At("RETURN"), cancellable = true)
	private void jojo_ripples$directionalGravityVehicleAttachment(
			Entity vehicle, CallbackInfoReturnable<Vec3> cir) {
		Entity entity = (Entity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction != Direction.DOWN) {
			cir.setReturnValue(DirectionalGravityTransforms.toWorld(
					direction, cir.getReturnValue()));
		}
	}

	@WrapMethod(method = "move")
	private void jojo_ripples$directionalGravityTravelMoveBoundary(
			MoverType type, Vec3 requested,
			Operation<Void> original) {
		Entity entity = (Entity) (Object) this;
		Direction direction =
				DirectionalGravityRuntime.localFrameDirection(entity);
		if (direction == Direction.DOWN) {
			original.call(type, requested);
			return;
		}

		Vec3 start = entity.position();
		entity.setDeltaMovement(
				DirectionalGravityTransforms.toWorld(
						direction, entity.getDeltaMovement()));
		DirectionalGravityRuntime.setWorldMoveAdapterActive(
				entity, true);
		try {
			original.call(type,
					DirectionalGravityTransforms.toWorld(
							direction, requested));
		}
		finally {
			DirectionalGravityRuntime.recordLocalMovement(
					entity,
					DirectionalGravityTransforms.toLocal(
							direction,
							entity.position().subtract(start)));
			entity.setDeltaMovement(
					DirectionalGravityTransforms.toLocal(
							direction,
							entity.getDeltaMovement()));
			DirectionalGravityRuntime.setWorldMoveAdapterActive(
					entity, false);
		}
	}

	@WrapMethod(method = "onAboveBubbleCol")
	private void jojo_ripples$directionalGravityBubbleSurface(
			boolean downward, Operation<Void> original) {
		Entity entity = (Entity) (Object) this;
		jojo_ripples$withLocalVelocityAdapter(
				entity, () -> original.call(downward));
	}

	@WrapMethod(method = "onInsideBubbleColumn")
	private void jojo_ripples$directionalGravityBubbleColumn(
			boolean downward, Operation<Void> original) {
		Entity entity = (Entity) (Object) this;
		jojo_ripples$withLocalVelocityAdapter(
				entity, () -> original.call(downward));
	}

	@Inject(method = "move", at = @At("HEAD"))
	private void jojo_ripples$startDirectionalGravityMove(
			MoverType type, Vec3 requested, CallbackInfo ci) {
		jojo_ripples$moveGravity =
				DirectionalGravityApi.getEffectiveDirection(
						(Entity) (Object) this);
		jojo_ripples$actualMovement = Vec3.ZERO;
		jojo_ripples$relativeCollision = null;
	}

	@Inject(method = "collide", at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$directionalGravityCollide(
			Vec3 requested, CallbackInfoReturnable<Vec3> cir) {
		Entity entity = (Entity) (Object) this;
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction != Direction.DOWN) {
			cir.setReturnValue(DirectionalGravityCollision.collide(
					entity, requested, direction));
		}
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
		jojo_ripples$relativeCollision = collision;
		verticalCollision = collision.gravityAxisCollision();
		verticalCollisionBelow = collision.groundCollision();
		horizontalCollision = collision.transverseCollision();
		minorHorizontalCollision = false;
		entity.setOnGroundWithMovement(
				collision.groundCollision(), actualMovement);
	}

	@Redirect(method = "move",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;"
							+ "setDeltaMovement(DDD)V"))
	private void jojo_ripples$directionalGravityCollisionVelocity(
			Entity entity, double vanillaX, double vanillaY,
			double vanillaZ) {
		if (jojo_ripples$moveGravity == Direction.DOWN
				|| jojo_ripples$relativeCollision == null) {
			entity.setDeltaMovement(
					vanillaX, vanillaY, vanillaZ);
			return;
		}

		DirectionalGravityTransforms.RelativeCollision collision =
				jojo_ripples$relativeCollision;
		Direction.Axis gravityAxis =
				jojo_ripples$moveGravity.getAxis();
		boolean preserveLandingImpact =
				collision.groundCollision();
		Vec3 velocity = entity.getDeltaMovement();
		entity.setDeltaMovement(
				collision.xCollision()
						&& !(preserveLandingImpact
								&& gravityAxis == Direction.Axis.X)
						? 0.0 : velocity.x,
				collision.yCollision()
						&& !(preserveLandingImpact
								&& gravityAxis == Direction.Axis.Y)
						? 0.0 : velocity.y,
				collision.zCollision()
						&& !(preserveLandingImpact
								&& gravityAxis == Direction.Axis.Z)
						? 0.0 : velocity.z);
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

	@Redirect(method = "move",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/phys/Vec3;"
							+ "horizontalDistance()D"))
	private double jojo_ripples$directionalGravityWalkDistance(
			Vec3 movement) {
		if (jojo_ripples$moveGravity == Direction.DOWN) {
			return movement.horizontalDistance();
		}
		return DirectionalGravityTransforms.toLocal(
				jojo_ripples$moveGravity, movement)
				.horizontalDistance();
	}

	@Redirect(method = "move",
			at = @At(value = "INVOKE",
					target = "Ljava/lang/Math;sqrt(D)D"))
	private double jojo_ripples$directionalGravityStepDistance(
			double vanillaSquaredDistance) {
		if (jojo_ripples$moveGravity == Direction.DOWN) {
			return Math.sqrt(vanillaSquaredDistance);
		}
		Entity entity = (Entity) (Object) this;
		Vec3 local = DirectionalGravityTransforms.toLocal(
				jojo_ripples$moveGravity,
				jojo_ripples$actualMovement);
		BlockState state = entity.level().getBlockState(
				entity.getOnPos());
		double localVertical =
				state.is(BlockTags.CLIMBABLE)
						|| state.is(Blocks.POWDER_SNOW)
				? local.y : 0.0;
		return Math.sqrt(local.x * local.x
				+ localVertical * localVertical
				+ local.z * local.z);
	}

	@Redirect(method = "move",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/Block;"
							+ "updateEntityAfterFallOn"
							+ "(Lnet/minecraft/world/level/BlockGetter;"
							+ "Lnet/minecraft/world/entity/Entity;)V"))
	private void jojo_ripples$deferDirectionalGravityLanding(
			Block block, BlockGetter level, Entity entity,
			@Local(ordinal = 0) BlockPos position,
			@Local(ordinal = 0) BlockState state) {
		if (jojo_ripples$moveGravity == Direction.DOWN) {
			jojo_ripples$updateEntityAfterFallOn(
					block, level, position, state, entity);
		}
	}

	@Inject(method = "move",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/Block;"
							+ "stepOn"
							+ "(Lnet/minecraft/world/level/Level;"
							+ "Lnet/minecraft/core/BlockPos;"
							+ "Lnet/minecraft/world/level/block/state/"
							+ "BlockState;"
							+ "Lnet/minecraft/world/entity/Entity;)V",
					shift = At.Shift.BEFORE))
	private void jojo_ripples$applyDirectionalGravityLanding(
			MoverType type, Vec3 requested, CallbackInfo ci,
			@Local(ordinal = 0) BlockPos position,
			@Local(ordinal = 0) BlockState state) {
		Entity entity = (Entity) (Object) this;
		if (jojo_ripples$moveGravity != Direction.DOWN
				&& jojo_ripples$relativeCollision != null
				&& jojo_ripples$relativeCollision
						.groundCollision()) {
			Block block = state.getBlock();
			jojo_ripples$withLocalVelocityAdapter(
					entity,
					() -> jojo_ripples$updateEntityAfterFallOn(
							block,
							entity.level(),
							position,
							state,
							entity));
		}
	}

	@Redirect(method = "checkFallDamage",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/Block;"
							+ "fallOn"
							+ "(Lnet/minecraft/world/level/Level;"
							+ "Lnet/minecraft/world/level/block/state/"
							+ "BlockState;"
							+ "Lnet/minecraft/core/BlockPos;"
							+ "Lnet/minecraft/world/entity/Entity;F)V"))
	private void jojo_ripples$directionalGravityFallOn(
			Block block, Level level, BlockState state, BlockPos pos,
			Entity entity, float fallDistance) {
		jojo_ripples$withLocalVelocityAdapter(
				entity,
				() -> block.fallOn(
						level, state, pos, entity, fallDistance));
	}

	@Inject(method = "move", at = @At("RETURN"))
	private void jojo_ripples$finishDirectionalGravityMove(
			MoverType type, Vec3 requested, CallbackInfo ci) {
		if (jojo_ripples$moveGravity != Direction.DOWN
				&& jojo_ripples$relativeCollision != null
				&& jojo_ripples$relativeCollision
						.gravityAxisCollision()
				&& !jojo_ripples$relativeCollision
						.groundCollision()) {
			Entity entity = (Entity) (Object) this;
			Vec3 velocity = entity.getDeltaMovement();
			entity.setDeltaMovement(switch (
					jojo_ripples$moveGravity.getAxis()) {
				case X -> new Vec3(
						0.0, velocity.y, velocity.z);
				case Y -> new Vec3(
						velocity.x, 0.0, velocity.z);
				case Z -> new Vec3(
						velocity.x, velocity.y, 0.0);
			});
		}
		jojo_ripples$moveGravity = Direction.DOWN;
		jojo_ripples$actualMovement = Vec3.ZERO;
		jojo_ripples$relativeCollision = null;
	}

	@Unique
	private static void jojo_ripples$withLocalVelocityAdapter(
			Entity entity, Runnable response) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		boolean worldVelocity =
				!DirectionalGravityRuntime.isLocalFrame(entity)
				|| DirectionalGravityRuntime
						.isWorldMoveAdapterActive(entity);
		if (direction == Direction.DOWN || !worldVelocity) {
			response.run();
			return;
		}
		entity.setDeltaMovement(
				DirectionalGravityTransforms.toLocal(
						direction, entity.getDeltaMovement()));
		try {
			response.run();
		}
		finally {
			entity.setDeltaMovement(
					DirectionalGravityTransforms.toWorld(
							direction,
							entity.getDeltaMovement()));
		}
	}

	@Unique
	private static void jojo_ripples$updateEntityAfterFallOn(
			Block block,
			BlockGetter level,
			BlockPos position,
			BlockState state,
			Entity entity) {
		if (!EntitySoftLandingRuntime.applyPostLandingMovement(
				level, position, state, entity)) {
			block.updateEntityAfterFallOn(level, entity);
		}
	}
}
