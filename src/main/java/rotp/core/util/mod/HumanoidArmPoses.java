package rotp.core.util.mod;

import rotp.core.block.WoodenCoffinBlock;
import rotp.core.init.ModEntityTypes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// 1.16 BipedModelMixin glider and coffin arm poses; no client classes so gametests can check them
public final class HumanoidArmPoses {
	// slots of the pose array HumanoidModelMixin fills from its parts
	public static final int LEFT_X = 0;
	public static final int LEFT_Y = 1;
	public static final int LEFT_Z = 2;
	public static final int RIGHT_X = 3;
	public static final int RIGHT_Y = 4;
	public static final int RIGHT_Z = 5;
	public static final int BODY_Z = 6;
	public static final int SIZE = 7;

	private HumanoidArmPoses() {}

	public static boolean ridingLeavesGlider(LivingEntity entity) {
		Entity vehicle = entity.getVehicle();
		return vehicle != null && vehicle.getType() == ModEntityTypes.LEAVES_GLIDER.get();
	}

	public static boolean sleepingInCoffin(LivingEntity entity) {
		return entity.isSleeping() && WoodenCoffinBlock.isSleepingInCoffin(entity);
	}

	// Rewrites the pose in place; returns true when a pose applied
	public static boolean apply(LivingEntity entity, float[] pose) {
		boolean applied = false;
		// both arms straight up while riding the leaves glider
		if (ridingLeavesGlider(entity)) {
			pose[LEFT_X] = (float) Math.PI;
			pose[LEFT_Y] = 0.0F;
			pose[LEFT_Z] = 0.0F;
			pose[RIGHT_X] = (float) Math.PI;
			pose[RIGHT_Y] = 0.0F;
			pose[RIGHT_Z] = 0.0F;
			pose[BODY_Z] = 0.0F;
			applied = true;
		}
		// arms held straight along the body in a coffin
		if (sleepingInCoffin(entity)) {
			pose[LEFT_X] = 0.0F;
			pose[LEFT_Z] = 0.0F;
			pose[RIGHT_X] = 0.0F;
			pose[RIGHT_Z] = 0.0F;
			applied = true;
		}
		return applied;
	}
}
