package com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.mob;

import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.mob.HardcodedMobControlCommands.CommandType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class MobControlPayloadContractSmokeTest {
	private MobControlPayloadContractSmokeTest() {}

	public static void run() {
		ClMobControlMovementPacket legal =
				new ClMobControlMovementPacket(7, 10.0D, 64.0D, -10.0D,
						30.0F, -45.0F, true);
		check(!ClMobControlMovementPacket.Handler.isInvalid(legal),
				"legal controlled-mob position and rotation must be accepted");
		check(!ClMobControlMovementPacket.Handler.isMovementTooFast(4.0D, 0.0D),
				"normal controlled-mob movement must be accepted");
		check(!ClMobControlMovementPacket.Handler.isMovementTooFast(100.0D, 0.0D),
				"movement exactly at the accepted threshold must remain valid");

		ClMobControlMovementPacket nonFinite =
				new ClMobControlMovementPacket(7, Double.NaN, 64.0D, 0.0D,
						0.0F, 0.0F, false);
		check(ClMobControlMovementPacket.Handler.isInvalid(nonFinite),
				"non-finite controlled-mob position must be rejected");
		ClMobControlMovementPacket outsideWorld =
				new ClMobControlMovementPacket(7, 30_000_001.0D, 64.0D, 0.0D,
						0.0F, 0.0F, false);
		check(ClMobControlMovementPacket.Handler.isInvalid(outsideWorld),
				"controlled-mob position outside the world boundary must be rejected");
		check(ClMobControlMovementPacket.Handler.isMovementTooFast(100.01D, 0.0D),
				"controlled-mob teleport above the movement threshold must be rejected");

		BlockHitResult target = BlockHitResult.miss(
				Vec3.ZERO, Direction.UP, BlockPos.ZERO);
		check(ClControlledMobCommandPacket.Handler.hasRequiredTarget(
				CommandType.PRESS_LMB, target),
				"legitimate controlled-mob primary action target must be accepted");
		check(!ClControlledMobCommandPacket.Handler.hasRequiredTarget(
				CommandType.PRESS_LMB, null),
				"controlled-mob primary action without a target must be rejected");
		check(ClControlledMobCommandPacket.Handler.hasRequiredTarget(
				CommandType.RELEASE_LMB, null),
				"targetless controlled-mob release command must remain valid");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
