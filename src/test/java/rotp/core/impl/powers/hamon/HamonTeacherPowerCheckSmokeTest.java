package rotp.core.impl.powers.hamon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 HamonUtil.startLearningHamon (:372, :385) asked canGetPower(HAMON) and givePower(HAMON): a player learns Hamon
 * from a teacher with no power, or with one that is replaceable with Hamon (an add-on power such as Speedwagon's).
 * The port asked for no power at all.
 */
public final class HamonTeacherPowerCheckSmokeTest {
	private static final String HAMON_UTIL = "src/main/java/rotp/core/impl/powers/hamon/HamonUtil.java";

	private HamonTeacherPowerCheckSmokeTest() {}

	public static void run() {
		String util = compact(source(HAMON_UTIL));
		int start = util.indexOf("publicstaticvoidstartLearningHamon(");
		check(start >= 0, "HamonUtil.startLearningHamon is missing");
		String learning = util.substring(start);
		check(learning.contains("if(playerPower!=null&&playerPower.canGetPower(ModPlayerPowers.HAMON.get())){"),
				"a teacher must give Hamon to a power replaceable with it, as 1.16 canGetPower(HAMON) did");
		check(!learning.substring(0, learning.indexOf("playerPower.setPowerType(ModPlayerPowers.HAMON.get());"))
				.contains("!playerPower.hasPower()"), "the teacher still asks for no power at all");
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
