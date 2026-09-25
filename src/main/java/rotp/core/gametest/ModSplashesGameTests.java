package rotp.core.gametest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import rotp.core.client.resources.ModSplashes;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ModSplashes: texts/splashes.txt lines (no blanks, no "//") replace the title splash
 * with chance n/(420+n), never on Dec 24 or Jan 1, Halloween line 1/50 on Oct 31, @p = user name.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModSplashesGameTests {
	private ModSplashesGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void splashFileKeepsThe116Lines(GameTestHelper helper) {
		String path = "assets/" + JojoMod.MOD_ID + "/texts/splashes.txt";
		List<String> lines;
		try (InputStream in = open(path)) {
			helper.assertTrue(in != null, path + " is missing");
			lines = ModSplashes.parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines());
		} catch (IOException e) {
			throw new GameTestAssertException("Could not read " + path + ": " + e);
		}
		// 16 lines in 1.16: 9 splashes, 4 blanks, 3 commented out
		helper.assertTrue(lines.size() == 9, "expected the 9 active 1.16 splashes, got " + lines);
		helper.assertTrue(lines.contains("I, @p, have a dream!") && lines.contains("Buon Giorno!"),
				"1.16 splash lines missing: " + lines);
		helper.assertTrue(lines.stream().noneMatch(line -> line.isEmpty() || line.startsWith("//")),
				"blank or commented lines were kept: " + lines);
		helper.assertTrue(ModSplashes.parse(Stream.of("  a  ", "", "   ", "//b", "c")).equals(List.of("a", "c")),
				"parse must trim and drop blank and // lines");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void splashPickFollows116Rules(GameTestHelper helper) {
		List<String> splashes = List.of("Buon Giorno!", "I, @p, have a dream!");
		int n = splashes.size();

		// Dec 24 and Jan 1 keep the vanilla holiday splash, even on a winning roll
		helper.assertTrue(ModSplashes.pick(splashes, Calendar.DECEMBER, 24, new Rolls(helper, 0, 0, 0), "Jotaro") == null,
				"a JoJo splash replaced the Dec 24 splash");
		helper.assertTrue(ModSplashes.pick(splashes, Calendar.JANUARY, 1, new Rolls(helper, 0, 0, 0), "Jotaro") == null,
				"a JoJo splash replaced the Jan 1 splash");

		// the highest winning roll is n-1 out of 420+n
		Rolls win = new Rolls(helper, n - 1, 1);
		String splash = ModSplashes.pick(splashes, Calendar.MAY, 5, win, "Jotaro");
		helper.assertTrue("I, Jotaro, have a dream!".equals(splash), "winning roll gave " + splash + " (@p must be the user name)");
		helper.assertTrue(win.bounds.equals(List.of(420 + n, n)), "rolls asked " + win.bounds + ", 1.16 uses 420+n then n");

		Rolls lose = new Rolls(helper, n);
		helper.assertTrue(ModSplashes.pick(splashes, Calendar.MAY, 5, lose, "Jotaro") == null, "roll n must keep the vanilla splash");
		helper.assertTrue(ModSplashes.pick(List.of(), Calendar.MAY, 5, new Rolls(helper), "Jotaro") == null,
				"an empty list must keep the vanilla splash");

		// Oct 31: 1/50 Halloween line, otherwise the normal chance
		Rolls halloween = new Rolls(helper, 0);
		helper.assertTrue(ModSplashes.HALLOWEEN_SPLASH.equals(ModSplashes.pick(splashes, Calendar.OCTOBER, 31, halloween, "Jotaro")),
				"Oct 31 roll 0 of 50 must give the Halloween line");
		helper.assertTrue(halloween.bounds.equals(List.of(50)), "Halloween roll asked " + halloween.bounds + ", 1.16 uses 50");
		helper.assertTrue("Buon Giorno!".equals(ModSplashes.pick(splashes, Calendar.OCTOBER, 31, new Rolls(helper, 1, 0, 0), "Jotaro")),
				"Oct 31 missed Halloween roll must fall back to the normal pick");
		helper.assertTrue(ModSplashes.pick(splashes, Calendar.OCTOBER, 30, new Rolls(helper, n), "Jotaro") == null,
				"the Halloween roll must only happen on Oct 31");
		helper.succeed();
	}

	private static InputStream open(String path) {
		InputStream in = ModSplashesGameTests.class.getResourceAsStream("/" + path);
		return in != null ? in : ModSplashesGameTests.class.getClassLoader().getResourceAsStream(path);
	}

	// scripted nextInt results, bounds recorded
	private static final class Rolls extends Random {
		private final GameTestHelper helper;
		private final Deque<Integer> results = new ArrayDeque<>();
		final List<Integer> bounds = new ArrayList<>();

		Rolls(GameTestHelper helper, int... results) {
			this.helper = helper;
			for (int result : results) {
				this.results.add(result);
			}
		}

		@Override
		public int nextInt(int bound) {
			bounds.add(bound);
			helper.assertTrue(!results.isEmpty(), "unexpected extra roll with bound " + bound + " after " + bounds);
			return results.poll();
		}
	}
}
