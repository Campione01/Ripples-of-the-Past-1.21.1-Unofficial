package rotp.core.command.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 /stand clear ran power.clear() and power.fullStandClear(): ResolveCounter.onClearStandType dropped the Resolve
 * value, boosts and records, and clearLevels the Resolve levels. /stand give and /stand random with replace ran
 * power.clear() first. StandRemoveResolveGameTests runs the commands and the stand remover item in the game.
 */
public final class StandRemoveResolveSmokeTest {
	private static final String STAND_COMMAND = "src/main/java/rotp/core/command/commands/StandCommand.java";
	private static final String STAND_POWER = "src/main/java/rotp/core/powersystem/standpower/StandPower.java";

	private StandRemoveResolveSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		String command = compact(source(STAND_COMMAND));
		String remove = between(command, "privatestaticintremoveStand(", "publicstaticCollection<StandPower>getStands(");
		check(remove.contains("singlePrevType=stand.getPowerType();}stand.applyDestructiveTransition(true);i++;"),
				"/jojo_ripples stand remove must clear as 1.16 /stand clear did (clear and fullStandClear)");
		check(!remove.contains("setStand(null)"), "/jojo_ripples stand remove only takes the Stand away again");

		String give = between(command, "privatestaticintsetStand(", "privatestaticvoidclearBeforeReplace(");
		check(give.contains("if(stand!=null&&(replace||!stand.hasPower())){clearBeforeReplace(stand);stand.setStand(standType);"),
				"/jojo_ripples stand give ... true must clear the power first, as 1.16 did");
		String random = between(command, "privatestaticintsetRandomStand(", "privatestaticintremoveStand(");
		check(random.contains("if(standType!=null){clearBeforeReplace(stand);stand.setStand(standType);"),
				"/jojo_ripples stand random ... true must clear the power first, as 1.16 did");
		check(command.contains("privatestaticvoidclearBeforeReplace(StandPowerstand){if(stand.hasPower()){"
				+ "stand.applyDestructiveTransition(false);}}"), "the replace clear is 1.16 power.clear()");

		// the transition both commands share with the stand remover items
		String power = compact(source(STAND_POWER));
		String transition = between(power, "publicvoidapplyDestructiveTransition(booleanfullReset){", "publicvoidclientApplyFullStandClear(");
		check(transition.contains("setStandInstance(Optional.empty());")
				&& transition.contains("resolveCounter.resetResolveValue(this);")
				&& transition.contains("if(fullReset){clearFullStandProgressionState();"),
				"the destructive Stand transition no longer resets the Resolve value or the progression");
	}

	private static String between(String text, String from, String to) {
		int start = text.indexOf(from);
		check(start >= 0, "missing " + from);
		int end = text.indexOf(to, start + from.length());
		check(end > start, "missing " + to + " after " + from);
		return text.substring(start, end);
	}

	private static String compact(String source) {
		return source
				.replaceAll("(?s)/\\*.*?\\*/", "")
				.replaceAll("//[^\\n]*", "")
				.replaceAll("\\s+", "");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
