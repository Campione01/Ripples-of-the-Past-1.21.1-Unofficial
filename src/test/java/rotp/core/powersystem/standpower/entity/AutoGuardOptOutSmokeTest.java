package rotp.core.powersystem.standpower.entity;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 auto-guarded an idle Stand only in the core StandEntity.actuallyHurt. An add-on Stand whose entity replaced
 * that method without the BLOCK_STAND_ENTITY line (Scary Monsters' GeoExampleStandEntity) never auto-guarded, while
 * a block its user held still blocked. StandEntity.autoGuardsOnHit is the opt-out: tryAutoBlock asks it before it
 * looks for any guard, and nothing that decides whether a running guard blocks asks it.
 */
public final class AutoGuardOptOutSmokeTest {
	private static final String STAND_ENTITY =
			"src/main/java/rotp/core/powersystem/standpower/entity/StandEntity.java";
	private static final String GAME_TESTS =
			"src/main/java/rotp/core/gametest/StandAutoGuardOptOutGameTests.java";

	private AutoGuardOptOutSmokeTest() {}

	public static void run() {
		verifyHookShape();
		verifyTryAutoBlockAsksFirst();
		verifyGameTestOptsOut();
	}

	// An add-on Stand entity must be able to override it.
	private static void verifyHookShape() {
		Method hook;
		try {
			Class<?> standEntity = Class.forName("rotp.core.powersystem.standpower.entity.StandEntity", false,
					AutoGuardOptOutSmokeTest.class.getClassLoader());
			hook = standEntity.getDeclaredMethod("autoGuardsOnHit");
		}
		catch (ReflectiveOperationException exception) {
			throw new AssertionError("StandEntity.autoGuardsOnHit() is missing", exception);
		}
		int modifiers = hook.getModifiers();
		check(Modifier.isProtected(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers)
				&& hook.getReturnType() == boolean.class,
				"autoGuardsOnHit must stay a protected, overridable boolean instance method");
		check(compact(source(STAND_ENTITY)).contains("protectedbooleanautoGuardsOnHit(){returntrue;}"),
				"every Stand auto-guards unless its entity opts out");
	}

	private static void verifyTryAutoBlockAsksFirst() {
		String stand = compact(source(STAND_ENTITY));
		requireInOrder(stand,
				"privatebooleantryAutoBlock(DamageSourcedmgSource,booleanblockableAngle){"
						+ "if(!autoGuardsOnHit()){returnfalse;}"
						+ "LivingEntityuser=getUser();",
				"guard=userPower!=null?getUnlockedGuardInOtherSlot(userPower):null;",
				"guardinstanceofEntityActionTypeownGuard?ownGuard:StandEntityAutoBlockAction.get();",
				"autoGuardAction=action;returntrue;}returnfalse;}");
		// Only the auto-guard asks: isStandBlocking and standDamageResistance still honour a held guard.
		check(occurrences(stand, "autoGuardsOnHit()") == 2,
				"only tryAutoBlock may ask autoGuardsOnHit");
	}

	private static void verifyGameTestOptsOut() {
		String tests = compact(source(GAME_TESTS));
		requireInOrder(tests,
				"publicstaticvoidoptedOutStandIsNotAutoGuarded(GameTestHelperhelper){",
				"publicstaticvoidoptedOutStandStillBlocksWithHeldGuard(GameTestHelperhelper){",
				"@OverrideprotectedbooleanautoGuardsOnHit(){returnfalse;}");
	}

	private static void requireInOrder(String text, String... tokens) {
		int from = 0;
		for (String token : tokens) {
			int at = text.indexOf(token, from);
			check(at >= 0, "auto-guard opt-out source lost or reordered: " + token);
			from = at + token.length();
		}
	}

	private static int occurrences(String text, String token) {
		int count = 0;
		for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
			count++;
		}
		return count;
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
