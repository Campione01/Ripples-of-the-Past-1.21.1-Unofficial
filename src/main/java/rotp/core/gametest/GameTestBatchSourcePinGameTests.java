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
import java.util.function.BiPredicate;
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
 * batch (or its exact isolated batch), so its stop cannot freeze a neighbour cell of the default batch. This pin reads the gametest sources, marks
 * every method that starts a time stop (directly, through a helper of its own file, or through a qualified helper of
 * another gametest file) and checks the compiled @GameTest batch of every marked test. A marked test that also goes
 * on in a later tick and does not move its stop far away holds the stop over the shared batch's cells: it must be one
 * of the ISOLATED tests, whose batches hold nothing else.
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
	// Compact calls that let a test go on in a later tick, and the call that moves a stop away from every test cell.
	private static final List<String> DEFERS = List.of("runAfterDelay(", "onEachTick(", "succeedWhen(", "runAtTickTime(",
			"startSequence(", "succeedOnTickWhen(", "succeedWhenEntityPresent(");
	private static final String MOVES_AWAY = ".withArea(";
	// Tests that hold a stop over their cells through later ticks, each in its exact batch.
	private static final Map<String, String> ISOLATED = Map.of(
			"CoreDetachedHeavyPulseGameTests.registeredRmbSeparatesNativeExtraAndAdmittedTail",
			GameTestBatches.DETACHED_HEAVY_TIME_STOP,
			"TheWorldHeavyShoutAndImpactGameTests.frozenBoatHurtByHeavyPunchSoundsOnTimeResume",
			GameTestBatches.HELD_TIME_STOP_BOAT,
			"VampirismEyeBeamTimeStopGameTests.shotFiredInStoppedTimeByAMovingOwnerExtendsBoundThenStopsDetached",
			GameTestBatches.HELD_TIME_STOP_EYE_BEAM,
			"VampirismEyeBeamTimeStopGameTests.shotBegunBeforeTheStopKeepsExtendingBoundToItsMovingOwner",
			GameTestBatches.HELD_TIME_STOP_EYE_BEAM,
			"VampirismEyeBeamTimeStopGameTests.shotThatRunsDryInStoppedTimeDetachesAfterThatTicksMove",
			GameTestBatches.HELD_TIME_STOP_EYE_BEAM,
			"VampirismEyeBeamTimeStopGameTests.stoppedOwnersShotAndBoundBeamsWaitForTimeToResume",
			GameTestBatches.HELD_TIME_STOP_EYE_BEAM);
	// An ISOLATED test removes its held stop when it fails or times out too: a GameTestListener on its test info.
	private static final String CLEANS_UP_ON_FAILURE = "testInfo.addListener(";
	// Detector self-check for the held stops: one that is held in place, one that is moved far away.
	private static final String MUST_HOLD_IN_PLACE = "TheWorldHeavyShoutAndImpactGameTests.frozenBoatHurtByHeavyPunchSoundsOnTimeResume";
	private static final String MUST_MOVE_AWAY = "TimeStopLowStaminaGameTests.lowStaminaTimeStopStartsEmptiesTheBarAndEndsOnItsFirstTick";
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

	/** Empty when the detector still works and every time-stop starter carries its exact required stopped batch. */
	static List<String> problems() {
		List<String> problems = new ArrayList<>();
		Path dir = sourceDir();
		if (dir == null) {
			problems.add("could not find " + SOURCE_DIR + " above " + Path.of("").toAbsolutePath());
			return problems;
		}
		Sources sources;
		try {
			sources = read(dir);
		}
		catch (IOException exception) {
			problems.add("could not read the gametest sources in " + dir + ": " + exception);
			return problems;
		}
		Map<String, Set<String>> starting = startingMethods(sources);
		Map<String, Set<String>> deferring = reaching(sources.bodies(), (file, body) -> DEFERS.stream().anyMatch(body::contains));
		Map<String, Set<String>> movingAway = reaching(sources.bodies(), (file, body) -> body.contains(MOVES_AWAY));
		for (String anchor : MUST_START) {
			if (!marks(starting, anchor)) {
				problems.add("the time-stop detector lost " + anchor);
			}
		}
		for (String anchor : MUST_NOT_START) {
			if (marks(starting, anchor)) {
				problems.add("the time-stop detector wrongly marks " + anchor);
			}
		}
		if (!marks(deferring, MUST_HOLD_IN_PLACE) || marks(movingAway, MUST_HOLD_IN_PLACE)) {
			problems.add("the held-stop detector lost " + MUST_HOLD_IN_PLACE);
		}
		if (!marks(deferring, MUST_MOVE_AWAY) || !marks(movingAway, MUST_MOVE_AWAY)) {
			problems.add("the moved-stop detector lost " + MUST_MOVE_AWAY);
		}
		Map<String, Set<String>> cleaningUp = reaching(sources.bodies(), (file, body) -> body.contains(CLEANS_UP_ON_FAILURE));
		List<String> noCleanup = new ArrayList<>();
		for (String isolated : ISOLATED.keySet()) {
			if (!marks(starting, isolated)) {
				problems.add("the time-stop detector lost the isolated " + isolated);
			}
			if (!marks(cleaningUp, isolated)) {
				noCleanup.add(isolated);
			}
		}
		if (!noCleanup.isEmpty()) {
			problems.add("these gametests hold a time stop over their cells and register no GameTestListener to remove it"
					+ " when they fail or time out, so it freezes the next batch: " + noCleanup);
		}
		ClassLoader loader = GameTestBatchSourcePinGameTests.class.getClassLoader();
		List<String> wrongBatch = new ArrayList<>();
		List<String> heldInShared = new ArrayList<>();
		List<String> sharedIsolated = new ArrayList<>();
		int tests = 0;
		for (String file : sources.bodies().keySet()) {
			Class<?> type;
			try {
				type = Class.forName(PACKAGE + "." + file, false, loader);
			}
			catch (ClassNotFoundException exception) {
				continue; // a helper-only file named unlike its classes
			}
			for (Method method : type.getDeclaredMethods()) {
				GameTest test = method.getAnnotation(GameTest.class);
				if (test == null) {
					continue;
				}
				String name = file + "." + method.getName();
				if (ISOLATED.containsValue(test.batch()) && !test.batch().equals(ISOLATED.get(name))) {
					sharedIsolated.add(name + " (batch " + test.batch() + ")");
				}
				if (!marks(starting, name)) {
					continue;
				}
				tests++;
				String requiredBatch = ISOLATED.getOrDefault(name, GameTestBatches.TIME_STOP);
				if (!requiredBatch.equals(test.batch())) {
					wrongBatch.add(name + " (batch " + test.batch() + ", required " + requiredBatch + ")");
				}
				if (!ISOLATED.containsKey(name) && marks(deferring, name) && !marks(movingAway, name)) {
					heldInShared.add(name);
				}
			}
		}
		if (tests < MUST_START.size()) {
			problems.add("only " + tests + " time-stop gametests were found");
		}
		if (!wrongBatch.isEmpty()) {
			problems.add("these gametests start a time stop outside their exact required stopped batch: " + wrongBatch);
		}
		if (!heldInShared.isEmpty()) {
			problems.add("these gametests go on in later ticks with a stop they do not move away, in the shared time stop"
					+ " batch where it freezes their neighbours: " + heldInShared);
		}
		if (!sharedIsolated.isEmpty()) {
			problems.add("these gametests share an isolated time stop batch: " + sharedIsolated);
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

	private static boolean marks(Map<String, Set<String>> marked, String classAndMethod) {
		int dot = classAndMethod.indexOf('.');
		Set<String> names = marked.get(classAndMethod.substring(0, dot));
		return names != null && names.contains(classAndMethod.substring(dot + 1));
	}

	/** File (class) name -> method name -> body; the files that import TimeStopState.Instance. */
	record Sources(Map<String, Map<String, String>> bodies, Set<String> importsInstance) {}

	/** File (class) name -> names of its methods that start a time stop. */
	static Map<String, Set<String>> startingMethods(Path dir) throws IOException {
		return startingMethods(read(dir));
	}

	static Map<String, Set<String>> startingMethods(Sources sources) {
		return reaching(sources.bodies(), (file, body) -> STARTS.stream().anyMatch(body::contains)
				|| sources.importsInstance().contains(file) && body.contains("newInstance("));
	}

	static Sources read(Path dir) throws IOException {
		Map<String, Map<String, String>> bodies = new TreeMap<>();
		Set<String> importsInstance = new TreeSet<>();
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
			if (compact(source).contains(INSTANCE_IMPORT)) {
				importsInstance.add(name);
			}
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
		}
		return new Sources(bodies, importsInstance);
	}

	/** File (class) name -> names of its methods whose compact body passes direct, or that call such a method. */
	static Map<String, Set<String>> reaching(Map<String, Map<String, String>> bodies, BiPredicate<String, String> direct) {
		Map<String, Set<String>> marked = new TreeMap<>();
		for (Map.Entry<String, Map<String, String>> file : bodies.entrySet()) {
			for (Map.Entry<String, String> method : file.getValue().entrySet()) {
				if (direct.test(file.getKey(), compact(method.getValue()))) {
					marked.computeIfAbsent(file.getKey(), key -> new TreeSet<>()).add(method.getKey());
				}
			}
		}
		// A method that calls a marked helper is marked too.
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Map.Entry<String, Map<String, String>> file : bodies.entrySet()) {
				for (Map.Entry<String, String> method : file.getValue().entrySet()) {
					Set<String> own = marked.get(file.getKey());
					if (own != null && own.contains(method.getKey())) {
						continue;
					}
					if (callsMarkedHelper(file.getKey(), method.getValue(), marked)) {
						marked.computeIfAbsent(file.getKey(), key -> new TreeSet<>()).add(method.getKey());
						changed = true;
					}
				}
			}
		}
		return marked;
	}

	private static boolean callsMarkedHelper(String file, String body, Map<String, Set<String>> marked) {
		for (Map.Entry<String, Set<String>> other : marked.entrySet()) {
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
