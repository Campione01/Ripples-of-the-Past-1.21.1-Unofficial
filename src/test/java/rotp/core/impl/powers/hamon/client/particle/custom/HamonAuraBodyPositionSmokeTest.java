package rotp.core.impl.powers.hamon.client.particle.custom;

import rotp.core.client.entityanim.pose.AnimFramePose;
import net.minecraft.world.phys.Vec3;

public final class HamonAuraBodyPositionSmokeTest {
	private HamonAuraBodyPositionSmokeTest() {}

	public static void run() {
		near(HamonAura3rdPersonParticle.getBodyPos((AnimFramePose) null, 0), Vec3.ZERO, "missing pose");
		AnimFramePose pose = new AnimFramePose();
		near(HamonAura3rdPersonParticle.getBodyPos(pose, 0), Vec3.ZERO, "missing body");
		pose.getForModelPart("body").positionOffset.set(4, -8, 12);
		near(HamonAura3rdPersonParticle.getBodyPos(pose, 0), new Vec3(.25, .5, -.75), "full pose");
		near(HamonAura3rdPersonParticle.getBodyPos(pose, 90), new Vec3(.75, .5, .25), "body yaw");
		pose.blendWeight = .125F;
		near(HamonAura3rdPersonParticle.getBodyPos(pose, 0), new Vec3(.03125, .0625, -.09375), "fade weight");
		near(HamonAura3rdPersonParticle.getBodyPos(pose, 90), new Vec3(.09375, .0625, .03125), "faded yaw");
		near(HamonAura3rdPersonParticle.getBodyPos(pose, 90), new Vec3(.09375, .0625, .03125), "repeated read");
		pose.blendWeight = 0;
		near(HamonAura3rdPersonParticle.getBodyPos(pose, 45), Vec3.ZERO, "expired pose");
		pose.blendWeight = 1;
		near(HamonAura3rdPersonParticle.getBodyPos(pose, 0), new Vec3(.25, .5, -.75), "shared offsets remain unchanged");
	}

	private static void near(Vec3 actual, Vec3 expected, String label) {
		if (!Double.isFinite(actual.lengthSqr()) || actual.distanceToSqr(expected) > 1.0E-12) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}
}
