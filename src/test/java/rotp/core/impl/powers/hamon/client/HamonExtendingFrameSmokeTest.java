package rotp.core.impl.powers.hamon.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.phys.Vec3;

/**
 * The Satiporoja scarf, its binding and the Snake Muffler strand are drawn along the line from the entity back to the
 * origin point. 1.16.5 ExtendingEntityRenderer draws at the entity with the model flip (1, -1, -1), so the strip starts
 * at the entity and its far end is the origin point.
 */
public final class HamonExtendingFrameSmokeTest {
	private static final float TOLERANCE = 1.0E-3F;

	private HamonExtendingFrameSmokeTest() {}

	public static void main(String[] args) {
		run();
		System.out.println("Hamon extending frame: PASS");
	}

	public static void run() {
		Vec3[] extents = {
				new Vec3(5.5, 0.0, 2.0), new Vec3(-5.5, 0.0, 2.0), new Vec3(5.5, 0.0, -2.0), new Vec3(-5.5, 0.0, -2.0),
				new Vec3(5.5, 1.7, 2.0), new Vec3(-5.5, -1.7, 2.0), new Vec3(5.5, 1.7, -2.0), new Vec3(-5.5, -1.7, -2.0),
				new Vec3(3.0, 0.0, 0.0), new Vec3(-3.0, 0.0, 0.0), new Vec3(0.0, 0.0, 3.0), new Vec3(0.0, 0.0, -3.0),
				new Vec3(0.0, 4.0, 0.0), new Vec3(0.0, -4.0, 0.0), new Vec3(0.3, 4.0, -0.2), new Vec3(0.3, -4.0, 0.2) };
		for (Vec3 extent : extents) {
			float length = (float) extent.length();
			Vector3f start = HamonExtendingFrame.frame(extent).transformPosition(new Vector3f(0.0F, 0.0F, 0.0F));
			Vector3f end = HamonExtendingFrame.frame(extent).transformPosition(new Vector3f(0.0F, 0.0F, length));
			check(near(start, 0.0F, 0.0F, 0.0F),
					"extent " + extent + ": the strip must start at the entity, started at " + start);
			check(near(end, (float) -extent.x, (float) -extent.y, (float) -extent.z),
					"extent " + extent + ": the strip must end at the origin point " + extent.reverse() + ", ended at " + end);
			// the renderer applies the frame to its pose stack: it must be the same transform
			PoseStack poseStack = new PoseStack();
			HamonExtendingFrame.apply(poseStack, extent);
			Matrix4f pose = poseStack.last().pose();
			Vector3f drawnEnd = pose.transformPosition(new Vector3f(0.0F, 0.0F, length));
			check(near(pose.transformPosition(new Vector3f(0.0F, 0.0F, 0.0F)), 0.0F, 0.0F, 0.0F)
					&& near(drawnEnd, (float) -extent.x, (float) -extent.y, (float) -extent.z),
					"extent " + extent + ": the pose stack of the renderer draws the strip to " + drawnEnd);
			Vector3f up = pose.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
			Vector3f frameUp = HamonExtendingFrame.frame(extent).transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
			check(up.distance(frameUp) < TOLERANCE, "extent " + extent + ": the pose stack flips the strip differently from the frame");
		}
	}

	private static boolean near(Vector3f point, float x, float y, float z) {
		return Math.abs(point.x - x) < TOLERANCE && Math.abs(point.y - y) < TOLERANCE && Math.abs(point.z - z) < TOLERANCE;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
