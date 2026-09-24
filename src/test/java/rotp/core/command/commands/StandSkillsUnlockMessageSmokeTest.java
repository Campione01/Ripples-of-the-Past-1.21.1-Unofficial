package rotp.core.command.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * "/jojo_ripples stand_skills unlock" is a port command (1.16 had none). Its failed texts take the skill and then
 * the player (or the player count), but the failure was thrown with the player alone, and a translation missing
 * an argument prints its raw pattern ("Failed to unlock Stand skill %s for %s").
 */
public final class StandSkillsUnlockMessageSmokeTest {
	// net.minecraft.network.chat.contents.TranslatableContents.FORMAT_PATTERN
	private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
	private static final String[] LOCALES = { "en_us", "en_pt", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br",
			"ru_ru", "uk_ua", "zh_cn", "zh_tw" };

	private StandSkillsUnlockMessageSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		verifyExceptions();
		verifyTranslations();
		verifySource();
	}

	// Creating the exception formats the text, which needs the game's language, so the text is built directly;
	// StandSkillsCommandGameTests throws it through the command.
	private static void verifyExceptions() {
		Component player = Component.literal("Steve");
		TranslatableContents single = contents(StandSkillsCommand.unlockFailedMessage(false, "star_finger", player));
		check("commands.standskills.unlock.failed.single".equals(single.getKey()),
				"a single target fails with the single text");
		check(single.getArgs().length == 2 && "star_finger".equals(single.getArgs()[0]) && single.getArgs()[1] == player,
				"the single failure names the skill, then the player");
		TranslatableContents multiple = contents(StandSkillsCommand.unlockFailedMessage(true, "star_finger", 3));
		check("commands.standskills.unlock.failed.multiple".equals(multiple.getKey()),
				"several targets fail with the multiple text");
		check(multiple.getArgs().length == 2 && "star_finger".equals(multiple.getArgs()[0])
				&& Integer.valueOf(3).equals(multiple.getArgs()[1]),
				"the multiple failure names the skill, then the player count");
	}

	// Every locale's text must use exactly the two arguments it gets, or the game prints the raw pattern.
	private static void verifyTranslations() {
		for (String locale : LOCALES) {
			JsonObject lang = JsonParser.parseString(read("src/main/resources/assets/jojo_ripples/lang/" + locale + ".json"))
					.getAsJsonObject();
			for (String key : new String[] { "commands.standskills.unlock.failed.single",
					"commands.standskills.unlock.failed.multiple",
					"commands.standskills.unlock.success.single",
					"commands.standskills.unlock.success.multiple" }) {
				check(lang.has(key), locale + " lacks " + key);
				check(argumentsUsed(lang.get(key).getAsString(), 2).equals(Set.of(0, 1)),
						locale + " " + key + " must use the skill and the player (or count), and nothing more");
			}
		}
		JsonObject en = JsonParser.parseString(read("src/main/resources/assets/jojo_ripples/lang/en_us.json")).getAsJsonObject();
		check(en.get("commands.standskills.unlock.failed.single").getAsString().contains("already unlocked"),
				"a single target only fails when the skill is already unlocked, and the text says so");
	}

	private static void verifySource() {
		String command = squash(read("src/main/java/rotp/core/command/commands/StandSkillsCommand.java"));
		check(command.contains(squash("UNLOCK_FAILED_SINGLE = new Dynamic2CommandExceptionType("
				+ "(skill, player) -> unlockFailedMessage(false, skill, player));"))
				&& command.contains(squash("UNLOCK_FAILED_MULTIPLE = new Dynamic2CommandExceptionType("
						+ "(skill, playerCount) -> unlockFailedMessage(true, skill, playerCount));")),
				"the failures must build the texts checked above");
		String unlock = between(command,
				"privatestaticintunlockSkill(CommandSourceStacksource,Collection<ServerPlayer>targets,StringskillName)",
				"privatestaticintunlockAllSkills(");
		check(!unlock.contains("UNLOCK_MSG.trySend(") && !unlock.contains("UNLOCK_MSG.fail"),
				"the shared failure takes only the player, so unlock must not throw it");
		check(unlock.contains(squash("if (successful <= 0) { throw targets.size() == 1"
				+ " ? UNLOCK_FAILED_SINGLE.create(skillName, targets.iterator().next().getDisplayName())"
				+ " : UNLOCK_FAILED_MULTIPLE.create(skillName, targets.size()); }")),
				"a failed unlock must name the skill and the player or the player count");
		check(unlock.contains(squash("UNLOCK_MSG.success.send(source, true, targets, successful,"
				+ " new Object[] { skillName }, new Object[] { skillName });")),
				"the success text still gets the skill before the player");
	}

	// The 0-based argument indices a text uses, per TranslatableContents.decomposeTemplate.
	private static Set<Integer> argumentsUsed(String text, int argumentCount) {
		Set<Integer> used = new HashSet<>();
		Matcher matcher = FORMAT.matcher(text);
		int next = 0;
		while (matcher.find()) {
			String type = matcher.group(2);
			if ("%".equals(type)) {
				continue;
			}
			check("s".equals(type), "unsupported format in: " + text);
			int index = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : next++;
			check(index >= 0 && index < argumentCount, "argument " + (index + 1) + " is missing for: " + text);
			used.add(index);
		}
		return used;
	}

	private static TranslatableContents contents(Component message) {
		check(message.getContents() instanceof TranslatableContents, "the failure must be a translatable text");
		return (TranslatableContents) message.getContents();
	}

	private static String between(String source, String start, String end) {
		int from = source.indexOf(start);
		int to = from < 0 ? -1 : source.indexOf(end, from);
		check(from >= 0 && to > from, "failed to locate " + start);
		return source.substring(from, to);
	}

	private static String squash(String source) {
		return source.replaceAll("\\s+", "");
	}

	private static String read(String relativePath) {
		Path path = Path.of(System.getProperty("user.dir")).resolve(relativePath);
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
