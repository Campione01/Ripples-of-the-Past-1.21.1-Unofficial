package com.github.standobyte.jojo.powersystem.playerpower;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.standobyte.jojo.powersystem.PowerData;
import com.mojang.datafixers.util.Either;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class PlayerPowerDataLifecycleSmokeTest {
	private PlayerPowerDataLifecycleSmokeTest() {}

	public static void run() {
		verifyDetachedCloneData();
		verifySuccessfulRestoreCommit();
		verifyFailedSuspensionCompensates();
		verifySuppressionIdentityGuard();
		verifyTransactionalSourceBoundary();
	}

	private static void verifyDetachedCloneData() {
		ResourceLocation retainedType = id("retained");
		CompoundTag retainedNbt = new CompoundTag();
		retainedNbt.putInt("Value", 37);
		ResourceLocation pendingType = id("pending");
		CompoundTag pendingNbt = new CompoundTag();
		pendingNbt.putInt("Value", 21);

		Map<ResourceLocation, Either<PowerData, CompoundTag>> source =
				new HashMap<>();
		source.put(retainedType, Either.right(retainedNbt));
		source.put(pendingType, Either.right(pendingNbt));

		Map<ResourceLocation, Either<PowerData, CompoundTag>> copy =
				PlayerPower.detachedPowerDataCopy(source, null);
		check(copy != source,
				"PlayerPower clone retained the source data map");
		check(copy.get(retainedType).right()
						.orElseThrow().getInt("Value") == 37,
				"retained NBT was not copied into clone-owned data");
		check(copy.get(pendingType).right()
						.orElseThrow().getInt("Value") == 21,
				"pending retained NBT changed during clone");

		retainedNbt.putInt("Value", 99);
		pendingNbt.putInt("Value", 99);
		check(copy.get(retainedType).right()
						.orElseThrow().getInt("Value") == 37,
				"death or dimension clone shares retained NBT");
		check(copy.get(pendingType).right()
						.orElseThrow().getInt("Value") == 21,
				"death or dimension clone shares pending retained NBT");

		copy.get(pendingType).right()
				.orElseThrow().putInt("Value", 7);
		check(pendingNbt.getInt("Value") == 99,
				"clone-owned retained NBT mutated the source entity");
	}

	private static void verifySuccessfulRestoreCommit() {
		String source = source(
				"src/main/java/com/github/standobyte/jojo/"
						+ "powersystem/playerpower/PlayerPower.java");
		String commit = section(
				source,
				"static void commitTemporaryRestore(",
				"public CompoundTag serializeNBT(");
		int remove = commit.indexOf("data.remove(temporaryTypeId);");
		int put = commit.indexOf(
				"data.put(restoredTypeId, Either.left(restoredData));");
		check(remove >= 0 && put > remove,
				"successful restore does not remove temporary data "
						+ "before installing retained data");
	}

	private static void verifyFailedSuspensionCompensates() {
		List<String> trace = new ArrayList<>();
		PlayerPower.TemporarySuspensionAttempt attempt =
				new PlayerPower.TemporarySuspensionAttempt();
		RuntimeException failure =
				new IllegalStateException("suspension failed");
		try {
			attempt.run(() -> {
				trace.add("suspend:remove");
				throw failure;
			});
			throw new AssertionError("expected suspension failure");
		}
		catch (RuntimeException actual) {
			check(actual == failure,
					"suspension failure identity was not preserved");
			attempt.restoreAfterFailure(
					() -> trace.add("restore:add"),
					actual);
		}
		check(trace.equals(List.of(
				"suspend:remove",
				"restore:add")),
				"a partially failed suspension did not run its inverse");
	}

	private static void verifySuppressionIdentityGuard() {
		RuntimeException failure =
				new IllegalStateException("primary failure");
		PlayerPower.addSuppressedIfDistinct(failure, failure);
		check(failure.getSuppressed().length == 0,
				"same Throwable was self-suppressed");

		RuntimeException secondary =
				new IllegalStateException("secondary failure");
		PlayerPower.addSuppressedIfDistinct(failure, secondary);
		check(failure.getSuppressed().length == 1
						&& failure.getSuppressed()[0] == secondary,
				"distinct rollback failure was not retained");
	}

	private static void verifyTransactionalSourceBoundary() {
		String source = source(
				"src/main/java/com/github/standobyte/jojo/"
						+ "powersystem/playerpower/PlayerPower.java");
		String begin = section(
				source,
				"public boolean beginTemporaryTransition(",
				"public boolean endTemporaryTransition()");
		check(begin.contains(
						"catch (RuntimeException | Error cleanupError)"),
				"temporary install cleanup Error can escape rollback");
		check(begin.contains(
						"catch (RuntimeException | Error rollbackError)"),
				"temporary install moveset rollback is not fail-closed");
		check(begin.contains(
						"addSuppressedIfDistinct(error, cleanupError);")
						&& begin.contains(
								"addSuppressedIfDistinct("
										+ "error, rollbackError);"),
				"temporary install rollback can self-suppress");
		check(begin.indexOf(
						"temporarilySuspendedTypeId = null;")
						< begin.indexOf(
								"moveset = initMoveset(previousType);"),
				"temporary install rollback rebuilds moveset before state");

		String sameEntityRestore = section(
				source,
				"public boolean endTemporaryTransition()",
				"public boolean restoreTemporaryTypeData(");
		assertStagedCleanupBeforeCommit(
				sameEntityRestore,
				"retainedMoveset = initMoveset(retainedType);",
				"same-entity temporary restore");

		String deathRestore = section(
				source,
				"public boolean restoreTemporaryTypeData(",
				"public Optional<CompoundTag> "
						+ "serializeRetainedTemporaryData(");
		assertStagedCleanupBeforeCommit(
				deathRestore,
				"restoredMoveset = initMoveset(restoredType);",
				"post-death temporary restore");
		check(deathRestore.contains(
						"temporaryType != null"),
				"post-death restore does not identify temporary data");

		String clone = section(
				source,
				"public void onPlayerCloneData(",
				"public CompoundTag serializeNBT(");
		check(clone.contains("detachedPowerDataCopy("),
				"PlayerPower clone does not detach its data map");
		check(clone.contains(
						"newEntityData.temporarilySuspendedData = null;"),
				"PlayerPower clone shares retained live data");
		check(!clone.contains("this.temporarilySuspendedData;"),
				"ordinary clone retains source-owned suspended data");

		check(source.contains(
						"\"TemporarilySuspendedPowerType\""),
				"reconnect no longer persists retained power identity");
		check(source.contains("CompoundTag::copy"),
				"clone/reconnect data detachment lacks NBT copying");
		check(source.contains("data -> data.serializeNBT(provider)"),
				"live retained data is not serialized into clone-owned NBT");
	}

	private static void assertStagedCleanupBeforeCommit(
			String source,
			String stageToken,
			String description) {
		int staged = source.indexOf(stageToken);
		int cleanup = source.indexOf(
				"temporaryData.onTemporaryPowerEnded(");
		int commit = source.indexOf("commitTemporaryRestore(");
		check(staged >= 0 && cleanup > staged && commit > cleanup,
				description
						+ " does not stage, clean, then commit");
		String cleanupBoundary = source.substring(cleanup, commit);
		check(cleanupBoundary.contains(
						"catch (RuntimeException | Error error)"),
				description
						+ " commits after a cleanup Error");
		check(cleanupBoundary.contains("return false;"),
				description
						+ " does not fail closed on cleanup failure");
	}

	private static String section(
			String source, String start, String end) {
		int startIndex = source.indexOf(start);
		int endIndex = source.indexOf(end, startIndex + start.length());
		check(startIndex >= 0 && endIndex > startIndex,
				"PlayerPower source boundary is missing: " + start);
		return source.substring(startIndex, endIndex);
	}

	private static String source(String relative) {
		Path path = Path.of(relative);
		if (!Files.isRegularFile(path)) {
			path = Path.of(System.getProperty("user.dir"))
					.resolve(relative);
		}
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError(
					"failed to read " + path, error);
		}
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(
				"rotp_test", path);
	}

	private static void check(
			boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

}
