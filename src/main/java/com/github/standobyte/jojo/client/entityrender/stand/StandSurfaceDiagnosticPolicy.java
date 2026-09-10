package com.github.standobyte.jojo.client.entityrender.stand;

final class StandSurfaceDiagnosticPolicy {
	static final String TARGET_PROPERTY = "rotp.standSurfaceDiagnosticTarget";

	private StandSurfaceDiagnosticPolicy() {}

	static String configuredTarget() {
		return System.getProperty(TARGET_PROPERTY, "");
	}

	static boolean useNearestSurface(String targetEntityTypeId, String entityTypeId, float alpha,
			boolean bodyVisible, boolean maskCapture, boolean classicObstruction,
			boolean afterimage, boolean barrageSwings) {
		return useNearestSurface(targetEntityTypeId, entityTypeId, alpha, bodyVisible,
				maskCapture, classicObstruction, afterimage, barrageSwings, false);
	}

	static boolean useNearestSurface(String targetEntityTypeId, String entityTypeId, float alpha,
			boolean bodyVisible, boolean maskCapture, boolean classicObstruction,
			boolean afterimage, boolean barrageSwings, boolean separateBarrageStream) {
		return targetEntityTypeId != null && !targetEntityTypeId.isEmpty()
				&& targetEntityTypeId.equals(entityTypeId)
				&& alpha > 0.0F && alpha < 1.0F
				&& bodyVisible && !maskCapture && !classicObstruction
				&& !afterimage && (!barrageSwings || separateBarrageStream);
	}
}
