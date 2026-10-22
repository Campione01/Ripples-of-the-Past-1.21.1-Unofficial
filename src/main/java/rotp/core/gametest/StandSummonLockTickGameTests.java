package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands.magiciansred.MRCrossfireHurricaneEntity;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.tick (:1270-1290) either counted a new Stand's summon lock down or ticked its task, never both, so a
 * task set as its Stand was summoned, with the lock at L, first ticked in the Stand's tick L + 1, while its user's power
 * counted a held action (a charge, a hold with a maximum) from the press through the lock (PowerBaseImpl.tickHeldAction);
 * in a tick it did not count the
 * lock, it counted the gradual summon (gradualSummonWeaknessTicks) down only while the Stand was not stunned.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandSummonLockTickGameTests {
	private StandSummonLockTickGameTests() {}

	private static final short KEY = 126;

	/*
	 * Crazy Diamond (lock 3) and Magician's Red (lock 7): a heavy punch and a held flame burst whose press summons the
	 * whole Stand, a punch and a held guard pressed in the tick the Stand is summoned, and a punch whose press summons only
	 * an arm, which has no lock (1.16 onArmsOnlyModeUpdated) and ticks at once.
	 */
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aStandActionPressedAsItsStandIsSummonedFirstTicksAfterItsSummonLockAsIn116(GameTestHelper helper) {
		List<Press> presses = new ArrayList<>();
		try {
			press(helper, presses, "crazy_diamond", "heavy_punch", InputMethod.CLICK, false);
			press(helper, presses, "magicians_red", "flame_burst", InputMethod.HOLD, false);
			press(helper, presses, "crazy_diamond", "punch", InputMethod.CLICK, true);
			press(helper, presses, "magicians_red", "guard", InputMethod.HOLD, true);
			press(helper, presses, "crazy_diamond", "punch", InputMethod.CLICK, false);
		}
		catch (RuntimeException | Error failure) {
			presses.forEach(Press::close);
			throw failure;
		}
		helper.runAfterDelay(12, () -> {
			try {
				List<String> wrong = new ArrayList<>();
				for (Press press : presses) {
					boolean lockRanOut = press.lock == 0 || press.locks.size() >= press.lock
							&& press.locks.get(press.lock - 1) == 0 && (press.lock < 2 || press.locks.get(press.lock - 2) == 1);
					if (press.firstTick != press.lock + 1 || !lockRanOut || press.heldAfterItsTick) {
						wrong.add(press.name + " with the summon lock at " + press.lock + " first ticked in the Stand's tick "
								+ press.firstTick + " (1.16: " + (press.lock + 1) + "), the lock after each tick " + press.locks
								+ (press.heldAfterItsTick ? ", and the tick the lock ran out still held back an action tick after"
										+ " its own" : ""));
					}
				}
				helper.assertTrue(wrong.isEmpty(), "1.16 counted a new Stand's summon lock down or ticked its task, never"
						+ " both, so a task pressed as the Stand was summoned first ticked the tick after the lock ran out: "
						+ String.join("; ", wrong));
			}
			finally {
				presses.forEach(Press::close);
			}
			helper.succeed();
		});
	}

	private static final int CHARGE_WATCHED_TICKS = 32;

	/*
	 * 1.16 counted a held charge from its press in its user's power (PowerBaseImpl.tickHeldAction, with the held recheck),
	 * which the summon lock did not hold back (StandEntity.tick held back only the Stand's task), and moved the task on at
	 * the charge's length, its next phase then waiting for the lock. Crossfire Hurricane (holdToFire(20, false)) held with
	 * Magician's Red (lock 7) not out, through the real auto-summon: the shot in tick 21, as with the Stand out; with the
	 * lock made 24, longer than the charge: the charge ends in the lock and the shot comes in tick 25 (L + 1); let go in
	 * tick 3: it ends there without a shot; made unusable in tick 3: its held recheck ends it in tick 4, in the lock.
	 */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aHeldChargeCountsFromItsPressThroughItsStandsSummonLockAsIn116(GameTestHelper helper) {
		List<Charge> charges = new ArrayList<>();
		try {
			for (Charge.Row row : Charge.Row.values()) {
				Charge charge = new Charge(row);
				charges.add(charge);
				charge.start(helper, charges.size() - 1);
			}
		}
		catch (RuntimeException | Error failure) {
			charges.forEach(Charge::close);
			throw failure;
		}
		helper.runAfterDelay(CHARGE_WATCHED_TICKS, () -> {
			try {
				List<String> wrong = new ArrayList<>();
				for (Charge charge : charges) {
					if (!charge.asIn116()) {
						wrong.add(charge.describe());
					}
				}
				helper.assertTrue(wrong.isEmpty(), "1.16's power counted a held charge from its press through its Stand's"
						+ " summon lock, and only the task's next phase waited for the lock: " + String.join("; ", wrong));
			}
			finally {
				charges.forEach(Charge::close);
			}
			helper.succeed();
		});
	}

	private static final int HELD_TICKS_READ_AFTER = 5;

	/*
	 * 1.16 counted a held action from its press in its user's power (PowerBaseImpl.tickHeldAction), which the summon lock
	 * did not hold back: its hold maximum (getHoldDurationMax), its held recheck and the ticks held its hold-only cooldown
	 * took (stopHeldAction). Crazy Diamond's barrage (1.16 holdType, its hold maximum 20 + 5 x durability) held from the
	 * tick Crazy Diamond was summoned whole (lock 3) and, as the control, with the lock taken off first: both go into their
	 * recovery in tick maximum + 1 from the press and have counted 5 held ticks after tick 5; its user stunned after tick
	 * 1: the held recheck ends the hold in tick 2, in the lock.
	 */
	@GameTest(template = "empty", timeoutTicks = 140)
	public static void aHeldPerformCountsItsHoldFromItsPressThroughItsStandsSummonLockAsIn116(GameTestHelper helper) {
		List<Hold> holds = new ArrayList<>();
		try {
			for (Hold.Row row : Hold.Row.values()) {
				Hold hold = new Hold(row);
				holds.add(hold);
				hold.start(helper, holds.size() - 1);
			}
		}
		catch (RuntimeException | Error failure) {
			holds.forEach(Hold::close);
			throw failure;
		}
		helper.runAfterDelay(holds.get(0).max + 6, () -> {
			try {
				List<String> wrong = new ArrayList<>();
				for (Hold hold : holds) {
					if (!hold.asIn116()) {
						wrong.add(hold.describe());
					}
				}
				helper.assertTrue(wrong.isEmpty(), "1.16's power counted a held action from its press through its Stand's"
						+ " summon lock (its hold maximum, held recheck and held ticks), and only the task waited: "
						+ String.join("; ", wrong));
			}
			finally {
				holds.forEach(Hold::close);
			}
			helper.succeed();
		});
	}

	private static final int STUNNED_TICKS = 5;

	/*
	 * Crazy Diamond summoned as arms and then whole (its gradual summon, 1.16 onArmsOnlyModeUpdated) and Crazy Diamond
	 * summoned whole (its summon lock of 3), each with its user stunned at once, which stuns the Stand too: the lock runs
	 * out through the stun, while the gradual summon and the arms' weaker stats wait for the stun to end.
	 */
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theGradualSummonCountsOnlyWhileTheStandIsNotStunnedAsIn116(GameTestHelper helper) {
		Watch gradual = new Watch("crazy_diamond summoned as arms, then whole");
		Watch whole = new Watch("crazy_diamond summoned whole");
		Runnable close = () -> {
			gradual.close();
			whole.close();
		};
		try {
			gradual.start(helper, 0, true);
			whole.start(helper, 1, false);
		}
		catch (RuntimeException | Error failure) {
			close.run();
			throw failure;
		}
		helper.runAfterDelay(STUNNED_TICKS, () -> {
			try {
				helper.assertTrue(gradual.ticks() >= 3 && gradual.stunned.stream().allMatch(stunned -> stunned)
						&& gradual.fades.stream().allMatch(fade -> fade == gradual.fade)
						&& gradual.armsStats.stream().allMatch(arms -> arms),
						"1.16 counted the gradual summon down only in a tick its Stand was not stunned: " + gradual.describe());
				boolean lockRanOut = whole.ticks() >= whole.lock && whole.stunned.stream().allMatch(stunned -> stunned);
				for (int i = 0; i < whole.ticks(); i++) {
					lockRanOut &= whole.locks.get(i) == Math.max(whole.lock - 1 - i, 0);
				}
				helper.assertTrue(lockRanOut, "1.16 counted the summon lock down through a stun: " + whole.describe());
				gradual.user.removeEffect(ModStatusEffects.STUN);
				whole.user.removeEffect(ModStatusEffects.STUN);
				helper.assertTrue(!ModStatusEffects.isStunned(gradual.stand), "Fixture: the stun did not end on "
						+ gradual.name);
			}
			catch (RuntimeException | Error failure) {
				close.run();
				throw failure;
			}
			helper.runAfterDelay(gradual.fade + 1, () -> {
				try {
					int stunnedTicks = gradual.stunned.indexOf(false);
					boolean countedOn = stunnedTicks >= 3 && gradual.ticks() >= stunnedTicks + gradual.fade;
					for (int i = stunnedTicks; countedOn && i < gradual.ticks(); i++) {
						int left = Math.max(gradual.fade - 1 - (i - stunnedTicks), 0);
						countedOn = gradual.fades.get(i) == left && gradual.armsStats.get(i) == (left > 0)
								&& !gradual.stunned.get(i);
					}
					helper.assertTrue(countedOn, "Once the stun ended, the gradual summon counted down a tick at a time"
							+ " and the arms' weaker stats went with its end: " + gradual.describe());
				}
				finally {
					close.run();
				}
				helper.succeed();
			});
		});
	}

	private static void press(GameTestHelper helper, List<Press> presses, String standId, String abilityName,
			InputMethod method, boolean summonedFirst) {
		Press press = new Press(standId + "'s " + abilityName + (summonedFirst ? " pressed as it was summoned" : ""));
		presses.add(press);
		press.start(helper, presses.size() - 1, standId, abilityName, method, summonedFirst);
	}

	private static final class Press {
		final String name;
		final List<Integer> locks = new ArrayList<>();
		Player user;
		StandPower power;
		StandType type;
		StandEntity stand;
		EntityActionInstance action;
		int lock;
		float ticksAtPress;
		int standTicks;
		int firstTick = -1;
		boolean heldAfterItsTick;
		Consumer<EntityTickEvent.Post> listener;
		boolean closed;

		Press(String name) {
			this.name = name;
		}

		// the user, held still above its own cell, presses the ability through the real condition check and key press
		void start(GameTestHelper helper, int cell, String standId, String abilityName, InputMethod method,
				boolean summonedFirst) {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 pos = helper.absoluteVec(new Vec3(1.5 + cell * 3, 12, 1.5));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user for " + name);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(power.getSummonedStandEntity() == null, "Fixture: " + standId + " is already out");
			if (summonedFirst) {
				helper.assertTrue(type.summon(user, power), "Could not summon " + standId);
			}
			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability != null, "Missing " + name);
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
			helper.assertTrue(AbilityInput.withConditionCheck(selected, user, method), name + " was refused with "
					+ standId + " not out");
			BufferingState pressed = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, user, null, method, 0, pressed, ability.getAbilityId());
			stand = power.getSummonedStandEntity();
			action = stand != null ? stand.getCurStandAction() : null;
			helper.assertTrue(pressed.isActionSuccess && stand != null && stand.isAddedToLevel() && action != null,
					"The press of " + name + " did not summon " + standId + " into the action: " + stand + ", " + action);
			lock = stand.summonLockTicks;
			boolean armsOnly = stand.isArmsOnlyMode();
			helper.assertTrue((armsOnly ? lock == 0 : lock > 1) && stand.gradualSummonWeaknessTicks == 0,
					"Fixture: the new " + standId + (armsOnly ? " (arms only)" : "") + "'s summon lock is " + lock + ", its fade "
							+ stand.gradualSummonWeaknessTicks);
			ticksAtPress = action.getFullTicksPassed();
			// after the Stand's tick and its action's (EntityTickEvent.Post)
			listener = event -> {
				if (closed || event.getEntity() != stand) {
					return;
				}
				standTicks++;
				locks.add(stand.summonLockTicks);
				if (firstTick < 0 && (stand.getCurStandAction() != action || action.getFullTicksPassed() != ticksAtPress)) {
					firstTick = standTicks;
				}
				// the tick that ran the lock out held back its own action tick, not a later one
				if (standTicks == lock) {
					heldAfterItsTick = stand.takeSummonLockedTick();
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, listener);
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (listener != null) {
				NeoForge.EVENT_BUS.unregister(listener);
			}
			if (user != null) {
				AbilityInput.keyRelease(KEY, user);
				if (power != null && type != null && power.isSummoned()) {
					type.forceUnsummon(user, power);
				}
				user.discard();
			}
		}
	}

	// Crossfire Hurricane held with Magician's Red not out, and the Stand's action after each of the Stand's ticks
	private static final class Charge {
		enum Row { HELD, LOCK_LONGER_THAN_THE_CHARGE, LET_GO_IN_TICK_3, MADE_UNUSABLE_IN_TICK_3 }

		static final String ABILITY = "crossfire_hurricane";
		static final int LONGER_LOCK = 24;
		final Row row;
		final List<String> states = new ArrayList<>();
		final List<Integer> locks = new ArrayList<>();
		Player user;
		StandPower power;
		StandType type;
		StandEntity stand;
		EntityActionInstance action;
		int lock;
		int standTicks;
		int shotTick = -1;
		int goneTick = -1;
		boolean endedAtRelease;
		Consumer<EntityTickEvent.Post> listener;
		boolean closed;

		Charge(Row row) {
			this.row = row;
		}

		void start(GameTestHelper helper, int cell) {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 pos = helper.absoluteVec(new Vec3(1.5 + cell * 3, 12, 7.5));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user for " + row);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Magician's Red");
			power.setResolveLevel(4);
			power.getCurTypeData()._lockedAbilities.remove(ABILITY);
			power.setStamina(power.getMaxStamina());
			Ability ability = power.getAbility(ABILITY);
			helper.assertTrue(ability != null && ability.isAbilityUnlocked(power) && power.getSummonedStandEntity() == null,
					"Fixture: Crossfire Hurricane is locked, or Magician's Red is already out");
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
			helper.assertTrue(AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD),
					"Crossfire Hurricane was refused with Magician's Red not out");
			BufferingState pressed = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.HOLD, 0, pressed, ability.getAbilityId());
			stand = power.getSummonedStandEntity();
			action = stand != null ? stand.getCurStandAction() : null;
			helper.assertTrue(pressed.isActionSuccess && stand != null && stand.isAddedToLevel() && action != null
					&& action.getPhase() == ActionPhase.BUTTON_CHARGE && action.getCurPhaseLength() == 20,
					"The held press of Crossfire Hurricane did not summon Magician's Red into its 20-tick charge: " + stand
							+ ", " + action);
			if (row == Row.LOCK_LONGER_THAN_THE_CHARGE) {
				stand.summonLockTicks = LONGER_LOCK;
			}
			lock = stand.summonLockTicks;
			helper.assertTrue(lock == (row == Row.LOCK_LONGER_THAN_THE_CHARGE ? LONGER_LOCK : 7),
					"Fixture: Magician's Red's summon lock is " + lock);
			// after the Stand's tick and its action's (EntityTickEvent.Post)
			listener = event -> {
				if (closed || event.getEntity() != stand) {
					return;
				}
				standTicks++;
				locks.add(stand.summonLockTicks);
				EntityActionInstance now = stand.getCurStandAction();
				states.add(now == action ? action.getPhase() + " " + (int) action.getPhaseTick() : String.valueOf(now));
				if (goneTick < 0 && now != action) {
					goneTick = standTicks;
				}
				if (shotTick < 0 && !helper.getLevel().getEntitiesOfClass(MRCrossfireHurricaneEntity.class,
						stand.getBoundingBox().inflate(48), shot -> shot.getOwner() == stand).isEmpty()) {
					shotTick = standTicks;
				}
				if (standTicks == 3 && row == Row.LET_GO_IN_TICK_3) {
					AbilityInput.keyRelease(KEY, user);
					endedAtRelease = stand.getCurStandAction() == null;
				}
				else if (standTicks == 3 && row == Row.MADE_UNUSABLE_IN_TICK_3) {
					power.getCurTypeData()._lockedAbilities.add(ABILITY);
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, listener);
		}

		// the charge's own tick after the Stand's k-th tick: 1.16's power counted k of them from the press
		private boolean chargedTicks(int ticks) {
			for (int k = 1; k <= ticks; k++) {
				if (!states.get(k - 1).equals(ActionPhase.BUTTON_CHARGE + " " + k)) {
					return false;
				}
			}
			return true;
		}

		boolean asIn116() {
			if (standTicks < 25) {
				return false;
			}
			return switch (row) {
				// counted in the lock (the tick the lock ran out too), fired after tick 20, shot in the next
				case HELD -> chargedTicks(19) && states.get(19).equals(ActionPhase.PERFORM + " 0") && shotTick == 21;
				// fired in the lock, its perform waiting for the lock: the shot in tick L + 1
				case LOCK_LONGER_THAN_THE_CHARGE -> chargedTicks(19) && states.get(19).equals(ActionPhase.PERFORM + " 0")
						&& states.get(LONGER_LOCK - 1).equals(ActionPhase.PERFORM + " 0") && shotTick == LONGER_LOCK + 1;
				case LET_GO_IN_TICK_3 -> chargedTicks(3) && endedAtRelease && goneTick == 4 && shotTick < 0;
				// 1.16's held recheck in the power, in the lock
				case MADE_UNUSABLE_IN_TICK_3 -> chargedTicks(3) && goneTick == 4 && locks.get(3) > 0 && shotTick < 0;
			};
		}

		String describe() {
			return "Crossfire Hurricane " + row.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " (the summon lock " + lock
					+ "): shot in tick " + shotTick + ", gone after tick " + goneTick + (row == Row.LET_GO_IN_TICK_3
							? ", ended at the release " + endedAtRelease : "") + ", the action after each tick " + states
					+ ", the lock " + locks;
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (listener != null) {
				NeoForge.EVENT_BUS.unregister(listener);
			}
			if (user != null) {
				AbilityInput.keyRelease(KEY, user);
				if (power != null && type != null && power.isSummoned()) {
					type.forceUnsummon(user, power);
				}
				user.discard();
			}
		}
	}

	// Crazy Diamond's barrage held from the tick Crazy Diamond was summoned whole, and its action after each of its ticks
	private static final class Hold {
		enum Row { NO_LOCK, LOCKED, STUNNED_AFTER_TICK_1 }

		final Row row;
		final List<String> states = new ArrayList<>();
		final List<Integer> locks = new ArrayList<>();
		Player user;
		StandPower power;
		StandType type;
		StandEntity stand;
		EntityActionInstance action;
		int lock;
		int max;
		int standTicks;
		int recoveryTick = -1;
		int heldTicks = -1;
		Consumer<EntityTickEvent.Post> listener;
		boolean closed;

		Hold(Row row) {
			this.row = row;
		}

		void start(GameTestHelper helper, int cell) {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 pos = helper.absoluteVec(new Vec3(1.5 + cell * 3, 12, 1.5));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user for " + row);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(type.summon(user, power), "Could not summon Crazy Diamond");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && !stand.isArmsOnlyMode(), "Fixture: Crazy Diamond is not out whole: " + stand);
			if (row == Row.NO_LOCK) {
				stand.summonLockTicks = 0;
			}
			lock = stand.summonLockTicks;
			helper.assertTrue(lock == (row == Row.NO_LOCK ? 0 : 3), "Fixture: Crazy Diamond's summon lock is " + lock);
			Ability ability = power.getAbility("barrage");
			helper.assertTrue(ability != null, "Missing Crazy Diamond's barrage");
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
			helper.assertTrue(AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD),
					"Crazy Diamond's barrage was refused for " + row);
			BufferingState pressed = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.HOLD, 0, pressed, ability.getAbilityId());
			action = stand.getCurStandAction();
			helper.assertTrue(pressed.isActionSuccess && action instanceof StandEntityBarrageAbility.StandEntityBarrage
					&& action.getPhase() == ActionPhase.PERFORM && action.isCooldownTakenAfterHold(),
					"The held press of Crazy Diamond's barrage did not set its held perform: " + action);
			max = StandEntityBarrageAbility.getBarrageHoldDurationMax(stand);
			helper.assertTrue(max > lock + 1 && max < 100, "Fixture: the barrage's hold maximum is " + max);
			// after the Stand's tick and its action's (EntityTickEvent.Post)
			listener = event -> {
				if (closed || event.getEntity() != stand) {
					return;
				}
				standTicks++;
				locks.add(stand.summonLockTicks);
				power.setStamina(power.getMaxStamina());
				EntityActionInstance now = stand.getCurStandAction();
				states.add(now == action ? action.getPhase() + " " + (int) action.getPhaseTick() : String.valueOf(now));
				if (recoveryTick < 0 && (now != action || action.getPhase() != ActionPhase.PERFORM)) {
					recoveryTick = standTicks;
				}
				if (standTicks == HELD_TICKS_READ_AFTER) {
					heldTicks = heldCooldownTicks(action);
				}
				if (standTicks == 1 && row == Row.STUNNED_AFTER_TICK_1) {
					user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, listener);
		}

		// the ticks held the barrage's hold-only cooldown takes as its hold ends (1.16 stopHeldAction's ticksHeld)
		private static int heldCooldownTicks(EntityActionInstance action) {
			try {
				Field field = EntityActionInstance.class.getDeclaredField("heldCooldownTicks");
				field.setAccessible(true);
				return field.getInt(action);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Fixture: cannot read the ticks held", e);
			}
		}

		boolean asIn116() {
			if (standTicks < max + 2) {
				return false;
			}
			return switch (row) {
				// the hold maximum counted from the press: the recovery in tick max + 1, the lock's ticks included
				case NO_LOCK, LOCKED -> recoveryTick == max + 1 && heldTicks == HELD_TICKS_READ_AFTER
						&& states.get(max).startsWith(ActionPhase.RECOVERY.name());
				// 1.16's held recheck in the power, in the lock
				case STUNNED_AFTER_TICK_1 -> recoveryTick == 2 && locks.get(1) > 0
						&& states.get(1).startsWith(ActionPhase.RECOVERY.name());
			};
		}

		String describe() {
			return "Crazy Diamond's barrage " + row.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " (the summon lock "
					+ lock + ", the hold maximum " + max + "): its hold ended after tick " + recoveryTick + ", the ticks held"
					+ " after tick " + HELD_TICKS_READ_AFTER + " " + heldTicks + ", the action after each tick " + states
					+ ", the lock " + locks;
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (listener != null) {
				NeoForge.EVENT_BUS.unregister(listener);
			}
			if (user != null) {
				AbilityInput.keyRelease(KEY, user);
				user.removeEffect(ModStatusEffects.STUN);
				if (power != null && type != null && power.isSummoned()) {
					type.forceUnsummon(user, power);
				}
				user.discard();
			}
		}
	}

	// a Crazy Diamond and its stunned user, with the Stand's summon counters and state after each of its ticks
	private static final class Watch {
		final String name;
		final List<Integer> locks = new ArrayList<>();
		final List<Integer> fades = new ArrayList<>();
		final List<Boolean> stunned = new ArrayList<>();
		final List<Boolean> armsStats = new ArrayList<>();
		Player user;
		StandPower power;
		StandType type;
		StandEntity stand;
		int lock;
		int fade;
		Consumer<EntityTickEvent.Post> listener;
		boolean closed;

		Watch(String name) {
			this.name = name;
		}

		void start(GameTestHelper helper, int cell, boolean asArmsFirst) {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 pos = helper.absoluteVec(new Vec3(1.5 + cell * 3, 12, 4.5));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user for " + name);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type instanceof EntityStandType && StandPowerTransitions.insert(power, new StandInstance(type))
					.status() == StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
			if (asArmsFirst) {
				helper.assertTrue(((EntityStandType) type).summon(user, power, entity -> entity.setArmsOnlyMode(), true),
						"Could not summon Crazy Diamond's arms");
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null && stand.isArmsOnlyMode(), "Fixture: Crazy Diamond is not out as arms: " + stand);
				stand.fullSummonFromArms();
			}
			else {
				helper.assertTrue(type.summon(user, power), "Could not summon Crazy Diamond");
				stand = power.getSummonedStandEntity();
			}
			helper.assertTrue(stand != null && !stand.isArmsOnlyMode(), "Fixture: Crazy Diamond is not out whole: " + stand);
			lock = stand.summonLockTicks;
			fade = stand.gradualSummonWeaknessTicks;
			helper.assertTrue(asArmsFirst ? lock == 0 && fade > 1 && hasArmsStats() : lock > 1 && fade == 0,
					"Fixture: " + name + " has the summon lock " + lock + ", the gradual summon " + fade + ", arms' stats "
							+ hasArmsStats());
			user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 100));
			helper.assertTrue(ModStatusEffects.isStunned(stand), "Fixture: stunning its user did not stun " + name);
			listener = event -> {
				if (closed || event.getEntity() != stand) {
					return;
				}
				locks.add(stand.summonLockTicks);
				fades.add(stand.gradualSummonWeaknessTicks);
				stunned.add(ModStatusEffects.isStunned(stand));
				armsStats.add(hasArmsStats());
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, listener);
		}

		boolean hasArmsStats() {
			AttributeInstance damage = stand.getAttribute(Attributes.ATTACK_DAMAGE);
			return damage != null && damage.hasModifier(JojoMod.resLoc("stand_arms_only_attack_damage"));
		}

		int ticks() {
			return fades.size();
		}

		String describe() {
			return name + " (lock " + lock + ", gradual summon " + fade + "): after each tick the lock " + locks
					+ ", the gradual summon " + fades + ", stunned " + stunned + ", the arms' weaker stats " + armsStats;
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (listener != null) {
				NeoForge.EVENT_BUS.unregister(listener);
			}
			if (user != null) {
				if (power != null && type != null && power.isSummoned()) {
					type.forceUnsummon(user, power);
				}
				user.discard();
			}
		}
	}
}
