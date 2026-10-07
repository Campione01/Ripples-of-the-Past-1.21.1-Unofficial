package rotp.core.client.entityanim.barrage;

/**
 * The roll an afterimage arm gets from its random spread offset. 1.16 applied it to Stand arms only
 * (StandTwoHandedBarrageAnimation.animateSwing); a player's afterimage arm went through
 * ArmsBarrageAnimation.animateSwing, which drops the offset.
 * No client classes here, so server gametests can run it.
 */
public final class AfterimageArmRoll {
	private AfterimageArmRoll() {}

	/** Radians to add to the arm's zRot. */
	public static float of(boolean playerModel, float swingAmount, float rollOffset) {
		return playerModel ? 0.0F : swingAmount * rollOffset;
	}
}
