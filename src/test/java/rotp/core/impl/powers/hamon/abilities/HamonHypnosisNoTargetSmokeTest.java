package rotp.core.impl.powers.hamon.abilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 1.16 HamonHypnosis required an ENTITY target. With the sky or a block under the aim, PowerBaseImpl.checkTarget
 * emptied the target and failed with ActionConditionResult.NEGATIVE, which has no message, both on the press and
 * on a held tick. "This entity cannot be hypnotized" (hypnosis) only came from HamonHypnosis.checkTarget, for an
 * entity: a non-living one or one canBeHypnotized rejected. The ability class needs a running game, so the
 * target check is pinned from the source.
 */
public final class HamonHypnosisNoTargetSmokeTest {
	private HamonHypnosisNoTargetSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		String code = squash(stripComments(read("src/main/java/rotp/core/impl/powers/hamon/abilities/HamonHypnosisAbility.java")));
		String check = body(code, "privatestaticConditionCheckcheckHypnosisTarget(ActionTargettarget,LivingEntityuser)");
		int noEntity = check.indexOf(squash("if (target.getType() != TargetType.ENTITY || target.getMainEntity() == null) {"
				+ " return ConditionCheck.NEGATIVE; }"));
		int notLiving = check.indexOf(squash("if (!(target.getMainEntity() instanceof LivingEntity livingTarget)) {"
				+ " return ConditionCheck.createNegative(\"hypnosis\"); }"));
		int verdict = check.indexOf(squash("switch (HamonHypnosisState.canBeHypnotized(livingTarget, user))"));
		check(noEntity >= 0 && notLiving > noEntity && verdict > notLiving,
				"no entity under the aim fails silently; a non-living entity gets the hypnosis message");
		check(check.contains(squash("case INVALID -> ConditionCheck.createNegative(\"hypnosis\");"))
				&& check.contains(squash("case ALREADY_TAMED_BY_USER -> ConditionCheck.createNegative(\"already_tamed\");")),
				"an entity that cannot be hypnotized keeps the 1.16 messages");
		check(count(check, "createNegative(\"hypnosis\")") == 2,
				"only an aimed entity may produce the hypnosis message");

		for (String locale : new String[] { "en_us", "zh_cn" }) {
			JsonObject lang = JsonParser.parseString(read("src/main/resources/assets/jojo_ripples/lang/" + locale + ".json"))
					.getAsJsonObject();
			check(lang.has("jojo.message.action_condition.hypnosis") && lang.has("jojo.message.action_condition.already_tamed"),
					locale + " must keep the hypnosis messages");
		}
	}

	private static String body(String code, String declaration) {
		int start = code.indexOf(declaration);
		int open = start < 0 ? -1 : code.indexOf('{', start + declaration.length());
		check(open >= 0, "failed to locate " + declaration);
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
		throw new AssertionError("unbalanced body after " + declaration);
	}

	private static int count(String text, String token) {
		int count = 0;
		for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + token.length())) {
			count++;
		}
		return count;
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
