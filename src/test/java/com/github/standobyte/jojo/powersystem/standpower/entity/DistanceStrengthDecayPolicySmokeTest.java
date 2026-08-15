package com.github.standobyte.jojo.powersystem.standpower.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.standobyte.jojo.api.stand.StandManualMovementObserversSmokeTest;

public final class DistanceStrengthDecayPolicySmokeTest {
	private DistanceStrengthDecayPolicySmokeTest() {}

	public static void main(String[] args) {
		StandManualMovementObserversSmokeTest.run();

		StandControlType.validate(
				StandControlType.CLOSE_RANGE_DIRECT,
				2, 10, true, true);
		StandControlType.validate(
				StandControlType.LONG_DISTANCE_OPERATION,
				50, 100, true, false);
		StandControlType.validate(
				StandControlType.AUTOMATIC,
				1, 1, false, false);
		StandControlType.validate(
				StandControlType.COLONY,
				1, 100, false, false);
		StandControlType.validate(
				StandControlType.PHENOMENON,
				1, 1, false, false);
		expectFailure(
				() -> StandControlType.validate(
						null, 1, 2, true, true),
				"standControlType is required");
		expectFailure(
				() -> StandControlType.validate(
						StandControlType.CLOSE_RANGE_DIRECT,
						Double.NaN, 2, true, true),
				"effectiveRange must be finite and positive");
		expectFailure(
				() -> StandControlType.validate(
						StandControlType.CLOSE_RANGE_DIRECT,
						2, 1, true, true),
				"rangeMax must be finite and at least effectiveRange");
		expectFailure(
				() -> StandControlType.validate(
						StandControlType.CLOSE_RANGE_DIRECT,
						2, 10, false, false),
				"CLOSE_RANGE_DIRECT requires manualControl and distanceStrengthDecay");
		expectFailure(
				() -> StandControlType.validate(
						StandControlType.CLOSE_RANGE_DIRECT,
						2, 2, true, true),
				"CLOSE_RANGE_DIRECT requires a non-empty decay interval");
		expectFailure(
				() -> StandControlType.validate(
						StandControlType.LONG_DISTANCE_OPERATION,
						50, 100, true, true),
				"LONG_DISTANCE_OPERATION requires manualControl without distanceStrengthDecay");
		expectFailure(
				() -> StandControlType.validate(
						StandControlType.AUTOMATIC,
						1, 10, true, false),
				"AUTOMATIC cannot use generic manual control or distance decay");
		expectFailure(
				() -> StandControlType.validate(
						StandControlType.HYBRID_FORM,
						1, 10, false, true),
				"distanceStrengthDecay requires manualControl");

		check(StandStatFormulas.rangeStrengthFactor(true, 2, 10, 2) == 1F,
				"close-range stands must keep full strength inside effective range");
		float quarterRange = StandStatFormulas.rangeStrengthFactor(true, 2, 10, 4);
		float halfRange = StandStatFormulas.rangeStrengthFactor(true, 2, 10, 6);
		float threeQuarterRange = StandStatFormulas.rangeStrengthFactor(true, 2, 10, 8);
		check(1F > quarterRange
				&& quarterRange > halfRange
				&& halfRange > threeQuarterRange
				&& threeQuarterRange > 0.25F,
				"close-range strength decay must be monotonic between effective and max range");
		check(StandStatFormulas.rangeStrengthFactor(true, 2, 10, 10) == 0.25F,
				"close-range stands must decay to the existing floor at max range");
		check(StandStatFormulas.rangeStrengthFactor(false, 50, 100, 100) == 1F,
				"native long-range stands must not receive close-range strength decay");
		check(StandStatFormulas.getHeavyAttackDamage(16)
				> StandStatFormulas.getHeavyAttackDamage(8)
				&& StandStatFormulas.getHeavyAttackDamage(8)
				> StandStatFormulas.getHeavyAttackDamage(4),
				"heavy attack damage must remain monotonic with decayed Stand strength");
		check(StandStatFormulas.getHeavyAttackKnockback(16, 0)
				> StandStatFormulas.getHeavyAttackKnockback(8, 0)
				&& StandStatFormulas.getHeavyAttackKnockback(8, 0)
				> StandStatFormulas.getHeavyAttackKnockback(4, 0),
				"heavy attack knockback must remain monotonic with decayed Stand strength");

		Path root = Path.of(System.getProperty("user.dir"));
		String type = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/powersystem/standpower/entity/EntityStandType.java"));
		check(type.contains("protected StandControlType standControlType;")
				&& type.contains("configureStandControlPolicy(")
				&& type.contains(
						"Explicit Stand control ranges must match StandStats"),
				"entity Stand construction must require one complete explicit control policy");
		check(!type.contains("manualControlEnabled = true")
				&& type.contains("new DefaultedValue.Bool(false)"),
				"legacy entity Stand construction must fail closed instead of inheriting permissive booleans");
		check(type.contains("if (!manualControlConfigured)")
				&& type.contains("if (!distanceStrengthDecayConfigured)"),
				"explicit false policy fields must remain distinguishable from omitted fields");
		check(type.contains("\"standControlType\"")
				&& type.contains("standControlType = standControlTypeDefault")
				&& type.contains("\"distanceStrengthDecayEnabled\"")
				&& type.contains("distanceStrengthDecayEnabled.reset()"),
				"control type and decay policy must round-trip through config and restore explicit defaults");
		check(type.contains("validateStandControlPolicy();")
				&& type.contains("public StandControlType getStandControlType()"),
				"missing or invalid legacy/addon policy data must be rejected before product use");

		String entity = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/powersystem/standpower/entity/StandEntity.java"));
		check(entity.contains("entityStandType.usesDistanceStrengthDecay()"),
				"stand entities must initialize the type policy");
		check(entity.contains(
				"distanceStrengthDecayEnabled && isManuallyControlled(),"),
				"distance strength decay must require active O-key manual control and the initialized type policy");
		check(entity.contains("double range = getMaxRangeForMovement(user);"),
				"manual movement must remain capped independently by max range");

		String movementPacket = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/subsystems/entity_puppetcontrol/client/stand/ClStandManualMovementPacket.java"));
		check(movementPacket.contains(
				"stand.absMoveTo(stand.getX(), stand.getY(), stand.getZ(), yRot, xRot)")
				&& !movementPacket.contains(
						"stand.absMoveTo(posXcl, posYcl, posZcl, yRot, xRot)"),
				"manual movement packets must preserve the range-clamped server position");

		String heavyPunch = read(root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/stands/_entitybase/StandEntityHeavyPunchAbility.java"));
		check(heavyPunch.contains("StandStatFormulas.getHeavyAttackKnockback("),
				"production heavy punches must use the shared distance-sensitive knockback formula");

		assertBuiltInPolicy(
				root, "StandInitHierophantGreen.java", "Hierophant Green",
				StandControlType.LONG_DISTANCE_OPERATION,
				"50", "100", true, false);
		assertBuiltInPolicy(
				root, "StandInitSilverChariot.java", "Silver Chariot",
				StandControlType.CLOSE_RANGE_DIRECT,
				"2", "10", true, true);
		assertBuiltInPolicy(
				root, "StandInitStarPlatinum.java", "Star Platinum",
				StandControlType.CLOSE_RANGE_DIRECT,
				"1", "2", true, true);
		assertBuiltInPolicy(
				root, "StandInitGoldExperience.java", "Gold Experience",
				StandControlType.CLOSE_RANGE_DIRECT,
				"1", "2", true, true);
		assertBuiltInPolicy(
				root, "StandInitCrazyDiamond.java", "Crazy Diamond",
				StandControlType.CLOSE_RANGE_DIRECT,
				"1", "2", true, true);
		assertBuiltInPolicy(
				root, "StandInitMagiciansRed.java", "Magician's Red",
				StandControlType.CLOSE_RANGE_DIRECT,
				"5", "10", true, true);
		assertBuiltInPolicy(
				root, "StandInitTheWorld.java", "The World",
				StandControlType.CLOSE_RANGE_DIRECT,
				"5", "10", true, true);
	}

	private static void assertBuiltInPolicy(
			Path root,
			String sourceName,
			String standName,
			StandControlType controlType,
			String effectiveRange,
			String rangeMax,
			boolean manualControl,
			boolean distanceStrengthDecay) {
		String source = read(root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/stands/" + sourceName));
		String compact = source.replaceAll("\\s+", "");
		check(compact.contains(".range(" + effectiveRange + "," + rangeMax + ")")
				&& compact.contains(
						"StandControlType." + controlType.name()
								+ "," + effectiveRange
								+ "," + rangeMax
								+ "," + manualControl
								+ "," + distanceStrengthDecay + ")")
				&& !source.contains(".manualControl(")
				&& !source.contains(".distanceStrengthDecay("),
				standName + " must declare one complete atomic Stand control policy");
	}

	private static void expectFailure(
			Runnable action, String expectedMessage) {
		try {
			action.run();
			throw new AssertionError(
					"expected policy validation failure: " + expectedMessage);
		}
		catch (IllegalStateException expected) {
			check(expected.getMessage().contains(expectedMessage),
					"unexpected policy failure: " + expected.getMessage());
		}
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new AssertionError("failed to read " + path, e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
