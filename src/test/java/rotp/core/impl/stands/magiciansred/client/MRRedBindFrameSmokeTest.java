package rotp.core.impl.stands.magiciansred.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.phys.Vec3;

/**
 * The Red Bind strip is drawn from the bound entity back to the origin point. 1.16.5 ExtendingEntityRenderer draws at the
 * entity with the model flip (1, -1, -1) and RepeatingModel turns it by the yaw and pitch of (entity - origin), so the
 * strip starts at the entity and its +z run, one segment at a time, ends at the origin point. The kick finisher draws a
 * second strip from the other hand through the same frame.
 */
public final class MRRedBindFrameSmokeTest {
	private static final float TOLERANCE = 1.0E-3F;
	private static final float SEGMENT_LENGTH = 3.0F / 16.0F;
	private static final Vector3f[] MODEL_AXES = {
			new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F) };

	private MRRedBindFrameSmokeTest() {}

	public static void main(String[] args) {
		run();
		System.out.println("Red Bind strip frame: PASS");
	}

	public static void run() {
		testStripRunsFromTheEntityToTheOrigin();
		testRendererDrawsBothStripsThroughTheFrame();
	}

	private static void testStripRunsFromTheEntityToTheOrigin() {
		for (Vec3 extent : extents()) {
			float length = (float) extent.length();
			Vector3f origin = new Vector3f((float) -extent.x, (float) -extent.y, (float) -extent.z);
			PoseStack poseStack = new PoseStack();
			MRRedBindFrame.apply(poseStack, extent);
			Matrix4f pose = new Matrix4f(poseStack.last().pose());
			Vector3f start = pose.transformPosition(new Vector3f(0.0F, 0.0F, 0.0F));
			check(start.length() < TOLERANCE, "extent " + extent + ": the strip must start at the entity, started at " + start);
			Vector3f end = pose.transformPosition(new Vector3f(0.0F, 0.0F, length));
			check(end.distance(origin) < TOLERANCE,
					"extent " + extent + ": the strip must end at the origin point " + origin + ", ended at " + end);

			// the renderer moves the pose along +z by one segment for as long as the rest is at least one segment
			float remaining = length;
			while (remaining >= SEGMENT_LENGTH) {
				remaining -= SEGMENT_LENGTH;
				poseStack.translate(0, 0, SEGMENT_LENGTH);
			}
			Vector3f lastSegment = poseStack.last().pose().transformPosition(new Vector3f(0.0F, 0.0F, 0.0F));
			check(lastSegment.distance(origin) < SEGMENT_LENGTH + TOLERANCE,
					"extent " + extent + ": the last segment must sit within one segment of the origin point " + origin + ", sits at " + lastSegment);

			for (Vector3f axis : MODEL_AXES) {
				Vector3f drawn = pose.transformDirection(new Vector3f(axis));
				Vector3f donor = donorDirection(extent, axis);
				check(drawn.distance(donor) < TOLERANCE,
						"extent " + extent + ": the model axis " + axis + " is drawn along " + drawn + ", 1.16.5 draws it along " + donor);
			}
		}
	}

	private static void testRendererDrawsBothStripsThroughTheFrame() {
		String renderer = read(Path.of(System.getProperty("user.dir")).resolve(
				"src/main/java/rotp/core/impl/stands/magiciansred/client/MRRedBindRenderer.java"));
		int start = renderer.indexOf("private void renderRedBind(");
		int end = renderer.indexOf("private Vec3 getOriginPos(");
		check(start >= 0 && end > start, "renderRedBind moved: update this contract");
		String draw = renderer.substring(start, end);
		check(draw.contains("MRRedBindFrame.apply(poseStack, extentVec);") && !draw.contains(".translate("),
				"a Red Bind strip must be drawn at the entity through the frame, not moved to the origin point");
		check(renderer.contains("renderRedBind(entity, partialTick, poseStack, buffer, false, flameLight, alpha);")
				&& renderer.contains("renderRedBind(entity, partialTick, poseStack, buffer, true, flameLight, alpha);"),
				"the kick finisher's second strip must be drawn through the same frame as the first");
	}

	private static List<Vec3> extents() {
		List<Vec3> extents = new ArrayList<>();
		for (double x : new double[] { 5.5, -5.5 }) {
			for (double z : new double[] { 2.0, -2.0 }) {
				for (double y : new double[] { 0.0, 1.7, -1.7 }) {
					extents.add(new Vec3(x, y, z));
				}
			}
		}
		extents.addAll(List.of(new Vec3(3.0, 0.0, 0.0), new Vec3(-3.0, 0.0, 0.0), new Vec3(0.0, 0.0, 3.0), new Vec3(0.0, 0.0, -3.0),
				new Vec3(0.0, 4.0, 0.0), new Vec3(0.0, -4.0, 0.0), new Vec3(0.3, 4.0, -0.2), new Vec3(-0.3, -4.0, 0.2),
				new Vec3(0.1, 0.05, -0.05), new Vec3(-7.25, 3.1, 4.4)));
		return extents;
	}

	// 1.16.5 as plain matrix calls: a model direction goes through XP(pitch), then YP(yaw), then the flip (1, -1, -1)
	private static Vector3f donorDirection(Vec3 extent, Vector3f direction) {
		double yaw = -Math.atan2(extent.x, extent.z);
		double pitch = -Math.atan2(extent.y, Math.sqrt(extent.x * extent.x + extent.z * extent.z));
		double y1 = direction.y * Math.cos(pitch) - direction.z * Math.sin(pitch);
		double z1 = direction.y * Math.sin(pitch) + direction.z * Math.cos(pitch);
		double x2 = direction.x * Math.cos(yaw) + z1 * Math.sin(yaw);
		double z2 = -direction.x * Math.sin(yaw) + z1 * Math.cos(yaw);
		return new Vector3f((float) x2, (float) -y1, (float) -z2);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
