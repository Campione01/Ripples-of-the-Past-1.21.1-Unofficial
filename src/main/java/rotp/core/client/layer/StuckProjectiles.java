package rotp.core.client.layer;

import rotp.core.impl.stands.goldexperience.GEStuckObjectsState;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

// Dist-neutral math of the 1.16 KnifeLayer / MobStuckArrowLayer (no client classes here).
public final class StuckProjectiles {
	// Stuck objects keep the player's model scale on every mob.
	public static final float PLAYER_SCALE = 0.9375F;

	private StuckProjectiles() {}

	public enum Type {
		ARROW,
		KNIFE
	}

	public static int numStuck(Type type, LivingEntity entity) {
		return switch (type) {
		case ARROW -> entity.getArrowCount();
		case KNIFE -> GEStuckObjectsState.stuckKnives(entity);
		};
	}

	// 1.16: new Random(entity id + projectile type ordinal).
	public static long seed(LivingEntity entity, Type type) {
		return (long) entity.getId() + type.ordinal();
	}

	public static float cubeArea(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		float x = maxX - minX;
		float y = maxY - minY;
		float z = maxZ - minZ;
		return 2 * (x * y + y * z + z * x);
	}

	// Area-weighted cube index; roll is in [0, total area).
	public static int pickWeighted(float[] areas, float roll) {
		for (int i = 0; i < areas.length; i++) {
			if (roll < areas[i]) {
				return i;
			}
			roll -= areas[i];
		}
		return areas.length - 1;
	}

	// Point in 0..1 cube coordinates: a random face, or the inner half for slimes.
	public static float[] cubePoint(RandomSource random, boolean slime) {
		float[] point = new float[3];
		if (slime) {
			for (int i = 0; i < 3; i++) {
				point[i] = random.nextFloat() * 0.5F + 0.25F;
			}
			return point;
		}
		int face = random.nextInt(6);
		int axis = face / 2;
		for (int i = 0; i < 3; i++) {
			point[i] = i == axis ? face % 2 : random.nextFloat();
		}
		return point;
	}

	// Maps a 0..1 cube coordinate to the direction the projectile points into the body.
	public static float direction(float cubeCoord) {
		return -(cubeCoord * 2.0F - 1.0F);
	}

	public static float yRot(float x, float z) {
		return (float) (Math.atan2(x, z) * (180F / (float) Math.PI));
	}

	public static float xRot(float x, float y, float z) {
		float horizontal = (float) Math.sqrt(x * x + z * z);
		return (float) (Math.atan2(y, horizontal) * (180F / (float) Math.PI));
	}

	// Undoes the renderer's own scale so the objects are drawn at player size.
	public static float[] scaleBack(float scaleX, float scaleY, float scaleZ) {
		return new float[] { scaleBackAxis(scaleX), scaleBackAxis(scaleY), scaleBackAxis(scaleZ) };
	}

	private static float scaleBackAxis(float scale) {
		return scale != 0 ? PLAYER_SCALE / scale : PLAYER_SCALE;
	}
}
