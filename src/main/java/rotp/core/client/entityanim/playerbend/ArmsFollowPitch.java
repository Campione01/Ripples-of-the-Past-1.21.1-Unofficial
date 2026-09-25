package rotp.core.client.entityanim.playerbend;

import javax.annotation.Nullable;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import rotp.core.client.entityanim.action.AnimInstructionTimelines;

import net.minecraft.util.Mth;

/**
 * 1.16 KosmXArmsRotationModifier: the Overdrive barrage (punch_barrage), Blade Barrage and Divine Sandstorm
 * turned both player arms, and the barrage afterimage arms, about the X axis by the look pitch.
 * A clip opts in with "arms_follow_pitch = 1" in its timeline.
 * No client classes here, so server gametests can run it.
 */
public final class ArmsFollowPitch {
	public static final String TIMELINE_KEY = "arms_follow_pitch";

	private ArmsFollowPitch() {}

	public static boolean isSet(@Nullable AnimInstructionTimelines timelines) {
		return timelines != null && timelines.getNumericTimelineVal(TIMELINE_KEY, 0) != 0;
	}

	/** Tilts the given arm angles (radians, in place, null = no arm) by the look pitch in degrees if the clip opts in. */
	public static void tiltArms(@Nullable AnimInstructionTimelines timelines, float xRotDeg,
			@Nullable Vector3f leftArm, @Nullable Vector3f rightArm) {
		if (!isSet(timelines)) return;
		float xRotRad = xRotDeg * Mth.DEG_TO_RAD;
		if (leftArm != null) rotateAngles(leftArm, xRotRad);
		if (rightArm != null) rotateAngles(rightArm, xRotRad);
	}

	/** 1.16 ClientUtil.rotateAngles: ZYX model part angles turned about the parent X axis, in place. */
	public static Vector3f rotateAngles(Vector3f angles, float xRotRad) {
		Quaternionf q = new Quaternionf().rotationX(xRotRad)
				.mul(new Quaternionf().rotationZYX(angles.z, angles.y, angles.x));
		// JOML 1.10.5 getEulerAnglesZYX has a wrong x term (+y*y), so extract like 1.16 MathUtil.Matrix4ZYX
		float m00 = 1 - 2 * (q.y * q.y + q.z * q.z);
		float m01 = 2 * (q.x * q.y - q.z * q.w);
		float m10 = 2 * (q.x * q.y + q.z * q.w);
		float m11 = 1 - 2 * (q.z * q.z + q.x * q.x);
		float m20 = 2 * (q.x * q.z - q.y * q.w);
		float m21 = 2 * (q.y * q.z + q.x * q.w);
		float m22 = 1 - 2 * (q.x * q.x + q.y * q.y);
		float yRot = (float) Math.asin(-Mth.clamp(m20, -1F, 1F));
		if (Math.abs(m20) < 0.999999) {
			return angles.set((float) Math.atan2(m21, m22), yRot, (float) Math.atan2(m10, m00));
		}
		// gimbal lock: x folded into z
		return angles.set(0F, yRot, (float) Math.atan2(-m01, m11));
	}
}
