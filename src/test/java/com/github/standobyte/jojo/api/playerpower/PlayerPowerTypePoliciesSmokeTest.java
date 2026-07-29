package com.github.standobyte.jojo.api.playerpower;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerData;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerType;

import net.minecraft.resources.ResourceLocation;

public final class PlayerPowerTypePoliciesSmokeTest {
	private PlayerPowerTypePoliciesSmokeTest() {}

	public static void run() {
		TestPowerType defaults = new TestPowerType(
				id("defaults"), false, 1.0F, false);
		TestPowerType replacement = new TestPowerType(
				id("replacement"), true, 0.0F, true);

		check(!defaults.isReplaceableWith(replacement),
				"PlayerPower types must reject replacement by default");
		check(defaults.getTargetResolveMultiplier(null, null) == 1.0F,
				"target Resolve multiplier must default to one");
		check(replacement.isReplaceableWith(defaults),
				"replacement policy override was not dispatched");
		check(replacement.getTargetResolveMultiplier(
				null, null) == 0.0F,
				"target Resolve multiplier override was not dispatched");

		verifyRuntimeCallPaths();
	}

	private static void verifyRuntimeCallPaths() {
		Path root = Path.of(System.getProperty("user.dir"));
		String playerPower = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "powersystem/playerpower/PlayerPower.java"));
		check(playerPower.contains(
				"return current.isReplaceableWith(type);"),
				"replacement wrapper does not dispatch the type policy");
		check(playerPower.contains(
				"current.getTargetResolveMultiplier(\n"
				+ "\t\t\t\t\tthis, attackingStand)"),
				"Resolve wrapper does not dispatch the type policy");
		check(playerPower.contains(
				"catch (RuntimeException error)"),
				"PlayerPower policy wrappers must isolate addon failures");

		String resolve = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "mechanics/resolve/ResolveCounter.java"));
		check(resolve.contains(
				"targetPower\n"
				+ "\t\t\t\t\t\t.getTargetResolveMultiplier("
				+ "attackerStand)"),
				"Resolve award path does not use the safe wrapper");

		String pillarman = read(root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/"
				+ "powers/pillarman/PillarmanPowerType.java"));
		check(pillarman.contains(
				"public float getTargetResolveMultiplier(")
				&& pillarman.contains(
						"return getEvolutionStage(power);"),
				"built-in Pillarman Resolve semantics were not retained");
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
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

	private static final class TestPowerType
			extends PlayerPowerType<PlayerPowerData> {
		private final boolean replaceable;
		private final float multiplier;
		private final boolean overrides;

		private TestPowerType(
				ResourceLocation id,
				boolean replaceable,
				float multiplier,
				boolean overrides) {
			super(id, new MovesetBuilder());
			this.replaceable = replaceable;
			this.multiplier = multiplier;
			this.overrides = overrides;
		}

		@Override
		public PlayerPowerData newDataInstance() {
			return null;
		}

		@Override
		public PowerClass<PlayerPower> getPowerClass() {
			return null;
		}

		@Override
		public boolean isReplaceableWith(
				PlayerPowerType<?> newType) {
			return overrides
					? replaceable
					: super.isReplaceableWith(newType);
		}

		@Override
		public float getTargetResolveMultiplier(
				PlayerPower power,
				com.github.standobyte.jojo.powersystem.standpower.StandPower attackingStand) {
			return overrides
					? multiplier
					: super.getTargetResolveMultiplier(
							power, attackingStand);
		}
	}
}
