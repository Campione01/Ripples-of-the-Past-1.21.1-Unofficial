package com.github.standobyte.jojo.subsystems.target;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.util.functions.AABBUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HitResultUtil {

	public static ActionTarget clipEntityLook(LivingEntity aiming, Predicate<Entity> entityFilter, double standPrecision) {
		return HitResultUtil.clip(aiming.getEyePosition(), aiming.getLookAngle(), 
				aiming.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), aiming.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 
				aiming.level(), entityFilter, aiming, standPrecision);
	}

	public static boolean isTargetWithinRange(ActionTarget target, LivingEntity aiming,
			Level level, double blockMaxRange, double entityMaxRange) {
		return aiming != null && isTargetWithinRange(target, aiming.getEyePosition(), level, blockMaxRange, entityMaxRange);
	}

	public static boolean isTargetWithinRange(ActionTarget target, Vec3 startingPos,
			Level level, double blockMaxRange, double entityMaxRange) {
		if (target == null || target.isEmpty(level)) {
			return false;
		}
		double maxRange = switch (target.getType()) {
			case BLOCK -> blockMaxRange;
			case ENTITY -> entityMaxRange;
			default -> 0.0D;
		};
		if (maxRange <= 0.0D) {
			return false;
		}
		double maxRangeSqr = maxRange * maxRange;
		return target.getBoundingBox(level)
				.map(aabb -> aabb.distanceToSqr(startingPos) <= maxRangeSqr)
				.orElse(false);
	}

	public static List<ActionTarget> clipMultipleEntities(Entity aiming, double entityMaxRange, @Nullable Predicate<Entity> entityFilter, 
			double rayTraceInflate, double standPrecision) {
		return HitResultUtil.clipMultipleEntities(aiming.getEyePosition(), aiming.getLookAngle(), entityMaxRange, 
				aiming.level(), aiming, entityFilter, rayTraceInflate, standPrecision);
	}

	public static List<ActionTarget> clipMultipleTargets(Entity aiming, double maxRange, @Nullable Predicate<Entity> entityFilter,
			double rayTraceInflate, double standPrecision) {
		return HitResultUtil.clipMultipleTargets(aiming.getEyePosition(), aiming.getLookAngle(), maxRange,
				aiming.level(), aiming, entityFilter, rayTraceInflate, standPrecision);
	}

	public static List<ActionTarget> clipMultipleTargets(Vec3 startingPos, Vec3 directionVec, double maxRange,
			Level level, @Nullable Entity aiming, @Nullable Predicate<Entity> entityFilter,
			double rayTraceInflate, double standPrecision) {
		List<ActionTarget> entityTargets = clipMultipleEntities(startingPos, directionVec, maxRange,
				level, aiming, entityFilter, rayTraceInflate, standPrecision);
		if (!entityTargets.isEmpty() || directionVec.lengthSqr() < 1.0E-7) {
			return entityTargets;
		}

		Vec3 rayVec = directionVec.normalize().scale(maxRange);
		Vec3 endPos = startingPos.add(rayVec);
		CollisionContext entityCtx = aiming != null ? CollisionContext.of(aiming) : CollisionContext.empty();
		BlockHitResult blockHitResult = clipBlocks(new ClipContext(startingPos, endPos,
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entityCtx), level);
		ActionTarget blockTarget = ActionTarget.fromVanilla(blockHitResult);
		return blockTarget.isEmpty(level) ? List.of() : List.of(blockTarget);
	}

	public static List<ActionTarget> clipMultipleEntities(Vec3 startingPos, Vec3 directionVec, double entityMaxRange, 
			Level level, @Nullable Entity aiming, @Nullable Predicate<Entity> entityFilter, 
			double rayTraceInflate, double standPrecision) {
		if (directionVec.lengthSqr() < 1.0E-7) {
			return List.of();
		}
		Vec3 rayVec = directionVec.normalize().scale(entityMaxRange);
		Vec3 endPos = startingPos.add(rayVec);
		AABB boundingBox = aiming != null ? aiming.getBoundingBox().expandTowards(rayVec).inflate(1.0D) : 
			new AABB(startingPos, endPos).inflate(1.0D);
		double maxDistanceSqr = entityMaxRange * entityMaxRange;
		List<EntityClip> clippedTargets = new ArrayList<>();
		for (Entity potentialTarget : level.getEntities(aiming, boundingBox, e -> !e.isSpectator() && e.isPickable() && 
				(entityFilter == null || entityFilter.test(e)))) {
			AABB targetAABB = potentialTarget.getBoundingBox().inflate(potentialTarget.getPickRadius() + rayTraceInflate);
			AABB precisionAABB = standPrecisionTargetHitbox(targetAABB, standPrecision);
			Optional<Vec3> clipOptional = precisionAABB.clip(startingPos, endPos);
			if (precisionAABB.contains(startingPos)) {
				clippedTargets.add(new EntityClip(
						new ActionTarget(potentialTarget).withClipPos(Optional.of(clipOptional.orElse(startingPos))), 0.0D));
			}
			else if (clipOptional.isPresent()) {
				Vec3 clipVec = clipOptional.get();
				double clipDistanceSqr = startingPos.distanceToSqr(clipVec);
				if (clipDistanceSqr < maxDistanceSqr || maxDistanceSqr == 0.0D) {
					if (aiming != null && potentialTarget.getRootVehicle() == aiming.getRootVehicle() && !potentialTarget.canRiderInteract()) {
						if (maxDistanceSqr == 0.0D) {
							clippedTargets.add(new EntityClip(
									new ActionTarget(potentialTarget).withClipPos(Optional.of(clipVec)), 0.0D));
						}
					}
					else {
						clippedTargets.add(new EntityClip(
								new ActionTarget(potentialTarget).withClipPos(Optional.of(clipVec)), clipDistanceSqr));
					}
				}
			}
		}
		clippedTargets.sort(Comparator.comparingDouble(EntityClip::distanceSqr));
		return clippedTargets.stream()
				.map(EntityClip::target)
				.toList();
	}

	public static ActionTarget clip(Vec3 startingPos, Vec3 directionVec, double blockMaxRange, double entityMaxRange, 
			Level level, Predicate<Entity> entityFilter, @Nullable Entity aiming, double standPrecision) {
		boolean hitFluids = false;
		CollisionContext entityCtx = aiming != null ? CollisionContext.of(aiming) : CollisionContext.empty();

		double maxRange = Math.max(blockMaxRange, entityMaxRange);


		// raytrace blocks

		Vec3 endPosBlocks = startingPos.add(directionVec.x * blockMaxRange, directionVec.y * blockMaxRange, directionVec.z * blockMaxRange);
		ClipContext blockClipCtx = new ClipContext(startingPos, endPosBlocks, 
				ClipContext.Block.COLLIDER, hitFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, entityCtx);
		BlockHitResult blockHitResult = clipBlocks(blockClipCtx, level);

		maxRange = entityMaxRange;
		AABB blockHitAABB = null;

		if (blockHitResult.getType() != HitResult.Type.MISS) {
			blockHitAABB = new AABB(blockHitResult.getBlockPos());
			Vec3 blockClipPos = blockHitAABB.clip(startingPos, endPosBlocks).orElse(blockHitResult.getLocation());
			maxRange = Math.min(maxRange, Math.sqrt(blockClipPos.distanceToSqr(startingPos)));
		}


		// raytrace entities
		
		Vec3 endPosEntities = startingPos.add(directionVec.x * maxRange, directionVec.y * maxRange, directionVec.z * maxRange);
		AABB boundingBox = new AABB(startingPos, endPosEntities).inflate(1.0, 1.0, 1.0);

		double closestEntityDistSqr = maxRange * maxRange;
		Entity closestEntity = null;
		Vec3 closestEntityPos = null;
		AABB entityHitAABB = null;

		for (Entity potentialTarget : level.getEntities(aiming, boundingBox, entityFilter)) {
			AABB targetAABB = potentialTarget.getBoundingBox().inflate(potentialTarget.getPickRadius());
			AABB precisionAABB = standPrecisionTargetHitbox(targetAABB, standPrecision);
			
			if (targetAABB.contains(startingPos)) {
				if (closestEntityDistSqr >= 0.0) {
					closestEntity = potentialTarget;
					Optional<Vec3> clip = targetAABB.clip(startingPos, endPosEntities);
					closestEntityPos = clip.orElse(startingPos);
					closestEntityDistSqr = 0.0;
					entityHitAABB = targetAABB;
				}
			}
			else {
				Optional<Vec3> precisionClip;
				if (precisionAABB.contains(startingPos)) {
					precisionClip = Optional.of(startingPos);
				}
				else {
					precisionClip = precisionAABB.clip(startingPos, endPosEntities);
				}
				if (precisionClip.isPresent()) {
					Optional<Vec3> clip = targetAABB.clip(startingPos, endPosEntities);
					Vec3 clipPos;
					if (clip.isPresent()) clipPos = clip.get();
					else {
						Vec3 point = precisionClip.get();
						clipPos = new Vec3(
								Mth.clamp(point.x, targetAABB.minX, targetAABB.maxX),
								Mth.clamp(point.y, targetAABB.minY, targetAABB.maxY),
								Mth.clamp(point.z, targetAABB.minZ, targetAABB.maxZ));
					}
					
					double distSqr = startingPos.distanceToSqr(clipPos);
					if (distSqr < closestEntityDistSqr) {
						closestEntity = potentialTarget;
						closestEntityPos = clipPos;
						closestEntityDistSqr = distSqr;
						entityHitAABB = targetAABB;
					}
				}
			}
		}

		ActionTarget entityHitResult = closestEntity == null ? null : new ActionTarget(closestEntity).withClipPos(Optional.ofNullable(closestEntityPos));


		ActionTarget hitResult;
		AABB hitResultAABB;
		if (entityHitResult != null) {
			hitResult = entityHitResult;
			maxRange = entityMaxRange;
			hitResultAABB = entityHitAABB;
		}
		else {
			hitResult = ActionTarget.fromVanilla(blockHitResult);
			maxRange = blockMaxRange;
			hitResultAABB = blockHitAABB;
		}

		// filter out if it's too far

		if (hitResult.getType() != ActionTarget.TargetType.EMPTY) {
			if (hitResultAABB.distanceToSqr(startingPos) > maxRange * maxRange) {
//				Vec3 pos = hitResultAABB.getCenter();
//				Direction direction = Direction.getApproximateNearest(pos.x - startingPos.x, pos.y - startingPos.y, pos.z - startingPos.z);
				hitResult = ActionTarget.EMPTY;
			}
		}

		return hitResult;
	}
	
	public static AABB standPrecisionTargetHitbox(AABB aabb, double precision) {
		if (precision > 4) {
			double scale = precision / 5 + 0.2;

			double xSize = aabb.getXsize();
			double ySize = aabb.getYsize();
			double zSize = aabb.getZsize();

			aabb = AABBUtil.scale(aabb, 
					Math.min(scale, 1 + 4 / xSize), 
					Math.min(scale, 1 + 4 / ySize), 
					Math.min(scale, 1 + 4 / zSize));
		}
		return aabb;
	}
	
	public static BlockHitResult clipBlocks(ClipContext blockClipCtx, Level level) {
		return BlockGetter.traverseBlocks(blockClipCtx.getFrom(), blockClipCtx.getTo(), blockClipCtx, 
				(ClipContext ctx, BlockPos blockPos) -> {
					BlockState blockState = level.getBlockState(blockPos);
					FluidState fluidState = level.getFluidState(blockPos);
					Vec3 from = ctx.getFrom();
					Vec3 to = ctx.getTo();

					VoxelShape blockShape = ctx.getBlockShape(blockState, level, blockPos);
					BlockHitResult blockClip = blockShape.clip(from, to, blockPos);
					if (blockClip != null) {
						BlockHitResult blockI9nClip = blockState.getInteractionShape(level, blockPos).clip(from, to, blockPos);
						if (blockI9nClip != null
								&& blockI9nClip.getLocation().subtract(from).lengthSqr() < blockClip.getLocation().subtract(from).lengthSqr()) {
							blockClip = blockClip.withDirection(blockI9nClip.getDirection());
						}
					}

					VoxelShape fluidShape = ctx.getFluidShape(fluidState, level, blockPos);
					BlockHitResult fluidClip = fluidShape.clip(from, to, blockPos);

					double blockDist = blockClip == null ? Double.MAX_VALUE : ctx.getFrom().distanceToSqr(blockClip.getLocation());
					double fluidDist = fluidClip == null ? Double.MAX_VALUE : ctx.getFrom().distanceToSqr(fluidClip.getLocation());
					return blockDist <= fluidDist ? blockClip : fluidClip;
				}, 
				(ClipContext ctx) -> {
					Vec3 clipVec = ctx.getFrom().subtract(ctx.getTo());
					return BlockHitResult.miss(ctx.getTo(), Direction./*getApproximateNearest*/getNearest(clipVec.x, clipVec.y, clipVec.z), BlockPos.containing(ctx.getTo()));
				});
	}

	private static class EntityClip {
		private final ActionTarget target;
		private final double distanceSqr;

		private EntityClip(ActionTarget target, double distanceSqr) {
			this.target = target;
			this.distanceSqr = distanceSqr;
		}

		private ActionTarget target() {
			return target;
		}

		private double distanceSqr() {
			return distanceSqr;
		}
	}

}
