package com.github.standobyte.jojo.mixin.hamon;

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.github.standobyte.jojoimpl.powers.hamon.HamonMovementHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Mixin(Entity.class)
public class EntityLiquidWalkingMixin {
	@ModifyVariable(method = "move", ordinal = 1, index = 3, at = @At(
			value = "INVOKE_ASSIGN",
			target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 jojo_ripples$fluidCollision(Vec3 originalDisplacement) {
		if (!((Object) this instanceof LivingEntity entity)) {
			return originalDisplacement;
		}

		if (originalDisplacement.y <= 0.0D && !jojo_ripples$isTouchingFluid(entity, entity.getBoundingBox().deflate(0.001D))) {
			Map<Vec3, Double> points = jojo_ripples$findFluidDistances(entity, originalDisplacement);
			Double highestDistance = null;

			for (Map.Entry<Vec3, Double> point : points.entrySet()) {
				if (highestDistance == null || point.getValue() != null && point.getValue() > highestDistance) {
					highestDistance = point.getValue();
				}
			}

			if (highestDistance != null) {
				Vec3 finalDisplacement = new Vec3(originalDisplacement.x, highestDistance, originalDisplacement.z);
				AABB finalBox = entity.getBoundingBox().move(finalDisplacement).deflate(0.001D);
				if (!jojo_ripples$isTouchingFluid(entity, finalBox)) {
					entity.fallDistance = 0.0F;
					entity.setOnGround(true);
					return finalDisplacement;
				}
			}
		}

		return originalDisplacement;
	}

	@Unique
	private static Map<Vec3, Double> jojo_ripples$findFluidDistances(LivingEntity entity, Vec3 originalDisplacement) {
		AABB box = entity.getBoundingBox().move(originalDisplacement);
		HashMap<Vec3, Double> points = new HashMap<>();
		points.put(new Vec3(box.minX, box.minY, box.minZ), null);
		points.put(new Vec3(box.minX, box.minY, box.maxZ), null);
		points.put(new Vec3(box.maxX, box.minY, box.minZ), null);
		points.put(new Vec3(box.maxX, box.minY, box.maxZ), null);

		double fluidStepHeight = entity.onGround() ? Math.max(1.0D, entity.maxUpStep()) : 0.0D;
		for (Map.Entry<Vec3, Double> entry : points.entrySet()) {
			Vec3 point = entry.getKey();
			for (int i = 0; ; --i) {
				BlockPos landingPos = BlockPos.containing(point.x, point.y + i + fluidStepHeight, point.z);
				FluidState landingState = entity.level().getFluidState(landingPos);
				double distanceToFluidSurface = landingPos.getY() + landingState.getOwnHeight() - entity.getY();
				double limitingVelocity = originalDisplacement.y;

				if (distanceToFluidSurface < limitingVelocity || distanceToFluidSurface > fluidStepHeight) {
					break;
				}

				if (!landingState.isEmpty() && HamonMovementHelper.onLiquidWalkingEvent(entity, landingState)) {
					entry.setValue(distanceToFluidSurface);
					break;
				}
			}
		}

		return points;
	}

	@Unique
	private static boolean jojo_ripples$isTouchingFluid(LivingEntity entity, AABB box) {
		int minX = Mth.floor(box.minX);
		int maxX = Mth.ceil(box.maxX);
		int minY = Mth.floor(box.minY);
		int maxY = Mth.ceil(box.maxY);
		int minZ = Mth.floor(box.minZ);
		int maxZ = Mth.ceil(box.maxZ);
		Level world = entity.level();

		if (world.hasChunksAt(minX, minY, minZ, maxX, maxY, maxZ)) {
			BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
			for (int x = minX; x < maxX; ++x) {
				for (int y = minY; y < maxY; ++y) {
					for (int z = minZ; z < maxZ; ++z) {
						mutable.set(x, y, z);
						FluidState fluidState = world.getFluidState(mutable);

						if (!fluidState.isEmpty()) {
							double surfaceY = fluidState.getHeight(world, mutable) + y;
							if (surfaceY >= box.minY) {
								return true;
							}
						}
					}
				}
			}
		}

		return false;
	}
}
