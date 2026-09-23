package rotp.core.powersystem.ability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.impl.powers.hamon.abilities.HamonSendoOverdriveAbility;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 PowerBaseImpl.onClickAction performed an action without a hold on the click, and the key's release only
 * reached a held action (InputHandler.stopHeldAction). A key with both a click and a hold bind resolves the click on
 * release and sends the release in the same frame, before the action's first tick, so a release that reached the
 * click could drop it unperformed. A click-bound action that holds (a charge, a holding phase, a hold-to-fire
 * windup) still hears its release. 1.16 StandEntity.isStandBlocking was StandPose.BLOCK, set by every
 * StandEntityBlock, so a guard counts in any moveset slot. The production code that marks a click and asks the
 * guard check needs a world, so its wiring is pinned from the sources.
 */
public final class ClickReleaseAndGuardSlotSmokeTest {
	private ClickReleaseAndGuardSlotSmokeTest() {}

	public static void run() {
		verifyClickReleaseIgnored();
		verifyHoldKeepsRelease();
		verifyClickBoundHoldsKeepRelease();
		verifyGuardInAnySlot();
		verifyClickFlagWiring();
		verifyGuardWiring();
	}

	private static void verifyClickReleaseIgnored() {
		EntityActionAbility plain = entityAbility("click_plain");
		check(!new EntityActionInstance(plain).isStartedByClick(), "an action is not a click until an input says so");
		for (ActionPhase phase : new ActionPhase[] { ActionPhase.WINDUP, ActionPhase.PERFORM, ActionPhase.RECOVERY }) {
			ReleaseRecorder click = started(new ReleaseRecorder(plain), true, phase, 0.0F);
			click.onKeyRelease(null);
			check(!click.released && click.getPhase() == phase && click.getPhaseTick() == 0,
					"a release must not reach a click action in " + phase);
		}
	}

	private static void verifyHoldKeepsRelease() {
		EntityActionAbility plain = entityAbility("hold_plain");
		ReleaseRecorder held = started(new ReleaseRecorder(plain), false, ActionPhase.PERFORM, 0.0F);
		held.onKeyRelease(null);
		check(held.released && held.getPhase() == ActionPhase.RECOVERY,
				"an action a HOLD input started must still end on release");
	}

	private static void verifyClickBoundHoldsKeepRelease() {
		EntityActionAbility charge = entityAbility("click_charge");
		charge.setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 10);
		ReleaseRecorder charging = started(new ReleaseRecorder(charge), true, ActionPhase.BUTTON_CHARGE, 10.0F);
		charging.onKeyRelease(null);
		check(charging.released, "a click-bound charge must still hear its release");
		ReleaseRecorder charged = started(new ReleaseRecorder(charge), true, ActionPhase.PERFORM, 10.0F);
		charged.onKeyRelease(null);
		check(charged.released, "an action with a charge keeps its release semantics after the charge");

		EntityActionAbility holding = entityAbility("click_holding");
		holding.setButtonHoldPhase(ActionPhase.PERFORM);
		ReleaseRecorder beforeHold = started(new ReleaseRecorder(holding), true, ActionPhase.WINDUP, 0.0F);
		beforeHold.onKeyRelease(null);
		check(beforeHold.released, "a click-bound hold must still hear a release that comes before its holding phase");

		// Sendo Overdrive sits in a CLICK hotbar slot and charges in its windup.
		HamonSendoOverdriveAbility sendo = new AbilityType<HamonSendoOverdriveAbility>(id("click_release_sendo"),
				HamonSendoOverdriveAbility::new).createInstance(abilityId("sendo_overdrive"));
		HamonSendoOverdriveAbility.SendoOverdriveInstance windup = started(
				new HamonSendoOverdriveAbility.SendoOverdriveInstance(sendo), true, ActionPhase.WINDUP, 0.0F);
		windup.onKeyRelease(null);
		check(windup.getPhase() == ActionPhase.PERFORM, "a clicked Sendo Overdrive must still fire on release");
		HamonSendoOverdriveAbility.SendoOverdriveInstance fired = started(
				new HamonSendoOverdriveAbility.SendoOverdriveInstance(sendo), true, ActionPhase.PERFORM, 0.0F);
		fired.onKeyRelease(null);
		check(!fired.isOver() && fired.getPhase() == ActionPhase.PERFORM, "a release after the fire changes nothing");
	}

	private static void verifyGuardInAnySlot() {
		check(JojoModUtil.isStandGuardAbility(guard("guard")), "the guard slot's block is a guard");
		check(JojoModUtil.isStandGuardAbility(guard("kiss_block")),
				"a StandEntityBlockAbility is a guard whatever slot holds it");
		StandEntityBlockAbility subclass = new AbilityType<StandEntityBlockAbility>(id("click_release_block_subclass"),
				(type, abilityId) -> new StandEntityBlockAbility(type, abilityId) {})
				.createInstance(abilityId("kraft_work_block"));
		check(JojoModUtil.isStandGuardAbility(subclass), "a StandEntityBlockAbility subclass is a guard");
		check(JojoModUtil.isStandGuardAbility(entityAbility("guard")),
				"whatever fills the guard slot keeps counting as before");
		check(!JojoModUtil.isStandGuardAbility(entityAbility("kiss_bomb")), "another action is not a guard");
		check(!JojoModUtil.isStandGuardAbility((EntityActionType) null), "no action is not a guard");
	}

	// The key's input method reaches setOrBufferAction, which marks a click before the action is set.
	private static void verifyClickFlagWiring() {
		String input = code("src/main/java/rotp/core/powersystem/ability/input/AbilityInput.java");
		check(input.contains(squash("ability.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);")),
				"a key press must hand its input method to the ability");
		String buffer = code("src/main/java/rotp/core/powersystem/ability/input/ActionInputBuffer.java");
		check(buffer.contains(squash("ability.onKeyPress(user.level(), user, replayInput, replaying.inputMethod, 0, bufferingState);")),
				"a buffered input must replay with the input method it was pressed with");

		String ability = code("src/main/java/rotp/core/powersystem/ability/EntityActionAbility.java");
		check(body(ability, "publicHeldInputonKeyPress(").contains(
				squash("return setOrBufferAction(level, user, user, inputMethod, extraClientInput, clickHoldResolveTime, bufferingState);")),
				"onKeyPress must reach setOrBufferAction with the input method");
		String set = body(ability, "publicHeldInputsetOrBufferAction(");
		int init = set.indexOf(squash("EntityActionInstance action = initActionOnAbilityUse(level, user, performer, extraClientInput);"));
		int flag = set.indexOf(squash("action.setStartedByClick(inputMethod == InputMethod.CLICK);"));
		int started = set.indexOf(squash("actionComponent.setAction(action, user, SyncType.TRACKING_AND_SELF);"));
		check(init >= 0 && flag > init && started > flag && count(set, "setStartedByClick(") == 1,
				"setOrBufferAction must mark a CLICK-started action before it is set");
	}

	// Blocking, auto-guard and the RMB guard timing all ask the guard check by class, not by the "guard" slot.
	private static void verifyGuardWiring() {
		String stand = code("src/main/java/rotp/core/powersystem/standpower/entity/StandEntity.java");
		String blocking = body(stand, "publicbooleanisStandBlocking()");
		check(blocking.contains(squash("curAction.getPhase() == ActionPhase.PERFORM"))
				&& blocking.contains(squash("JojoModUtil.isStandGuardAbility(curAction.ability)"))
				&& !blocking.contains("nameInMoveset") && !blocking.contains("\"guard\""),
				"isStandBlocking must count a guard in PERFORM by class, whatever its slot");

		String damage = body(stand, "protectedfloatgetDamageAfterMagicAbsorb(DamageSourcedmgSource,floatdmgAmount)");
		int autoBlock = damage.indexOf(squash("tryAutoBlock(dmgSource, blockableAngle);"));
		check(autoBlock >= 0 && damage.indexOf(squash("boolean isBlocking = isStandBlocking() && blockableAngle;")) > autoBlock,
				"a hit must try the auto-guard, then block through isStandBlocking");

		String auto = body(stand, "privatebooleantryAutoBlock(DamageSourcedmgSource,booleanblockableAngle)");
		int noGuardSlot = auto.indexOf(squash("if (userPower == null || !userPower.isAbilityUnlocked(\"guard\")) {"));
		int fallback = auto.indexOf(squash("guard = userPower != null ? getUnlockedGuardInOtherSlot(userPower) : null;"), noGuardSlot);
		int guardSlot = auto.indexOf(squash("guard = userPower.getAbility(\"guard\");"), fallback);
		check(noGuardSlot >= 0 && fallback > noGuardSlot && guardSlot > fallback
				&& auto.indexOf(squash("if (guard instanceof EntityActionType guardActionType) {"), guardSlot) > guardSlot,
				"the auto-guard must fall back to an unlocked guard in another slot");
		String other = body(stand, "privatestaticAbilitygetUnlockedGuardInOtherSlot(StandPowerpower)");
		check(other.contains(squash("for (Ability ability : power.getMoveset().abilities.values()) {"))
				&& other.contains(squash("JojoModUtil.isStandGuardAbility(actionType)"))
				&& other.contains(squash("ability.isAbilityUnlocked(power)")),
				"the other-slot guard must be found by class among the unlocked abilities");

		String input = code("src/main/java/rotp/core/client/input/InputHandler.java");
		check(input.contains(squash("ambiguousKeyPress = isGuardClickAmbiguity(heldAbility, clickAbility)")),
				"an ambiguous key must ask the guard check for its click timing");
		String ambiguity = body(input, "privatestaticbooleanisGuardClickAmbiguity(");
		check(ambiguity.contains(squash("\"guard\".equals(heldAbility.baseAbility.name())"))
				&& ambiguity.contains(squash("heldAbility.baseAbility instanceof StandEntityBlockAbility")),
				"the RMB guard timing must cover a guard in any slot");
	}

	private static final class ReleaseRecorder extends EntityActionInstance {
		private boolean released;

		private ReleaseRecorder(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onButtonStopHold() {
			released = true;
			startRecovery();
		}
	}

	private static <T extends EntityActionInstance> T started(T action, boolean click, ActionPhase phase, float charge) {
		action.phasesLength.put(ActionPhase.BUTTON_CHARGE, charge);
		action.phasesLength.put(ActionPhase.WINDUP, 30.0F);
		action.phasesLength.put(ActionPhase.PERFORM, 5.0F);
		action.phasesLength.put(ActionPhase.RECOVERY, 5.0F);
		action.setStartedByClick(click);
		action.setPhaseStart(phase);
		check(action.getPhase() == phase, "could not put the test action in " + phase);
		return action;
	}

	private static StandEntityBlockAbility guard(String slot) {
		return new AbilityType<StandEntityBlockAbility>(id("click_release_guard_" + slot), StandEntityBlockAbility::new)
				.createInstance(abilityId(slot));
	}

	private static EntityActionAbility entityAbility(String name) {
		return new AbilityType<EntityActionAbility>(id("click_release_" + name),
				(type, abilityId) -> new EntityActionAbility(type, abilityId, EntityActionInstance::new))
				.createInstance(abilityId(name));
	}

	private static AbilityId abilityId(String name) {
		return new AbilityId(null, id("test_power"), name);
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("jojo_ripples", path);
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

	private static int count(String source, String token) {
		int found = 0;
		for (int at = source.indexOf(token); at >= 0; at = source.indexOf(token, at + token.length())) {
			found++;
		}
		return found;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
