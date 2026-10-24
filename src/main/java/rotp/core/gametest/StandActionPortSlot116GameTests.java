package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferedInputEntry;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionInstancePacket;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16's power held and performed a StandAction (PowerBaseImpl.heldActionData), never its Stand's task: one pressed while
 * the Stand ran a task went off at once beside it (only StandEntityAction checked the task), a held hold-type task's hold
 * ended as it went off (stopHeldAction(false)), and it met its Stand's stun only at its press and in its hold (its
 * performer, StandAction.getPerformer, the summoned Stand). The time stop refused a press during a task that could not be
 * cancelled, stopped one that could as its hold started (TimeStop.checkSpecificConditions, startedHolding), and gave the
 * Stand its windup pose from the power's held action (StandEntity.getCurPose). A port of a StandAction runs in its user's
 * Stand-power slot with its Stand out or not (EntityActionAbility#performsInUsersStandPowerSlot; S548 rows 26-29, D3).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandActionPortSlot116GameTests {
	private static final short TASK_KEY = 71;
	private static final short PORT_KEY = 72;
	private static final short STOP_KEY = 73;
	private static final short LATE_KEY = 74;
	private static final String PORT = "stand_action_port_slot_probe";
	private static final String HELD_PORT = "held_stand_action_port_slot_probe";
	private static final int WINDUP = 3;
	private static final int CHARGE = 10;
	private static final String STUN_KEY = "jojo.message.action_condition.stun";

	private StandActionPortSlot116GameTests() {}

	/*
	 * Star Platinum (no summon lock) and two moveset test ports of 1.16 StandActions that take the user's Stand-power slot
	 * (a 3-tick windup; a 10-tick charge fired into its perform), each pressed through the real condition check and
	 * AbilityInput.keyPress, the slots ticked by hand, the user's before the Stand's. Rows 26-27: pressed in the punch's
	 * windup, the port goes off at once in its user's slot, performed by the Stand, and performs in its 4th tick while the
	 * punch performs in the tick it does alone. Row 28: pressed during a held barrage with a recovery, the port goes off at
	 * once and the barrage's hold ends into its recovery. The stun: pressed with the Stand stunned it is refused for it; the
	 * held port performs in its 10th tick (the power fired it), and ends unperformed in its 4th when the Stand is stunned
	 * after its 3rd; stunned right after its press, the port still performs in its 4th tick.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandActionPortGoesOffAtOnceBesideItsStandsTaskAndMeetsItsStunOnlyAtItsPressAndHoldAsIn116(
			GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int punchAlone;
		try (Cell cell = new Cell(helper, 0)) {
			cell.press("punch", InputMethod.CLICK, TASK_KEY);
			EntityActionInstance punch = cell.standSlot().getAction();
			helper.assertTrue(punch != null && punch.getPhase() == ActionPhase.WINDUP, "setup: the punch is not in its windup: "
					+ punch);
			punchAlone = cell.tickUntil(() -> punch.getPhase() == ActionPhase.PERFORM, 20);
			helper.assertTrue(punchAlone > 0, "setup: the punch alone never performed");
		}

		try (Cell cell = new Cell(helper, 1)) {
			cell.press("punch", InputMethod.CLICK, TASK_KEY);
			EntityActionInstance punch = cell.standSlot().getAction();
			BufferingState pressed = cell.press(PORT, InputMethod.CLICK, PORT_KEY);
			EntityActionInstance port = cell.userSlot().getAction();
			if (!cell.wentOffAtOnce(pressed, port) || cell.standSlot().getAction() != punch
					|| punch.getPhase() != ActionPhase.WINDUP) {
				wrong.add("pressed in the punch's windup: " + cell.describePress(pressed, port) + ", the Stand's action "
						+ cell.standSlot().getAction() + " in " + punch.getPhase() + " (1.16: at once in its user's slot,"
						+ " performed by the Stand, beside the punch)");
			}
			else {
				int punchPerform = cell.tickUntil(() -> punch.getPhase() == ActionPhase.PERFORM, 20);
				cell.tickUntil(() -> ((PortAction) port).performed, 20);
				int portPerform = ((PortAction) port).performedTick;
				if (punchPerform != punchAlone || portPerform != WINDUP + 1 || cell.standSlot().getAction() != punch) {
					wrong.add("pressed in the punch's windup: the port performed in tick " + portPerform + " (1.16: "
							+ (WINDUP + 1) + "), the punch in tick " + punchPerform + " (alone " + punchAlone
							+ "), the Stand's action then " + cell.standSlot().getAction());
				}
			}
		}

		try (Cell cell = new Cell(helper, 2)) {
			cell.press("barrage", InputMethod.HOLD, TASK_KEY);
			EntityActionInstance barrage = cell.standSlot().getAction();
			helper.assertTrue(barrage != null && barrage.getPhase() == ActionPhase.PERFORM
					&& barrage.phasesLength.getFloat(ActionPhase.RECOVERY) > 0 && barrage.isHoldTypeStandAction(),
					"setup: Star Platinum is not holding a barrage with a recovery: " + barrage + " in "
							+ (barrage != null ? barrage.getPhase() : null));
			cell.tick();
			BufferingState pressed = cell.press(PORT, InputMethod.CLICK, PORT_KEY);
			EntityActionInstance port = cell.userSlot().getAction();
			if (!cell.wentOffAtOnce(pressed, port) || cell.standSlot().getAction() != barrage
					|| barrage.getPhase() != ActionPhase.RECOVERY || AbilityInput.isHeldByKey(cell.user, barrage)) {
				wrong.add("pressed during a held barrage: " + cell.describePress(pressed, port) + ", the barrage "
						+ barrage.getPhase() + ", held by its key " + AbilityInput.isHeldByKey(cell.user, barrage)
						+ " (1.16: at once, the barrage's hold ended into its recovery)");
			}
		}

		try (Cell cell = new Cell(helper, 3)) {
			cell.stunStand();
			BufferingState pressed = cell.press(PORT, InputMethod.CLICK, PORT_KEY);
			if (pressed.isActionSuccess || cell.userSlot().getAction() != null || !STUN_KEY.equals(cell.refusal)) {
				wrong.add("pressed with its Stand stunned: started " + pressed.isActionSuccess + ", the refusal " + cell.refusal
						+ ", the user's Stand-power action " + cell.userSlot().getAction() + " (1.16: refused for the stun)");
			}
		}

		try (Cell cell = new Cell(helper, 4)) {
			BufferingState pressed = cell.press(HELD_PORT, InputMethod.HOLD, PORT_KEY);
			EntityActionInstance port = cell.userSlot().getAction();
			int performed = cell.wentOffAtOnce(pressed, port) && port.getPhase() == ActionPhase.BUTTON_CHARGE
					? cell.tickUntil(() -> ((PortAction) port).performed, CHARGE + 5) : -1;
			if (performed != CHARGE) {
				wrong.add("held: " + cell.describePress(pressed, port) + ", performed in tick " + performed + " (1.16: its"
						+ " power fired it in tick " + CHARGE + ")");
			}
		}

		try (Cell cell = new Cell(helper, 5)) {
			BufferingState pressed = cell.press(HELD_PORT, InputMethod.HOLD, PORT_KEY);
			EntityActionInstance port = cell.userSlot().getAction();
			int over = -1;
			if (cell.wentOffAtOnce(pressed, port)) {
				for (int tick = 0; tick < 3; tick++) {
					cell.tick();
				}
				cell.stunStand();
				over = cell.tickUntil(() -> port.isOver() || cell.userSlot().getAction() != port, CHARGE + 5);
			}
			if (over != 4 || ((PortAction) port).performed) {
				wrong.add("held, its Stand stunned after its 3rd tick: " + cell.describePress(pressed, port) + ", over in tick "
						+ over + ", performed " + (port instanceof PortAction action && action.performed)
						+ " (1.16: its hold ended in tick 4, unperformed)");
			}
		}

		try (Cell cell = new Cell(helper, 6)) {
			BufferingState pressed = cell.press(PORT, InputMethod.CLICK, PORT_KEY);
			EntityActionInstance port = cell.userSlot().getAction();
			int performed = -1;
			if (cell.wentOffAtOnce(pressed, port)) {
				cell.stunStand();
				performed = cell.tickUntil(() -> ((PortAction) port).performed, 20);
			}
			if (performed != WINDUP + 1 || !ModStatusEffects.isStunned(cell.stand)) {
				wrong.add("its Stand stunned right after its press: " + cell.describePress(pressed, port) + ", performed in tick "
						+ performed + ", the Stand stunned then " + ModStatusEffects.isStunned(cell.stand) + " (1.16: performed"
						+ " at its press, here in tick " + (WINDUP + 1) + ")");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's power performed a StandAction beside its Stand's task and met its Stand's"
				+ " stun only at its press and in its hold: " + String.join("; ", wrong));
		helper.succeed();
	}

	/*
	 * The World at Resolve 4 (a 20-tick charge), out, its time stop pressed through the real condition check and
	 * AbilityInput.keyPress. During the punch's windup (a task that cannot be cancelled) it is refused without a message
	 * and the punch performs. In the punch's recovery (a task that can be) it stops the punch and charges in its user's
	 * Stand-power slot, performed by The World, which draws its windup pose from it while the user does not; the user's
	 * client gets it in that slot. A punch pressed during the charge waits in the Stand's input buffer (owner decision
	 * N2; 1.16 refused it) and the HUD draws it unavailable, the Stand's slot staying empty while the charge goes on.
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void theTimeStopWithItsStandOutChargesInItsUsersStandPowerSlotKeepingItsTaskRulesAsIn116(
			GameTestHelper helper) {
		try (Scene s = new Scene(helper, "PortSlotStopDuringPunch")) {
			s.press(s.standPower, "punch", InputMethod.CLICK, TASK_KEY);
			EntityActionInstance punch = s.standSlot().getAction();
			helper.assertTrue(punch != null && punch.getPhase() == ActionPhase.WINDUP, "setup: The World's punch is not in its"
					+ " windup: " + punch);
			BufferingState stop = s.press(s.standPower, "time_stop", InputMethod.HOLD, STOP_KEY);
			helper.assertTrue(!stop.isActionSuccess && !stop.shouldBuffer && s.refusal == null
					&& s.userSlot().getAction() == null && s.standSlot().getAction() == punch,
					"1.16 refused the time stop, without a message, while its Stand's task could not be cancelled (TimeStop"
							+ " :81-94): started " + stop.isActionSuccess + ", buffered " + stop.shouldBuffer + ", the refusal "
							+ s.refusal + ", the user's Stand-power action " + s.userSlot().getAction() + ", the Stand's "
							+ s.standSlot().getAction());
			int performed = 0;
			for (int tick = 1; tick <= 20 && performed == 0; tick++) {
				s.standSlot().tick();
				if (punch.getPhase() == ActionPhase.PERFORM) {
					performed = tick;
				}
			}
			helper.assertTrue(performed > 0, "1.16's refused time stop left the punch running, which never performed here");
		}

		try (Scene s = new Scene(helper, "PortSlotStopAfterPunch")) {
			s.press(s.standPower, "punch", InputMethod.CLICK, TASK_KEY);
			EntityActionInstance punch = s.standSlot().getAction();
			for (int tick = 0; tick < 20 && punch != null && punch.getPhase() != ActionPhase.RECOVERY; tick++) {
				s.standSlot().tick();
			}
			helper.assertTrue(punch != null && punch.getPhase() == ActionPhase.RECOVERY && s.standSlot().getAction() == punch,
					"setup: The World's punch is not in its recovery: " + punch);
			BufferingState pressed = s.press(s.standPower, "time_stop", InputMethod.HOLD, STOP_KEY);
			EntityActionInstance stop = s.userSlot().getAction();
			helper.assertTrue(pressed.isActionSuccess && !pressed.shouldBuffer && stop != null
					&& stop.ability == s.standPower.getAbility("time_stop") && stop.getPhase() == ActionPhase.BUTTON_CHARGE
					&& stop.getPerformer() == s.stand && punch.isOver() && s.standSlot().getAction() == null
					&& AbilityInput.getHeldAction(s.user, PowerClass.STAND) == stop,
					"1.16 stopped the Stand's cancellable task as the time stop's hold started and its user's power held the"
							+ " charge, The World out (TimeStop :107-112, StandAction.getPerformer): started "
							+ pressed.isActionSuccess + ", buffered " + pressed.shouldBuffer + ", the user's Stand-power action "
							+ stop + (stop != null ? " in " + stop.getPhase() + " performed by " + stop.getPerformer() : "")
							+ ", the punch over " + punch.isOver() + ", the Stand's action " + s.standSlot().getAction());
			helper.assertTrue(LivingComponentAction.getPoseComponent(s.stand) == s.userSlot()
					&& LivingComponentAction.getPoseComponent(s.user) == LivingComponentAction.getComponent(s.user),
					"1.16 drew the held time stop's windup on its Stand (StandEntity.getCurPose) and not on its user: the"
							+ " Stand's pose from " + describe(s, LivingComponentAction.getPoseComponent(s.stand))
							+ ", the user's from " + describe(s, LivingComponentAction.getPoseComponent(s.user)));
			EntityActionInstance clientStop = s.clientCopyOf(stop, LivingComponentAction.STAND_POWER_SLOT);
			helper.assertTrue(clientStop.posesItsStand() && ((EntityActionAbility) stop.ability).isHeldSincePress(clientStop),
					"the user's client's copy of the time stop poses its Stand and is held: " + clientStop.getPhase());

			BufferingState late = s.press(s.standPower, "punch", InputMethod.CLICK, LATE_KEY);
			BufferedInputEntry buffered = buffered(s.input().inputBuffer(PowerClass.STAND));
			helper.assertTrue(!late.isActionSuccess && late.shouldBuffer && buffered != null
					&& "punch".equals(buffered.baseAbilityId().nameInMoveset()) && s.standSlot().getAction() == null
					&& s.standPower.getAbility("punch").isHeldBackByHeldAction(s.user),
					"a punch pressed during the time stop's charge waits in the Stand's input buffer (owner decision N2; 1.16"
							+ " refused it while its power held the charge) and is drawn unavailable: started "
							+ late.isActionSuccess + ", buffered " + late.shouldBuffer + ", the Stand's buffer " + buffered
							+ ", the Stand's action " + s.standSlot().getAction());
			float charged = stop.getPhaseTick();
			for (int tick = 0; tick < 3; tick++) {
				s.userSlot().tick();
				s.standSlot().tick();
			}
			helper.assertTrue(s.userSlot().getAction() == stop && stop.getPhase() == ActionPhase.BUTTON_CHARGE
					&& stop.getPhaseTick() == charged + 3 && s.standSlot().getAction() == null,
					"the time stop charges on in its user's slot, the Stand's slot empty: " + stop.getPhase() + " tick "
							+ stop.getPhaseTick() + " (from " + charged + "), the Stand's action " + s.standSlot().getAction());
		}
		helper.succeed();
	}

	/*
	 * 1.16 EntityStandType.getStaminaRegen (:159-167) asked the Stand's task alone: with the Stand out the bar regenerated
	 * (1.5 a tick before the user's factors) unless that task could not (StandEntityAction.canStaminaRegen, false by
	 * default: nothing), and the time stop its user's power held was no task. The World at Resolve 4, out: what one tick of
	 * its power adds to a half-full bar with no action, in its punch's windup (a task: nothing), and in each of the time
	 * stop's first 3 charge ticks, which go on in its user's Stand-power slot (as with no action).
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void theTimeStopsChargeWithItsStandOutRegeneratesStaminaAsWithNoTaskAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "PortSlotStopStamina")) {
			helper.assertTrue(!s.standPower.isStaminaInfinite(), "setup: The World's user has infinite stamina");
			float idle = s.staminaGainInATick();
			helper.assertTrue(idle > 0, "setup: The World out with no action regenerated " + idle + " in a tick");
			s.press(s.standPower, "punch", InputMethod.CLICK, TASK_KEY);
			EntityActionInstance punch = s.standSlot().getAction();
			float inPunch = s.standPower.getStaminaTickGain();
			helper.assertTrue(punch != null && punch.getPhase() == ActionPhase.WINDUP && inPunch == 0,
					"setup: 1.16's Stand task that cannot regenerate (the punch) held the bar back: the punch " + punch
							+ " in " + (punch != null ? punch.getPhase() : null) + ", the tick's gain " + inPunch);
			for (int tick = 0; tick < 20 && punch.getPhase() != ActionPhase.RECOVERY; tick++) {
				s.standSlot().tick();
			}
			BufferingState pressed = s.press(s.standPower, "time_stop", InputMethod.HOLD, STOP_KEY);
			EntityActionInstance stop = s.userSlot().getAction();
			helper.assertTrue(pressed.isActionSuccess && stop != null && stop.getPhase() == ActionPhase.BUTTON_CHARGE
					&& stop.getPerformer() == s.stand && s.standSlot().getAction() == null,
					"setup: the time stop is not charging in its user's Stand-power slot beside an empty Stand slot: started "
							+ pressed.isActionSuccess + ", the user's Stand-power action " + stop + ", the Stand's action "
							+ s.standSlot().getAction());
			List<Float> gains = new ArrayList<>();
			boolean asIdle = true;
			for (int tick = 0; tick < 3; tick++) {
				float gain = s.staminaGainInATick();
				gains.add(gain);
				asIdle &= Math.abs(gain - idle) < 1.0E-4F;
				s.userSlot().tick();
				s.standSlot().tick();
			}
			helper.assertTrue(asIdle && s.userSlot().getAction() == stop && stop.getPhase() == ActionPhase.BUTTON_CHARGE,
					"1.16 regenerated its user's stamina during the time stop's charge with the Stand out as with no task (its"
							+ " power held the charge; EntityStandType.getStaminaRegen asked the Stand's task alone): the first"
							+ " 3 charge ticks added " + gains + " where a tick with no action added " + idle + " (the charge "
							+ stop.getPhase() + ", the user's Stand-power action " + s.userSlot().getAction() + ")");
		}
		helper.succeed();
	}

	/*
	 * 1.16 TheWorldTimeStop.cancelHeldOnGettingAttacked (:49-51): PowerBaseImpl.onUserGettingAttacked (:470-478) stopped
	 * the power's held time stop on a hit, and stopHeldAction(false) fired nothing. The World at Resolve 4, out: its time
	 * stop charges in its user's Stand-power slot, whose hit check (EntityActionAbility.onUserGettingAttacked) is now the
	 * only one that reaches it. A pig's hit on the user 3 ticks into the charge drops it, and no stop starts by its length.
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void aHitOnTheWorldsUserDropsItsTimeStopChargeWithTheWorldOutAsIn116(GameTestHelper helper) {
		boolean[] started = { false };
		int[] userId = { Integer.MIN_VALUE };
		java.util.function.Consumer<TimeStopLifecycleEvent.PreStart> noStop = event -> {
			if (event.getInstance().userId() == userId[0]) {
				started[0] = true;
				event.setCanceled(true);
			}
		};
		NeoForge.EVENT_BUS.addListener(TimeStopLifecycleEvent.PreStart.class, noStop);
		try (Scene s = new Scene(helper, "PortSlotStopHit")) {
			userId[0] = s.user.getId();
			BufferingState pressed = s.press(s.standPower, "time_stop", InputMethod.HOLD, STOP_KEY);
			EntityActionInstance stop = s.userSlot().getAction();
			helper.assertTrue(pressed.isActionSuccess && stop != null && stop.getPhase() == ActionPhase.BUTTON_CHARGE
					&& stop.getPerformer() == s.stand && s.standSlot().getAction() == null, "setup: the time stop is not"
							+ " charging in its user's Stand-power slot, The World out: started " + pressed.isActionSuccess
							+ ", the user's Stand-power action " + stop);
			for (int tick = 0; tick < 3; tick++) {
				s.userSlot().tick();
				s.standSlot().tick();
			}
			Pig pig = EntityType.PIG.create(helper.getLevel());
			helper.assertTrue(pig != null && !stop.isOver(), "setup: no pig, or the charge ended before the hit");
			EntityActionAbility.onUserGettingAttacked(new LivingDamageEvent.Post(s.user,
					new DamageContainer(s.user.damageSources().mobAttack(pig), 2.0F)));
			boolean droppedAtTheHit = stop.isOver();
			for (int tick = 0; tick < 30; tick++) {
				s.userSlot().tick();
				s.standSlot().tick();
			}
			helper.assertTrue(droppedAtTheHit && !started[0] && s.userSlot().getAction() == null,
					"1.16 stopped The World's held time stop on a hit to its user (cancelHeldOnGettingAttacked) and fired"
							+ " nothing: the charge dropped at the hit " + droppedAtTheHit + ", a stop started by its length "
							+ started[0] + ", the user's Stand-power action " + s.userSlot().getAction());
		}
		finally {
			NeoForge.EVENT_BUS.unregister(noStop);
		}
		helper.succeed();
	}

	private static String describe(Scene s, @Nullable LivingComponentAction slot) {
		return slot == s.userSlot() ? "the user's Stand-power slot" : slot == s.standSlot() ? "the Stand's slot"
				: slot == LivingComponentAction.getComponent(s.user) ? "the user's own slot" : String.valueOf(slot);
	}

	@Nullable
	private static BufferedInputEntry buffered(ActionInputBuffer buffer) {
		try {
			Field field = ActionInputBuffer.class.getDeclaredField("buffered");
			field.setAccessible(true);
			return (BufferedInputEntry) field.get(buffer);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("ActionInputBuffer.buffered is not reachable", e);
		}
	}

	// as ClAbilityInputPacket handles a press: the key's resolved ability, its condition check, then the press
	private static BufferingState press(GameTestHelper helper, Power<?> power, net.minecraft.world.entity.LivingEntity user,
			String name, InputMethod inputMethod, short key, String[] refusal) {
		BufferingState state = BufferingState.clickCanBuffer();
		Ability base = power.getAbility(name);
		helper.assertTrue(base != null, "Missing ability " + name);
		AbilityConditionCheck container = resolve(power, base);
		refusal[0] = null;
		if (container != null && !container.conditionCheck.isPositive()) {
			Component warning = container.conditionCheck.getWarning();
			refusal[0] = warning == null ? null : warning.getContents() instanceof TranslatableContents contents
					? contents.getKey() : warning.getString();
		}
		if (container != null && AbilityInput.withConditionCheck(container, user, inputMethod)) {
			AbilityInput.keyPress(key, container.ability, user, null, inputMethod, 0, state, base.getAbilityId());
		}
		return state;
	}

	// the power resolves its keys once a tick (Power.updateAvailableMoves)
	private static AbilityConditionCheck resolve(Power<?> power, Ability ability) {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(power, false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
		return power.updateAvailableMoves().getContextVariationContainer(ability);
	}

	/** A player with Star Platinum out and the two test ports in its moveset. */
	private static final class Cell implements AutoCloseable {
		private final GameTestHelper helper;
		private final net.minecraft.world.entity.player.Player user;
		private final StandPower power;
		private final StandType type;
		private final StandEntity stand;
		private final String[] refusalHolder = new String[1];
		@Nullable private String refusal;
		private int ticks;

		private Cell(GameTestHelper helper, int index) {
			this.helper = helper;
			user = GameTestPlayers.makeServerMockPlayer(helper, net.minecraft.world.level.GameType.SURVIVAL);
			Vec3 pos = helper.absoluteVec(new Vec3(1.5, 12 + index * 3, 1.5));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.getMoveset().abilities.put(PORT, new StandActionPort(ModStandAbilities.PUNCH.get(),
					new AbilityId(PowerClass.STAND, type.getId(), PORT), false));
			power.getMoveset().abilities.put(HELD_PORT, new StandActionPort(ModStandAbilities.PUNCH.get(),
					new AbilityId(PowerClass.STAND, type.getId(), HELD_PORT), true));
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Fixture: Star Platinum is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
		}

		LivingComponentAction standSlot() {
			return LivingComponentAction.getComponent(stand);
		}

		LivingComponentAction userSlot() {
			return LivingComponentAction.getComponent(user, PowerClass.STAND);
		}

		BufferingState press(String name, InputMethod inputMethod, short key) {
			power.setStamina(power.getMaxStamina());
			BufferingState state = StandActionPortSlot116GameTests.press(helper, power, user, name, inputMethod, key,
					refusalHolder);
			refusal = refusalHolder[0];
			return state;
		}

		boolean wentOffAtOnce(BufferingState pressed, @Nullable EntityActionInstance port) {
			return pressed.isActionSuccess && !pressed.shouldBuffer && port instanceof PortAction
					&& port.getPerformer() == stand && LivingComponentAction.getCurEntityAction(user, PowerClass.STAND) == port;
		}

		String describePress(BufferingState pressed, @Nullable EntityActionInstance port) {
			return "started " + pressed.isActionSuccess + ", buffered " + pressed.shouldBuffer + ", the user's Stand-power"
					+ " action " + port + (port != null ? " performed by " + port.getPerformer() : "");
		}

		void stunStand() {
			stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
		}

		// the user's slots tick before its Stand's, as the level ticks the user first
		void tick() {
			ticks++;
			userSlot().tick();
			standSlot().tick();
			if (userSlot().getAction() instanceof PortAction port && port.performed && port.performedTick < 0) {
				port.performedTick = ticks;
			}
		}

		// the tick, counted from the cell's presses, after which it was reached
		int tickUntil(java.util.function.BooleanSupplier reached, int maxTicks) {
			for (int tick = 1; tick <= maxTicks; tick++) {
				tick();
				if (reached.getAsBoolean()) {
					return ticks;
				}
			}
			return -1;
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			userSlot().setAction(null, SyncType.NO_SYNC);
			stand.removeEffect(ModStatusEffects.STUN);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	/** A player with The World at Resolve 4, out, its time stop unlocked, its connection recorded. */
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final FakePlayer user;
		private final RecordingConnection recording;
		private final StandPower standPower;
		private final StandType theWorld;
		private final StandEntity stand;
		private final String[] refusalHolder = new String[1];
		@Nullable private String refusal;

		private Scene(GameTestHelper helper, String name) {
			this.helper = helper;
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			user.getAbilities().instabuild = false;
			recording = new RecordingConnection(user);
			user.connection = recording;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user");
			theWorld = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			helper.assertTrue(theWorld != null, "Missing Stand type the_world");
			standPower = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(standPower, new StandInstance(theWorld)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant The World");
			standPower.setResolveLevel(4);
			standPower.getCurTypeData()._lockedAbilities.remove("time_stop");
			standPower.setStamina(standPower.getMaxStamina());
			helper.assertTrue(theWorld.summon(user, standPower), "Could not summon The World");
			stand = standPower.getSummonedStandEntity();
			helper.assertTrue(stand != null && standPower.getAbility("time_stop").isAbilityUnlocked(standPower),
					"setup: The World is not out or its time stop is locked");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
		}

		LivingComponentAction standSlot() {
			return LivingComponentAction.getComponent(stand);
		}

		LivingComponentAction userSlot() {
			return LivingComponentAction.getComponent(user, PowerClass.STAND);
		}

		EntityActionInputState input() {
			return user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		}

		BufferingState press(Power<?> power, String name, InputMethod inputMethod, short key) {
			standPower.setStamina(standPower.getMaxStamina());
			BufferingState state = StandActionPortSlot116GameTests.press(helper, power, user, name, inputMethod, key,
					refusalHolder);
			refusal = refusalHolder[0];
			return state;
		}

		// what one tick of the Stand power adds to a half-full bar
		float staminaGainInATick() {
			standPower.setStamina(standPower.getMaxStamina() / 2);
			float before = standPower.getStamina();
			standPower.tick();
			return standPower.getStamina() - before;
		}

		// the copy of the action the user's client got with the given slot (the last such packet for this user)
		EntityActionInstance clientCopyOf(EntityActionInstance action, int slot) {
			TrEntityActionInstancePacket last = null;
			for (Packet<?> packet : recording.packets) {
				if (packet instanceof ClientboundCustomPayloadPacket custom
						&& custom.payload() instanceof TrEntityActionInstancePacket sent
						&& field(sent, "performerId", Integer.class) == user.getId()
						&& field(sent, "slot", Integer.class) == slot) {
					last = sent;
				}
			}
			EntityActionInstance sentAction = last != null ? field(last, "sendAction", EntityActionInstance.class) : null;
			helper.assertTrue(sentAction == action, "the user's client got for slot " + slot + " " + sentAction + ", not "
					+ action + " (the action's own slot " + action.actionSlot() + ")");
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			try {
				EntityActionInstance.encode(buf, sentAction);
				EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
				helper.assertTrue(copy != null && copy.getClass() == action.getClass(), "setup: the action decodes as " + copy);
				return copy;
			}
			finally {
				buf.release();
			}
		}

		private static <T> T field(Object owner, String name, Class<T> type) {
			try {
				Field field = owner.getClass().getDeclaredField(name);
				field.setAccessible(true);
				return type.cast(field.get(owner));
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("cannot read " + name, e);
			}
		}

		@Override
		public void close() {
			input().heldKeys.clear();
			userSlot().setAction(null, SyncType.NO_SYNC);
			helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(user.getId());
			user.removeAllEffects();
			if (standPower.isSummoned()) {
				theWorld.forceUnsummon(user, standPower);
			}
			user.discard();
		}
	}

	// a fake player's connection that keeps what the server sends it
	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		final List<Packet<?>> packets = new ArrayList<>();

		RecordingConnection(FakePlayer player) {
			super(player.getServer(), player.connection.getConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		@Override
		public void send(Packet<?> packet) {
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(this::send);
			}
			else {
				packets.add(packet);
			}
		}

		@Override
		public void send(Packet<?> packet, PacketSendListener listener) {
			send(packet);
		}
	}

	// a port of a 1.16 StandAction in its user's Stand-power slot: a 3-tick windup, or a 10-tick charge fired into its perform
	private static final class StandActionPort extends StandEntityAbility {
		StandActionPort(AbilityType<?> abilityType, AbilityId abilityId, boolean held) {
			super(abilityType, abilityId, PortAction::new);
			if (held) {
				setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, CHARGE);
			}
			else {
				setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP);
			}
			setDefaultPhaseLength(ActionPhase.PERFORM, 2);
			setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
		}

		@Override
		public boolean performsInUsersStandPowerSlot() {
			return true;
		}
	}

	private static final class PortAction extends EntityActionInstance {
		boolean performed;
		int performedTick = -1;

		PortAction(EntityActionType ability) {
			super(ability);
		}

		@Override
		public boolean isHeldBackBySummonLock() {
			return false;
		}

		@Override
		public void actionPerformStart() {
			performed = true;
		}
	}
}
