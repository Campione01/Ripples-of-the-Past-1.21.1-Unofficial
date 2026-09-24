package rotp.core.mechanics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.world.phys.Vec3;

/**
 * 1.16 KnockbackCollisionImpact parts the port had lost: the stuck-entity trigger in tick() (an armed entity that did
 * not move sideways since the last tick checks what it presses into), a floor landing that just ends the impact (no
 * explosion, no wall damage), and the strength in its NBT. TimeStopKnockbackGameTests runs the first two in a world.
 */
public final class KnockbackImpactRestSmokeTest {
	private static final String IMPACT = "src/main/java/rotp/core/mechanics/KnockbackCollisionImpact.java";

	private KnockbackImpactRestSmokeTest() {}

	public static void run() {
		Vec3 pos = new Vec3(3.5, 64, -2.25);
		check(!KnockbackCollisionImpact.stuckSinceLastTick(null, pos), "the first armed tick counted as stuck");
		check(KnockbackCollisionImpact.stuckSinceLastTick(pos, pos), "an entity that did not move was not stuck");
		check(KnockbackCollisionImpact.stuckSinceLastTick(pos, pos.add(0, 0.4, 0)),
				"moving only up or down must still count as stuck (1.16 compared x and z)");
		check(KnockbackCollisionImpact.stuckSinceLastTick(pos, pos.add(1E-8, 0, -1E-8)), "a rounding move broke the stuck check");
		check(!KnockbackCollisionImpact.stuckSinceLastTick(pos, pos.add(0.05, 0, 0))
				&& !KnockbackCollisionImpact.stuckSinceLastTick(pos, pos.add(0, 0, -0.05)),
				"a moving entity counted as stuck");

		String code = code(IMPACT);
		String tick = body(code, "@Overridepublicvoidtick()");
		check(tick.contains("Vec3entityPos=entity.position();if(stuckSinceLastTick(prevTickPos,entityPos)){"
				+ "collideBreakBlocks(deltaMovement,deltaMovement,entity.level());}prevTickPos=entityPos;"),
				"tick() must run 1.16's stuck-entity trigger");
		String collide = body(code, "protectedvoidcollideBoundingBox(Entityentity,Vec3movementVec,booleancollideBlocks,booleanbreakBlocks)");
		check(collide.contains("booleanhitFloor=faceHit==Direction.DOWN;")
				&& collide.contains("if(breakBlocks&&!hitFloor){")
				&& collide.contains("if(wallDamage.floatValue()>0&&!hitFloor){"),
				"a floor landing must end the impact without the explosion or the wall damage");
		check(body(code, "@OverridepublicCompoundTagserializeNBT(HolderLookup.Providerprovider)")
				.contains("nbt.putDouble(\"Power\",knockbackImpactStrength);"),
				"the impact strength must be saved under the key deserializeNBT reads");
	}

	private static String body(String code, String signature) {
		int start = code.indexOf(signature + "{");
		check(start >= 0, IMPACT + " has no " + signature);
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
		throw new AssertionError(IMPACT + ": unbalanced " + signature);
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
