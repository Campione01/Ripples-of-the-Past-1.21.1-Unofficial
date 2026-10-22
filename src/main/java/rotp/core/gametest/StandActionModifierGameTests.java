package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityActionModifier (Reworked's Star Platinum blink among them): its perform added it to the Stand's running
 * task (StandEntityTask.addModifierAction), which then ran the modifier's phase hooks right after its own action's on every
 * tick (StandEntityTask.tick: tickAction(action), then each modifier), its standPerform in the task's PERFORM's first tick,
 * after the action's own standPerform, also in the tick where the action stopped the task, in the phase the task kept; and
 * each modifier's phaseTransition right after the action's (StandEntityTask.phaseTransition) as moveToPhase entered a phase
 * with ticks, or ran out the last one. FEATURE_STAND_ACTION_MODIFIERS_V1 gives an add-on the same: a Stand effect with
 * isFromStandAction attaches to the Stand's current action as it starts, and its onModifiedActionTick runs right after the
 * action's own tick until the effect is stopped, its onModifiedActionPhaseTransition right after the action's onSetPhase;
 * once a tick, as 1.16's task kept its modifiers in a set, also where a client initializes the effect twice as it adds it
 * from its add packet.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandActionModifierGameTests {
	private static final EntityCustomEffectType<RecordingModifier> MODIFIER = new EntityCustomEffectType<>(
			JojoMod.resLoc("stand_action_modifier_test"), RecordingModifier::new);

	private StandActionModifierGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aModifierOfTheStandsRunningActionRunsRightAfterItAsIn116(GameTestHelper helper) {
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StandActionModifier"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		StandPower power = null;
		try {
			helper.assertTrue(standType != null, "setup: no Star Platinum");
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the Stand user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED && standType.summon(user, power), "setup: could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "setup: the Stand is missing");
			stand.summonLockTicks = 0;
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);

			// every tick, right after the action's own tick, its perform start included
			List<String> events = new ArrayList<>();
			EntityActionInstance action = start(helper, power, user, stand, events, false);
			RecordingModifier modifier = attach(helper, power, action, events);
			tickUntilOver(standAction, action);
			helper.assertTrue(events.equals(List.of("action WINDUP 0", "modifier WINDUP 0", "action WINDUP 1",
					"modifier WINDUP 1", "action WINDUP -> PERFORM", "modifier WINDUP -> PERFORM in PERFORM", "action PERFORM 0",
					"perform start", "modifier PERFORM 0", "action PERFORM 1", "modifier PERFORM 1", "action PERFORM -> RECOVERY",
					"modifier PERFORM -> RECOVERY in RECOVERY", "action RECOVERY 0", "modifier RECOVERY 0", "action RECOVERY 1",
					"modifier RECOVERY 1", "modifier RECOVERY -> null in RECOVERY")), "1.16 ran a task's modifier right after its action on"
							+ " each tick, its standPerform after the action's, and its phaseTransition after the action's: " + events);
			helper.assertTrue(modifier.standAction == action, "setup: the modifier is not the action's");
			List<String> run = List.copyOf(events);

			// a modifier stopped no longer runs
			events.clear();
			action = start(helper, power, user, stand, events, false);
			modifier = attach(helper, power, action, events);
			standAction.tick();
			modifier.remove();
			tickUntilOver(standAction, action);
			helper.assertTrue(events.equals(List.of("action WINDUP 0", "modifier WINDUP 0", "action WINDUP 1",
					"action WINDUP -> PERFORM", "action PERFORM 0", "perform start", "action PERFORM 1",
					"action PERFORM -> RECOVERY", "action RECOVERY 0", "action RECOVERY 1")),
					"a stopped modifier still ran: " + events);

			// an action that ends itself at its perform start still runs its modifier in that tick, in the phase it ended in
			// (1.16: the stopped task kept its phase), and tells it no end of its phases (1.16 stopTask told no modifier)
			events.clear();
			action = start(helper, power, user, stand, events, true);
			attach(helper, power, action, events);
			tickUntilOver(standAction, action);
			helper.assertTrue(events.equals(List.of("action WINDUP 0", "modifier WINDUP 0", "action WINDUP 1",
					"modifier WINDUP 1", "action WINDUP -> PERFORM", "modifier WINDUP -> PERFORM in PERFORM", "action PERFORM 0",
					"perform start", "modifier PERFORM 0")) && action.isOver() && action.getPhase() == null,
					"1.16 ran the task's modifiers after its action also in the tick that action stopped the task, in the phase"
							+ " the task kept (StandEntityTask.tick), and told them no phase change for the stop: " + events
							+ ", the action over " + action.isOver());

			// a phase with no length is passed over, as 1.16 moveToPhase passed over one without ticks, so the modifier is told
			// of the run-out from the perform (the action itself, as its onSetPhase shows, passed into that recovery)
			events.clear();
			action = start(helper, power, user, stand, events, false);
			action.phasesLength.put(ActionPhase.RECOVERY, 0F);
			attach(helper, power, action, events);
			tickUntilOver(standAction, action);
			helper.assertTrue(events.equals(List.of("action WINDUP 0", "modifier WINDUP 0", "action WINDUP 1",
					"modifier WINDUP 1", "action WINDUP -> PERFORM", "modifier WINDUP -> PERFORM in PERFORM", "action PERFORM 0",
					"perform start", "modifier PERFORM 0", "action PERFORM 1", "modifier PERFORM 1", "action PERFORM -> RECOVERY",
					"modifier PERFORM -> null in RECOVERY")), "1.16 told a task's modifiers only of the phases with ticks it entered and of its"
							+ " end (moveToPhase): " + events);

			// added as a client adds it from the effect's add packet, which initializes it twice, it is on the action once and
			// runs once a tick, as 1.16's client added it to its task's set (TrStandTaskModifierPacket)
			events.clear();
			action = start(helper, power, user, stand, events, false);
			modifier = addAsAClientDoes(helper, power, user, events, false);
			helper.assertTrue(modifier.standAction == action, "setup: the Stand effect did not attach to the Stand's running action");
			tickUntilOver(standAction, action);
			helper.assertTrue(events.equals(run) && timesOn(action, modifier) == 1, "1.16 kept a task's modifier once (a set, on"
					+ " the client too), so it ran once a tick: on the action " + timesOn(action, modifier) + " times, " + events);
			power.userStandEffects.removeEffect(modifier);

			// the same for a modifier of the user's own running action (isFromUserAction)
			events.clear();
			EntityActionInstance userAction = startUserAction(helper, power, user, events);
			modifier = addAsAClientDoes(helper, power, user, events, true);
			helper.assertTrue(modifier.userAction == userAction, "setup: the Stand effect did not attach to the user's running action");
			tickUntilOver(LivingComponentAction.getComponent(user, PowerClass.STAND), userAction);
			helper.assertTrue(events.equals(run) && timesOn(userAction, modifier) == 1, "a modifier of the user's running action"
					+ " ran other than once a tick: on the action " + timesOn(userAction, modifier) + " times, " + events);
			power.userStandEffects.removeEffect(modifier);
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.succeed();
	}

	// set on the Stand as a press sets it: a windup, a perform and a recovery of two ticks each
	private static EntityActionInstance start(GameTestHelper helper, StandPower power, FakePlayer user, StandEntity stand,
			List<String> events, boolean endsAtPerform) {
		RecordingAbility ability = new RecordingAbility(ModStandAbilities.PUNCH.get(),
				new AbilityId(PowerClass.STAND, power.getPowerType().getId(), "stand_action_modifier_test"), events, endsAtPerform);
		EntityActionInstance action = ability.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
		helper.assertTrue(action.getPhase() == ActionPhase.WINDUP && LivingComponentAction.getComponent(stand).getAction() == action,
				"setup: the test action is not the Stand's, in its windup: " + action.getPhase());
		return action;
	}

	// as an add-on's ability adds it to its user's Stand effects: it attaches to the Stand's current action as it starts
	private static RecordingModifier attach(GameTestHelper helper, StandPower power, EntityActionInstance action,
			List<String> events) {
		RecordingModifier modifier = MODIFIER.create(helper.getLevel());
		modifier.events = events;
		modifier.withStand(power);
		helper.assertTrue(modifier.standAction == action && action.getPunchModifiers().contains(modifier),
				"setup: the Stand effect did not attach to the Stand's running action");
		return modifier;
	}

	// as a client adds it from the effect's add packet (TrEntityCustomEffectsPacket.tryApplyClient): initialized with its
	// user, then added to the user's Stand effects, whose putEffectInstance initializes it again
	private static RecordingModifier addAsAClientDoes(GameTestHelper helper, StandPower power, FakePlayer user,
			List<String> events, boolean ofUserAction) {
		RecordingModifier modifier = MODIFIER.create(helper.getLevel());
		modifier.events = events;
		modifier.isFromStandAction = !ofUserAction;
		modifier.isFromUserAction = ofUserAction;
		modifier.withEntity(user);
		power.userStandEffects.addEffect(modifier);
		return modifier;
	}

	// set on the user as a press of a Stand action the user performs sets it, in its Stand-power slot, with the same phases
	private static EntityActionInstance startUserAction(GameTestHelper helper, StandPower power, FakePlayer user,
			List<String> events) {
		RecordingUserAbility ability = new RecordingUserAbility(ModStandAbilities.PUNCH.get(),
				new AbilityId(PowerClass.STAND, power.getPowerType().getId(), "stand_action_modifier_user_test"), events);
		EntityActionInstance action = ability.initActionOnAbilityUse(helper.getLevel(), user, user, null);
		LivingComponentAction.getComponent(user, PowerClass.STAND).setAction(action, user, SyncType.NO_SYNC);
		helper.assertTrue(action.getPhase() == ActionPhase.WINDUP && LivingComponentAction.getComponent(user, PowerClass.STAND).getAction() == action,
				"setup: the test action is not the user's, in its windup: " + action.getPhase());
		return action;
	}

	private static long timesOn(EntityActionInstance action, StandEffectInstance modifier) {
		return action.getPunchModifiers().stream().filter(attached -> attached == modifier).count();
	}

	private static void tickUntilOver(LivingComponentAction standAction, EntityActionInstance action) {
		for (int tick = 0; tick < 20 && !action.isOver(); tick++) {
			standAction.tick();
		}
	}

	private static final class RecordingAbility extends StandEntityAbility {
		private RecordingAbility(AbilityType<?> abilityType, AbilityId abilityId, List<String> events, boolean endsAtPerform) {
			super(abilityType, abilityId, ability -> new RecordingAction(ability, events, endsAtPerform));
			setDefaultPhaseLength(ActionPhase.WINDUP, 2);
			setDefaultPhaseLength(ActionPhase.PERFORM, 2);
			setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
		}
	}

	private static final class RecordingUserAbility extends EntityActionAbility {
		private RecordingUserAbility(AbilityType<?> abilityType, AbilityId abilityId, List<String> events) {
			super(abilityType, abilityId, ability -> new RecordingAction(ability, events, false));
			setDefaultPhaseLength(ActionPhase.WINDUP, 2);
			setDefaultPhaseLength(ActionPhase.PERFORM, 2);
			setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
		}
	}

	private static final class RecordingAction extends EntityActionInstance {
		private final List<String> events;
		private final boolean endsAtPerform;

		private RecordingAction(EntityActionType ability, List<String> events, boolean endsAtPerform) {
			super(ability);
			this.events = events;
			this.endsAtPerform = endsAtPerform;
		}

		@Override
		public void actionTick() {
			events.add("action " + getPhase() + " " + (int) getPhaseTick());
		}

		@Override
		public void actionPerformStart() {
			events.add("perform start");
			if (endsAtPerform) {
				forceStop();
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (getPhase() != newPhase) {
				events.add("action " + getPhase() + " -> " + newPhase);
			}
		}
	}

	// an add-on's modifier of the running action (1.16 StandEntityActionModifier), here only recording when it runs
	private static final class RecordingModifier extends StandEffectInstance {
		private List<String> events = new ArrayList<>();

		private RecordingModifier(EntityCustomEffectType<?> effectType) {
			super(effectType);
			isFromStandAction = true;
		}

		@Override
		public void onModifiedActionTick(EntityActionInstance action) {
			events.add("modifier " + action.getPhase() + " " + (int) action.getPhaseTick());
		}

		@Override
		public void onModifiedActionPhaseTransition(EntityActionInstance action, ActionPhase prevPhase, ActionPhase nextPhase) {
			events.add("modifier " + prevPhase + " -> " + nextPhase + " in " + action.getPhase());
		}

		@Override
		protected void start() {}

		@Override
		protected void tick() {}

		@Override
		protected void stop() {}
	}
}
