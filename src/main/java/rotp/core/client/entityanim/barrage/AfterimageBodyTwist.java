package rotp.core.client.entityanim.barrage;

import org.joml.Quaternionf;

/**
 * 1.16 KosmXPlayerBarrageAfterimagesAnim: the afterimage arms of a player barrage are drawn without the body's
 * current twist (beforeSwingsRender), each with the twist the body has at its own swing's clip tick
 * (KosmXPlayerBarrageAnim.rotateBody). Yaws are the 'body' bone's yRot in radians.
 * No client classes here, so server gametests can run it.
 */
public final class AfterimageBodyTwist {
	private AfterimageBodyTwist() {}

	/** Once for all afterimages, in the frame the body bone has turned, before an arm is moved to its offset. */
	public static Quaternionf leaveCurrent(float currentBodyYaw) {
		return new Quaternionf().rotationY(-currentBodyYaw);
	}

	/** Per afterimage arm, after it is moved to its offset. */
	public static Quaternionf takeSwing(float swingBodyYaw) {
		return new Quaternionf().rotationY(swingBodyYaw);
	}
}
