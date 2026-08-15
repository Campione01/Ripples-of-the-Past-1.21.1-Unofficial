package com.github.standobyte.jojoimpl.powers.hamon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonOverdriveChargeReleaseSmokeTest;

public final class HamonPayloadContractSmokeTest {
	private HamonPayloadContractSmokeTest() {}

	public static void run() {
		HamonOverdriveChargeReleaseSmokeTest.run();
		testTrainingDayThresholds();
		testStatTrainingLimit();
		testClearLifecycleContracts();
		testSyncedConfigAndUiContracts();

		Set<String> known = HamonTeachersSkillsPacket.knownTeacherSkills(
				Set.of("overdrive", "not_a_registered_hamon_skill"),
				"overdrive"::equals);
		check(known.equals(Set.of("overdrive")),
				"teacher-skill handler allowlist must retain known skills and remove unknown names");

		Set<String> maximumTeacherSkills = new HashSet<>();
		for (int i = 0; i < 128; i++) {
			maximumTeacherSkills.add("extension_skill_" + i);
		}
		HamonTeachersSkillsPacket maximumTeacherPacket =
				new HamonTeachersSkillsPacket(maximumTeacherSkills);
		check(maximumTeacherPacket.teacherSkills().size() == 128,
				"fixed teacher-skill wire capacity must allow extension headroom");
		maximumTeacherSkills.add("extension_skill_128");
		expectThrows(IllegalArgumentException.class,
				() -> new HamonTeachersSkillsPacket(maximumTeacherSkills),
				"teacher-skill packet must reject count above fixed wire capacity");
		expectThrows(IllegalArgumentException.class,
				() -> new HamonTeachersSkillsPacket(Set.of("x".repeat(129))),
				"teacher-skill packet must reject names above fixed wire capacity");

		List<String> maximumLearnableSkills = new ArrayList<>();
		for (int i = 0; i < 128; i++) {
			maximumLearnableSkills.add("extension_skill_" + i);
		}
		HamonStatFeedbackPacket maximumFeedback = new HamonStatFeedbackPacket(
				1, HamonStatFeedbackPacket.Stat.STRENGTH, 1, 0.0F,
				false, maximumLearnableSkills);
		check(maximumFeedback.newlyLearnableSkills().size() == 128,
				"fixed learnable-skill wire capacity must allow extension headroom");
		maximumLearnableSkills.add("extension_skill_128");
		expectThrows(IllegalArgumentException.class,
				() -> new HamonStatFeedbackPacket(
						1, HamonStatFeedbackPacket.Stat.STRENGTH, 1, 0.0F,
						false, maximumLearnableSkills),
				"learnable-skill feedback must reject count above fixed wire capacity");
		expectThrows(IllegalArgumentException.class,
				() -> new HamonStatFeedbackPacket(
						1, HamonStatFeedbackPacket.Stat.STRENGTH, 1, 0.0F,
						false, List.of("x".repeat(129))),
				"learnable-skill feedback must reject names above fixed wire capacity");
	}

	private static void testClearLifecycleContracts() {
		String source = readSource(Path.of(
				"src/main/java/com/github/standobyte/jojoimpl/powers/hamon/HamonData.java"));
		check(source.contains("ServerPlayerGameModeAccessor")
						&& source.contains(".jojo_ripples$hasDelayedDestroy()")
						&& !source.contains("gameMode.hasDelayedDestroy"),
				"server Hamon mining exercise ignores delayed block destruction");
		String accessor = readSource(Path.of(
				"src/main/java/com/github/standobyte/jojo/mixin/hamon/ServerPlayerGameModeAccessor.java"));
		check(accessor.contains("@Mixin(ServerPlayerGameMode.class)")
						&& accessor.contains("@Accessor(\"hasDelayedDestroy\")")
						&& accessor.contains("boolean jojo_ripples$hasDelayedDestroy();"),
				"delayed block-destruction accessor contract is missing");
		String mixins = readSource(Path.of(
				"src/main/resources/jojo_ripples.mixins.json"));
		check(mixins.contains("\"hamon.ServerPlayerGameModeAccessor\""),
				"delayed block-destruction accessor is not registered as a common mixin");
		String accessTransformer = readSource(Path.of(
				"src/main/resources/META-INF/accesstransformer.cfg"));
		check(!accessTransformer.contains("ServerPlayerGameMode hasDelayedDestroy"),
				"delayed block-destruction access must not depend on transformed compile caches");
		int callbackStart = source.indexOf("public void onPowerCleared(");
		int suspendStart = source.indexOf(
				"public void onTemporaryPowerSuspended(", callbackStart);
		int restoreStart = source.indexOf(
				"public void onTemporaryPowerRestored(", suspendStart);
		int endedStart = source.indexOf(
				"public void onTemporaryPowerEnded(", restoreStart);
		int callbackEnd = source.indexOf(
				"private void tickHamonAura(", endedStart);
		check(callbackStart >= 0 && suspendStart > callbackStart
						&& restoreStart > suspendStart
						&& endedStart > restoreStart
						&& callbackEnd > endedStart,
				"Hamon power lifecycle callback boundary is missing");
		String ordinaryClear = source.substring(callbackStart, suspendStart);
		int stopMeditation = ordinaryClear.indexOf(
				"setIsMeditating(user, false);");
		int clearModifiers = ordinaryClear.indexOf(
				"removeTrainingAttributeModifiers(user);");
		check(stopMeditation >= 0 && clearModifiers > stopMeditation,
				"Hamon clear must stop meditation before removing modifiers");

		String suspension = source.substring(suspendStart, restoreStart);
		int stopSuspendedMeditation = suspension.indexOf(
				"setIsMeditating(user, false);");
		int removeSuspendedModifiers = suspension.indexOf(
				"removeTrainingAttributeModifiers(user);");
		check(stopSuspendedMeditation >= 0
						&& removeSuspendedModifiers > stopSuspendedMeditation,
				"retained Hamon meditation or modifiers remain active while suspended");

		String restoration = source.substring(restoreStart, endedStart);
		check(restoration.contains("giveBreathingTrainingBuffs(user);")
						&& restoration.contains("updateExerciseAttributes(user);"),
				"retained Hamon modifiers are not restored from retained values");

		String temporaryEnd = source.substring(endedStart, callbackEnd);
		int stopTemporaryMeditation = temporaryEnd.indexOf(
				"setIsMeditating(temporaryUser, false);");
		int sourceCleanup = temporaryEnd.indexOf(
				"removeTrainingAttributeModifiers(temporaryUser);");
		int destinationGuard = temporaryEnd.indexOf(
				"restoredPower.getUser() != temporaryUser");
		int destinationCleanup = temporaryEnd.indexOf(
				"removeTrainingAttributeModifiers(restoredPower.getUser());");
		check(stopTemporaryMeditation >= 0
						&& sourceCleanup > stopTemporaryMeditation
						&& destinationGuard > sourceCleanup
						&& destinationCleanup > destinationGuard,
				"temporary Hamon end does not clean source and destination");

		String temporaryLifecycle = source.substring(suspendStart, callbackEnd);
		for (String destructiveMutation : List.of(
				"setBreathingLevel(",
				"clearExerciseTicks(",
				"setTrainingTicks(")) {
			check(!temporaryLifecycle.contains(destructiveMutation),
					"temporary Hamon lifecycle mutates retained training: "
							+ destructiveMutation);
		}

		int cleanupStart = source.indexOf(
				"private void removeTrainingAttributeModifiers(");
		int cleanupEnd = source.indexOf(
				"private static void updateAttributeModifier(", cleanupStart);
		check(cleanupStart >= 0 && cleanupEnd > cleanupStart,
				"Hamon training-modifier cleanup boundary is missing");
		String cleanup = source.substring(cleanupStart, cleanupEnd);
		for (String modifier : List.of(
				"BREATHING_TRAINING_ATTACK_DAMAGE",
				"BREATHING_TRAINING_ATTACK_SPEED",
				"BREATHING_TRAINING_MOVEMENT_SPEED",
				"BREATHING_TRAINING_SWIMMING_SPEED",
				"RUNNING_COMPLETED",
				"MINING_COMPLETED")) {
			check(cleanup.contains(modifier),
					"Hamon clear cleanup is missing modifier: " + modifier);
		}
		check(cleanup.contains("user.level().isClientSide()")
						&& cleanup.contains(
								"lastAppliedBreathingBuffLevel = Integer.MIN_VALUE;")
						&& cleanup.contains(
								"lastAppliedExerciseMask = Integer.MIN_VALUE;"),
				"Hamon clear cleanup must be server-safe and repeatable");

		String abandonPacket = readSource(Path.of(
				"src/main/java/com/github/standobyte/jojoimpl/powers/hamon/ClHamonAbandonButtonPacket.java"));
		check(abandonPacket.contains("power.setPowerType(null);"),
				"Hamon abandon must use the ordinary destructive transition");

		String playerPower = readSource(Path.of(
				"src/main/java/com/github/standobyte/jojo/powersystem/playerpower/PlayerPower.java"));
		int setterStart = playerPower.indexOf("public void setPowerType(");
		int setterEnd = playerPower.indexOf(
				"public void applyTrackedPowerType(", setterStart);
		check(setterStart >= 0 && setterEnd > setterStart,
				"ordinary PlayerPower setter boundary is missing");
		String setter = playerPower.substring(setterStart, setterEnd);
		int freshGrant = setter.indexOf("discardStalePowerDataForGrant(");
		int createData = setter.indexOf("onSetPowerType(old, type);");
		check(freshGrant >= 0 && createData > freshGrant,
				"Hamon reacquisition can reuse inactive cached training data");
		String deserialize = section(
				playerPower,
				"public void deserializeNBT(",
				"public static PlayerPower get(");
		check(deserialize.contains(
						"nbt.contains(\"PowerType\", net.minecraft.nbt.Tag.TAG_STRING)")
						&& deserialize.contains("this.curPowerType = Optional.empty();")
						&& deserialize.contains("catch (RuntimeException error)"),
				"legacy PlayerPower load does not tolerate missing or invalid IDs");

		int beginStart = playerPower.indexOf(
				"public boolean beginTemporaryTransition(");
		int beginEnd = playerPower.indexOf(
				"public boolean endTemporaryTransition()", beginStart);
		check(beginStart >= 0 && beginEnd > beginStart,
				"temporary PlayerPower begin boundary is missing");
		String begin = playerPower.substring(beginStart, beginEnd);
		int nonDelegatedGuard = begin.indexOf("if (!delegates)");
		int suspensionCallback = begin.indexOf(
				"previousData.onTemporaryPowerSuspended(");
		check(nonDelegatedGuard >= 0
						&& suspensionCallback > nonDelegatedGuard,
				"delegated Hamon is incorrectly suspended");
	}

	private static void testTrainingDayThresholds() {
		check(close(HamonData.dailyExerciseLevelChange(0, 0.0F), -1.0F),
				"no daily exercise must produce the maximum base deterioration");
		check(close(HamonData.dailyExerciseLevelChange(2, 0.0F), 0.0F),
				"exactly 50 percent daily exercise progress must not increase breathing");
		check(HamonData.dailyExerciseLevelChange(2, 0.01F) > 0.0F,
				"daily exercise progress just above 50 percent must increase breathing");
		check(close(HamonData.dailyExerciseLevelChange(2, 0.5F), 0.5F),
				"partial third exercise must contribute continuously to day-end growth");
		check(close(HamonData.dailyExerciseLevelChange(3, 0.0F), 1.0F),
				"three complete exercises must produce the maximum base increase");
	}

	private static void testStatTrainingLimit() {
		int levelTenLastPoint = HamonData.pointsAtLevel(11) - 1;
		int levelElevenStart = HamonData.pointsAtLevel(11);
		check(HamonData.applyTrainingStatLimit(levelElevenStart, false, 10.0F, -1)
				== levelElevenStart,
				"default gap -1 must leave ordinary stat training unrestricted");
		check(HamonData.applyTrainingStatLimit(levelTenLastPoint, false, 10.0F, 0)
				== levelTenLastPoint,
				"configured gap must allow all progress inside the limit level");
		check(HamonData.applyTrainingStatLimit(levelElevenStart, false, 10.0F, 0)
				== levelTenLastPoint,
				"ordinary training must stop at the final point before levelLimit + 1");
		check(HamonData.applyTrainingStatLimit(levelElevenStart, true, 10.0F, 0)
				== levelElevenStart,
				"command and S2C ignoreTraining paths must bypass the breathing limit");
		check(HamonData.applyTrainingStatLimit(levelElevenStart, false, 11.0F, 0)
				== levelElevenStart,
				"raising breathing training must raise the stat level limit");
		check(HamonData.statLevelLimit(100.0F, Integer.MAX_VALUE) == HamonData.MAX_STAT_LEVEL,
				"configured stat limit arithmetic must clamp without overflow");
	}

	private static void testSyncedConfigAndUiContracts() {
		check(JojoModConfig.getCommonConfigInstance(false).breathingHamonStatGap.get() == -1,
				"breathing stat gap must default to disabled");
		JojoModConfig.applySyncedConfig(new JojoModConfig.Common.SyncedValues(
				JojoModConfig.getCommonConfigInstance(false)));
		check(JojoModConfig.getCommonConfigInstance(true).breathingHamonStatGap.get() == -1,
				"client config apply must include breathing stat gap");
		JojoModConfig.resetSyncedConfig();
		check(JojoModConfig.getCommonConfigInstance(true).breathingHamonStatGap.get() == -1,
				"client config reset must restore the breathing stat gap default");

		String configSource = readSource(Path.of(
				"src/main/java/com/github/standobyte/jojo/JojoModConfig.java"));
		for (String token : List.of(
				"breathingHamonStatGap = ConfigValue.fromSpec(",
				"breathingHamonStatGap = ConfigValue.synced(-1)",
				"breathingHamonStatGap.set(values.breathingHamonStatGap)",
				"breathingHamonStatGap.clearCache()",
				"this.breathingHamonStatGap = config.breathingHamonStatGap.get()",
				"this.breathingHamonStatGap = Mth.clamp(buf.readVarInt(), -1, 100)",
				"buf.writeVarInt(Mth.clamp(breathingHamonStatGap, -1, 100))")) {
			check(configSource.contains(token),
					"common config sync/cache contract is missing: " + token);
		}

		String screenSource = readSource(Path.of(
				"src/main/java/com/github/standobyte/jojoimpl/powers/hamon/client/HamonStatsScreen.java"));
		for (String token : List.of(
				"scrolling.pushOffsetScissor",
				"HAMON_BACKGROUND",
				"renderTiledBackground",
				"hamon.strength_stat.desc",
				"hamon.control_stat.desc",
				"hamon.breathing_stat.desc",
				"jojo_ripples.hamon.training.average.desc",
				"hamon.exercise.all.day_end_increase",
				"hamon.stat_limited",
				"mouseAtMeditationBar",
				"confirmAbandonButton",
				"cancelAbandonButton")) {
			check(screenSource.contains(token),
					"Hamon stats UI source contract is missing: " + token);
		}
		check(!screenSource.contains("meditationButton"),
				"Hamon stats UI retained the non-donor fixed meditation button");
		String render = section(
				screenSource,
				"public void render(GuiGraphics gui",
				"private void renderTiledBackground(");
		int hideWidgets = render.indexOf("setAllButtonsVisible(false);");
		int paper = render.indexOf("super.render(gui");
		int background = render.indexOf("renderTiledBackground(gui");
		int contentsDraw = screenSource.indexOf("renderScrollableContents(gui");
		int abandonDraw = screenSource.indexOf("abandonButton.render(gui", contentsDraw);
		check(hideWidgets >= 0 && paper > hideWidgets && background > paper,
				"Hamon background pass must suppress the inherited widget draw");
		check(contentsDraw >= 0 && abandonDraw > contentsDraw,
				"scrolling Hamon abandon control must render after the contents");
		String meditationClick = section(
				screenSource,
				"public boolean mouseClicked(",
				"private boolean mouseAtMeditationBar(");
		int meditationPacket = meditationClick.indexOf(
				"new ClHamonMeditationPacket()");
		int close = meditationClick.indexOf("onClose();");
		check(meditationPacket >= 0 && close > meditationPacket,
				"clicking the donor meditation bar must send then close the page");

		Path langDir = Path.of("src/main/resources/assets/jojo_ripples/lang");
		try (var languages = Files.list(langDir)) {
			List<Path> shipped = languages.filter(path -> path.toString().endsWith(".json")).toList();
			check(!shipped.isEmpty(), "expected shipped language JSON files");
			for (Path language : shipped) {
				String json = readSource(language);
				check(json.contains("\"jojo.config.breathingStatGap\""),
						language + " must name the breathing stat gap config");
				check(json.contains("\"jojo.config.breathingStatGap.tooltip\""),
						language + " must describe the breathing stat gap config");
			}
		}
		catch (IOException e) {
			throw new AssertionError("failed to enumerate shipped language files", e);
		}
	}

	private static String readSource(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new AssertionError("failed to read source contract: " + path, e);
		}
	}

	private static String section(String source, String start, String end) {
		int startIndex = source.indexOf(start);
		int endIndex = source.indexOf(end, startIndex + start.length());
		check(startIndex >= 0 && endIndex > startIndex,
				"source boundary is missing: " + start);
		return source.substring(startIndex, endIndex);
	}

	private static boolean close(float actual, float expected) {
		return Math.abs(actual - expected) < 1.0E-6F;
	}

	private static void expectThrows(
			Class<? extends Throwable> expected, Runnable action, String message) {
		try {
			action.run();
		}
		catch (Throwable actual) {
			if (expected.isInstance(actual)) {
				return;
			}
			throw new AssertionError(message, actual);
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
