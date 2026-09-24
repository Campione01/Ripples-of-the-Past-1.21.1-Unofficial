package rotp.core.command.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.impl.powers.pillarman.PillarmanMode;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * 1.16 PillarmanModeCommand passed the Mode enum to its success message, printed by name. A 1.21 translation argument
 * must be a Component, Number, Boolean or String, so /pillarman set mode threw IllegalArgumentException after
 * setting the mode. PillarmanModeCommandGameTests runs the command.
 */
public final class PillarmanModeCommandSmokeTest {
	private static final String COMMAND = "src/main/java/rotp/core/command/commands/PillarmanModeCommand.java";

	private PillarmanModeCommandSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		for (PillarmanMode mode : PillarmanMode.values()) {
			Component single = PillarmanModeCommand.modeSuccessSingle(mode, Component.literal("Probe"));
			Component multiple = PillarmanModeCommand.modeSuccessMultiple(mode, 3);
			check(args(single)[0].equals(mode.name()) && args(multiple)[0].equals(mode.name()),
					"the mode message must print the mode by name, as 1.16 did: " + mode);
			check(args(multiple)[1].equals(3), "the multiple-target message lost its player count");
		}

		String command = compact(source(COMMAND));
		String setMode = between(command, "privatestaticintsetMode(", "staticComponentmodeSuccessSingle(");
		check(setMode.contains("source.sendSuccess(()->modeSuccessSingle(selectedMode,targetName),true);")
				&& setMode.contains("source.sendSuccess(()->modeSuccessMultiple(selectedMode,successCount),true);"),
				"/pillarman set mode must build its message through the checked helpers");
		check(!setMode.contains("Component.translatable("), "/pillarman set mode builds a raw message again");
	}

	private static Object[] args(Component message) {
		check(message.getContents() instanceof TranslatableContents, "the mode message is not a translation");
		return ((TranslatableContents) message.getContents()).getArgs();
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
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
