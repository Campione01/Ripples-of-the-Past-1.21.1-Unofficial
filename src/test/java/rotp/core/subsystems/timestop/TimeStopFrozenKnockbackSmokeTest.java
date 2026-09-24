package rotp.core.subsystems.timestop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 left a stopped entity's motion alone (the entity just did not tick) and stacked the knockback it took on top
 * (GameplayEventHandler.stackKnockbackInstead), so a mob beaten up in stopped time flew once time resumed. An angled
 * punch still split into strength * cos(xRot) along the ground and strength * -sin(xRot) upwards.
 * TimeStopKnockbackGameTests runs this in a world.
 */
public final class TimeStopFrozenKnockbackSmokeTest {
	private static final String STATE = "src/main/java/rotp/core/subsystems/timestop/TimeStopState.java";
	private static final String EVENTS = "src/main/java/rotp/core/core/EventHandler.java";

	private TimeStopFrozenKnockbackSmokeTest() {}

	public static void run() {
		checkAngledKnockback();
		checkFreezeKeepsMotion();
		checkStoppedKnockbackRoute();
	}

	private static void checkAngledKnockback() {
		close(TimeStopState.angledKnockbackHorizontal(0.4F, 0.0F), 0.4F, "a straight hit lost ground knockback");
		close(TimeStopState.angledKnockbackHorizontal(0.4F, -60.0F), 0.2F, "an uppercut kept its whole ground knockback");
		close(TimeStopState.angledKnockbackHorizontal(0.4F, 60.0F), 0.2F, "a downward hit kept its whole ground knockback");
		check(TimeStopState.angledKnockbackHorizontal(0.4F, -90.0F) == 0.0F
				&& TimeStopState.angledKnockbackHorizontal(0.4F, 90.0F) == 0.0F,
				"a straight up or down hit pushed along the ground");
	}

	private static void checkFreezeKeepsMotion() {
		String state = code(STATE);
		for (String method : new String[] {
				"publicvoidfreezeEntity(Entityentity)",
				"publicvoidunfreezeEntity(Entityentity)",
				"privatevoidapplyInterruptedFreezeState(Entityentity)",
				"publicvoidreconcileFrozenEntity(Entityentity)",
				"privatevoidreconcileFrozenEntities()",
				"publicbooleaninterruptTickEarly(Entityentity)" }) {
			check(!body(state, method, STATE).contains("setDeltaMovement("),
					method + " must leave a stopped entity's motion alone");
		}
		check(state.contains("privaterecordFrozenEntityState(Vec3position,floatfallDistance,@NullableBooleanwasNoAi){}"),
				"TimeStopState must not keep a stopped entity's motion aside to restore on resume");
		check(body(state, "publicvoidupdateGrabbedEntityPosition(LivingEntityentity)", STATE)
				.contains("entity.setDeltaMovement(Vec3.ZERO);"),
				"a held entity must drop its old momentum");
	}

	private static void checkStoppedKnockbackRoute() {
		check(body(code(EVENTS), "publicstaticvoidstackKnockbackForFrozenTimeStopTargets(LivingKnockBackEventevent)", EVENTS)
				.contains("TimeStopState.stackKnockbackWhileStopped(event);"),
				"knockback on a stopped entity must stack through TimeStopState.stackKnockbackWhileStopped");
		String stack = body(code(STATE), "publicstaticvoidstackKnockbackWhileStopped(LivingKnockBackEventevent)", STATE);
		check(stack.contains("strength=angledKnockbackHorizontal(strength,angled.jojo_ripples$knockbackXRotDeg());")
				&& stack.contains("angled.jojo_ripples$setKnockbackXRotAppliedStrength(strength);"),
				"stacked knockback must keep an angled hit's ground part");
		check(stack.contains("event.setCanceled(true);DamageUtil.applyKnockbackStack(target,strength,event.getRatioX(),event.getRatioZ());"
				+ "DamageSourceModified.afterKnockbackApplied(target,source);"),
				"stacked knockback must add up on the motion and keep an angled hit's upward part");
	}

	private static String body(String code, String signature, String path) {
		int start = code.indexOf(signature + "{");
		check(start >= 0, path + " has no " + signature);
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
		throw new AssertionError(path + ": unbalanced " + signature);
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

	private static void close(float actual, float expected, String message) {
		check(Math.abs(actual - expected) < 1.0E-3F, message + ": expected " + expected + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
