package com.github.standobyte.jojo.api.power;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.github.standobyte.jojo.api.power.PowerSkillUnlocks.Result;
import com.github.standobyte.jojo.api.power.PowerSkillUnlocks.UnlockAccess;

import net.minecraft.resources.ResourceLocation;

public final class PowerSkillUnlocksSmokeTest {
	private PowerSkillUnlocksSmokeTest() {}

	public static void run() {
		PowerSkillUnlocks.resetForTests();
		ResourceLocation ownerId = id("projectile_threat");
		PowerSkillUnlocks.Owner owner =
				PowerSkillUnlocks.register(ownerId);
		check(owner.id().equals(ownerId),
				"registered unlock owner lost its ID");

		boolean nullOwnerRejected = false;
		try {
			PowerSkillUnlocks.register(null);
		}
		catch (NullPointerException expected) {
			nullOwnerRejected = true;
		}
		check(nullOwnerRejected,
				"null explicit-unlock owner must be rejected");

		boolean duplicateRejected = false;
		try {
			PowerSkillUnlocks.register(ownerId);
		}
		catch (IllegalStateException expected) {
			duplicateRejected = true;
		}
		check(duplicateRejected,
				"duplicate explicit-unlock owner must be rejected");

		FakeAccess access = new FakeAccess();
		access.aliases.put("time_stop", "time_stop_skill");
		check(PowerSkillUnlocks.forceUnlock(
				null, access, "time_stop")
				== Result.INVALID_REQUEST,
				"null owner request must fail closed");
		check(PowerSkillUnlocks.forceUnlock(
				id("unregistered"), access, "time_stop")
				== Result.INVALID_REQUEST,
				"unregistered owner request must fail closed");
		check(PowerSkillUnlocks.forceUnlock(
				ownerId, access, "missing")
				== Result.UNKNOWN_SKILL_OR_ABILITY,
				"unknown logical ability must not mutate data");
		check(access.syncs == 0,
				"unknown unlock request synchronized data");

		check(PowerSkillUnlocks.forceUnlock(
				ownerId, access, "time_stop")
				== Result.UNLOCKED,
				"known ability alias was not explicitly unlocked");
		check(access.unlocked.getOrDefault(
				"time_stop_skill", false),
				"canonical skill state was not changed");
		check(access.syncs == 1,
				"first explicit unlock must synchronize once");

		check(PowerSkillUnlocks.forceUnlock(
				ownerId, access, "time_stop")
				== Result.ALREADY_UNLOCKED,
				"repeat unlock must report idempotent state");
		check(access.syncs == 1,
				"repeat unlock synchronized unchanged data");

		access.server = false;
		check(PowerSkillUnlocks.forceUnlock(
				ownerId, access, "time_stop")
				== Result.NOT_SERVER,
				"client unlock request must fail closed");
		access.server = true;
		access.hasPower = false;
		check(PowerSkillUnlocks.forceUnlock(
				ownerId, access, "time_stop")
				== Result.NO_CURRENT_POWER,
				"unlock without a current power must fail closed");

		verifyLivePersistenceAndSyncContract();
		PowerSkillUnlocks.resetForTests();
	}

	private static void verifyLivePersistenceAndSyncContract() {
		Path root = Path.of(System.getProperty("user.dir"));
		String source = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/api/"
				+ "power/PowerSkillUnlocks.java"));
		check(source.contains(
				"currentData._setSkillUnlocked(\n"
				+ "\t\t\t\t\t\t\tskillName, true, false)"),
				"live explicit unlock must bypass EXP charging");
		check(source.contains(
				"currentData.syncOnUpdate(power.getUser())"),
				"live explicit unlock must use normal persistent-data sync");
	}

	private static final class FakeAccess
			implements UnlockAccess {
		private final Map<String, String> aliases =
				new HashMap<>();
		private final Map<String, Boolean> unlocked =
				new HashMap<>();
		private boolean server = true;
		private boolean hasPower = true;
		private int syncs;

		@Override
		public boolean isLogicalServer() {
			return server;
		}

		@Override
		public boolean hasCurrentPower() {
			return hasPower;
		}

		@Override
		public String resolveSkill(String skillOrAbilityName) {
			return aliases.get(skillOrAbilityName);
		}

		@Override
		public boolean isUnlocked(String skillName) {
			return unlocked.getOrDefault(skillName, false);
		}

		@Override
		public boolean unlock(String skillName) {
			return unlocked.put(skillName, true) == null;
		}

		@Override
		public void sync() {
			syncs++;
		}
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
}
