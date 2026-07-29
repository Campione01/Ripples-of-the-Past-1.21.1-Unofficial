package com.github.standobyte.jojo.api.gravity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DirectionalGravityV2SmokeTest {
	private static final double EPSILON = 1.0E-9;

	private DirectionalGravityV2SmokeTest() {}

	public static void main(String[] args) {
		run();
		System.out.println(
				"Directional gravity v2 focused smoke passed.");
	}

	public static void run() {
		verifyV1SourceCompatibility();
		verifyElytraLook(Direction.NORTH);
		verifyElytraLook(Direction.EAST);
		verifyFlowingWater(Direction.NORTH);
		verifyFlowingWater(Direction.EAST);
		verifyLandingAdapter();
		verifyLandingContactPosition();
		verifyRelativeFloatingThreshold();
		verifyPassengerAndCamera();
		verifyWorldSpaceNetworkBoundary();
		verifyHookContract();
	}

	private static void verifyV1SourceCompatibility() {
		try {
			DirectionalGravityApi.class.getDeclaredMethod(
					"bind", LivingEntity.class,
					ResourceLocation.class,
					DirectionalGravitySource.class);
			DirectionalGravityApi.class.getDeclaredMethod(
					"bind", LivingEntity.class,
					ResourceLocation.class, int.class,
					DirectionalGravitySource.class);
			DirectionalGravityApi.class.getDeclaredMethod(
					"directionChanged", LivingEntity.class,
					ResourceLocation.class,
					DirectionalGravitySource.class);
			DirectionalGravityApi.class.getDeclaredMethod(
					"unbind", LivingEntity.class,
					ResourceLocation.class,
					DirectionalGravitySource.class);
			DirectionalGravityApi.class.getDeclaredMethod(
					"getDirection",
					net.minecraft.world.entity.Entity.class);
			DirectionalGravityApi.class.getDeclaredMethod(
					"getEffectiveDirection",
					net.minecraft.world.entity.Entity.class);
		}
		catch (NoSuchMethodException exception) {
			throw new AssertionError(
					"directional gravity v1 source surface changed",
					exception);
		}
	}

	private static void verifyElytraLook(Direction direction) {
		Vec3 localLook = new Vec3(0.42, -0.31, 0.853)
				.normalize();
		Vec3 localVelocity = new Vec3(0.18, -0.12, 0.64);
		Vec3 worldLook = DirectionalGravityTransforms.toWorld(
				direction, localLook);
		checkClose(DirectionalGravityTransforms.toLocal(
				direction, worldLook), localLook,
				direction + " elytra look must enter local once");

		Vec3 localResult = elytraStep(
				localVelocity, localLook, -0.35F, 0.08);
		Vec3 worldResult = DirectionalGravityTransforms.toWorld(
				direction, localResult);
		checkClose(DirectionalGravityTransforms.toLocal(
				direction, worldResult), localResult,
				direction + " elytra result must leave local once");

		Vec3 doubleRotatedLook =
				DirectionalGravityTransforms.toWorld(
						direction, worldLook);
		Vec3 wrong = elytraStep(
				localVelocity, doubleRotatedLook,
				-0.35F, 0.08);
		check(localResult.distanceToSqr(wrong) > 1.0E-6,
				direction
						+ " fixture must detect a double look transform");
	}

	private static Vec3 elytraStep(Vec3 velocity, Vec3 look,
			float pitch, double gravity) {
		double lookHorizontal =
				Math.sqrt(look.x * look.x + look.z * look.z);
		double speedHorizontal = velocity.horizontalDistance();
		double lookLength = look.length();
		double lift = Math.cos(pitch);
		lift = lift * lift * Math.min(1.0, lookLength / 0.4);
		Vec3 result = velocity.add(
				0.0, gravity * (-1.0 + lift * 0.75), 0.0);
		if (result.y < 0.0 && lookHorizontal > 0.0) {
			double dive = result.y * -0.1 * lift;
			result = result.add(
					look.x * dive / lookHorizontal,
					dive,
					look.z * dive / lookHorizontal);
		}
		if (pitch < 0.0F && lookHorizontal > 0.0) {
			double climb = speedHorizontal
					* -Math.sin(pitch) * 0.04;
			result = result.add(
					-look.x * climb / lookHorizontal,
					climb * 3.2,
					-look.z * climb / lookHorizontal);
		}
		return result.multiply(0.99, 0.98, 0.99);
	}

	private static void verifyFlowingWater(Direction direction) {
		Vec3 worldVelocity = new Vec3(0.12, -0.04, 0.08);
		Vec3 worldCurrent = new Vec3(0.03, 0.0, -0.02);
		Vec3 pushedWorld = worldVelocity.add(worldCurrent);
		Vec3 localAtTravel =
				DirectionalGravityTransforms.toLocal(
						direction, pushedWorld);
		Vec3 expectedLocal =
				DirectionalGravityTransforms.toLocal(
						direction, worldVelocity)
				.add(DirectionalGravityTransforms.toLocal(
						direction, worldCurrent));
		checkClose(localAtTravel, expectedLocal,
				direction
						+ " flowing-water push must enter travel in local");
		checkClose(DirectionalGravityTransforms.toWorld(
				direction, localAtTravel), pushedWorld,
				direction
						+ " flowing-water push must restore world velocity");
	}

	private static void verifyLandingAdapter() {
		for (Direction direction : Direction.values()) {
			Vec3 localImpact = new Vec3(0.2, -0.75, -0.1);
			Vec3 worldImpact =
					DirectionalGravityTransforms.toWorld(
							direction, localImpact);
			Vec3 localResponse =
					DirectionalGravityTransforms.toLocal(
							direction, worldImpact);
			localResponse = new Vec3(
					localResponse.x,
					-localResponse.y * 0.8,
					localResponse.z);
			Vec3 worldResponse =
					DirectionalGravityTransforms.toWorld(
							direction, localResponse);
			checkClose(DirectionalGravityTransforms.toLocal(
					direction, worldResponse),
					new Vec3(0.2, 0.6, -0.1),
					direction
							+ " block bounce must use local vertical");
		}
	}

	private static void verifyLandingContactPosition() {
		Vec3 position = new Vec3(10.05, 64.05, -3.05);
		BlockPos globalDown =
				DirectionalGravityTransforms.floorBlockPos(
						Direction.DOWN, position, 0.2);
		for (Direction direction : Direction.values()) {
			BlockPos expected = BlockPos.containing(
					position.add(Vec3.atLowerCornerOf(
							direction.getNormal()).scale(0.2)));
			BlockPos actual =
					DirectionalGravityTransforms.floorBlockPos(
							direction, position, 0.2);
			check(actual.equals(expected),
					direction
							+ " landing contact must follow gravity");
			if (direction != Direction.DOWN) {
				check(!actual.equals(globalDown),
						direction
								+ " landing contact used global down");
			}
		}
	}

	private static void verifyRelativeFloatingThreshold() {
		Vec3 eastLocalResidual = new Vec3(0.0, 0.0, 0.1);
		Vec3 eastWorldResidual =
				DirectionalGravityTransforms.toWorld(
						Direction.EAST, eastLocalResidual);
		check(eastWorldResidual.y < -0.03125
				&& eastLocalResidual.y >= -0.03125,
				"floating fixture must reject a world-Y gate");

		Vec3 northLocalResidual = new Vec3(0.0, -0.75, 0.0);
		Vec3 northWorldResidual =
				DirectionalGravityTransforms.toWorld(
						Direction.NORTH, northLocalResidual);
		check(northWorldResidual.y >= -0.03125
				&& northLocalResidual.y < -0.03125,
				"floating fixture must require relative vertical");
	}

	private static void verifyPassengerAndCamera() {
		for (Direction direction : Direction.values()) {
			Vec3 vehiclePosition = new Vec3(4.0, 70.0, -2.0);
			Vec3 localAttachment = new Vec3(0.0, 0.65, 0.0);
			Vec3 worldAttachment =
					DirectionalGravityTransforms.toWorld(
							direction, localAttachment);
			Vec3 passengerPosition =
					vehiclePosition.subtract(worldAttachment);
			checkClose(passengerPosition.add(worldAttachment),
					vehiclePosition,
					direction
							+ " rider attachment must not move vehicle");

			Vec3 cameraUp = DirectionalGravityTransforms.toWorld(
					direction, new Vec3(0.0, 1.0, 0.0));
			Vec3 cameraForward =
					DirectionalGravityTransforms.toWorld(
							direction,
							new Vec3(0.0, 0.0, -1.0));
			checkClose(cameraUp.dot(cameraForward), 0.0,
					direction + " camera basis");
		}
	}

	private static void verifyWorldSpaceNetworkBoundary() {
		for (Direction direction
				: new Direction[] {Direction.NORTH, Direction.EAST}) {
			Vec3 start = new Vec3(10.0, 64.0, -3.0);
			Vec3 localMovement = new Vec3(0.15, 0.4, -0.2);
			Vec3 worldMovement =
					DirectionalGravityTransforms.toWorld(
							direction, localMovement);
			Vec3 packetPosition = start.add(worldMovement);
			Vec3 serverWorldDelta = packetPosition.subtract(start);
			checkClose(serverWorldDelta, worldMovement,
					direction
							+ " packet delta must remain world-space");
			checkClose(DirectionalGravityTransforms.toLocal(
					direction, serverWorldDelta),
					localMovement,
					direction
							+ " server must only project validation components");
		}
	}

	private static void verifyHookContract() {
		String entity = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "directional_gravity/"
						+ "EntityDirectionalGravityMixin.java");
		String living = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "directional_gravity/"
						+ "LivingEntityDirectionalGravityMixin.java");
		String player = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "directional_gravity/"
						+ "PlayerDirectionalGravityMixin.java");
		String localPlayer = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "client/directional_gravity/"
						+ "LocalPlayerDirectionalGravityMixin.java");
		String server = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "directional_gravity/"
						+ "ServerGamePacketListenerDirectionalGravityMixin.java");
		String serverPlayer = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "directional_gravity/"
						+ "ServerPlayerDirectionalGravityMixin.java");
		String runtime = source(
				"src/main/java/com/github/standobyte/jojo/"
						+ "subsystems/directional_gravity/"
						+ "DirectionalGravityRuntime.java");
		String camera = source(
				"src/main/java/com/github/standobyte/jojo/mixin/"
						+ "client/directional_gravity/"
						+ "CameraDirectionalGravityMixin.java");

		require(entity, "method = \"makeBoundingBox\"");
		require(entity, "method = \"calculateViewVector\"");
		require(entity, "method = \"getVehicleAttachmentPoint\"");
		require(entity, "@WrapMethod(method = \"move\")");
		require(entity, "method = \"collide\"");
		require(entity, "method = \"checkSupportingBlock\"");
		require(entity,
				"jojo_ripples$directionalGravityCollisionVelocity");
		check(!entity.contains(
				"stopCollidedVelocity("
						+ "entity.getDeltaMovement(), collision)"),
				"landing impact velocity was cleared before bounce");
		require(entity, "updateEntityAfterFallOn");
		require(entity, "fallOn");
		require(living, "@WrapMethod(method = \"travel\")");
		require(living,
				"localFrameDirection(living)");
		require(living, "method = \"jumpFromGround\"");
		require(living, "jumpInFluid");
		require(player, "@WrapMethod(method = \"travel\")");
		require(player, "method = \"maybeBackOffFromEdge\"");
		require(localPlayer, "method = \"aiStep\"");
		check(!localPlayer.contains("sendPosition"),
				"LocalPlayer packet emission must remain unmodified");
		require(server, "method = \"handleMovePlayer\"");
		require(server, "toLocal");
		require(server, "@ModifyConstant");
		require(server, "Double.NEGATIVE_INFINITY");
		require(server,
				"localResidual > -0.5 && localResidual < 0.5");
		require(serverPlayer, "method = \"doCheckFallDamage\"");
		require(serverPlayer,
				"@Inject(method = \"doCheckFallDamage\", "
						+ "at = @At(\"HEAD\"))");
		require(serverPlayer,
				"jojo_ripples$directionalGravityServerFallY");
		require(serverPlayer,
				"jojo_ripples$directionalGravityServerFall(\n"
						+ "\t\t\tdouble vanillaY)");
		check(!serverPlayer.contains(
				"double vanillaY, double movementX"),
				"server fall @ModifyArg must use single-argument mode");
		require(runtime, "if (depth == 0)");
		require(runtime, "setLocalFrameDepth(depth + 1)");
		require(camera, "method = \"setup\"");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + path,
					exception);
		}
	}

	private static void require(String source, String snippet) {
		check(source.contains(snippet),
				"missing directional gravity hook: " + snippet);
	}

	private static void checkClose(Vec3 actual, Vec3 expected,
			String message) {
		checkClose(actual.x, expected.x, message + " x");
		checkClose(actual.y, expected.y, message + " y");
		checkClose(actual.z, expected.z, message + " z");
	}

	private static void checkClose(double actual, double expected,
			String message) {
		if (Math.abs(actual - expected) > EPSILON) {
			throw new AssertionError(message + ": expected "
					+ expected + ", got " + actual);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
