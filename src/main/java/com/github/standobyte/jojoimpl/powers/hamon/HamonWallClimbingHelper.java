package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.util.functions.CollisionHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class HamonWallClimbingHelper {
	public static final double MAX_WALL_DISTANCE = 0.5D;

	private static final CollisionContext NO_CLIMBING_ON_BARRIERS = new CollisionContext() {
		@Override
		public boolean isDescending() {
			return false;
		}

		@Override
		public boolean isAbove(VoxelShape shape, BlockPos pos, boolean canAscend) {
			return false;
		}

		@Override
		public boolean isHoldingItem(Item item) {
			return false;
		}

		@Override
		public boolean canStandOnFluid(FluidState fluid1, FluidState fluid2) {
			return false;
		}
	};

	private HamonWallClimbingHelper() {}

	public static Vec3 collide(Entity entity, AABB box, Vec3 offsetVec, boolean excludeBarriers) {
		CollisionContext context = excludeBarriers ? NO_CLIMBING_ON_BARRIERS : CollisionContext.of(entity);
		CollisionHelper.BlockCollisionResult collision = CollisionHelper.collideBoundingBox(
				offsetVec, box, entity.level(), context);
		return new Vec3(collision.x, collision.y, collision.z);
	}

	public static boolean disableBlockCollisionShape(CollisionContext context) {
		return context == NO_CLIMBING_ON_BARRIERS;
	}
}
