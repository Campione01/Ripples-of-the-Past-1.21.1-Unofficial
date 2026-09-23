package rotp.core.subsystems.timestop;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 trained, timed and costed each TimeStop action on its own (TimeStopInstance.onRemoved,
 * TimeStop.getTimeStopTicks(power, action), getStaminaCostTicking), and a TimeStopInstant cooled
 * itself and its base time stop. The port keys all of it by the time stop's moveset name, so Shadow
 * The World's and Catch the Rainbow's time stops train, while the core's "time_stop" keeps its key.
 */
public final class TimeStopPerAbilityLearningSmokeTest {
	private static final ResourceLocation SHADOW = id("rotp_shadowtheworld", "shadowtheworld");
	private static final ResourceLocation THE_WORLD = id("jojo_ripples", "the_world");
	private static final String SHADOW_TS = "shadow_world_time_stop";
	private static final String SHADOW_BLINK = "shadow_world_ts_blink";
	private static final float EPSILON = 0.0001F;

	private TimeStopPerAbilityLearningSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		verifyLearningNames();
		verifyDurationAndCostPerAbility();
		verifyBlinkCooldowns();
		verifySourceWiring();
	}

	private static void verifyLearningNames() {
		TimeStopAbility shadow = timeStop(SHADOW, SHADOW_TS);
		TimeStopAbility theWorld = timeStop(THE_WORLD, TimeStopLearning.TIME_STOP);
		TimeStopAbility unbound = new AbilityType<TimeStopAbility>(id("jojo_ripples", "time_stop"),
				TimeStopAbility::new).createInstance(new AbilityId(null, null, "jojo_ripples:time_stop"));
		check(SHADOW_TS.equals(shadow.getLearningAbilityName())
				&& SHADOW_TS.equals(TimeStopLearning.getLearningName(shadow)),
				"an add-on time stop must train under its own moveset name");
		check(TimeStopLearning.TIME_STOP.equals(theWorld.getLearningAbilityName()),
				"the core's time stop must keep the saved key time_stop");
		check(TimeStopLearning.TIME_STOP.equals(unbound.getLearningAbilityName())
				&& TimeStopLearning.TIME_STOP.equals(TimeStopLearning.getLearningName((Ability) null)),
				"a time stop outside a moveset falls back to time_stop");
		Ability plain = new AbilityType<Ability>(id("rotp_test", "plain"), Ability::new)
				.createInstance(new AbilityId(null, SHADOW, "plain"));
		check("plain".equals(TimeStopLearning.getLearningName(plain)),
				"a non-trainable ability is keyed by its moveset name");

		StubPower shadowPower = power(shadow, blink(SHADOW, SHADOW_BLINK, SHADOW_TS));
		check(TimeStopState.getDefaultTimeStopAbility(shadowPower) == shadow
				&& SHADOW_TS.equals(TimeStopLearning.getDefaultLearningName(shadowPower)),
				"a Stand without time_stop defaults to its own time stop");
		check(SHADOW_TS.equals(TimeStopLearning.getLearningName(shadowPower, SHADOW_TS))
				&& "missing".equals(TimeStopLearning.getLearningName(shadowPower, "missing")),
				"a blink reads the training of the time stop it is bound to");
		check(List.of(SHADOW_TS).equals(List.copyOf(TimeStopLearning.getTimeStopLearningNames(shadowPower))),
				"daily decay must reach the Stand's own time stop");

		StubPower worldPower = power(theWorld, blink(THE_WORLD, "time_stop_blink", null));
		check(TimeStopLearning.TIME_STOP.equals(TimeStopLearning.getDefaultLearningName(worldPower))
				&& List.of(TimeStopLearning.TIME_STOP).equals(
						List.copyOf(TimeStopLearning.getTimeStopLearningNames(worldPower))),
				"The World keeps learning and decaying under time_stop");
		check(TimeStopLearning.TIME_STOP.equals(TimeStopLearning.getDefaultLearningName(null))
				&& List.of(TimeStopLearning.TIME_STOP).equals(
						List.copyOf(TimeStopLearning.getTimeStopLearningNames(power()))),
				"a Stand without time stops keeps the old time_stop key");
	}

	private static void verifyDurationAndCostPerAbility() {
		StubPower shadowPower = power(timeStop(SHADOW, SHADOW_TS));
		shadowPower.learning().put(SHADOW_TS, 40.0F);
		check(TimeStopLearning.getSavedTimeStopTicks(shadowPower, SHADOW_TS) == 45
				&& TimeStopLearning.getTimeStopTicks(shadowPower, SHADOW_TS) == 45,
				"a trained add-on time stop must last 5 + its own points");
		check(TimeStopLearning.getTimeStopTicks(shadowPower) == 45
				&& TimeStopLearning.getSavedTimeStopTicks(shadowPower) == 45,
				"the one-argument duration must read the Stand's own time stop");
		check(TimeStopLearning.getSavedTimeStopTicks(shadowPower, TimeStopLearning.TIME_STOP)
				== TimeStopLearning.MIN_TIME_STOP_TICKS,
				"another key's training must not lengthen this time stop");
		checkClose(TimeStopLearning.getTimeStopStaminaCostTick(shadowPower, TimeStopLearning.TIME_STOP), 180.0F,
				"an untrained key must be priced by its own 5 ticks");
		checkClose(TimeStopLearning.getTimeStopStaminaCost(shadowPower, TimeStopLearning.TIME_STOP, 9), 225.0F,
				"a start is clamped to the priced time stop's own duration");
		checkClose(TimeStopLearning.getTimeStopStaminaCostTick(shadowPower, SHADOW_TS), 20.0F,
				"per-tick cost must be 9 * 100 / the time stop's own ticks");
		checkClose(TimeStopLearning.getTimeStopStaminaCostTick(shadowPower), 20.0F,
				"one-argument per-tick cost must follow the Stand's own time stop");
		checkClose(TimeStopLearning.getTimeStopBlinkStaminaCostTicking(shadowPower, SHADOW_TS), 16.0F,
				"the blink pays 0.8 of its base time stop's per-tick cost");
		checkClose(TimeStopLearning.getTimeStopStaminaCost(shadowPower, SHADOW_TS, 45), 225.0F,
				"a full release costs the whole start cost");
		checkClose(TimeStopLearning.getTimeStopStaminaCost(shadowPower, SHADOW_TS, 9), 45.0F,
				"a partial release is priced against the time stop's own duration");

		StubPower worldPower = power(timeStop(THE_WORLD, TimeStopLearning.TIME_STOP));
		worldPower.learning().put(TimeStopLearning.TIME_STOP, 20.0F);
		worldPower.learning().put(SHADOW_TS, 90.0F);
		check(TimeStopLearning.getTimeStopTicks(worldPower) == 25
				&& TimeStopLearning.getTimeStopTicks(worldPower, TimeStopLearning.TIME_STOP) == 25,
				"The World's saved time_stop progress must read as before");
		checkClose(TimeStopLearning.getTimeStopStaminaCostTick(worldPower), 36.0F,
				"The World's per-tick cost must read as before");
	}

	private static void verifyBlinkCooldowns() {
		TimeStopBlinkAbility shadowBlink = blink(SHADOW, SHADOW_BLINK, SHADOW_TS);
		StubPower shadowPower = power(timeStop(SHADOW, SHADOW_TS), shadowBlink);
		TimeStopCooldowns.setTimeStopBlinkCooldowns(shadowPower, shadowBlink, 12);
		check(shadowPower.cooldowns().getOrDefault(SHADOW_BLINK, 0) == 6
				&& shadowPower.cooldowns().getOrDefault(SHADOW_TS, 0) == 6,
				"a blink must cool itself and its base time stop by 3 * ticks / 6");
		check(!shadowPower.cooldowns().containsKey(TimeStopCooldowns.TIME_STOP)
				&& !shadowPower.cooldowns().containsKey(TimeStopCooldowns.TIME_STOP_BLINK),
				"an add-on blink must not cool The World's names");

		StubPower cooling = power(timeStop(SHADOW, SHADOW_TS), shadowBlink);
		cooling.setAbilityCooldown(SHADOW_TS, 50);
		TimeStopCooldowns.setTimeStopBlinkCooldowns(cooling, shadowBlink, 12);
		check(cooling.cooldowns().get(SHADOW_TS) == 50 && cooling.cooldowns().get(SHADOW_BLINK) == 6,
				"a base time stop already cooling down keeps its own cooldown");

		TimeStopBlinkAbility worldBlink = blink(THE_WORLD, TimeStopCooldowns.TIME_STOP_BLINK, null);
		StubPower byAbility = power(timeStop(THE_WORLD, TimeStopLearning.TIME_STOP), worldBlink);
		StubPower byName = power(timeStop(THE_WORLD, TimeStopLearning.TIME_STOP), worldBlink);
		TimeStopCooldowns.setTimeStopBlinkCooldowns(byAbility, worldBlink, 12);
		TimeStopCooldowns.setTimeStopBlinkCooldowns(byName, 12);
		check(byAbility.cooldowns().equals(byName.cooldowns())
				&& byName.cooldowns().getOrDefault(TimeStopCooldowns.TIME_STOP_BLINK, 0) == 6
				&& byName.cooldowns().getOrDefault(TimeStopCooldowns.TIME_STOP, 0) == 6,
				"The World's blink cooldown must stay on time_stop_blink and time_stop");
	}

	private static void verifySourceWiring() {
		Path root = Path.of(System.getProperty("user.dir"));
		String state = read(root.resolve("src/main/java/rotp/core/subsystems/timestop/TimeStopState.java"));
		String learning = read(root.resolve("src/main/java/rotp/core/subsystems/timestop/TimeStopLearning.java"));
		String ability = read(root.resolve("src/main/java/rotp/core/impl/stands/theworld/TimeStopAbility.java"));
		String blink = read(root.resolve("src/main/java/rotp/core/impl/stands/theworld/TimeStopBlinkAbility.java"));

		String settle = between(state,
				"private void applyTimeStopCooldowns(List<Instance> removedInstances)",
				"private String getCooldownAbilityName(Instance removed, StandPower power)");
		int starter = settle.indexOf("getStartingAbility(removed, power)");
		check(starter >= 0 && starter < settle.indexOf("timeStopStarters.remove(removed.id());"),
				"the starting time stop must be read before the starter is dropped");
		check(settle.contains("settlement.timeStop(),\n                    settlement.effectiveTicksPassed());")
				&& settle.indexOf("refundUnusedTimeStopStartCost(") < settle.indexOf("TimeStopLearning.onTimeStopEnded("),
				"learning must go to the starting time stop, after the refund is priced");
		// the refund is a share of the start's own recorded charge: TimeStopRefundPolicySmokeTest

		String ended = between(learning,
				"public static void onTimeStopEnded(StandPower standPower, @Nullable Ability timeStop, int ticksPassed)",
				"public static void onTsPunchTimeSkip");
		check(ended.contains("addTimeStopLearning(standPower, getLearningName(timeStop),")
				&& ended.contains("timeStop.isAbilityUnlocked(standPower)"),
				"an ended time stop must learn under its own key while it is unlocked");
		check(!learning.contains("getAbilityLearningProgressPoints(TIME_STOP)")
				&& !learning.contains("addAbilityLearningProgressPoints(TIME_STOP,"),
				"no learning read or write may stay fixed on time_stop");
		check(between(learning, "public static void applyDailyDecay", "static Set<String> getTimeStopLearningNames")
						.contains("for (String learningName : getTimeStopLearningNames(standPower))"),
				"daily decay must run for each time stop of the Stand");

		check(ability.contains("return abilityId.powerTypeId() != null ? name() : TimeStopLearning.TIME_STOP;"),
				"a time stop must train under its own moveset name");
		String start = between(ability,
				"private boolean startTimeStopAfterHold(LivingEntity user, boolean standAlreadySummoned, int requestedTimeStopTicks)",
				"public static class TimeStopAction");
		check(start.contains("TimeStopLearning.getTimeStopTicks(power, learningName)")
				&& start.contains("TimeStopLearning.getTimeStopStaminaCostTick(power, learningName)")
				&& start.contains("power, learningName, instance.totalTicks())"),
				"a started time stop must be timed and priced by its own training");
		String release = between(ability, "public void onButtonStopHold()", "LivingComponentAction.getComponent(performer)");
		check(release.contains("TimeStopLearning.getSavedTimeStopTicks(power, learningName)")
				&& release.contains("getAbilityLearningProgressPoints(learningName)"),
				"an early release must read the released time stop's own training");

		check(blink.contains("TimeStopLearning.getLearningName(power, timeStopAbilityName)")
				&& blink.contains("TimeStopLearning.getTimeStopTicks(power, learningName)")
				&& blink.contains("TimeStopCooldowns.setTimeStopBlinkCooldowns(power, this, impliedTicks);"),
				"a blink must reach, cost and cool down by the time stop it is bound to");
	}

	private static TimeStopAbility timeStop(ResourceLocation stand, String name) {
		return new AbilityType<TimeStopAbility>(id("jojo_ripples", "time_stop"), TimeStopAbility::new)
				.createInstance(new AbilityId(null, stand, name));
	}

	private static TimeStopBlinkAbility blink(ResourceLocation stand, String name, String timeStopName) {
		TimeStopBlinkAbility blink = new AbilityType<TimeStopBlinkAbility>(id("jojo_ripples", "time_stop_blink"),
				TimeStopBlinkAbility::new).createInstance(new AbilityId(null, stand, name));
		return timeStopName != null ? blink.setTimeStopAbilityName(timeStopName) : blink;
	}

	private static StubPower power(Ability... abilities) {
		Map<String, Ability> byName = new LinkedHashMap<>();
		for (Ability ability : abilities) {
			byName.put(ability.name(), ability);
		}
		StubPower power = allocate(StubPower.class);
		power.moveset = new Moveset(byName, null, null);
		power.data = allocate(StubData.class);
		return power;
	}

	/** A Stand user with no entity: human limits, not creative, no policy. */
	private static final class StubPower extends StandPower {
		private Moveset moveset;
		private StubData data;
		private Map<String, Integer> cooldowns;

		private StubPower() {
			super(null);
		}

		Map<String, Integer> cooldowns() {
			if (cooldowns == null) {
				cooldowns = new HashMap<>();
			}
			return cooldowns;
		}

		Map<String, Float> learning() {
			return data.learning();
		}

		@Override
		public LivingEntity getUser() {
			return null;
		}

		@Override
		public StandType getPowerType() {
			return null;
		}

		@Override
		public boolean hasPower() {
			return true;
		}

		@Override
		public Moveset getMoveset() {
			return moveset;
		}

		@Override
		public StandTypePersistentData getCurTypeData() {
			return data;
		}

		@Override
		public int getAbilityCooldown(String abilityName) {
			return cooldowns().getOrDefault(abilityName, 0);
		}

		@Override
		public boolean isAbilityOnCooldown(String abilityName) {
			return getAbilityCooldown(abilityName) > 0;
		}

		@Override
		public void setAbilityCooldown(String abilityName, int cooldown) {
			cooldowns().put(abilityName, cooldown);
		}
	}

	private static final class StubData extends StandTypePersistentData {
		private Map<String, Float> learning;

		private StubData() {
			super(null);
		}

		Map<String, Float> learning() {
			if (learning == null) {
				learning = new HashMap<>();
			}
			return learning;
		}

		@Override
		public float getAbilityLearningProgressPoints(String abilityName) {
			return learning().getOrDefault(abilityName, -1.0F);
		}
	}

	private static <T> T allocate(Class<T> type) {
		try {
			Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
			Field singleton = unsafeClass.getDeclaredField("theUnsafe");
			singleton.setAccessible(true);
			Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
			return type.cast(allocateInstance.invoke(singleton.get(null), type));
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError("could not allocate " + type.getSimpleName(), e);
		}
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
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

	private static void checkClose(float actual, float expected, String message) {
		if (Math.abs(actual - expected) > EPSILON) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
