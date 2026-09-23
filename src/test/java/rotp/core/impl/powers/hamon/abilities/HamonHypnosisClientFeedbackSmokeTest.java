package rotp.core.impl.powers.hamon.abilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 HamonHypnosis.holdTick: on every held tick the user's own client checked its own mouse target and, while it
 * passed, showed Hamon sparks halfway along the look with their crackle. In the port the aim the server keeps for a
 * player never reaches that player's client, so the client branch must check the aim the client itself sends
 * (ClientsideAim). The branch needs Minecraft.getInstance(), so it is pinned from the source.
 */
public final class HamonHypnosisClientFeedbackSmokeTest {
	private HamonHypnosisClientFeedbackSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		String code = code("src/main/java/rotp/core/impl/powers/hamon/abilities/HamonHypnosisAbility.java");

		String heldTick = body(code,
				"protectedvoidonHeldTick(HamonHeldActionInstanceaction,LivingEntityuser,Power<?>context,HamonDatahamon,intticksHeld)");
		int clientStart = heldTick.indexOf(squash("if (level.isClientSide()) {"));
		int clientEnd = clientStart < 0 ? -1 : heldTick.indexOf("return;", clientStart);
		check(clientStart >= 0 && clientEnd > clientStart, "Hypnosis must keep a client branch in onHeldTick");
		String client = heldTick.substring(clientStart, clientEnd);
		int player = client.indexOf(squash("isClientPlayer(user)"));
		int aim = client.indexOf(squash("ActionTarget target = clientPlayerAim(level);"), player);
		int passed = client.indexOf(squash("checkConditions(context, target, user).isPositive()"), aim);
		int feedback = client.indexOf(squash("hypnosisClientFeedback(user, livingTarget);"), passed);
		check(player >= 0 && aim > player && passed > aim && feedback > passed,
				"the user's own client must check the aim it sends and then show the sparks");
		for (String serverOnly : new String[] { "checkConditions(context)", "getAimTarget(", "getHypnosisTarget(" }) {
			check(!client.contains(serverOnly),
					"the client branch must not use the server-kept aim: " + serverOnly);
		}

		check(body(code, "privatestaticbooleanisClientPlayer(LivingEntityuser)")
				.contains(squash("return user == Minecraft.getInstance().player;")),
				"only the user's own client shows the feedback");
		check(body(code, "privatestaticActionTargetclientPlayerAim(Levellevel)")
				.contains(squash("return ClientsideAim.playerAim.getTarget().resolveEntityId(level);")),
				"the client must read the aim ClientsideAim sends to the server");

		String conditions = body(code,
				"privateConditionCheckcheckConditions(Power<?>context,ActionTargettarget,LivingEntityuser)");
		int mainMod = conditions.indexOf(squash("checkMainModLogicConditions(context)"));
		int hamon = conditions.indexOf(squash("super.checkSpecificConditions(context)"), mainMod);
		int target = conditions.indexOf(squash("checkHypnosisTarget(target, user)"), hamon);
		check(mainMod >= 0 && hamon > mainMod && target > hamon,
				"the client check must run the main-mod and Hamon checks, then the target check");

		String sparks = body(code, "privatestaticvoidhypnosisClientFeedback(LivingEntityuser,LivingEntitylivingTarget)");
		check(sparks.contains(squash("HamonSparksLoopSound.playSparkSound(user, particlesPos, 1.0F, true);"))
				&& sparks.contains(squash("CustomParticlesHelper.createHamonSparkParticles(null, particlesPos, 1);")),
				"the feedback must play the spark sound and draw the sparks");
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

	// The braces-balanced body that follows the first match of a (squashed) declaration.
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
