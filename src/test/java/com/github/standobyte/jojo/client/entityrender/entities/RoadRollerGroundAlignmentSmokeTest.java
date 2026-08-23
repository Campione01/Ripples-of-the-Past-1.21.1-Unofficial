package com.github.standobyte.jojo.client.entityrender.entities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RoadRollerGroundAlignmentSmokeTest {
	private static final float MODEL_PIXELS_PER_BLOCK = 16.0F;
	private static final float MAX_UPRIGHT_GROUND_CLEARANCE = 1.0F / MODEL_PIXELS_PER_BLOCK;

	private RoadRollerGroundAlignmentSmokeTest() {}

	public static void run() {
		testPhysicalBoundsSettleAtEntityY();
		testUprightWheelBottomMatchesEntityY();
		testRendererAndItemOwnershipContract();
	}

	private static void testPhysicalBoundsSettleAtEntityY() {
		EntityDimensions dimensions = EntityDimensions.fixed(4.0F, 2.0F);
		AABB bounds = dimensions.makeBoundingBox(Vec3.ZERO);
		check(bounds.minY == 0.0D && bounds.maxY == 2.0D,
				"the Road Roller entity position must remain the physical AABB bottom");
	}

	private static void testUprightWheelBottomMatchesEntityY() {
		ModelPart roadRoller = RoadRollerModel.createBodyLayer().bakeRoot().getChild("road_roller");
		ModelPart frontWheel = roadRoller.getChild("front_wheel");
		float wheelBottomModelY = roadRoller.y + frontWheel.y
				+ frontWheel.cubes.getFirst().maxY;
		float wheelBottomAboveEntityY = -wheelBottomModelY / MODEL_PIXELS_PER_BLOCK;
		check(wheelBottomAboveEntityY >= 0.0F
				&& wheelBottomAboveEntityY <= MAX_UPRIGHT_GROUND_CLEARANCE,
				"the upright front wheel must meet the AABB ground plane within one model pixel");
		check(wheelBottomAboveEntityY + 1.0F > 1.0F,
				"the generic half-height renderer offset would reproduce the visible one-block float");
	}

	private static void testRendererAndItemOwnershipContract() {
		Path root = Path.of(System.getProperty("user.dir"));
		String renderer = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/entityrender/entities/"
						+ "RoadRollerRenderer.java"));
		check(renderer.contains("offsetModelByEntityHeight(false);"),
				"the entity renderer must not add the generic half-height model offset");

		String entity = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/customobjects/RoadRollerEntity.java"));
		check(entity.contains("move(MoverType.SELF, getDeltaMovement());")
				&& entity.contains("if (onGround())")
				&& entity.contains("float damage = (float) -getDeltaMovement().y * 10.0F;"),
				"falling, collision settlement, and landing damage must remain entity-owned");

		String itemRenderer = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/client/itemrender/"
						+ "RoadRollerItemRenderer.java"));
		check(itemRenderer.contains("poseStack.scale(1.0F, -1.0F, -1.0F);")
				&& !itemRenderer.contains("offsetModelByEntityHeight"),
				"the entity-only ground correction must not alter item presentation");
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
