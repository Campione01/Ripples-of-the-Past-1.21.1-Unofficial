package rotp.core.mechanics.resolve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 StandPower.setResolveLevel fired STAND_MAX (jojo/stand_max) for any level at the Stand's maximum, so
 * "/stand_level set" and "add" granted it as well as Resolve did; skipProgression set the level on the
 * ResolveCounter directly and did not. The trigger sits in ModCriteriaTriggers with the other triggers.
 * The advancement needs a real server player, so the wiring is pinned from the source.
 */
public final class StandMaxOnLevelSetSmokeTest {
	private StandMaxOnLevelSetSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		String power = code("src/main/java/rotp/core/powersystem/standpower/StandPower.java");
		check(body(power, "publicvoidsetResolveLevel(intlevel)").equals("{setResolveLevel(level,true);}"),
				"every public level change goes through the STAND_MAX check");
		String set = body(power, "privatevoidsetResolveLevel(intlevel,booleanfireStandMax)");
		int changed = set.indexOf("if(data!=null&&type!=null&&data.setResolveLevel(this,level)&&!user.level().isClientSide()){");
		check(changed >= 0, "failed to locate the level change");
		int changedOpen = set.indexOf('{', changed);
		String changedBlock = blockAt(set, changedOpen);
		int fire = set.indexOf("if(fireStandMax&&data!=null&&type!=null){ResolveAdvancements.onResolveLevelSet(this,level);}");
		check(fire >= changedOpen + changedBlock.length(),
				"STAND_MAX must be checked after the level is set, whether it changed or not, as in 1.16");
		check(!changedBlock.contains("onResolveLevelSet"), "STAND_MAX must not depend on the level changing");

		String skip = body(power, "publicvoidskipProgression()");
		check(skip.contains("setResolveLevel(getMaxResolveLevel(),false);") && !skip.contains("setResolveLevel(getMaxResolveLevel());"),
				"skipping progression must not grant stand_max, as in 1.16");

		String levelCommand = code("src/main/java/rotp/core/command/commands/StandLevelCommand.java");
		check(body(levelCommand, "privatestaticintsetStandLevel(").contains("stand.setResolveLevel(level);")
				&& body(levelCommand, "privatestaticintaddStandLevel(").contains("stand.setResolveLevel(stand.getResolveLevel()+levels);"),
				"the stand_level command sets the level through StandPower.setResolveLevel");

		String advancements = code("src/main/java/rotp/core/mechanics/resolve/ResolveAdvancements.java");
		check(body(advancements, "publicstaticvoidonResolveLevelSet(StandPowerstand,intlevel)")
				.contains("ModCriteriaTriggers.STAND_MAX.get().trigger(player);")
				&& !advancements.contains("RegisterEvent"),
				"STAND_MAX is the ModCriteriaTriggers entry");
		String triggers = code("src/main/java/rotp/core/init/ModCriteriaTriggers.java");
		check(triggers.contains("STAND_MAX=TRIGGER_TYPES.register(\"stand_max\",NoConditionsTrigger::new);")
				&& "jojo_ripples:stand_max".equals(ResolveAdvancements.STAND_MAX_ID.toString()),
				"the trigger keeps the id the advancement waits for");
	}

	// A core source without comments or whitespace, read from the project root.
	private static String code(String relativePath) {
		Path path = Path.of(System.getProperty("user.dir")).resolve(relativePath);
		try {
			return squash(stripComments(Files.readString(path)));
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static String body(String code, String declaration) {
		int start = code.indexOf(declaration);
		int open = start < 0 ? -1 : code.indexOf('{', start + declaration.length());
		check(open >= 0, "failed to locate " + declaration);
		return blockAt(code, open);
	}

	// The braces-balanced block that opens at the given index.
	private static String blockAt(String code, int open) {
		int depth = 0;
		for (int i = open; i < code.length(); i++) {
			char c = code.charAt(i);
			if (c == '"' || c == '\'') {
				i = literalEnd(code, i) - 1;
			}
			else if (c == '{') {
				depth++;
			}
			else if (c == '}' && --depth == 0) {
				return code.substring(open, i + 1);
			}
		}
		throw new AssertionError("unbalanced block at " + open);
	}

	private static String squash(String source) {
		return source.replaceAll("\\s+", "");
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
