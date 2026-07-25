package com.github.standobyte.jojo.client.entityrender.stand;

import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojoimpl.stands.starplatinum.client.StarPlatinumZoomClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class ClassicStandObstruction {
	enum Result {
		NONE,
		ARMS_ONLY,
		ARMS_ONLY_OUTLINE
	}

	private ClassicStandObstruction() {}

	static Result resolve(StandEntity entity, float partialTick, boolean bodyVisible, boolean outlineEnabled) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.options.getCameraType().isFirstPerson() || !bodyVisible) {
			return Result.NONE;
		}
		Entity cameraEntity = minecraft.getCameraEntity();
		if (cameraEntity == null || !(cameraEntity == entity.getUser())) {
			return Result.NONE;
		}
		if (StarPlatinumZoomClient.isZooming()) {
			return obstructed(outlineEnabled);
		}
		if (!entity.isArmsOnlyMode()) {
			LivingEntity user = entity.getUser();
			Vec3 diffVec = entity.getPosition(partialTick).subtract(user.getPosition(partialTick));
			Vec3 lookVec = Vec3.directionFromRotation(0, user.getViewYRot(partialTick));
			diffVec = new Vec3(diffVec.x, 0, diffVec.z);
			lookVec = new Vec3(lookVec.x, 0, lookVec.z);
			double distanceSqr = diffVec.lengthSqr();
			if (distanceSqr < 0.25 || distanceSqr < 9 && lookVec.dot(diffVec) > distanceSqr / 2) {
				return obstructed(outlineEnabled);
			}
		}
		return Result.NONE;
	}

	private static Result obstructed(boolean outlineEnabled) {
		return outlineEnabled ? Result.ARMS_ONLY_OUTLINE : Result.ARMS_ONLY;
	}
}
