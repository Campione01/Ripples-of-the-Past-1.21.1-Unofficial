package com.github.standobyte.jojo.api.client.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public final class ScopedPlayerModelVisibilitySmokeTest {
	private ScopedPlayerModelVisibilitySmokeTest() {}

	public static void run() {
		PlayerModel<?> model = new PlayerModel<>(
				LayerDefinition.create(
						PlayerModel.createMesh(
								CubeDeformation.NONE, false),
						64, 64)
						.bakeRoot(),
				false);
		model.hat.visible = false;
		model.rightSleeve.visible = false;

		ScopedPlayerModelVisibility.VisibilitySnapshot snapshot =
				ScopedPlayerModelVisibility.VisibilitySnapshot
						.capture(model);
		for (ScopedPlayerModelVisibility.Part part
				: ScopedPlayerModelVisibility.Part.values()) {
			part.setVisible(model, false);
		}
		snapshot.restore(model);

		check(model.head.visible, "head visibility was not restored");
		check(!model.hat.visible, "pre-hidden hat was not restored");
		check(model.leftArm.visible,
				"left arm visibility was not restored");
		check(!model.rightSleeve.visible,
				"pre-hidden right sleeve was not restored");
		check(model.leftLeg.visible,
				"left leg visibility was not restored");
		check(model.rightPants.visible,
				"right pants visibility was not restored");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
