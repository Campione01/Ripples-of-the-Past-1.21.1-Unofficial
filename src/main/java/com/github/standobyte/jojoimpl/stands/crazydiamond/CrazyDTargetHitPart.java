package com.github.standobyte.jojoimpl.stands.crazydiamond;

import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.util.functions.MathUtil;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

enum CrazyDTargetHitPart {
	HEAD,
	TORSO_ARMS,
	LEGS;

	static final int NONE_SYNC_ID = -1;

	static CrazyDTargetHitPart getHitTarget(Entity target, Entity aiming) {
		double distanceToTarget = getDistance(aiming, target.getBoundingBox());
		double aimY = aiming.getEyePosition(1.0F).add(aiming.getLookAngle().scale(distanceToTarget)).y;
		return getHitTarget(target, aimY - target.getY());
	}

	static CrazyDTargetHitPart getHitTarget(Entity target, double targetY) {
		double height = target.getBbHeight();
		if (targetY < height * 0.75D) {
			return targetY < height * 0.375D ? LEGS : TORSO_ARMS;
		}
		return HEAD;
	}

	@Nullable
	static CrazyDTargetHitPart fromSyncId(int syncId) {
		if (syncId < 0 || syncId >= values().length) {
			return null;
		}
		return values()[syncId];
	}

	int syncId() {
		return ordinal();
	}

	@Nullable
	Vec3 getPartCenter(LivingEntity target) {
		return switch (this) {
			case HEAD -> new Vec3(target.getX(), target.getY(1.0D), target.getZ());
			case TORSO_ARMS -> new Vec3(target.getX(), target.getY(0.7D), target.getZ())
					.add(new Vec3(target.getBbWidth() * 0.375F, 0.0D, 0.0D)
							.yRot((180.0F - target.getYRot()) * MathUtil.DEG_TO_RAD));
			case LEGS -> new Vec3(target.getX(), target.getY(0.0D), target.getZ());
		};
	}

	private static double getDistance(Entity entity, AABB targetAabb) {
		Vec3 startPos = entity.getEyePosition(1.0F);
		if (targetAabb.contains(startPos)) {
			return 0.0D;
		}
		Vec3 endPos = new Vec3(
				Mth.lerp(0.5D, targetAabb.minX, targetAabb.maxX),
				Mth.lerp(entity.getBbHeight() == 0.0F ? 0.0D : entity.getEyeHeight() / entity.getBbHeight(), targetAabb.minY, targetAabb.maxY),
				Mth.lerp(0.5D, targetAabb.minZ, targetAabb.maxZ));
		Optional<Vec3> clipOptional = targetAabb.clip(startPos, endPos);
		return clipOptional.map(clipVec -> startPos.distanceTo(clipVec) - entity.getBbWidth() / 2.0D).orElse(-1.0D);
	}
}
