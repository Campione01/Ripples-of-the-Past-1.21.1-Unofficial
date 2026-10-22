package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonProtectionAbility;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSpecialActions;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl kept one held action per power, and each power asked only its own: its client sent no press of a
 * held action while that action's power held one (ActionsOverlayGui.onActionClick), checkRequirements refused every other
 * action of the power, silently, while the power's held action did not allow others (heldAllowsOtherAction: a Stand's
 * holdType action, and Vampirism's Blood Drain and Freeze, Zombie Devour and Pillar Man Absorption), and the HUD drew the
 * power's slots from that check. The port's held-press, refusal and HUD rules hold for the non-Stand power too, each
 * power by its own held action (S548 rows 33, 35, 35a); the presses 1.16 refused that the performer's own action takes
 * still wait in the input buffer (owner decision N2).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeldActionRulesPerPower116GameTests {
	private static final short HOLD_KEY = 51;
	private static final short OTHER_KEY = 52;
	private static final String CONDITION = "jojo.message.action_condition.";

	private HeldActionRulesPerPower116GameTests() {}

	/*
	 * Row 33: Vampire Freeze held, then Blood Drain (a held action) pressed: 1.16's client never sent it and the Freeze went
	 * on; the port's Freeze gives way to every press, so the press waits in the input buffer instead of ending it. The
	 * Freeze let every other Vampirism action through (heldAllowsOtherAction), so no slot is drawn unavailable for it, and
	 * a click (Claw Lacerate) still goes off at once and ends it (row 34). The user's client's copy of the Freeze answers
	 * as the server's.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aNonStandHeldPressWaitsWhileItsPowerHoldsAnActionAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "PerPowerHeldPressVampire", ModPlayerPowers.VAMPIRISM.get())) {
			s.vampire();
			s.aim(s.cow(1.5D));
			EntityActionInstance freeze = s.hold("vampirism_freeze");

			BufferingState drain = s.press("vampirism_blood_drain", InputMethod.HOLD, OTHER_KEY);
			helper.assertTrue(drain.shouldBuffer && !drain.isActionSuccess && s.component.getAction() == freeze
					&& freeze.getPhase() == ActionPhase.PERFORM && s.isHeldByKey(HOLD_KEY),
					"1.16's client sent no Blood Drain (a held action) pressed while Vampirism held the Freeze, which went on;"
							+ " the input buffer keeps the press: buffered " + drain.shouldBuffer + ", started "
							+ drain.isActionSuccess + ", the user's action " + s.component.getAction() + ", the Freeze in "
							+ freeze.getPhase());
			AbilityInput.keyRelease(OTHER_KEY, s.user);

			for (String name : new String[] { "vampirism_blood_drain", "vampirism_claw_lacerate",
					"vampirism_space_ripper_stingy_eyes" }) {
				AbilityConditionCheck key = s.resolve(name);
				helper.assertTrue(key != null && !key.ability.isHeldBackByHeldAction(s.user),
						"1.16's Freeze let every other Vampirism action through (heldAllowsOtherAction), so its HUD drew "
								+ name + " as its own check drew it: held back "
								+ (key != null && key.ability.isHeldBackByHeldAction(s.user)));
			}

			EntityActionAbility freezeAbility = s.ability("vampirism_freeze");
			EntityActionInstance client = clientCopy(helper, freeze, s.user);
			helper.assertTrue(freezeAbility.isHeldSincePress(client) && client.heldAllowsOtherAbilities()
					&& freeze.heldAllowsOtherAbilities(),
					"the user's client's copy of the held Freeze, as its HUD reads it, is held " + freezeAbility.isHeldSincePress(client)
							+ " and lets the others through " + client.heldAllowsOtherAbilities() + " (the server's "
							+ freeze.heldAllowsOtherAbilities() + ")");

			BufferingState lacerate = s.press("vampirism_claw_lacerate", InputMethod.CLICK, OTHER_KEY);
			EntityActionInstance now = s.component.getAction();
			helper.assertTrue(lacerate.isActionSuccess && !lacerate.shouldBuffer && now != null && now != freeze
					&& now.ability == s.power.getAbility("vampirism_claw_lacerate") && freeze.isOver(),
					"1.16 let Claw Lacerate (a click) through the Freeze at once and ended the Freeze: started "
							+ lacerate.isActionSuccess + ", buffered " + lacerate.shouldBuffer + ", the user's action " + now
							+ ", the Freeze over " + freeze.isOver());
			AbilityInput.keyRelease(OTHER_KEY, s.user);
		}
		helper.succeed();
	}

	/*
	 * Rows 35 and 35a: Hamon Breath held lets no other Hamon action through. 1.16's HUD drew the other Hamon slots
	 * unavailable, and Hamon Protection (a click HamonAction) was refused without a message; the port's Hamon presses its
	 * performer takes wait in the input buffer (N2), Protection, a plain ability, is refused and toggles only once the
	 * breath is let go. The Stand power is not held by the breath: its slots are drawn as before and the Stand's guard,
	 * a held press, goes off at once (row 2), the breath going on.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aNonStandHoldRefusesAndDimsItsPowersOtherSlotsAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "PerPowerHamonBreath", ModPlayerPowers.HAMON.get())) {
			s.hamon();
			s.stand("star_platinum");
			// Hamon Overdrive asks for an entity within its reach
			s.aim(s.cow(1.5D));
			EntityActionInstance breath = s.hold("hamon_breath");

			for (String name : new String[] { "hamon_overdrive", "hamon_healing" }) {
				AbilityConditionCheck key = s.resolve(name);
				helper.assertTrue(key != null && key.conditionCheck.isPositive() && key.ability.isHeldBackByHeldAction(s.user),
						"1.16's HUD drew " + name + " unavailable while Hamon held the breath (checkRequirements refused it);"
								+ " the port's press waits in the buffer: its check " + describe(key)
								+ ", held back " + (key != null && key.ability.isHeldBackByHeldAction(s.user)));
			}
			AbilityConditionCheck protectionKey = s.resolve("hamon_protection");
			helper.assertTrue(protectionKey != null && protectionKey.conditionCheck == ConditionCheck.NEGATIVE
					&& protectionKey.ability.isHeldBackByHeldAction(s.user),
					"1.16 refused Hamon Protection, without a message, while Hamon held the breath, and drew it unavailable:"
							+ " its check " + describe(protectionKey));

			boolean protectionBefore = HamonProtectionAbility.isProtectionEnabled(s.power);
			s.user.actionBar.clear();
			BufferingState protection = s.press("hamon_protection", InputMethod.CLICK, OTHER_KEY);
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			helper.assertTrue(!protection.isActionSuccess && !protection.shouldBuffer
					&& HamonProtectionAbility.isProtectionEnabled(s.power) == protectionBefore && s.user.keys().isEmpty()
					&& s.component.getAction() == breath && s.isHeldByKey(HOLD_KEY),
					"1.16 refused Hamon Protection pressed during the held breath, silently: went off "
							+ protection.isActionSuccess + ", buffered " + protection.shouldBuffer + ", toggled "
							+ (HamonProtectionAbility.isProtectionEnabled(s.power) != protectionBefore) + ", messages "
							+ s.user.keys() + ", the user's action " + s.component.getAction());

			BufferingState overdrive = s.press("hamon_overdrive", InputMethod.CLICK, OTHER_KEY);
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			helper.assertTrue(overdrive.shouldBuffer && !overdrive.isActionSuccess && s.component.getAction() == breath,
					"a Hamon Overdrive pressed during the held breath waits in the input buffer (N2): buffered "
							+ overdrive.shouldBuffer + ", the user's action " + s.component.getAction());

			EntityActionAbility breathAbility = s.ability("hamon_breath");
			EntityActionInstance client = clientCopy(helper, breath, s.user);
			helper.assertTrue(breathAbility.isHeldSincePress(client) && !client.heldAllowsOtherAbilities(),
					"the user's client's copy of the held breath, as its HUD reads it, is held "
							+ breathAbility.isHeldSincePress(client) + " and lets the others through "
							+ client.heldAllowsOtherAbilities());

			for (String name : new String[] { "punch", "guard", "barrage" }) {
				Ability standAbility = s.standPower.getAbility(name);
				helper.assertTrue(standAbility != null && !standAbility.isHeldBackByHeldAction(s.user),
						"1.16's Stand power held nothing while Hamon held the breath: its HUD drew " + name + " as before");
			}
			BufferingState guard = s.pressStand("guard", InputMethod.HOLD, OTHER_KEY);
			EntityActionInstance standNow = LivingComponentAction.getCurEntityAction(s.stand);
			helper.assertTrue(guard.isActionSuccess && !guard.shouldBuffer && standNow != null
					&& standNow.ability == s.standPower.getAbility("guard") && s.component.getAction() == breath
					&& breath.getPhase() == ActionPhase.PERFORM,
					"1.16 ran the Stand's guard beside the held breath (one held action per power): started "
							+ guard.isActionSuccess + ", buffered " + guard.shouldBuffer + ", the Stand's action " + standNow
							+ ", the user's action " + s.component.getAction());
			AbilityInput.keyRelease(OTHER_KEY, s.user);

			AbilityInput.keyRelease(HOLD_KEY, s.user);
			s.user.actionBar.clear();
			BufferingState afterBreath = s.press("hamon_protection", InputMethod.CLICK, OTHER_KEY);
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			helper.assertTrue(afterBreath.isActionSuccess
					&& HamonProtectionAbility.isProtectionEnabled(s.power) != protectionBefore,
					"Hamon Protection pressed once the breath was let go toggles: went off " + afterBreath.isActionSuccess
							+ ", toggled " + (HamonProtectionAbility.isProtectionEnabled(s.power) != protectionBefore));
		}
		helper.succeed();
	}

	/*
	 * 1.16 EntityStandType.unsummon -> StandEntity.retractStand(true) set the unsummon task only while the Stand power held
	 * no action (userPower.getHeldAction() == null) and left a held action alone. So with Star Platinum out the summon key
	 * unsummoned it while Hamon held the breath (the other power's hold) or while the key of a clicked punch was still down
	 * (no held action), and did nothing while the Stand's guard was held, until it was let go. Before, the port started no
	 * unsummon while any ability key was down.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void onlyTheStandPowersHeldActionHoldsBackItsUnsummonAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "PerPowerUnsummon", ModPlayerPowers.HAMON.get())) {
			s.hamon();
			s.stand("star_platinum");
			EntityActionInstance breath = s.hold("hamon_breath");
			s.standType.onUserSummonCommand(s.user, s.standPower);
			helper.assertTrue(s.unsummoning() && s.component.getAction() == breath && s.isHeldByKey(HOLD_KEY)
					&& breath.getPhase() == ActionPhase.PERFORM,
					"1.16 unsummoned the Stand while Hamon held the breath (only the Stand power's held action held it back):"
							+ " the Stand's action " + LivingComponentAction.getCurEntityAction(s.stand) + ", the breath going on "
							+ (s.component.getAction() == breath && s.isHeldByKey(HOLD_KEY)));
			AbilityInput.keyRelease(HOLD_KEY, s.user);
			s.clearStandAction();

			BufferingState punch = s.pressStand("punch", InputMethod.CLICK, OTHER_KEY);
			EntityActionInstance punchAction = LivingComponentAction.getCurEntityAction(s.stand);
			helper.assertTrue(punch.isActionSuccess && punchAction != null && s.isHeldByKey(OTHER_KEY)
					&& AbilityInput.getHeldAction(s.user, PowerClass.STAND) == null, "setup: the punch did not go off with its"
							+ " key down and nothing held: started " + punch.isActionSuccess + ", the Stand's action " + punchAction);
			s.standType.onUserSummonCommand(s.user, s.standPower);
			helper.assertTrue(s.unsummoning(), "1.16 unsummoned the Stand while a clicked punch's key was still down (the"
					+ " power held no action): the Stand's action " + LivingComponentAction.getCurEntityAction(s.stand));
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			s.clearStandAction();

			BufferingState guard = s.pressStand("guard", InputMethod.HOLD, OTHER_KEY);
			EntityActionInstance guardAction = LivingComponentAction.getCurEntityAction(s.stand);
			helper.assertTrue(guard.isActionSuccess && guardAction != null
					&& AbilityInput.getHeldAction(s.user, PowerClass.STAND) == guardAction, "setup: the guard is not held:"
							+ " started " + guard.isActionSuccess + ", the Stand's action " + guardAction);
			s.standType.onUserSummonCommand(s.user, s.standPower);
			helper.assertTrue(!s.unsummoning() && LivingComponentAction.getCurEntityAction(s.stand) == guardAction
					&& s.standPower.isSummoned(), "1.16 started no unsummon while the Stand power held its guard, and left the"
							+ " guard alone: the Stand's action " + LivingComponentAction.getCurEntityAction(s.stand));
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			s.standType.onUserSummonCommand(s.user, s.standPower);
			helper.assertTrue(s.unsummoning(), "the guard let go, 1.16's summon key unsummoned the Stand: the Stand's action "
					+ LivingComponentAction.getCurEntityAction(s.stand));
		}
		helper.succeed();
	}

	private static String describe(AbilityConditionCheck key) {
		if (key == null) {
			return "no key";
		}
		ConditionCheck check = key.conditionCheck;
		Component warning = check.getWarning();
		return check.isPositive() ? "usable" : warning == null ? "refused without a message"
				: "refused with " + (warning.getContents() instanceof TranslatableContents contents ? contents.getKey()
						: warning.getString());
	}

	// the action as the user's client holds it: decoded from the action the server sends it (EntityActionInstance.encode)
	private static EntityActionInstance clientCopy(GameTestHelper helper, EntityActionInstance action, LivingEntity performer) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			EntityActionInstance.encode(buf, action);
			EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
			helper.assertTrue(copy != null && copy.getClass() == action.getClass() && copy.getPhase() == action.getPhase(),
					"setup: the action decodes as " + copy);
			Field field = EntityActionInstance.class.getDeclaredField("performer");
			field.setAccessible(true);
			field.set(copy, performer);
			return copy;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("setup: cannot give the client's copy its performer", e);
		}
		finally {
			buf.release();
		}
	}

	private static final class RecordingPlayer extends FakePlayer {
		private final List<Component> actionBar = new ArrayList<>();

		private RecordingPlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			if (actionBar) {
				this.actionBar.add(message);
			}
		}

		private List<String> keys() {
			return actionBar.stream()
					.map(message -> message.getContents() instanceof TranslatableContents contents
							? contents.getKey() : message.getString())
					.filter(key -> key.startsWith(CONDITION))
					.toList();
		}
	}

	/** A player with a non-Stand power, looking south (+z), no gravity, empty hands; the scene does not tick. */
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final RecordingPlayer user;
		private final PlayerPower power;
		private final LivingComponentAction component;
		private final Vec3 origin;
		private final List<Entity> spawned = new ArrayList<>();
		private StandType standType;
		private StandPower standPower;
		private StandEntity stand;

		private Scene(GameTestHelper helper, String name, PlayerPowerType<?> type) {
			this.helper = helper;
			user = new RecordingPlayer(helper.getLevel(), name);
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			user.getAbilities().instabuild = false;
			user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the per-power player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(type);
			component = LivingComponentAction.getComponent(user);
		}

		private void vampire() {
			VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
			data.setVampireFullPower(true, user);
			VampirismState.get(user).blood().setCurrent(100.0F);
			data.setBloodLevel(VampirismState.get(user).blood().current());
			helper.assertTrue(data.getCuringStage(user) == 0 && data.hasBlood(user, 100.0F),
					"setup: the vampire is being cured or has not got its blood");
		}

		private void hamon() {
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
			hamon.learnSkill(ModHamonSkills.HEALING.get());
			hamon.learnSkill(ModHamonSkills.PROTECTION.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			helper.assertTrue(!hamon.isProtectionEnabled(), "setup: Hamon Protection is on");
		}

		// the user also has the Stand, summoned and out of its summon lock
		private void stand(String standId) {
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(standType != null, "Missing Stand type " + standId);
			standPower = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(standPower, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
			standPower.setResolveLevel(4);
			helper.assertTrue(standType.summon(user, standPower), "Could not summon " + standId);
			stand = standPower.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned " + standId + " is missing");
			stand.summonLockTicks = 0;
			standPower.setStamina(standPower.getMaxStamina());
		}

		private Cow cow(double ahead) {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create the cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + ahead, 180.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add the cow");
			spawned.add(cow);
			return cow;
		}

		private void aim(Entity target) {
			component.entityAim.setTarget(new ActionTarget(target));
		}

		private EntityActionAbility ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			return (EntityActionAbility) found;
		}

		// pressed as a held key and kept held
		private EntityActionInstance hold(String name) {
			BufferingState state = press(name, InputMethod.HOLD, HOLD_KEY);
			EntityActionInstance action = component.getAction();
			helper.assertTrue(state.isActionSuccess && action != null && action.ability == power.getAbility(name)
					&& isHeldByKey(HOLD_KEY) && action.getPhase() == ActionPhase.PERFORM,
					"setup: " + name + " is not held: started " + state.isActionSuccess + ", the user's action " + action
							+ (action != null ? " in " + action.getPhase() : ""));
			return action;
		}

		// as ClAbilityInputPacket handles a press: the key's resolved ability, its condition check, then the press
		private BufferingState press(String name, InputMethod inputMethod, short key) {
			return press(power, name, inputMethod, key);
		}

		private BufferingState pressStand(String name, InputMethod inputMethod, short key) {
			return press(standPower, name, inputMethod, key);
		}

		private BufferingState press(Power<?> pressed, String name, InputMethod inputMethod, short key) {
			BufferingState state = BufferingState.clickCanBuffer();
			Ability base = pressed.getAbility(name);
			helper.assertTrue(base != null, "Missing ability " + name);
			AbilityConditionCheck container = resolve(pressed, name);
			if (container != null && AbilityInput.withConditionCheck(container, user, inputMethod)) {
				AbilityInput.keyPress(key, container.ability, user, null, inputMethod, 0, state, base.getAbilityId());
			}
			return state;
		}

		private boolean isHeldByKey(short key) {
			return input().heldKeys.containsKey(key) && input().heldKeys.get(key).action != null;
		}

		private boolean unsummoning() {
			EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
			return action != null && action.ability == ModSpecialActions.STAND_UNSUMMON.get();
		}

		private void clearStandAction() {
			LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
		}

		private EntityActionInputState input() {
			return user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		}

		private AbilityConditionCheck resolve(String name) {
			return resolve(power, name);
		}

		// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
		private AbilityConditionCheck resolve(Power<?> resolved, String name) {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(resolved, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
			}
			return resolved.updateAvailableMoves().getContextVariationContainer(resolved.getAbility(name));
		}

		@Override
		public void close() {
			input().heldKeys.clear();
			component.entityAim.setTarget(ActionTarget.EMPTY);
			component.setAction(null, SyncType.NO_SYNC);
			if (stand != null) {
				LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
			}
			if (standPower != null && standPower.isSummoned() && standType != null) {
				standType.forceUnsummon(user, standPower);
			}
			user.removeAllEffects();
			spawned.forEach(Entity::discard);
			user.discard();
		}
	}
}
