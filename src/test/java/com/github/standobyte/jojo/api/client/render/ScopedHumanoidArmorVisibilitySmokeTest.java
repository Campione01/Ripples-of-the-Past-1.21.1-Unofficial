package com.github.standobyte.jojo.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public final class ScopedHumanoidArmorVisibilitySmokeTest {
	private ScopedHumanoidArmorVisibilitySmokeTest() {}

	public static void run() {
		PlayerModel<?> model = new PlayerModel<>(
				LayerDefinition.create(
						PlayerModel.createMesh(
								CubeDeformation.NONE, false),
						64, 64)
						.bakeRoot(),
				false);
		model.hat.visible = false;
		model.rightArm.visible = false;

		ScopedHumanoidArmorVisibility.VisibilitySnapshot snapshot =
				ScopedHumanoidArmorVisibility.VisibilitySnapshot
						.capture(model);
		for (ScopedHumanoidArmorVisibility.Part part
				: ScopedHumanoidArmorVisibility.Part.values()) {
			part.setVisible(model, false);
		}
		snapshot.restore(model);

		check(model.head.visible,
				"armor head visibility was not restored");
		check(!model.hat.visible,
				"pre-hidden armor hat was not restored");
		check(model.body.visible,
				"armor body visibility was not restored");
		check(model.leftArm.visible,
				"armor left arm visibility was not restored");
		check(!model.rightArm.visible,
				"pre-hidden armor right arm was not restored");
		check(model.leftLeg.visible && model.rightLeg.visible,
				"armor leg visibility was not restored");

		String mixin = read(Path.of(System.getProperty("user.dir"))
				.resolve("src/main/java/com/github/standobyte/jojo/"
						+ "mixin/client/render/"
						+ "HumanoidArmorLayerAddonVisibilityMixin.java"));
		check(mixin.contains("setPartVisibility(")
						&& mixin.contains("shift = At.Shift.AFTER")
						&& mixin.contains(
								"applyForArmorPiece(entity, armorModel)")
						&& mixin.contains("@At(\"RETURN\")")
						&& mixin.contains(
								"restoreAfterArmorPiece(armorModel)"),
				"armor-layer mutation/restore hook is incomplete");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
