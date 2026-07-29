package com.github.standobyte.jojo.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.standobyte.jojoimpl.powers.vampirism.VampirismState;

import net.minecraft.nbt.CompoundTag;

public final class CoreExtensionRegressionSmokeTest {
	private CoreExtensionRegressionSmokeTest() {}

	public static void run() {
		verifyVampirismAttachmentCopyContract();

		Path root = Path.of(System.getProperty("user.dir"));
		verifyTemporaryTransitionContract(root);
		verifyPassiveLifecycleContract(root);
		verifyStoneMaskContract(root);
		verifyItemUseParticleMixinContract(root);
		verifyCreeperFuseSuppressionMixinContract(root);
		verifyArchiveContract(root);
	}

	private static void verifyVampirismAttachmentCopyContract() {
		VampirismState source = new VampirismState();
		source.blood().setMax(1500.0F, false);
		source.blood().replenish(640.0F);
		source.blood().setDrainPerTick(0.25F);

		CompoundTag serialized = source.serializeNBT(null);
		VampirismState copied = new VampirismState();
		copied.deserializeNBT(null, serialized);

		check(copied.blood().current() == 640.0F,
				"Vampirism attachment copy cleared current blood");
		check(copied.blood().max() == 1500.0F,
				"Vampirism attachment copy changed max blood");
		check(copied.blood().drainPerTick() == 0.25F,
				"Vampirism attachment copy changed drain rate");
	}

	private static void verifyTemporaryTransitionContract(Path root) {
		String transitions = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/api/"
				+ "playerpower/PlayerPowerTransitions.java"));
		String restoreAfterDeath = between(
				transitions,
				"public static boolean restoreAfterDeath",
				"public record Snapshot");
		check(!restoreAfterDeath.contains(
				".onTemporaryPowerEnded("),
				"restoreAfterDeath must not own temporary cleanup");
		check(restoreAfterDeath.contains(
				"temporarySource,\n"
				+ "\t\t\t\ttemporaryType"),
				"restoreAfterDeath must pass the cleanup source");

		String playerPower = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "powersystem/playerpower/PlayerPower.java"));
		String restore = between(
				playerPower,
				"public boolean restoreTemporaryTypeData",
				"public Optional<CompoundTag> "
				+ "serializeRetainedTemporaryData");
		check(count(restore, ".onTemporaryPowerEnded(") == 1,
				"temporary cleanup must have exactly one owner");

		int stage = restore.indexOf(
				"restoredData.deserializeNBT(");
		int cleanup = restore.indexOf(
				"temporaryData.onTemporaryPowerEnded(");
		int commit = restore.indexOf("commitTemporaryRestore(");
		check(stage >= 0 && stage < cleanup && cleanup < commit,
				"retained data must stage before one cleanup and commit");

		String cleanupFailure = restore.substring(
				cleanup, commit);
		check(cleanupFailure.contains(
				"catch (RuntimeException | Error error)")
				&& cleanupFailure.contains("return false;"),
				"cleanup failure must abort before commit");

		String attachmentTypes = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/init/"
				+ "ModDataAttachmentTypes.java"));
		String vampirismRegistration = between(
				attachmentTypes,
				"VAMPIRISM_STATE =",
				"HAMON_CHARGE =");
		check(vampirismRegistration.contains(".copyOnDeath()"),
				"Vampirism attachment must opt into death copying");
	}

	private static void verifyPassiveLifecycleContract(Path root) {
		Path powers = root.resolve(
				"src/main/java/com/github/standobyte/jojoimpl/powers");
		for (String relative : new String[] {
				"zombie/ZombieData.java",
				"vampirism/VampirismData.java",
				"pillarman/PillarmanData.java" }) {
			String source = read(powers.resolve(relative));
			check(count(source,
					"public void onTemporaryPowerSuspended(") == 1,
					relative + " must suspend passive state");
			check(count(source,
					"public void onTemporaryPowerRestored(") == 1,
					relative + " must restore passive state");
			check(count(source,
					"public void onTemporaryPowerEnded(") == 1,
					relative + " must end temporary passive state");
		}
	}

	private static void verifyStoneMaskContract(Path root) {
		String bleeding = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "mechanics/BleedingEffect.java"));
		check(bleeding.contains("instanceof StoneMaskBlock"),
				"blood splash must include addon Stone Mask blocks");
		check(!bleeding.contains(
				".getBlock() == ModBlocks.STONE_MASK.get()"),
				"blood splash retained the core-only block predicate");

		String blockEntity = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/block/"
				+ "StoneMaskBlockEntity.java"));
		check(blockEntity.contains("state.getBlock().asItem()"),
				"Stone Mask block entity must derive its initial item");
		check(blockEntity.contains("!stateStack.isEmpty()")
				&& blockEntity.contains(
						"new ItemStack(ModItems.STONE_MASK.get())"),
				"Stone Mask block entity must retain a safe core fallback");
	}

	private static void verifyItemUseParticleMixinContract(Path root) {
		String mixin = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "item_use/LivingEntityItemUseParticlesMixin.java"));
		check(mixin.contains("@ModifyArg("),
				"item-use particle hook must modify the use call argument");
		check(mixin.contains(
				"method = \"triggerItemUseEffects\"")
				&& mixin.contains(
						"target = \"Lnet/minecraft/world/entity/"
						+ "LivingEntity;\"")
				&& mixin.contains("\"spawnItemParticles\""),
				"item-use particle hook must target the use-only call site");
		check(!mixin.contains(
				"method = \"spawnItemParticles\"")
				&& !mixin.contains("@ModifyVariable("),
				"item-use particle hook must not intercept break particles");
	}

	private static void verifyCreeperFuseSuppressionMixinContract(Path root) {
		String mixin = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/mixin/"
				+ "control/CreeperFuseSuppressionMixin.java"));
		check(mixin.contains("method = \"tick\"")
				&& mixin.contains("method = \"ignite\"")
				&& mixin.contains("method = \"explodeCreeper\""),
				"Creeper suppression must cover tick, ignite, and explosion");
		check(count(mixin, "at = @At(\"HEAD\")") == 3
				&& count(mixin, "cancellable = true") == 2,
				"Creeper suppression injection boundaries drifted");
		check(mixin.contains("oldSwell = 0;")
				&& mixin.contains("swell = 0;")
				&& mixin.contains("setSwellDir(-1);"),
				"Creeper suppression reset semantics drifted");
	}

	private static void verifyArchiveContract(Path root) {
		String build = read(root.resolve("build.gradle"));
		String archiveConfig = between(
				build,
				"tasks.withType(org.gradle.api.tasks.bundling."
				+ "AbstractArchiveTask)",
				"tasks.register('clientSettingsSmokeTest'");
		check(archiveConfig.contains(
				"preserveFileTimestamps = false"),
				"archive timestamps must be normalized");
		check(archiveConfig.contains(
				"reproducibleFileOrder = true"),
				"archive entry order must be reproducible");
	}

	private static String between(
			String source, String startToken, String endToken) {
		int start = source.indexOf(startToken);
		int end = source.indexOf(endToken, start);
		if (start < 0 || end < 0 || end <= start) {
			throw new AssertionError(
					"failed to locate source contract between "
							+ startToken + " and " + endToken);
		}
		return source.substring(start, end);
	}

	private static int count(String source, String token) {
		int count = 0;
		int index = 0;
		while ((index = source.indexOf(token, index)) >= 0) {
			count++;
			index += token.length();
		}
		return count;
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
