package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.power.AbilityRefusals;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Gold Experience Requiem's DisablerMixin and DisablerNonStandMixin refused a disabled user's every StandAction and
 * NonStandAction at the head of Action.checkConditions, on both sides: the HUD (ActionsOverlayGui, through
 * PowerBaseImpl.checkRequirements) greyed the slot, a press was refused with on_disable before the action's own checks
 * (its parts, its Stand's stun was asked earlier only in checkRequirements), and the held tick, which re-ran
 * checkConditions, ended a hold with the message; the remote control key sent its own packet and was never refused. The
 * core's hook for it is rotp.core.api.power.AbilityRefusals; the scene registers a refusal of its own user only. Own batch.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AbilityConditionRefusal116GameTests {
	private static final String BATCH = "ability_condition_refusal_116";
	private static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath("rotp_test", "condition_refusal_probe");
	private static final String KEY = "jojo.message.action_condition.on_disable";
	private static final short PUNCH_KEY = 117;
	private static final short PROTECTION_KEY = 118;
	private static final short MANUAL_KEY = 119;
	private static final short BARRAGE_KEY = 120;
	private static final short SENDO_KEY = 121;

	private AbilityConditionRefusal116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void anAddonRefusalRefusesEveryActionInTheConditionCheckButTheManualControlKeyAsIn116(GameTestHelper helper) {
		Set<UUID> refused = new HashSet<>();
		List<String> asked = new ArrayList<>();
		AbilityRefusals.register(OWNER, (ability, context, user) -> {
			if (!refused.contains(user.getUUID())) {
				return null;
			}
			asked.add(ability.name());
			return ConditionCheck.createNegative("on_disable");
		});
		try (Scene s = new Scene(helper)) {
			// premise: nothing refuses the user's abilities
			helper.assertTrue(s.resolve(s.standPower, "punch").conditionCheck.isPositive()
					&& s.resolve(s.power, "hamon_protection").conditionCheck.isPositive(), "setup: the punch or Hamon"
							+ " Protection is unusable before the refusal");
			refused.add(s.user.getUUID());

			// the HUD's check of each slot (AvailableAbilities), as the user's client draws it
			String punchSlot = answer(s.resolve(s.standPower, "punch").conditionCheck);
			String protectionSlot = answer(s.resolve(s.power, "hamon_protection").conditionCheck);
			helper.assertTrue(KEY.equals(punchSlot) && KEY.equals(protectionSlot), "1.16's HUD drew a disabled user's"
					+ " Stand and non-Stand actions unavailable (on_disable at the head of checkConditions); the port's slots"
					+ " answer punch " + punchSlot + ", Hamon Protection " + protectionSlot);

			// the presses, with the refusal's message
			BufferingState punch = s.press(s.standPower, "punch", PUNCH_KEY);
			BufferingState protection = s.press(s.power, "hamon_protection", PROTECTION_KEY);
			HamonData hamon = PlayerPower.getPowerData(s.user, ModPlayerPowers.HAMON).orElseThrow();
			helper.assertTrue(!punch.isActionSuccess && s.stand.getCurStandAction() == null && !protection.isActionSuccess
					&& !hamon.isProtectionEnabled() && s.user.keys().equals(List.of(KEY, KEY)), "1.16 refused a disabled"
							+ " user's punch and Hamon Protection with on_disable; the port's punch went off " + punch.isActionSuccess
							+ " (Stand action " + s.stand.getCurStandAction() + "), Protection " + protection.isActionSuccess
							+ " (on " + hamon.isProtectionEnabled() + "), messages " + s.user.keys());

			// before the action's own refusals: a stunned Stand's punch says on_disable, not stun
			s.user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 100));
			helper.assertTrue(ModStatusEffects.isStunned(s.stand), "setup: stunning the user did not stun Star Platinum");
			refused.remove(s.user.getUUID());
			String stunned = answer(s.resolve(s.standPower, "punch").conditionCheck);
			helper.assertTrue("jojo.message.action_condition.stun".equals(stunned), "setup: the stunned Stand's punch answers "
					+ stunned);
			refused.add(s.user.getUUID());
			String stunnedRefused = answer(s.resolve(s.standPower, "punch").conditionCheck);
			helper.assertTrue(KEY.equals(stunnedRefused), "1.16's disabled user's refusal came before the action's own checks;"
					+ " the port's stunned Stand's punch answers " + stunnedRefused);
			s.user.removeEffect(ModStatusEffects.STUN);

			// the manual control key was no 1.16 action: never asked, it toggles both ways without a message
			asked.clear();
			s.user.clearKeys();
			String manualSlot = answer(s.resolve(s.standPower, "manual_control").conditionCheck);
			s.press(s.standPower, "manual_control", MANUAL_KEY);
			boolean took = s.stand.isManuallyControlled();
			s.press(s.standPower, "manual_control", MANUAL_KEY);
			boolean gaveBack = !s.stand.isManuallyControlled() && EntityComponentController.getControlTarget(s.user) != s.stand;
			helper.assertTrue("usable".equals(manualSlot) && took && gaveBack && s.user.keys().isEmpty()
					&& !asked.contains("manual_control"), "1.16's remote control key, no action, let a disabled user take"
							+ " and give back manual control; the port's slot answers " + manualSlot + ", took " + took
							+ ", gave back " + gaveBack + ", messages " + s.user.keys() + ", the refusal asked " + asked);

			// a hold the refusal meets on its held tick ends with its message (1.16 tickHeldAction re-ran checkConditions)
			refused.remove(s.user.getUUID());
			s.user.clearKeys();
			BufferingState barrage = s.hold(s.standPower, "barrage", BARRAGE_KEY);
			EntityActionInstance held = s.stand.getCurStandAction();
			helper.assertTrue(barrage.isActionSuccess && held != null && AbilityInput.isHeldByKey(s.user, held),
					"setup: the barrage did not start held: " + held);
			refused.add(s.user.getUUID());
			EntityActionAbility barrageAbility = (EntityActionAbility) held.ability;
			String heldAnswer = answer(barrageAbility.checkHeldActionConditions(held, s.standPower));
			boolean stopped = barrageAbility.stopHeldActionIfConditionsFail(held);
			helper.assertTrue(KEY.equals(heldAnswer) && stopped && !barrageAbility.canFireReleasedHold(held)
					&& s.user.keys().equals(List.of(KEY)), "1.16's held tick re-ran checkConditions and ended a disabled"
							+ " user's hold with on_disable; the port's held recheck answers " + heldAnswer + ", stopped " + stopped
							+ ", messages " + s.user.keys());
			AbilityInput.keyRelease(BARRAGE_KEY, s.user);

			// 1.16 checkRequirements refused another action of a power holding a charge that let none through silently,
			// before checkConditions' head: Hamon Protection under a held Sendo Overdrive, the refusal not asked
			refused.remove(s.user.getUUID());
			s.user.clearKeys();
			BufferingState sendo = s.hold(s.power, "sendo_overdrive", SENDO_KEY, InputMethod.CLICK);
			EntityActionInstance charge = LivingComponentAction.getComponent(s.user).getAction();
			helper.assertTrue(sendo.isActionSuccess && charge != null
					&& charge.ability == s.power.getAbility("sendo_overdrive") && !charge.heldAllowsOtherAbilities()
					&& AbilityInput.getHeldAction(s.user, PowerClass.PLAYER_POWER) == charge,
					"setup: Sendo Overdrive is not held as a charge that lets no other Hamon action through: " + charge);
			refused.add(s.user.getUUID());
			asked.clear();
			String underCharge = answer(s.resolve(s.power, "hamon_protection").conditionCheck);
			helper.assertTrue("refused without a message".equals(underCharge) && !asked.contains("hamon_protection"),
					"1.16 refused a disabled user's Hamon Protection under a held Sendo Overdrive silently, before the"
							+ " head of checkConditions; the port's slot answers " + underCharge + ", the refusal asked " + asked);
			AbilityInput.keyRelease(SENDO_KEY, s.user);
			LivingComponentAction.getComponent(s.user).setAction(null, SyncType.NO_SYNC);

			// unregistered, the refusal is asked no more
			AbilityRefusals.unregister(OWNER);
			helper.assertTrue(s.resolve(s.standPower, "punch").conditionCheck.isPositive(), "The punch stays refused once the"
					+ " refusal is unregistered");
		}
		finally {
			AbilityRefusals.unregister(OWNER);
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntity.checkInputBuffer clicked a queued action again every tick (PowerBaseImpl.clickAction), so a punch
	 * queued behind a heavy punch before its user was disabled was refused each tick with on_disable (sendMessage), stayed
	 * queued and went off once the user was free and the Stand idle.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void aPressQueuedBeforeAnAddonRefusalSaysItsMessageEachTickAsIn116(GameTestHelper helper) {
		Set<UUID> refused = new HashSet<>();
		AbilityRefusals.register(OWNER, (ability, context, user) -> refused.contains(user.getUUID())
				? ConditionCheck.createNegative("on_disable") : null);
		try (Scene s = new Scene(helper)) {
			BufferingState heavy = s.press(s.standPower, "heavy_punch", PUNCH_KEY);
			EntityActionInstance heavyAction = s.stand.getCurStandAction();
			helper.assertTrue(heavy.isActionSuccess && heavyAction != null && heavyAction.getPhase() == ActionPhase.WINDUP,
					"setup: the heavy punch is not winding up: " + heavyAction);
			BufferingState queued = BufferingState.clickCanBuffer();
			AbilityConditionCheck punch = s.resolve(s.standPower, "punch");
			if (AbilityInput.withConditionCheck(punch, s.user, InputMethod.CLICK)) {
				AbilityInput.keyPress(PROTECTION_KEY, punch.ability, s.user, null, InputMethod.CLICK, 0, queued,
						s.standPower.getAbility("punch").getAbilityId());
			}
			AbilityInput.keyRelease(PROTECTION_KEY, s.user);
			EntityActionInputState input = s.user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			ActionInputBuffer buffer = input.inputBuffer(PowerClass.STAND);
			helper.assertTrue(queued.shouldBuffer && !queued.isActionSuccess && buffered(buffer) != null,
					"setup: the punch pressed during the heavy punch's windup is not queued");

			refused.add(s.user.getUUID());
			s.user.clearKeys();
			for (int tick = 0; tick < 2; tick++) {
				s.resolve(s.standPower, "punch");
				buffer.tickInputBuffer(input);
			}
			helper.assertTrue(s.user.keys().equals(List.of(KEY, KEY)) && buffered(buffer) != null
					&& s.stand.getCurStandAction() == heavyAction, "1.16 clicked the queued punch of a disabled user again each"
							+ " tick, refused with on_disable each time, and kept it queued: messages " + s.user.keys()
							+ ", queued " + buffered(buffer) + ", the Stand's action " + s.stand.getCurStandAction());

			refused.remove(s.user.getUUID());
			s.user.clearKeys();
			LivingComponentAction.getComponent(s.stand).setAction(null, SyncType.NO_SYNC);
			s.resolve(s.standPower, "punch");
			buffer.tickInputBuffer(input);
			EntityActionInstance replayed = s.stand.getCurStandAction();
			helper.assertTrue(replayed != null && replayed.ability == s.standPower.getAbility("punch") && buffered(buffer) == null
					&& s.user.keys().isEmpty(), "setup: the queued punch did not go off silently once the user was free and"
							+ " the Stand idle: the Stand's action " + replayed + ", messages " + s.user.keys());
		}
		finally {
			AbilityRefusals.unregister(OWNER);
		}
		helper.succeed();
	}

	private static Object buffered(ActionInputBuffer buffer) {
		try {
			Field field = ActionInputBuffer.class.getDeclaredField("buffered");
			field.setAccessible(true);
			return field.get(buffer);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot read the input buffer", e);
		}
	}

	private static String answer(ConditionCheck check) {
		if (check.isPositive()) {
			return "usable";
		}
		Component warning = check.getWarning();
		if (warning == null) {
			return "refused without a message";
		}
		return warning.getContents() instanceof TranslatableContents contents ? contents.getKey() : warning.getString();
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
					.filter(key -> key.startsWith("jojo.message.action_condition."))
					.toList();
		}

		private void clearKeys() {
			actionBar.clear();
		}
	}

	// a Hamon user with Protection and Sendo Overdrive learned and Star Platinum out, looking south; it does not tick
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final RecordingPlayer user;
		private final PlayerPower power;
		private final StandPower standPower;
		private final StandType standType;
		private final StandEntity stand;

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			user = new RecordingPlayer(helper.getLevel(), "ConditionRefusal");
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			user.getAbilities().instabuild = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.PROTECTION.get());
			hamon.learnSkill(ModHamonSkills.SENDO_OVERDRIVE.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			helper.assertTrue(!hamon.isProtectionEnabled(), "setup: Hamon Protection is on");
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(standType != null, "Missing Stand type star_platinum");
			standPower = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(standPower, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(standType.summon(user, standPower), "Could not summon Star Platinum");
			stand = standPower.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Star Platinum is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			standPower.setStamina(standPower.getMaxStamina());
			helper.assertTrue(standPower.getAbility("manual_control") instanceof StandEntityManualControlToggle,
					"Missing the manual control key");
		}

		// as ClAbilityInputPacket handles a click: the key's resolved ability, its condition check, then the press
		private BufferingState press(Power<?> pressed, String name, short key) {
			BufferingState state = BufferingState.clickOnly();
			Ability base = pressed.getAbility(name);
			helper.assertTrue(base != null, "Missing ability " + name);
			AbilityConditionCheck container = resolve(pressed, name);
			if (container != null && AbilityInput.withConditionCheck(container, user, InputMethod.CLICK)) {
				AbilityInput.keyPress(key, container.ability, user, null, InputMethod.CLICK, 0, state, base.getAbilityId());
			}
			AbilityInput.keyRelease(key, user);
			return state;
		}

		// a HOLD press, its key kept held
		private BufferingState hold(Power<?> pressed, String name, short key) {
			return hold(pressed, name, key, InputMethod.HOLD);
		}

		private BufferingState hold(Power<?> pressed, String name, short key, InputMethod inputMethod) {
			BufferingState state = BufferingState.clickOnly();
			Ability base = pressed.getAbility(name);
			helper.assertTrue(base != null, "Missing ability " + name);
			AbilityConditionCheck container = resolve(pressed, name);
			if (container != null && AbilityInput.withConditionCheck(container, user, inputMethod)) {
				AbilityInput.keyPress(key, container.ability, user, null, inputMethod, 0, state, base.getAbilityId());
			}
			return state;
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
			AbilityConditionCheck container = resolved.updateAvailableMoves().getContextVariationContainer(resolved.getAbility(name));
			helper.assertTrue(container != null, "The HUD resolves no slot for " + name);
			return container;
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			user.removeEffect(ModStatusEffects.STUN);
			LivingComponentAction.getComponent(user).setAction(null, SyncType.NO_SYNC);
			LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
			if (user.hasData(ModDataAttachmentTypes.CONTROLLER)) {
				user.getData(ModDataAttachmentTypes.CONTROLLER).stopControlling();
			}
			StandEntityManualControlToggle.off(user.level(), stand, false);
			if (standPower.isSummoned()) {
				standType.forceUnsummon(user, standPower);
			}
			stand.discard();
			user.discard();
		}
	}
}
