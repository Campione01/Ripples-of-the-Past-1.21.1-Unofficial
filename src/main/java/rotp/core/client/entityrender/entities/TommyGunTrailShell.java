package rotp.core.client.entityrender.entities;

/**
 * One segment of the bullet trail in the segment's own frame: x runs back along the trail from its newer end,
 * y and z span the beam. As in the 1.16 TommyGunBulletRenderer it is a square shell whose four sides sit one unit
 * off the axis, with a cap on the newest end, and every quad is drawn from both faces.
 */
final class TommyGunTrailShell {
	static final float V1 = 0.015625F;

	@FunctionalInterface
	interface VertexSink {
		void vertex(float x, float y, float z, float u, float v);
	}

	private TommyGunTrailShell() {}

	static void emit(float length, float u0, float u1, boolean front, VertexSink sink) {
		if (front) {
			for (float face = 1.0F; face >= -1.0F; face -= 2.0F) {
				sink.vertex(0.0F, -face, 1.0F, V1, V1);
				sink.vertex(0.0F, -face, -1.0F, 0.0F, V1);
				sink.vertex(0.0F, face, -1.0F, 0.0F, V1 * 2.0F);
				sink.vertex(0.0F, face, 1.0F, V1, V1 * 2.0F);
			}
		}
		for (int turns = 1; turns <= 4; turns++) {
			for (float face = 1.0F; face >= -1.0F; face -= 2.0F) {
				side(sink, turns, 0.0F, -face, u1, 0.0F);
				side(sink, turns, length, -face, u0, 0.0F);
				side(sink, turns, length, face, u0, V1);
				side(sink, turns, 0.0F, face, u1, V1);
			}
		}
	}

	// the point (x, y, 1) after that many quarter turns about the trail axis
	private static void side(VertexSink sink, int turns, float x, float y, float u, float v) {
		switch (turns & 3) {
			case 1 -> sink.vertex(x, -1.0F, y, u, v);
			case 2 -> sink.vertex(x, -y, -1.0F, u, v);
			case 3 -> sink.vertex(x, 1.0F, -y, u, v);
			default -> sink.vertex(x, y, 1.0F, u, v);
		}
	}
}
