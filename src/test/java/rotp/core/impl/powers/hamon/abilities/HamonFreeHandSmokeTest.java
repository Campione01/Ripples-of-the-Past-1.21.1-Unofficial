package rotp.core.impl.powers.hamon.abilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 1.16 Action.checkHeldItems and the Hamon overrides asked MCUtil.isHandFree / areHandsFree, which count the Gloves
 * and Bubble Gloves (GlovesItem.openFingers) as a free hand. The port's rule is UtilFunctions.isHandFree /
 * areHandsFree / itemHandFree; an ItemStack.isEmpty() hand gate refuses gloves. The world checks are in
 * HamonGlovesPillarmanFlagGameTests.
 */
public final class HamonFreeHandSmokeTest {
	private static final String HAMON = "src/main/java/rotp/core/impl/powers/hamon/";
	private static final Pattern EMPTY_HAND_GATE = Pattern.compile(
			"(getMainHandItem\\(\\)|getOffhandItem\\(\\)|getItemInHand\\([^()]*(\\([^()]*\\))?[^()]*\\))\\s*\\.isEmpty\\(\\)");

	private HamonFreeHandSmokeTest() {}

	public static void run() {
		String util = read("src/main/java/rotp/core/util/functions/UtilFunctions.java");
		check(util.contains("stack.getItem() instanceof GlovesItem gloves")
						&& util.contains("gloves.openFingers()"),
				"UtilFunctions.itemHandFree no longer counts gloves as a free hand");

		for (String file : List.of(
				"HamonHealingAbility.java",
				"HamonSendoOverdriveAbility.java",
				"HamonSunlightYellowOverdriveAbility.java",
				"HamonOverdriveAbility.java",
				"HamonOverdriveBeatAbility.java",
				"HamonTurquoiseBlueOverdriveAbility.java",
				"HamonZoomPunchAbility.java",
				"HamonLifeMagnetismAbility.java")) {
			String source = read(HAMON + "abilities/" + file);
			check(source.contains("UtilFunctions.isHandFree("), file + " lost the 1.16 free-hand helper");
			check(!EMPTY_HAND_GATE.matcher(source).find(), file + " gates a hand on ItemStack.isEmpty(), refusing gloves");
		}
		for (String file : List.of(
				"HamonOverdriveBarrageAbility.java",
				"HamonSunlightYellowOverdriveBarrageAbility.java",
				"HamonWallClimbingAbility.java",
				"HamonRebuffOverdriveAbility.java")) {
			String source = read(HAMON + "abilities/" + file);
			check(source.contains("UtilFunctions.areHandsFree(user, InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND)"),
					file + " lost the 1.16 both-hands helper");
			check(!EMPTY_HAND_GATE.matcher(source).find(), file + " gates a hand on ItemStack.isEmpty(), refusing gloves");
		}
		String climbClient = read(HAMON + "client/HamonWallClimbingClientHelper.java");
		check(climbClient.contains("!UtilFunctions.itemHandFree(player.getMainHandItem())")
						&& climbClient.contains("!UtilFunctions.itemHandFree(player.getOffhandItem())")
						&& !EMPTY_HAND_GATE.matcher(climbClient).find(),
				"1.16 wall climbing kept going with gloves in hand (MCUtil.itemHandFree)");

		String syo = read(HAMON + "abilities/HamonSunlightYellowOverdriveAbility.java");
		check(syo.contains("return UtilFunctions.isHandFree(user, getRequiredFreeHand());"),
				"S.Y.O. / Scarlet Overdrive free-hand gate (press, hold and punch) drifted");
		String punch = between(syo, "private void performPunch(LivingEntity user)", "protected void doHamonAttack(");
		int hamonGate = punch.indexOf("if (ability.isRequiredHandFree(user)) {");
		int melee = punch.indexOf("HamonAbilityHelpers.doMeleeAttack(user, livingTarget);");
		check(hamonGate >= 0 && melee > hamonGate,
				"the S.Y.O. punch must gate only its Hamon part on the free hand");

		String beat = read(HAMON + "abilities/HamonOverdriveBeatAbility.java");
		String beatPunch = between(beat, "private void punch(LivingEntity user)", "public void onActionCleared(");
		int beatGate = beatPunch.indexOf("if (UtilFunctions.isHandFree(user, InteractionHand.OFF_HAND)) {");
		int beatMelee = beatPunch.indexOf("HamonAbilityHelpers.doMeleeAttack(user, livingTarget);");
		check(beatGate >= 0 && beatMelee > beatGate
						&& beatPunch.indexOf("if (hamon == null) {") >= 0,
				"1.16 Overdrive Beat: a filled off hand loses only the Hamon part, the melee hit still lands");

		for (String file : List.of(
				"HamonSendoOverdriveAbility.java",
				"HamonOverdriveBarrageAbility.java",
				"HamonSunlightYellowOverdriveBarrageAbility.java",
				"HamonHealingAbility.java")) {
			String source = read(HAMON + "abilities/" + file);
			check(source.contains("protected ConditionCheck checkHeldItems(LivingEntity user)"),
					file + " must keep its free-hand check in checkHeldItems (press and every held tick)");
		}
	}

	private static String read(String relative) {
		Path path = Path.of(System.getProperty("user.dir")).resolve(relative);
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new AssertionError("failed to read " + path, e);
		}
	}

	private static String between(String text, String start, String end) {
		int from = text.indexOf(start);
		int to = from >= 0 ? text.indexOf(end, from + start.length()) : -1;
		check(from >= 0 && to > from, "source section missing: " + start + " ... " + end);
		return text.substring(from, to);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
