package rotp.core.powersystem.standpower.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 StandEntity.actuallyHurt blocked a frontal hit on an idle Stand with the plain BLOCK_STAND_ENTITY task, so
 * the Stand's own guard (Kraft Work's KraftWorkBlock.standTickPerform) did not tick then. The port's tryAutoBlock
 * starts the Stand's own guard for 5 ticks; isAutoGuarding tells that guard apart from one the user holds, and the
 * hit is still blocked.
 */
public final class AutoGuardMarkerSmokeTest {
	private static final String STAND_ENTITY =
			"src/main/java/rotp/core/powersystem/standpower/entity/StandEntity.java";
	private static final String ENTITY_ACTION_TYPE =
			"src/main/java/rotp/core/powersystem/entityaction/type/EntityActionType.java";

	private AutoGuardMarkerSmokeTest() {}

	public static void run() {
		verifyMarkerIdentity();
		verifyTryAutoBlockMarks();
	}

	// The marker is the instance tryAutoBlock built; a user's press of the same guard builds another one.
	private static void verifyMarkerIdentity() {
		StandEntityBlockAbility guard = new AbilityType<StandEntityBlockAbility>(id("auto_guard_marker_block"),
				StandEntityBlockAbility::new).createInstance(new AbilityId(null, id("test_power"), "guard"));
		EntityActionInstance auto = guard.createActionObj();
		EntityActionInstance held = guard.createActionObj();
		check(auto != null && held != null && auto != held, "each guard start must build its own action");
		requireInOrder(compact(source(ENTITY_ACTION_TYPE)),
				"defaultEntityActionInstanceinitActionOnAbilityUse(",
				"EntityActionInstanceaction=createActionObj();");
	}

	private static void verifyTryAutoBlockMarks() {
		String source = compact(source(STAND_ENTITY));
		requireInOrder(source,
				"tryAutoBlock(dmgSource,blockableAngle);booleanisBlocking=isStandBlocking()&&blockableAngle;",
				"@NullableprivateEntityActionInstanceautoGuardAction;",
				"privatebooleantryAutoBlock(DamageSourcedmgSource,booleanblockableAngle){",
				"action.phasesLength.put(ActionPhase.PERFORM,5F);action.setStartingPhase();"
						+ "standAction.setAction(action,user,SyncType.TRACKING_AND_SELF);"
						+ "autoGuardAction=action;returntrue;}returnfalse;}",
				"publicbooleanisAutoGuarding(){EntityActionInstancecurAction=getCurStandAction();"
						+ "returncurAction!=null&&curAction==autoGuardAction;}");
		check(occurrences(source, "autoGuardAction=") == 1, "only tryAutoBlock may mark the auto-guard");
	}

	private static void requireInOrder(String text, String... tokens) {
		int from = 0;
		for (String token : tokens) {
			int at = text.indexOf(token, from);
			check(at >= 0, "auto-guard source lost or reordered: " + token);
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

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("jojo_ripples", path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
