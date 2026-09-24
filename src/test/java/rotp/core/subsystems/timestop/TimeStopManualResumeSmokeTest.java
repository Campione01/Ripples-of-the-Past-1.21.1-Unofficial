package rotp.core.subsystems.timestop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.world.level.ChunkPos;

/**
 * 1.16 TimeResume.perform set a time stop to 11 ticks on the first press (the resume sound plays at 10, so time
 * resumes about 0.55 s after the press) and to 0 only on a second press, or when 11 or fewer ticks were left.
 * TimeStopProgressionGameTests runs the whole countdown in a world.
 */
public final class TimeStopManualResumeSmokeTest {
	private static final String STATE = "src/main/java/rotp/core/subsystems/timestop/TimeStopState.java";

	private TimeStopManualResumeSmokeTest() {}

	public static void run() {
		checkPressTicks();
		checkRequestWiring();
	}

	private static void checkPressTicks() {
		check(TimeStopState.Instance.TIME_RESUME_SOUND_TICKS == 10
				&& TimeStopState.Instance.TIME_RESUME_VOICELINE_TICKS == 30
				&& TimeStopState.Instance.TIME_RESUME_FIRST_CLICK_TICKS == 11,
				"the 1.16 resume timings (sound 10, line 30, first press 11) drifted");
		TimeStopState.Instance running = new TimeStopState.Instance(1, 200, 200, new ChunkPos(2, 3), 1, 5, "default");
		check(TimeStopState.manualResumeTicks(running) == 11, "the first press must leave 11 ticks");
		check(TimeStopState.manualResumeTicks(running.withTicksLeft(12, false)) == 11,
				"a first press with 12 ticks left must still leave 11");
		check(TimeStopState.manualResumeTicks(running.withTicksLeft(11, false)) == 0
				&& TimeStopState.manualResumeTicks(running.withTicksLeft(4, false)) == 0,
				"a first press with 11 or fewer ticks left must resume at once");
		TimeStopState.Instance pressed = running.withTicksLeft(11, true);
		check(TimeStopState.manualResumeTicks(pressed) == 0
				&& TimeStopState.manualResumeTicks(pressed.tickDown()) == 0
				&& TimeStopState.manualResumeTicks(running.withTicksLeft(50, true)) == 0,
				"a second press must resume at once, however many ticks the first one left");
		// a press in the opening settle ends the settle, so the 11 ticks count down at once
		TimeStopState.Instance settling = running.withStartupDelay(35);
		check(settling.tickDown().ticksLeft() == 200, "the opening settle must still hold an untouched stop");
		check(settling.withTicksLeft(11, true).tickDown().ticksLeft() == 10,
				"a first press in the opening settle must still resume 11 ticks later");
	}

	private static void checkRequestWiring() {
		String state = code(STATE);
		String request = body(state, "publicbooleanrequestManualResume(intid)");
		check(request.contains("intticks=manualResumeTicks(instance);")
				&& request.contains("Instanceupdated=instance.withTicksLeft(ticks,true,forceResumeVoiceLine);")
				&& !request.contains("withTicksLeft(0,"),
				"requestManualResume must set the 1.16 first-press ticks, not end the stop at once");
		check(request.contains("booleanforceResumeVoiceLine=!instance.ticksManuallySet()"
				+ "&&instance.ticksLeft()>Instance.TIME_RESUME_VOICELINE_TICKS&&ticks<Instance.TIME_RESUME_VOICELINE_TICKS;"),
				"a stop first cut from over 30 ticks must still say its resume line (1.16 alwaysSayVoiceLine)");
		check(request.contains("if(ticks<=0&&instance.ticksLeft()>Instance.TIME_RESUME_SOUND_TICKS){playResumeSound(updated,true);}")
				&& request.indexOf("playResumeSound(") == request.lastIndexOf("playResumeSound("),
				"with 11 ticks left the resume sound must come on its tick, not twice at the press");
		String ticks = body(state, "staticintmanualResumeTicks(Instanceinstance)");
		check(ticks.contains("return!instance.ticksManuallySet()&&instance.ticksLeft()>Instance.TIME_RESUME_FIRST_CLICK_TICKS"
				+ "?Instance.TIME_RESUME_FIRST_CLICK_TICKS:0;"),
				"manualResumeTicks must be 1.16 TimeResume.perform");
		String lifecycle = body(state, "publicvoidtickLifecycle()");
		check(lifecycle.contains("booleanmanualResume=instance.ticksManuallySet()&&instance.ticksLeft()<=0;"),
				"a manually resumed stop must end only when its ticks run out");
	}

	private static String body(String code, String signature) {
		int start = code.indexOf(signature + "{");
		check(start >= 0, STATE + " has no " + signature);
		int open = start + signature.length();
		int depth = 0;
		for (int i = open; i < code.length(); i++) {
			char c = code.charAt(i);
			if (c == '{') {
				depth++;
			}
			else if (c == '}' && --depth == 0) {
				return code.substring(open + 1, i);
			}
		}
		throw new AssertionError(STATE + ": unbalanced " + signature);
	}

	private static String code(String relativePath) {
		Path path = Path.of(System.getProperty("user.dir")).resolve(relativePath);
		try {
			return stripComments(Files.readString(path)).replaceAll("\\s+", "");
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static String stripComments(String source) {
		StringBuilder code = new StringBuilder(source.length());
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '"' || c == '\'') {
				int end = literalEnd(source, i);
				code.append(source, i, end);
				i = end - 1;
			}
			else if (source.startsWith("//", i)) {
				int end = source.indexOf('\n', i);
				i = (end < 0 ? source.length() : end) - 1;
			}
			else if (source.startsWith("/*", i)) {
				int end = source.indexOf("*/", i + 2);
				i = (end < 0 ? source.length() : end + 2) - 1;
			}
			else {
				code.append(c);
			}
		}
		return code.toString();
	}

	private static int literalEnd(String source, int start) {
		char quote = source.charAt(start);
		for (int i = start + 1; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '\\') {
				i++;
			}
			else if (c == quote) {
				return i + 1;
			}
		}
		return source.length();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
