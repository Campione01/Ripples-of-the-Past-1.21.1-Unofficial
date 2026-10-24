package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 performed a StandAction (TimeStop among them) in its user's power, which a new Stand's summon lock never held
 * back: StandEntity.tick (:1270-1281) held back only the Stand's task, counted the lock down through a stun, and went on
 * holding back the task set after the StandAction. The port's time stop runs in its user's Stand-power slot, performed
 * by the Stand (EntityActionAbility#performsInUsersStandPowerSlot), where the lock never holds it back; it does not take
 * the lock off the Stand either (an add-on port that still runs on its Stand: EntityActionInstance#isHeldBackBySummonLock).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandActionSummonLockGameTests {
	private StandActionSummonLockGameTests() {}

	private static final short TIME_STOP_KEY = 125;
	private static final short PUNCH_KEY = 124;
	// Star Platinum's time stop charge (1.16 holdToFire(40, false)), fired in the 40th tick from its press
	private static final int CHARGE = 40;
	// a summon lock longer than the charge, as only a Stand slower than speed 16 has one at all
	private static final int LOCK = 45;
	private static final int PUNCH_PRESS_TICK = CHARGE + 1;
	private static final int WATCHED_TICKS = LOCK + 3;

	/*
	 * Star Platinum's time stop held with Star Platinum not out (the real auto-summon), with no summon lock and with its
	 * lock made 45; pressed in the tick Star Platinum was summoned with the lock 45; and with the lock 45 and its user
	 * stunned at once. Each starts the stop in tick 40, as 1.16's power performed it, the user's client's copy of it is
	 * not held back by the lock either, the lock counts down from 45 through the stun as the summon fade-in, and a punch
	 * pressed after the stop, a Stand task, first ticks in tick 46, the tick after the lock ran out (with no lock: 42).
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void aTimeStopTicksThroughItsStandsSummonLockAsIn116WhileTheStandsNextTaskWaitsForIt(GameTestHelper helper) {
		List<Stop> stops = new ArrayList<>();
		try {
			for (Stop.Row row : Stop.Row.values()) {
				Stop stop = new Stop(row);
				stops.add(stop);
				stop.begin(helper, stops.size() - 1);
			}
		}
		catch (RuntimeException | Error failure) {
			stops.forEach(Stop::close);
			throw failure;
		}
		helper.runAfterDelay(WATCHED_TICKS, () -> {
			try {
				List<String> wrong = new ArrayList<>();
				for (Stop stop : stops) {
					if (!stop.asIn116()) {
						wrong.add(stop.describe());
					}
				}
				helper.assertTrue(wrong.isEmpty(), "1.16's power performed the time stop, a StandAction, whatever its Stand's"
						+ " summon lock, which went on counting and holding back the Stand's next task: "
						+ String.join("; ", wrong));
			}
			finally {
				stops.forEach(Stop::close);
			}
			helper.succeed();
		});
	}

	private static final short PORT_KEY = 123;
	private static final String PORT = "summon_lock_stand_slot_port_probe";
	private static final int PORT_WINDUP = 3;
	private static final int PORT_LOCK = 14;
	// once the port is over and before the lock runs out
	private static final int PORT_PUNCH_PRESS_TICK = 10;

	/*
	 * A port of a 1.16 StandAction that still runs on its Stand's own slot (an add-on's: performsInUsersStandPowerSlot
	 * false, isHeldBackBySummonLock false), pressed with a 3-tick windup in the tick Star Platinum was summoned with a
	 * summon lock of 14, performs in the Stand's 4th tick as with no lock, also with the Stand stunned from its press on,
	 * while the lock counts down through it; a punch pressed after the port is over, a Stand task, first ticks in tick 15,
	 * the tick after the lock ran out (1.16 StandEntity.tick :1270-1281 held back only the Stand's task).
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandSlotPortTicksThroughItsStandsSummonLockWhileAPunchPressedAfterItWaitsAsIn116(GameTestHelper helper) {
		List<PortCell> cells = new ArrayList<>();
		try {
			for (boolean stunned : new boolean[] { false, true }) {
				PortCell cell = new PortCell(stunned);
				cells.add(cell);
				cell.pressAsSummoned(helper, cells.size() - 1);
			}
		}
		catch (RuntimeException | Error failure) {
			cells.forEach(PortCell::close);
			throw failure;
		}
		helper.runAfterDelay(PORT_LOCK + 4, () -> {
			try {
				List<String> wrong = new ArrayList<>();
				for (PortCell cell : cells) {
					if (!cell.asIn116()) {
						wrong.add(cell.describe());
					}
				}
				helper.assertTrue(wrong.isEmpty(), "1.16's summon lock held back only the Stand's task, not a StandAction"
						+ " its power performed: " + String.join("; ", wrong));
			}
			finally {
				cells.forEach(PortCell::close);
			}
			helper.succeed();
		});
	}

	// the action as the user's client holds it: decoded from the action the server sends it (EntityActionInstance.encode)
	private static EntityActionInstance clientCopyOf(GameTestHelper helper, EntityActionInstance action, StandEntity stand) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			EntityActionInstance.encode(buf, action);
			EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
			helper.assertTrue(copy != null && copy.getClass() == action.getClass(), "Fixture: the action decodes as " + copy);
			Field performer = EntityActionInstance.class.getDeclaredField("performer");
			performer.setAccessible(true);
			performer.set(copy, stand);
			return copy;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot give the client's copy its Stand", e);
		}
		finally {
			buf.release();
		}
	}

	// Star Platinum's time stop, its stop moved far from every test cell, and the Stand after each of its ticks
	private static final class Stop {
		enum Row { NO_LOCK, NOT_OUT, SUMMONED_FIRST, STUNNED }

		final Row row;
		final List<Integer> locks = new ArrayList<>();
		Player user;
		StandPower power;
		StandType type;
		StandEntity stand;
		EntityActionInstance action;
		int lock;
		int standTicks;
		int startTick = -1;
		boolean stunnedAtStart;
		boolean copyHeldBack = true;
		EntityActionInstance punch;
		float punchTicksAtPress;
		int punchFirstTick = -1;
		boolean punchCopyHeldBack;
		final List<Object> listeners = new ArrayList<>();
		boolean closed;

		Stop(Row row) {
			this.row = row;
		}

		String name() {
			return row.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		}

		void begin(GameTestHelper helper, int cell) {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 pos = helper.absoluteVec(new Vec3(1.5 + cell * 3, 12, 1.5));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user for " + name());
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			// "time_stop" names the ability as GameTestBatchSourcePinGameTests counts a time stop press
			Ability timeStop = power.getAbility("time_stop");
			helper.assertTrue(timeStop instanceof TimeStopAbility, "Fixture: Star Platinum has no time stop: " + timeStop);
			power.getCurTypeData()._lockedAbilities.remove(timeStop.name());
			power.setResolveLevel(Math.max(power.getResolveLevel(), timeStop.getRequiredResolveLevel(power)));
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(timeStop.isAbilityUnlocked(power) && power.getSummonedStandEntity() == null,
					"Fixture: the time stop is locked, or Star Platinum is already out");
			int far = 50600 + cell * 8;
			Consumer<TimeStopLifecycleEvent.PreStart> started = event -> {
				if (!closed && event.getInstance().userId() == user.getId()) {
					event.setInstance(event.getInstance().withArea(new ChunkPos(far, -far), 1));
					if (startTick < 0) {
						startTick = standTicks + 1;
						stunnedAtStart = stand != null && ModStatusEffects.isStunned(stand);
					}
				}
			};
			listeners.add(started);
			NeoForge.EVENT_BUS.addListener(TimeStopLifecycleEvent.PreStart.class, started);
			if (row == Row.SUMMONED_FIRST) {
				helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
				power.getSummonedStandEntity().summonLockTicks = LOCK;
			}
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(timeStop);
			helper.assertTrue(AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD),
					"The time stop was refused for " + name());
			BufferingState pressed = BufferingState.clickOnly();
			AbilityInput.keyPress(TIME_STOP_KEY, selected.ability, user, null, InputMethod.HOLD, 0, pressed,
					timeStop.getAbilityId());
			stand = power.getSummonedStandEntity();
			action = LivingComponentAction.getCurEntityAction(user, PowerClass.STAND);
			helper.assertTrue(pressed.isActionSuccess && stand != null && stand.isAddedToLevel()
					&& action instanceof TimeStopAbility.TimeStopAction && action.getPhase() == ActionPhase.BUTTON_CHARGE
					&& action.getCurPhaseLength() == CHARGE && action.getPerformer() == stand, "The held time stop did not"
							+ " set its " + CHARGE + "-tick charge in its user's Stand-power slot, performed by Star Platinum, for "
							+ name() + ": " + stand + ", " + action);
			if (row == Row.NOT_OUT || row == Row.STUNNED) {
				helper.assertTrue(stand.summonLockTicks == 0, "Fixture: Star Platinum has a summon lock of its own: "
						+ stand.summonLockTicks);
				stand.summonLockTicks = LOCK;
			}
			if (row == Row.STUNNED) {
				user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 100));
				helper.assertTrue(ModStatusEffects.isStunned(stand), "Fixture: stunning its user did not stun Star Platinum");
			}
			lock = stand.summonLockTicks;
			copyHeldBack = clientCopyOf(helper, action, stand).isHeldBackBySummonLock();
			// after the Stand's tick and its action's (EntityTickEvent.Post)
			Consumer<EntityTickEvent.Post> ticked = event -> {
				if (closed || event.getEntity() != stand) {
					return;
				}
				standTicks++;
				locks.add(stand.summonLockTicks);
				if (punch != null && punchFirstTick < 0
						&& (stand.getCurStandAction() != punch || punch.getFullTicksPassed() != punchTicksAtPress)) {
					punchFirstTick = standTicks;
				}
				if (standTicks == PUNCH_PRESS_TICK && row != Row.STUNNED) {
					pressPunch(helper);
				}
			};
			listeners.add(ticked);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, ticked);
		}

		// a Stand task pressed once the time stop has started, while the lock (if any) still runs
		private void pressPunch(GameTestHelper helper) {
			AbilityInput.keyRelease(TIME_STOP_KEY, user);
			power.setStamina(power.getMaxStamina());
			Ability ability = power.getAbility("punch");
			if (ability == null || stand.getCurStandAction() != null) {
				return;
			}
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
			if (!AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK)) {
				return;
			}
			BufferingState pressed = BufferingState.clickOnly();
			AbilityInput.keyPress(PUNCH_KEY, selected.ability, user, null, InputMethod.CLICK, 0, pressed, ability.getAbilityId());
			EntityActionInstance set = stand.getCurStandAction();
			if (pressed.isActionSuccess && set != null && set != action) {
				punch = set;
				punchTicksAtPress = set.getFullTicksPassed();
				punchCopyHeldBack = clientCopyOf(helper, set, stand).isHeldBackBySummonLock();
			}
		}

		boolean asIn116() {
			if (standTicks < WATCHED_TICKS || startTick != CHARGE || copyHeldBack || lock != (row == Row.NO_LOCK ? 0 : LOCK)) {
				return false;
			}
			for (int k = 1; k <= standTicks; k++) {
				if (locks.get(k - 1) != Math.max(lock - k, 0)) {
					return false;
				}
			}
			if (row == Row.STUNNED) {
				return stunnedAtStart;
			}
			return punch != null && punchCopyHeldBack && punchFirstTick == (lock > 0 ? lock + 1 : PUNCH_PRESS_TICK + 1);
		}

		String describe() {
			return "Star Platinum's time stop " + name() + " (the summon lock " + lock + "): the stop started in tick "
					+ startTick + " (1.16: " + CHARGE + ")" + (row == Row.STUNNED ? ", its Stand stunned then " + stunnedAtStart
							: "") + ", the user's client's copy held back by the lock " + copyHeldBack + ", the lock after each"
					+ " tick " + locks + (row == Row.STUNNED ? "" : ", a punch pressed after tick " + PUNCH_PRESS_TICK
							+ (punch == null ? " was not set" : " first ticked in tick " + punchFirstTick + " (1.16: "
									+ (lock > 0 ? lock + 1 : PUNCH_PRESS_TICK + 1) + "), its client's copy held back by the"
									+ " lock " + punchCopyHeldBack));
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			for (Object listener : listeners) {
				NeoForge.EVENT_BUS.unregister(listener);
			}
			if (user != null) {
				removeStop();
				AbilityInput.keyRelease(TIME_STOP_KEY, user);
				AbilityInput.keyRelease(PUNCH_KEY, user);
				user.removeEffect(ModStatusEffects.TIME_STOP);
				user.removeEffect(ModStatusEffects.STUN);
				if (power != null && type != null && power.isSummoned()) {
					type.forceUnsummon(user, power);
				}
				user.discard();
			}
		}

		private void removeStop() {
			if (user.level() instanceof ServerLevel level) {
				level.getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(user.getId());
			}
		}
	}

	// Star Platinum with the Stand-slot port pressed as it is summoned with a lock, and the Stand after each of its ticks
	private static final class PortCell {
		final boolean stunned;
		final List<Integer> locks = new ArrayList<>();
		final List<Object> listeners = new ArrayList<>();
		Player user;
		StandPower power;
		StandType type;
		StandEntity stand;
		PortAction port;
		int standTicks;
		int performTick = -1;
		boolean stunnedAtPerform;
		EntityActionInstance punch;
		float punchTicksAtPress;
		int punchPressTick = -1;
		int punchFirstTick = -1;
		boolean closed;

		PortCell(boolean stunned) {
			this.stunned = stunned;
		}

		void pressAsSummoned(GameTestHelper helper, int cell) {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 pos = helper.absoluteVec(new Vec3(1.5, 12 + cell * 3, 1.5));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the port's user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.getMoveset().abilities.put(PORT, new StandSlotPort(ModStandAbilities.PUNCH.get(),
					new AbilityId(PowerClass.STAND, type.getId(), PORT)));
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Fixture: Star Platinum is missing");
			stand.summonLockTicks = PORT_LOCK;
			Ability ability = power.getAbility(PORT);
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
			helper.assertTrue(AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK), "The port was refused");
			BufferingState pressed = BufferingState.clickOnly();
			AbilityInput.keyPress(PORT_KEY, selected.ability, user, null, InputMethod.CLICK, 0, pressed, ability.getAbilityId());
			AbilityInput.keyRelease(PORT_KEY, user);
			helper.assertTrue(pressed.isActionSuccess && stand.getCurStandAction() instanceof PortAction set
					&& !set.isHeldBackBySummonLock() && LivingComponentAction.getCurEntityAction(user, PowerClass.STAND) == null,
					"Fixture: the port did not start on Star Platinum's own slot: " + stand.getCurStandAction());
			port = (PortAction) stand.getCurStandAction();
			if (stunned) {
				user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 100));
				helper.assertTrue(ModStatusEffects.isStunned(stand), "Fixture: stunning its user did not stun Star Platinum");
			}
			// after the Stand's tick and its action's (EntityTickEvent.Post)
			Consumer<EntityTickEvent.Post> ticked = event -> {
				if (closed || event.getEntity() != stand) {
					return;
				}
				standTicks++;
				locks.add(stand.summonLockTicks);
				if (performTick < 0 && port.performed) {
					performTick = standTicks;
					stunnedAtPerform = ModStatusEffects.isStunned(stand);
				}
				if (punch != null && punchFirstTick < 0
						&& (stand.getCurStandAction() != punch || punch.getFullTicksPassed() != punchTicksAtPress)) {
					punchFirstTick = standTicks;
				}
				if (!stunned && punch == null && standTicks >= PORT_PUNCH_PRESS_TICK && standTicks < PORT_LOCK
						&& stand.getCurStandAction() == null) {
					pressPunch();
				}
			};
			listeners.add(ticked);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, ticked);
		}

		private void pressPunch() {
			power.setStamina(power.getMaxStamina());
			Ability ability = power.getAbility("punch");
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
			if (ability == null || !AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK)) {
				return;
			}
			BufferingState pressed = BufferingState.clickOnly();
			AbilityInput.keyPress(PUNCH_KEY, selected.ability, user, null, InputMethod.CLICK, 0, pressed, ability.getAbilityId());
			AbilityInput.keyRelease(PUNCH_KEY, user);
			EntityActionInstance set = stand.getCurStandAction();
			if (pressed.isActionSuccess && set != null && set != port) {
				punch = set;
				punchTicksAtPress = set.getFullTicksPassed();
				punchPressTick = standTicks;
			}
		}

		boolean asIn116() {
			if (standTicks < PORT_LOCK + 4 || performTick != PORT_WINDUP + 1 || stunnedAtPerform != stunned) {
				return false;
			}
			for (int k = 1; k <= standTicks; k++) {
				if (locks.get(k - 1) != Math.max(PORT_LOCK - k, 0)) {
					return false;
				}
			}
			return stunned || punch != null && punchFirstTick == PORT_LOCK + 1;
		}

		String describe() {
			return "the Stand-slot port pressed as Star Platinum was summoned with the summon lock " + PORT_LOCK
					+ (stunned ? ", the Stand stunned from its press," : "") + " performed in tick " + performTick
					+ " (1.16: " + (PORT_WINDUP + 1) + "), the Stand stunned then " + stunnedAtPerform + ", the lock after each"
					+ " tick " + locks + (stunned ? "" : ", a punch pressed after tick " + punchPressTick
							+ (punch == null ? " was not set" : " first ticked in tick " + punchFirstTick + " (1.16: "
									+ (PORT_LOCK + 1) + ")"));
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			for (Object listener : listeners) {
				NeoForge.EVENT_BUS.unregister(listener);
			}
			if (user != null) {
				AbilityInput.keyRelease(PORT_KEY, user);
				AbilityInput.keyRelease(PUNCH_KEY, user);
				user.removeEffect(ModStatusEffects.STUN);
				if (power != null && type != null && power.isSummoned()) {
					type.forceUnsummon(user, power);
				}
				user.discard();
			}
		}
	}

	// a port of a 1.16 StandAction that still runs on its Stand's own slot (the hook's add-on case): a 3-tick windup
	private static final class StandSlotPort extends StandEntityAbility {
		StandSlotPort(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId, PortAction::new);
			setDefaultPhaseLength(ActionPhase.WINDUP, PORT_WINDUP);
			setDefaultPhaseLength(ActionPhase.PERFORM, 2);
			setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
		}
	}

	private static final class PortAction extends EntityActionInstance {
		boolean performed;

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
