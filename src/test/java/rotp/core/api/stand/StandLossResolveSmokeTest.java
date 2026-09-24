package rotp.core.api.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 cleared the Resolve value whenever a Stand was taken (StandPower.clear / putOutStand ->
 * ResolveCounter.onClearStandType): Boy II Man's third win and the legacy StandPowerTransitions.extract (the add-ons'
 * Whitesnake disc and Mobs With Powers removals) do too now. 1.16 /stand clear on a player without a Stand still
 * wiped the progression (commands.stand.remove.success.single.no_stand). StandLossResolveGameTests runs them.
 */
public final class StandLossResolveSmokeTest {
	private static final String TRANSITIONS = "src/main/java/rotp/core/api/stand/StandPowerTransitions.java";
	private static final String RPS = "src/main/java/rotp/core/impl/npc/rps/RockPaperScissorsGame.java";
	private static final String COMMAND = "src/main/java/rotp/core/command/commands/StandCommand.java";
	private static final String LANG = "src/main/resources/assets/jojo_ripples/lang/";

	private StandLossResolveSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		String transitions = compact(source(TRANSITIONS));
		String legacy = between(transitions, "staticResultextract(PowerAccesspower,ResourceLocationexpectedCurrent){",
				"staticResultreplace(PowerAccesspower,ResourceLocationexpectedCurrent,StandInstancereplacement){");
		check(legacy.contains("power.setStandInstance(Optional.empty());power.resetResolveValue();"),
				"the legacy extract must take the Resolve value with the Stand");
		check(transitions.contains("defaultvoidresetResolveValue(){}")
				&& transitions.contains("publicvoidresetResolveValue(){power.resolveCounter.resetResolveValue(power);}"),
				"the power access no longer resets the Resolve value");

		check(compact(source(RPS)).contains("loserStand.setStandInstance(Optional.empty());"
				+ "loserStand.resolveCounter.resetResolveValue(loserStand);"),
				"Boy II Man's third win must take the Resolve value with the Stand");

		String command = compact(source(COMMAND));
		String remove = between(command, "privatestaticintremoveStand(", "publicstaticCollection<StandPower>getStands(");
		check(remove.contains("if(stand!=null){if(stand.hasPower()){singlePrevType=stand.getPowerType();}"
				+ "stand.applyDestructiveTransition(true);i++;}"),
				"/jojo_ripples stand remove must clear a target without a Stand too");
		check(remove.contains("if(i>0&&targets.size()==1&&singlePrevType==null){"
				+ "Componenttarget=targets.iterator().next().getDisplayName();"
				+ "src.sendSuccess(()->Component.translatable(\"rotp.commands.stand.remove.success.single.no_stand\",target),true);"
				+ "returni;}"), "the no-Stand clear lost its message");
		for (String lang : new String[] { "en_us", "zh_cn" }) {
			check(source(LANG + lang + ".json").contains("\"rotp.commands.stand.remove.success.single.no_stand\""),
					lang + " lacks the no-Stand clear message");
		}
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
