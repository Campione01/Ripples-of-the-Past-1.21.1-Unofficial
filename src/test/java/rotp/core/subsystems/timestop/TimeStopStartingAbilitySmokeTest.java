package rotp.core.subsystems.timestop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 TimeStopInstance kept the TimeStop action that started it: the instance ended
 * when that action was no longer unlocked, and onRemoved put the cooldown on that action
 * and its instant variation. The port keys both on the starting ability, not on the
 * moveset name "time_stop", so add-on time stops under their own names keep running.
 */
public final class TimeStopStartingAbilitySmokeTest {
	private TimeStopStartingAbilitySmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		Path root = Path.of(System.getProperty("user.dir"));
		String state = read(root.resolve(
				"src/main/java/rotp/core/subsystems/timestop/TimeStopState.java"));
		String cooldowns = read(root.resolve(
				"src/main/java/rotp/core/subsystems/timestop/TimeStopCooldowns.java"));
		String ability = read(root.resolve(
				"src/main/java/rotp/core/impl/stands/theworld/TimeStopAbility.java"));
		String blink = read(root.resolve(
				"src/main/java/rotp/core/impl/stands/theworld/TimeStopBlinkAbility.java"));

		String commit = between(state,
				"public boolean commitPreStart(TimeStopLifecycleEvent.PreStart event, @Nullable Ability startingAbility)",
				"private void commitInstance");
		check(commit.contains("timeStopStarters.put(instance.id(), startingAbility.abilityId);")
				&& commit.contains("timeStopStarters.remove(instance.id());")
				&& commit.indexOf("timeStopStarters.put(") < commit.indexOf("commitInstance(instance);"),
				"commit must record (or clear) the starting ability before the instance goes live");
		check(state.contains("return commitPreStart(event, null);"),
				"API commits without an ability must clear a stale starter");
		check(ability.contains("state.commitPreStart(startEvent, this, chargedStartCost)")
				&& !ability.contains("state.commitPreStart(startEvent))"),
				"the time-stop ability must commit itself as the starter");

		String shouldEnd = between(state,
				"private boolean shouldEndTimeStop(Instance instance)",
				"private boolean playResumeVoiceLine");
		check(shouldEnd.contains("Ability timeStop = getStartingAbility(instance, power);")
				&& !shouldEnd.contains("getAbility(TimeStopLearning.TIME_STOP)"),
				"the end check must follow the starting ability, not the name time_stop");
		check(shouldEnd.indexOf("getStartingAbility(instance, power)")
				< shouldEnd.indexOf("instance.isStartupSettling()"),
				"a locked starting ability must still end the instance during the opening settle");

		String starting = between(state,
				"private Ability getStartingAbility(Instance instance, StandPower power)",
				"private static Ability getMovesetTimeStopAbility(StandPower power)");
		check(starting.contains("power.getMoveset().getAbility(starter.nameInMoveset())")
				&& starting.contains("starter.equals(ability.abilityId)"),
				"the starter must be the same moveset ability, not just a same-named one");
		String fallback = between(state,
				"private static Ability getMovesetTimeStopAbility(StandPower power)",
				"private void refundUnusedTimeStopStartCost");
		check(fallback.indexOf("getAbility(TimeStopLearning.TIME_STOP)")
				< fallback.indexOf("instanceof TimeStopAbility"),
				"API-started instances keep time_stop first, then any time-stop ability");

		String settle = between(state,
				"private void applyTimeStopCooldowns(List<Instance> removedInstances)",
				"private String getCooldownAbilityName(Instance removed, StandPower power)");
		int named = settle.indexOf("getCooldownAbilityName(removed, power)");
		int cleared = settle.indexOf("timeStopStarters.remove(removed.id());");
		check(named >= 0 && cleared > named,
				"the cooldown name must be read before the starter is dropped");
		check(settle.contains("cooldownAbilities.get(cooldown.getKey()),"),
				"the end cooldown must use the starting ability's name");

		check(cooldowns.contains("setTimeStopCooldownsOnTimeStopEnd(power, TIME_STOP, ticksPassed);"),
				"the two-argument end cooldown must keep The World and Star Platinum on time_stop");
		String cooldownByName = between(cooldowns,
				"public static void setTimeStopCooldownsOnTimeStopEnd(StandPower power, String timeStopAbilityName, int ticksPassed)",
				"public static void setTimeStopBlinkCooldowns");
		check(cooldownByName.contains("power.setAbilityCooldown(timeStopAbilityName, cooldown);")
				&& cooldownByName.contains("timeStopAbilityName.equals(blink.getTimeStopAbilityName())")
				&& cooldownByName.contains("return power.getMoveset().getAbility(TIME_STOP_BLINK) != null ? TIME_STOP_BLINK : null;"),
				"the ended time stop and the blink bound to it must get the cooldown");
		check(blink.contains("public String getTimeStopAbilityName()"),
				"the blink must expose the time stop it belongs to");
	}

	private static String between(String source, String startToken, String endToken) {
		int start = source.indexOf(startToken);
		int end = source.indexOf(endToken, start);
		if (start < 0 || end < 0 || end <= start) {
			throw new AssertionError(
					"failed to locate source contract between "
							+ startToken + " and " + endToken);
		}
		return source.substring(start, end);
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
