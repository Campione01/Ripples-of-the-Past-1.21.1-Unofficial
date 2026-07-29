package com.github.standobyte.jojo.api.power;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.unlockableskill.UnlockableSkill;

import net.minecraft.resources.ResourceLocation;

/**
 * Owner-scoped handles for explicit logical skill or ability unlocks.
 *
 * <p>This path bypasses menu prerequisites, Resolve gates, and EXP costs. It
 * remains server-authoritative, accepts only skills declared by the current
 * power type, persists through {@link PowerData}, and uses the normal power
 * data synchronization path.</p>
 */
public final class PowerSkillUnlocks {
	private static final Map<ResourceLocation, Owner> OWNERS =
			new LinkedHashMap<>();

	private PowerSkillUnlocks() {}

	public static synchronized Owner register(ResourceLocation owner) {
		Objects.requireNonNull(owner, "owner");
		Owner handle = new Owner(owner);
		if (OWNERS.putIfAbsent(owner, handle) != null) {
			throw new IllegalStateException(
					"Duplicate explicit power-skill unlock owner: "
							+ owner);
		}
		return handle;
	}

	public enum Result {
		UNLOCKED,
		ALREADY_UNLOCKED,
		NOT_SERVER,
		NO_CURRENT_POWER,
		UNKNOWN_SKILL_OR_ABILITY,
		INVALID_REQUEST,
		FAILED
	}

	public static final class Owner {
		private final ResourceLocation id;

		private Owner(ResourceLocation id) {
			this.id = id;
		}

		public ResourceLocation id() {
			return id;
		}

		public Result forceUnlock(
				Power<?> power,
				String skillOrAbilityName) {
			if (power == null) {
				return Result.INVALID_REQUEST;
			}
			return PowerSkillUnlocks.forceUnlock(
					id,
					new LiveUnlockAccess(power),
					skillOrAbilityName);
		}
	}

	static Result forceUnlock(
			ResourceLocation owner,
			UnlockAccess access,
			String skillOrAbilityName) {
		if (owner == null
				|| access == null
				|| skillOrAbilityName == null
				|| skillOrAbilityName.isBlank()) {
			return Result.INVALID_REQUEST;
		}
		synchronized (PowerSkillUnlocks.class) {
			if (!OWNERS.containsKey(owner)) {
				return Result.INVALID_REQUEST;
			}
		}
		try {
			if (!access.isLogicalServer()) {
				return Result.NOT_SERVER;
			}
			if (!access.hasCurrentPower()) {
				return Result.NO_CURRENT_POWER;
			}
			String skillName = access.resolveSkill(
					skillOrAbilityName);
			if (skillName == null) {
				return Result.UNKNOWN_SKILL_OR_ABILITY;
			}
			if (access.isUnlocked(skillName)) {
				return Result.ALREADY_UNLOCKED;
			}
			if (!access.unlock(skillName)) {
				return access.isUnlocked(skillName)
						? Result.ALREADY_UNLOCKED
						: Result.FAILED;
			}
			access.sync();
			return Result.UNLOCKED;
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"Explicit power-skill unlock owner {} failed for {}.",
					owner,
					skillOrAbilityName,
					error);
			return Result.FAILED;
		}
	}

	interface UnlockAccess {
		boolean isLogicalServer();

		boolean hasCurrentPower();

		@Nullable
		String resolveSkill(String skillOrAbilityName);

		boolean isUnlocked(String skillName);

		boolean unlock(String skillName);

		void sync();
	}

	private static final class LiveUnlockAccess
			implements UnlockAccess {
		private final Power<?> power;
		@Nullable
		private PowerData data;

		private LiveUnlockAccess(Power<?> power) {
			this.power = power;
		}

		@Override
		public boolean isLogicalServer() {
			return !power.getUser().level().isClientSide();
		}

		@Override
		public boolean hasCurrentPower() {
			return power.hasPower() && data() != null;
		}

		@Override
		public String resolveSkill(String skillOrAbilityName) {
			PowerData currentData = data();
			if (currentData == null) {
				return null;
			}
			Map<String, ? extends UnlockableSkill> skills =
					currentData.getAllSkills();
			if (skills.containsKey(skillOrAbilityName)) {
				return skillOrAbilityName;
			}
			String match = null;
			for (Map.Entry<String, ? extends UnlockableSkill>
					entry : skills.entrySet()) {
				if (!entry.getValue().unlocksAbilities.contains(
						skillOrAbilityName)) {
					continue;
				}
				if (match != null) {
					return null;
				}
				match = entry.getKey();
			}
			return match;
		}

		@Override
		public boolean isUnlocked(String skillName) {
			PowerData currentData = data();
			return currentData != null
					&& currentData.isSkillUnlocked(skillName);
		}

		@Override
		public boolean unlock(String skillName) {
			PowerData currentData = data();
			return currentData != null
					&& currentData._setSkillUnlocked(
							skillName, true, false);
		}

		@Override
		public void sync() {
			PowerData currentData = data();
			if (currentData != null) {
				currentData.syncOnUpdate(power.getUser());
			}
		}

		@Nullable
		private PowerData data() {
			if (data == null && power.hasPower()) {
				data = power.getCurTypeData();
			}
			return data;
		}
	}

	static synchronized void resetForTests() {
		OWNERS.clear();
	}
}
