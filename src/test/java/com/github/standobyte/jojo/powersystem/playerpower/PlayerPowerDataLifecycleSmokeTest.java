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
		verifyOrdinaryTransitionDataOwnership();
		verifySuccessfulRestoreCommit();
		verifyFailedSuspensionCompensates();
		verifySuppressionIdentityGuard();
		verifyTransactionalSourceBoundary();
	}

	private static void verifyOrdinaryTransitionDataOwnership() {
		ResourceLocation previousType = id("previous");
		ResourceLocation currentType = id("current");
		ResourceLocation unrelatedType = id("unrelated");
		Map<ResourceLocation, Either<PowerData, CompoundTag>> abandonedSave =
				new HashMap<>();
		CompoundTag staleTraining = new CompoundTag();
		staleTraining.putFloat("BreathingLevel", 87.0F);
		abandonedSave.put(currentType, Either.right(staleTraining));
		abandonedSave.put(unrelatedType, Either.right(new CompoundTag()));
		PlayerPower.discardStalePowerDataForGrant(
				abandonedSave, null, currentType);
		check(!abandonedSave.containsKey(currentType),
				"null-type legacy save retained stale Hamon grant data");
		check(abandonedSave.containsKey(unrelatedType)
						&& abandonedSave.size() == 1,
				"legacy Hamon migration removed another power type");

		Map<ResourceLocation, Either<PowerData, CompoundTag>> data =
				new HashMap<>();
		data.put(previousType, Either.right(new CompoundTag()));
		data.put(currentType, Either.right(new CompoundTag()));
		data.put(unrelatedType, Either.right(new CompoundTag()));
		PlayerPower.discardStalePowerDataForGrant(
				data, previousType, currentType);
		check(!data.containsKey(currentType),
				"ordinary PlayerPower grant reused stale incoming data");
		check(data.containsKey(previousType)
						&& data.containsKey(unrelatedType)
						&& data.size() == 2,
				"fresh PlayerPower grant removed old or unrelated data");

		data.put(currentType, Either.right(new CompoundTag()));

		PlayerPower.discardReplacedPowerData(
				data, previousType, currentType, false);
		check(!data.containsKey(previousType),
				"ordinary PlayerPower replacement retained old data");
		check(data.containsKey(currentType)
						&& data.containsKey(unrelatedType)
						&& data.size() == 2,
				"ordinary PlayerPower replacement removed unrelated data");

		data.put(previousType, Either.right(new CompoundTag()));
		PlayerPower.discardReplacedPowerData(
				data, previousType, currentType, true);
		check(data.containsKey(previousType),
				"temporary PlayerPower transition discarded retained data");

		PlayerPower.discardReplacedPowerData(
				data, previousType, previousType, false);
		check(data.containsKey(previousType),
				"same-type refresh discarded active data");
		PlayerPower.discardStalePowerDataForGrant(
				data, previousType, previousType);
		check(data.containsKey(previousType),
				"active PlayerPower load discarded saved data");

		String source = source(
				"src/main/java/com/github/standobyte/jojo/"
						+ "powersystem/playerpower/PlayerPower.java");
		String ordinarySet = section(
				source,
				"public void setPowerType(",
				"public void applyTrackedPowerType(");
		int changedBranch = ordinarySet.indexOf("if (old != type)");
		int serverGuard = ordinarySet.indexOf(
				"if (!user.level().isClientSide())", changedBranch);
		int freshGrant = ordinarySet.indexOf(
				"discardStalePowerDataForGrant(");
		int typeAssignment = ordinarySet.indexOf(
				"this.curPowerType = Optional.ofNullable(type);");
		int callback = ordinarySet.indexOf(
				"onSetPowerType(old, type);");
		int discard = ordinarySet.indexOf(
				"discardReplacedPowerData(", callback);
		check(changedBranch >= 0
						&& serverGuard > changedBranch
						&& freshGrant > serverGuard
						&& typeAssignment > freshGrant
						&& callback > typeAssignment,
				"ordinary grant does not discard stale target data before creation");
		check(callback >= 0 && discard > callback
						&& ordinarySet.substring(callback, discard)
								.contains("finally"),
				"ordinary replacement does not discard old data after callbacks");

		String trackedSet = section(
				source,
				"public void applyTrackedPowerType(",
				"public boolean beginTemporaryTransition(");
		check(trackedSet.contains("discardReplacedPowerData(")
						&& trackedSet.contains("retainedType != null"),
				"tracked transitions do not preserve retained temporary data");

		String temporaryTransitions = section(
				source,
				"public boolean beginTemporaryTransition(",
				"public Optional<CompoundTag> serializeRetainedTemporaryData(");
		check(!temporaryTransitions.contains(
						"discardStalePowerDataForGrant("),
				"temporary retained restore uses destructive grant preparation");

		String clone = section(
				source,
				"public void onPlayerCloneData(",
				"static Map<ResourceLocation, Either<PowerData, CompoundTag>>");
		check(!clone.contains(
						"discardStalePowerDataForGrant("),
				"PlayerPower clone uses destructive grant preparation");

		String load = section(
				source,
				"public void deserializeNBT(",
				"public static PlayerPower get(");
		check(!load.contains(
						"discardStalePowerDataForGrant("),
				"PlayerPower login load uses destructive grant preparation");

		String powerSource = source(
				"src/main/java/com/github/standobyte/jojo/"
						+ "powersystem/Power.java");
		String dataCreation = section(
				powerSource,
				"public PowerData getPowerTypeData(",
				"public Moveset getMoveset(");
		check(dataCreation.contains("if (dataEntry == null)")
						&& dataCreation.contains(
								"PowerData data = powerType.newDataInstance();")
						&& dataCreation.contains("data.onInit(this);"),
				"missing grant data does not create a fresh default instance");
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
