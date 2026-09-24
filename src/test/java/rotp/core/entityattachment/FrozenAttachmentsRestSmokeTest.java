package rotp.core.entityattachment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Attachments a stopped entity must keep as they were (FrozenAttachmentsGameTests runs them in a world):
 * the knockback impact (1.16 ticked it with the entity's own, untouched motion), the buffered ability input
 * (1.16 checked it in StandEntity.tick; the buffer already outlived a stop, the guard only stops a stopped user
 * replaying it) and the possession follow (1.16 moved the possessor in ServerPlayer.tick).
 * Each tick() must return before doing anything while TimeStopState.shouldFreezeOnServer holds.
 */
public final class FrozenAttachmentsRestSmokeTest {
	private static final String GUARD = "if(TimeStopState.shouldFreezeOnServer(%s)){return;}";

	private FrozenAttachmentsRestSmokeTest() {}

	public static void run() {
		requireFrozenGuardFirst("src/main/java/rotp/core/mechanics/KnockbackCollisionImpact.java", "entity");
		requireFrozenGuardFirst("src/main/java/rotp/core/powersystem/entityaction/EntityActionInputState.java", "user");
		requireFrozenGuardFirst("src/main/java/rotp/core/subsystems/entity_possessionv2/LivingComponentPossession.java",
				"thisEntity");
	}

	private static void requireFrozenGuardFirst(String path, String entity) {
		String code = squash(stripComments(read(path)));
		String tick = "@Overridepublicvoidtick(){";
		int start = code.indexOf(tick);
		check(start >= 0, path + " has no tick()");
		check(code.startsWith(String.format(GUARD, entity), start + tick.length()),
				path + " must skip its tick while its entity is stopped in time");
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
