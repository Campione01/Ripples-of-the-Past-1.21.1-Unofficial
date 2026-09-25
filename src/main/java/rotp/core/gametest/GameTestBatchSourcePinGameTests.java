package rotp.core.gametest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTestBatches: a gametest that puts a TimeStopState instance or starts TimeStopAbility must run in the TIME_STOP
 * batch, so its stop cannot freeze a neighbour cell of the default batch. This pin reads the gametest sources, marks
 * every method that starts a time stop (directly, through a helper of its own file, or through a qualified helper of
 * another gametest file) and checks the compiled @GameTest batch of every marked test.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GameTestBatchSourcePinGameTests {
	private static final String PACKAGE = "rotp.core.gametest";
	private static final String SOURCE_DIR = "src/main/java/rotp/core/gametest";
	// Compact (whitespace-free) calls that put a TimeStopState instance or start TimeStopAbility ("time_stop" is the
	// ability id a test presses). Building a TimeStopAbility without performing it does not count.
	private static final List<String> STARTS = List.of("tryPutInstance(", ".putInstance(",
			"newTimeStopState.Instance(", "startTimeStopAfterHold", "\"time_stop\"");
	private static final String INSTANCE_IMPORT = "importrotp.core.subsystems.timestop.TimeStopState.Instance;";
	// Detector self-check: a direct call, an own-file helper, a reflective helper, a pressed ability, a nested helper.
	private static final List<String> MUST_START = List.of(
			"TimeStopBlockEntityGameTests.furnaceInStoppedChunkDoesNotCook",
			"TimeStopKnockbackGameTests.knockbackTakenInStoppedTimeFliesWhenTimeResumes",
			"TimeAbilityAdvancementGameTests.onlyANineSecondTimeStopGrantsTimeStop9",
			"HeldConditionRecheckGameTests.lostStandBodyStopsEarlyTimeStopRelease",
			"FrozenGrabThrowGameTests.frozenThrowResumesOnceWithCollisionImpact");
	private static final List<String> MUST_NOT_START = List.of(
			"FrozenGrabThrowGameTests.normalTimeGrabThrowRemainsImmediate");
	private static final Pattern DECLARATION = Pattern.compile(
			"([\\w$]+(?:<[^;{}()]*>)?(?:\\[\\])*)\\s+([\\w$]+)\\s*\\([^;{}]*?\\)\\s*(?:throws\\s+[\\w.$,\\s]+)?\\{");
	private static final Set<String> NOT_A_TYPE = Set.of("new", "return", "throw", "else", "case", "yield", "assert");
	private static final Set<String> NOT_A_NAME = Set.of("if", "for", "while", "switch", "catch", "synchronized", "try",
			"do");

	private GameTestBatchSourcePinGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void everyTimeStopTestCarriesTimeStopBatch(GameTestHelper helper) {
		List<String> problems = problems();
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Empty when the detector still works and every gametest that starts a time stop carries the TIME_STOP batch. */
	static List<String> problems() {
		List<String> problems = new ArrayList<>();
		Path dir = sourceDir();
		if (dir == null) {
			problems.add("could not find " + SOURCE_DIR + " above " + Path.of("").toAbsolutePath());
			return problems;
		}
		Map<String, Set<String>> starting;
		try {
			starting = startingMethods(dir);
		}
		catch (IOException exception) {
			problems.add("could not read the gametest sources in " + dir + ": " + exception);
			return problems;
		}
		for (String anchor : MUST_START) {
			if (!starts(starting, anchor)) {
				problems.add("the time-stop detector lost " + anchor);
			}
		}
		for (String anchor : MUST_NOT_START) {
			if (starts(starting, anchor)) {
				problems.add("the time-stop detector wrongly marks " + anchor);
			}
		}
		ClassLoader loader = GameTestBatchSourcePinGameTests.class.getClassLoader();
		List<String> wrongBatch = new ArrayList<>();
		int tests = 0;
		for (Map.Entry<String, Set<String>> file : starting.entrySet()) {
			Class<?> type;
			try {
				type = Class.forName(PACKAGE + "." + file.getKey(), false, loader);
			}
			catch (ClassNotFoundException exception) {
				continue; // a helper-only file named unlike its classes
			}
			for (Method method : type.getDeclaredMethods()) {
				GameTest test = method.getAnnotation(GameTest.class);
				if (test == null || !file.getValue().contains(method.getName())) {
					continue;
				}
				tests++;
				if (!GameTestBatches.TIME_STOP.equals(test.batch())) {
					wrongBatch.add(file.getKey() + "." + method.getName() + " (batch " + test.batch() + ")");
				}
			}
		}
		if (tests < MUST_START.size()) {
			problems.add("only " + tests + " time-stop gametests were found");
		}
		if (!wrongBatch.isEmpty()) {
			problems.add("these gametests start a time stop without batch = GameTestBatches.TIME_STOP: " + wrongBatch);
		}
		return problems;
	}

	// The gametest server runs in <project>/build/run-gametest-core.
	private static Path sourceDir() {
		for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
			Path dir = at.resolve(SOURCE_DIR);
			if (Files.isRegularFile(dir.resolve("GameTestBatches.java"))) {
				return dir;
			}
		}
		return null;
	}

	private static boolean starts(Map<String, Set<String>> starting, String classAndMethod) {
		int dot = classAndMethod.indexOf('.');
		Set<String> names = starting.get(classAndMethod.substring(0, dot));
		return names != null && names.contains(classAndMethod.substring(dot + 1));
	}

	/** File (class) name -> names of its methods that start a time stop. */
	static Map<String, Set<String>> startingMethods(Path dir) throws IOException {
		Map<String, Map<String, String>> bodies = new TreeMap<>();
		Map<String, Set<String>> starting = new TreeMap<>();
		List<Path> files;
		try (Stream<Path> list = Files.list(dir)) {
			files = list.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
		}
		for (Path path : files) {
			String name = path.getFileName().toString();
			name = name.substring(0, name.length() - ".java".length());
			if (name.equals(GameTestBatchSourcePinGameTests.class.getSimpleName())) {
				continue; // its own token strings
			}
			String source = stripComments(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
			boolean importsInstance = compact(source).contains(INSTANCE_IMPORT);
			Map<String, String> methods = new TreeMap<>();
			Matcher matcher = DECLARATION.matcher(source);
			while (matcher.find()) {
				if (NOT_A_TYPE.contains(matcher.group(1)) || NOT_A_NAME.contains(matcher.group(2))) {
					continue;
				}
				int open = matcher.end() - 1;
				// overloads share one entry
				methods.merge(matcher.group(2), source.substring(open, bodyEnd(source, open)), String::concat);
			}
			bodies.put(name, methods);
			for (Map.Entry<String, String> method : methods.entrySet()) {
				String body = compact(method.getValue());
				if (STARTS.stream().anyMatch(body::contains) || importsInstance && body.contains("newInstance(")) {
					starting.computeIfAbsent(name, key -> new TreeSet<>()).add(method.getKey());
				}
			}
		}
		// A method that calls a starting helper starts a time stop too.
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Map.Entry<String, Map<String, String>> file : bodies.entrySet()) {
				for (Map.Entry<String, String> method : file.getValue().entrySet()) {
					Set<String> own = starting.get(file.getKey());
					if (own != null && own.contains(method.getKey())) {
						continue;
					}
					if (callsStartingHelper(file.getKey(), method.getValue(), starting)) {
						starting.computeIfAbsent(file.getKey(), key -> new TreeSet<>()).add(method.getKey());
						changed = true;
					}
				}
			}
		}
		return starting;
	}

	private static boolean callsStartingHelper(String file, String body, Map<String, Set<String>> starting) {
		for (Map.Entry<String, Set<String>> other : starting.entrySet()) {
			boolean sameFile = other.getKey().equals(file);
			if (!sameFile && !body.contains(other.getKey())) {
				continue;
			}
			String qualifier = sameFile ? "" : Pattern.quote(other.getKey()) + "\\s*\\.\\s*";
			for (String helperName : other.getValue()) {
				if (body.contains(helperName) && Pattern.compile(
						"(?<![\\w$])" + qualifier + Pattern.quote(helperName) + "\\s*\\(").matcher(body).find()) {
					return true;
				}
			}
		}
		return false;
	}

	// Index just past the brace that closes the one at open; string and char literals are skipped.
	private static int bodyEnd(String source, int open) {
		int depth = 0;
		for (int at = open; at < source.length(); at++) {
			char c = source.charAt(at);
			if (c == '"' || c == '\'') {
				at = literalEnd(source, at) - 1;
			}
			else if (c == '{') {
				depth++;
			}
			else if (c == '}' && --depth == 0) {
				return at + 1;
			}
		}
		return source.length();
	}

	// Index just past the string, text block or char literal that starts at from.
	private static int literalEnd(String source, int from) {
		if (source.startsWith("\"\"\"", from)) {
			int close = source.indexOf("\"\"\"", from + 3);
			return close < 0 ? source.length() : close + 3;
		}
		char quote = source.charAt(from);
		int at = from + 1;
		while (at < source.length() && source.charAt(at) != quote) {
			at += source.charAt(at) == '\\' ? 2 : 1;
		}
		return Math.min(at + 1, source.length());
	}

	// Blanks comments (keeping line breaks) and keeps literals.
	private static String stripComments(String source) {
		StringBuilder out = new StringBuilder(source.length());
		int at = 0;
		while (at < source.length()) {
			char c = source.charAt(at);
			int end;
			if (c == '"' || c == '\'') {
				end = literalEnd(source, at);
				out.append(source, at, end);
			}
			else if (source.startsWith("//", at)) {
				end = source.indexOf('\n', at);
				end = end < 0 ? source.length() : end;
				out.append(" ".repeat(end - at));
			}
			else if (source.startsWith("/*", at)) {
				end = source.indexOf("*/", at + 2);
				end = end < 0 ? source.length() : end + 2;
				out.append(source.substring(at, end).replaceAll("[^\\n]", " "));
			}
			else {
				end = at + 1;
				out.append(c);
			}
			at = end;
		}
		return out.toString();
	}

	private static String compact(String text) {
		return text.replaceAll("\\s+", "");
	}
}
