package rotp.core.client.entityrender.entities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The trail of a Tommy Gun bullet against the 1.16.5 TommyGunBulletRenderer: every expected vertex is the donor's
 * literal vertex put through the donor's own matrix calls (trailSegment, renderSide, renderFront).
 */
public final class TommyGunTrailShellSmokeTest {
	private static final float V1 = 0.015625F;
	private static final float LENGTH = 3.0F;
	private static final float U0 = 0.25F;
	private static final float U1 = 0.75F;

	private TommyGunTrailShellSmokeTest() {}

	public static void run() {
		testSidesFormTheDonorShell();
		testFrontCapIsDrawnFromBothFaces();
		testRendererContract();
	}

	private static void testSidesFormTheDonorShell() {
		List<float[]> actual = emit(false);
		List<float[]> expected = donorSides();
		for (int i = 0; i < actual.size(); i++) {
			float[] vertex = actual.get(i);
			check(Math.abs(Math.abs(vertex[1]) - 1.0F) < 1.0E-6F && Math.abs(Math.abs(vertex[2]) - 1.0F) < 1.0E-6F,
					"trail side vertex " + i + " " + text(vertex) + " is not on the donor's square shell, whose sides sit"
							+ " one unit off the trail axis");
		}
		check(actual.size() == expected.size(), "the trail must draw four sides from both faces, " + expected.size()
				+ " vertices, but draws " + actual.size());
		for (int i = 0; i < expected.size(); i++) {
			check(same(actual.get(i), expected.get(i)), "trail side vertex " + i + " is " + text(actual.get(i))
					+ ", the donor draws " + text(expected.get(i)));
		}
	}

	private static void testFrontCapIsDrawnFromBothFaces() {
		List<float[]> actual = emit(true);
		List<float[]> expected = donorFront();
		expected.addAll(donorSides());
		check(actual.size() == expected.size(), "the first trail segment must add the donor's two-faced front cap, "
				+ expected.size() + " vertices in all, but draws " + actual.size());
		for (int i = 0; i < expected.size(); i++) {
			check(same(actual.get(i), expected.get(i)), "first segment vertex " + i + " is " + text(actual.get(i))
					+ ", the donor draws " + text(expected.get(i)));
		}
	}

	private static void testRendererContract() {
		String renderer = read(Path.of(System.getProperty("user.dir")).resolve(
				"src/main/java/rotp/core/client/entityrender/entities/TommyGunBulletRenderer.java"));
		check(renderer.contains("TommyGunTrailShell.emit("), "the renderer must draw the trail through the tested shell");
		check(renderer.contains("public boolean shouldRender(TommyGunBulletEntity entity, Frustum")
				&& renderer.contains("super.shouldRender(")
				&& renderer.contains("frustum.isVisible(new AABB(entity.initialPos, entity.position()))"),
				"1.16: a bullet is drawn while the box from its first position to its head is on screen, not only while"
						+ " its head is");
		// the donor's helper swaps y and z of the normal it is given and the side turns then leave world-up on
		// every vertex; the segment pose must not turn it
		check(renderer.contains(".setNormal(0.0F, 1.0F, 0.0F)") && !renderer.contains("setNormal(pose"),
				"1.16: every trail vertex is lit as world-up, whatever way the segment points");
	}

	private static List<float[]> emit(boolean front) {
		List<float[]> vertices = new ArrayList<>();
		TommyGunTrailShell.emit(LENGTH, U0, U1, front, (x, y, z, u, v) -> vertices.add(new float[] { x, y, z, u, v }));
		return vertices;
	}

	// 1.16 renderSide, called four times: mulPose(XP 90) on the segment's pose, then translate(0, 0, 1) and the
	// quad; mulPose(XP 180) and the same quad again
	private static List<float[]> donorSides() {
		float[][] quad = {
				{ 0.0F, -1.0F, 0.0F, U1, 0.0F },
				{ LENGTH, -1.0F, 0.0F, U0, 0.0F },
				{ LENGTH, 1.0F, 0.0F, U0, V1 },
				{ 0.0F, 1.0F, 0.0F, U1, V1 } };
		List<float[]> vertices = new ArrayList<>();
		for (int side = 1; side <= 4; side++) {
			for (int face = 0; face < 2; face++) {
				for (float[] corner : quad) {
					double[] pos = { corner[0], corner[1], corner[2] };
					if (face == 1) {
						pos = aboutX(pos, 180.0D);
					}
					pos[2] += 1.0D;
					pos = aboutX(pos, 90.0D * side);
					vertices.add(new float[] { (float) pos[0], (float) pos[1], (float) pos[2], corner[3], corner[4] });
				}
			}
		}
		return vertices;
	}

	// 1.16 renderFront: mulPose(YP 90) and the quad; mulPose(XP 180) and the same quad again
	private static List<float[]> donorFront() {
		float[][] quad = {
				{ -1.0F, -1.0F, 0.0F, V1, V1 },
				{ 1.0F, -1.0F, 0.0F, 0.0F, V1 },
				{ 1.0F, 1.0F, 0.0F, 0.0F, V1 * 2.0F },
				{ -1.0F, 1.0F, 0.0F, V1, V1 * 2.0F } };
		List<float[]> vertices = new ArrayList<>();
		for (int face = 0; face < 2; face++) {
			for (float[] corner : quad) {
				double[] pos = { corner[0], corner[1], corner[2] };
				if (face == 1) {
					pos = aboutX(pos, 180.0D);
				}
				pos = aboutY(pos, 90.0D);
				vertices.add(new float[] { (float) pos[0], (float) pos[1], (float) pos[2], corner[3], corner[4] });
			}
		}
		return vertices;
	}

	private static double[] aboutX(double[] pos, double degrees) {
		double sin = Math.sin(Math.toRadians(degrees));
		double cos = Math.cos(Math.toRadians(degrees));
		return new double[] { pos[0], pos[1] * cos - pos[2] * sin, pos[1] * sin + pos[2] * cos };
	}

	private static double[] aboutY(double[] pos, double degrees) {
		double sin = Math.sin(Math.toRadians(degrees));
		double cos = Math.cos(Math.toRadians(degrees));
		return new double[] { pos[0] * cos + pos[2] * sin, pos[1], -pos[0] * sin + pos[2] * cos };
	}

	private static boolean same(float[] actual, float[] expected) {
		for (int i = 0; i < expected.length; i++) {
			if (Math.abs(actual[i] - expected[i]) > 1.0E-6F) {
				return false;
			}
		}
		return true;
	}

	private static String text(float[] vertex) {
		return "(" + vertex[0] + ", " + vertex[1] + ", " + vertex[2] + ") uv (" + vertex[3] + ", " + vertex[4] + ")";
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException exception) {
			throw new IllegalStateException("could not read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
