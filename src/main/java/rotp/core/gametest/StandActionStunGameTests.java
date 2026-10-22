package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
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
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.tick held back only a stunned Stand's task (a StandEntityAction), unless its action ignored performer
 * stun. A StandAction its user's power performed met the stun of its performer, the summoned Stand
 * (StandAction.getPerformer), only in PowerBaseImpl.checkRequirements: at its press, which refused it, on every tick of its
 * hold, which ended it, and as the hold fired, which fired nothing; a non-held one was performed at its press. The port's
 * Stand performs such an action, which is not held back by its Stand's stun when it is not held back by its summon lock
 * (EntityActionInstance#isHeldBackByStandStun).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandActionStunGameTests {
	private StandActionStunGameTests() {}

	private static final short KEY = 126;
	private static final String STAND_ACTION = "stand_action_stun_probe";
	private static final String HELD_STAND_ACTION = "held_stand_action_stun_probe";
	private static final int WINDUP = 3;
	private static final int CHARGE = 10;
	private static final int STUN = 10;
	private static final int WATCHED_TICKS = 30;
	private static final String STUN_KEY = "jojo.message.action_condition.stun";

	/*
	 * Star Platinum (no summon lock) performs a port of a StandAction with a 3-tick windup, its held port (a 10-tick charge
	 * that fires into its perform) and its punch, a 1.16 StandEntityAction, each with and without its Stand stunned (the
	 * user not) for 10 ticks. The StandAction's port performs in the tick it does unstunned, its Stand stunned right after
	 * its press, and its held port stunned as it fires; the held port stunned in its charge ends in the next tick without
	 * performing; pressed with its Stand stunned it is refused for the stun; the punch, the Stand's task, waits out the
	 * stun, performing as many ticks later as its Stand was stunned before.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandActionsPortTicksThroughItsStandsStunWhichItMeetsOnlyAtItsPressAndInItsHoldAsIn116(
			GameTestHelper helper) {
		List<Cell> cells = new ArrayList<>();
		try {
			for (Cell.Row row : Cell.Row.values()) {
				Cell cell = new Cell(row);
				cells.add(cell);
				cell.begin(helper, cells.size() - 1);
			}
		}
		catch (RuntimeException | Error failure) {
			cells.forEach(Cell::close);
			throw failure;
		}
		helper.runAfterDelay(WATCHED_TICKS, () -> {
			try {
				int punchPerform = cells.stream().filter(cell -> cell.row == Cell.Row.PUNCH).findFirst()
						.map(cell -> cell.performTick).orElse(-1);
				List<String> wrong = new ArrayList<>();
				for (Cell cell : cells) {
					if (!cell.asIn116(punchPerform)) {
						wrong.add(cell.describe(punchPerform));
					}
				}
				helper.assertTrue(wrong.isEmpty(), "1.16 checked a StandAction's Stand stun only at its press and in its"
						+ " hold, and held back only the Stand's task while the Stand was stunned: " + String.join("; ", wrong));
			}
			finally {
				cells.forEach(Cell::close);
			}
			helper.succeed();
		});
	}

	private static final class Cell {
		enum Row {
			STAND_ACTION, STAND_ACTION_STUNNED_AFTER_PRESS, PRESSED_WITH_STAND_STUNNED,
			HELD, HELD_STUNNED_IN_CHARGE, HELD_STUNNED_AS_IT_FIRES,
			PUNCH, PUNCH_STUNNED_AFTER_PRESS
		}

		final Row row;
		Player user;
		StandPower power;
		StandType type;
		StandEntity stand;
		EntityActionInstance action;
		int standTicks;
		int performTick = -1;
		int overTick = -1;
		int stunnedBeforePerform;
		boolean stunnedAtPerform;
		boolean userStunned;
		String refusal;
		final List<Object> listeners = new ArrayList<>();
		boolean closed;

		Cell(Row row) {
			this.row = row;
		}

		String name() {
			return row.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		}

		boolean held() {
			return row == Row.HELD || row == Row.HELD_STUNNED_IN_CHARGE || row == Row.HELD_STUNNED_AS_IT_FIRES;
		}

		boolean punch() {
			return row == Row.PUNCH || row == Row.PUNCH_STUNNED_AFTER_PRESS;
		}

		void begin(GameTestHelper helper, int cell) {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			// stacked above the structure: a cell further along x could leave the test's force-loaded chunks, where
			// its Stand would not tick
			Vec3 pos = helper.absoluteVec(new Vec3(1.5, 12 + cell * 3, 1.5));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user for " + name());
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.getMoveset().abilities.put(STAND_ACTION, new StandActionPort(ModStandAbilities.PUNCH.get(),
					new AbilityId(PowerClass.STAND, type.getId(), STAND_ACTION), false));
			power.getMoveset().abilities.put(HELD_STAND_ACTION, new StandActionPort(ModStandAbilities.PUNCH.get(),
					new AbilityId(PowerClass.STAND, type.getId(), HELD_STAND_ACTION), true));
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum for " + name());
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Fixture: Star Platinum is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;

			Ability ability = power.getAbility(punch() ? "punch" : held() ? HELD_STAND_ACTION : STAND_ACTION);
			if (row == Row.PRESSED_WITH_STAND_STUNNED) {
				stunStand();
			}
			AbilityConditionCheck selected = resolve(ability);
			InputMethod input = held() ? InputMethod.HOLD : InputMethod.CLICK;
			if (row == Row.PRESSED_WITH_STAND_STUNNED) {
				Component warning = selected != null ? selected.conditionCheck.getWarning() : null;
				refusal = selected == null || selected.conditionCheck.isPositive() ? "none"
						: warning != null && warning.getContents() instanceof TranslatableContents contents ? contents.getKey()
						: "no message";
			}
			if (selected != null && AbilityInput.withConditionCheck(selected, user, input)) {
				BufferingState pressed = BufferingState.clickOnly();
				AbilityInput.keyPress(KEY, selected.ability, user, null, input, 0, pressed, ability.getAbilityId());
				if (!held()) {
					AbilityInput.keyRelease(KEY, user);
				}
			}
			action = stand.getCurStandAction();
			if (row != Row.PRESSED_WITH_STAND_STUNNED) {
				helper.assertTrue(action != null && action.ability == ability, "Fixture: " + name() + " set "
						+ action + " on Star Platinum");
				helper.assertTrue(action.isHeldBackBySummonLock() == punch() && action.isHeldBackByStandStun() == punch(),
						"Fixture: " + name() + " is a port of a 1.16 " + (punch() ? "StandEntityAction" : "StandAction")
								+ " but held back by the lock " + action.isHeldBackBySummonLock() + ", by the stun "
								+ action.isHeldBackByStandStun());
			}
			if (row == Row.STAND_ACTION_STUNNED_AFTER_PRESS || row == Row.PUNCH_STUNNED_AFTER_PRESS) {
				stunStand();
			}
			// after the Stand's tick and its action's (EntityTickEvent.Post)
			Consumer<EntityTickEvent.Post> ticked = event -> {
				if (closed || event.getEntity() != stand) {
					return;
				}
				standTicks++;
				boolean stunned = ModStatusEffects.isStunned(stand);
				userStunned |= ModStatusEffects.isStunned(user);
				if (action != null && performTick < 0) {
					if (performed()) {
						performTick = standTicks;
						stunnedAtPerform = stunned;
					}
					else if (stunned) {
						stunnedBeforePerform++;
					}
				}
				if (action != null && overTick < 0 && (action.isOver() || stand.getCurStandAction() != action)) {
					overTick = standTicks;
				}
				if (row == Row.HELD_STUNNED_IN_CHARGE && standTicks == 3
						|| row == Row.HELD_STUNNED_AS_IT_FIRES && standTicks == CHARGE) {
					stunStand();
				}
			};
			listeners.add(ticked);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, ticked);
		}

		private boolean performed() {
			return action instanceof StandActionPortAction port ? port.performed : action.punchedTarget != null;
		}

		private void stunStand() {
			stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, STUN));
		}

		// the power resolves its keys once a tick (Power.updateAvailableMoves)
		private AbilityConditionCheck resolve(Ability ability) {
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

		boolean asIn116(int punchPerform) {
			if (userStunned || standTicks < WATCHED_TICKS) {
				return false;
			}
			return switch (row) {
				case STAND_ACTION -> performTick == WINDUP + 1;
				case STAND_ACTION_STUNNED_AFTER_PRESS -> performTick == WINDUP + 1 && stunnedAtPerform;
				case PRESSED_WITH_STAND_STUNNED -> action == null && STUN_KEY.equals(refusal);
				case HELD, HELD_STUNNED_AS_IT_FIRES -> performTick == CHARGE + 1 && (row == Row.HELD || stunnedAtPerform);
				case HELD_STUNNED_IN_CHARGE -> performTick < 0 && overTick == 4;
				case PUNCH -> performTick > 0;
				case PUNCH_STUNNED_AFTER_PRESS -> stunnedBeforePerform > 0 && punchPerform > 0
						&& performTick == punchPerform + stunnedBeforePerform;
			};
		}

		String describe(int punchPerform) {
			String expected = switch (row) {
				case STAND_ACTION, STAND_ACTION_STUNNED_AFTER_PRESS -> "performed in tick " + (WINDUP + 1);
				case PRESSED_WITH_STAND_STUNNED -> "refused at its press for the stun";
				case HELD, HELD_STUNNED_AS_IT_FIRES -> "performed in tick " + (CHARGE + 1);
				case HELD_STUNNED_IN_CHARGE -> "its hold ended in tick 4, unperformed";
				case PUNCH -> "performed";
				case PUNCH_STUNNED_AFTER_PRESS -> "performed in tick " + punchPerform + " + its stunned ticks";
			};
			return name() + ": performed in tick " + performTick + ", over in tick " + overTick + ", the Stand stunned in "
					+ stunnedBeforePerform + " ticks before it performed and " + stunnedAtPerform + " as it did"
					+ (row == Row.PRESSED_WITH_STAND_STUNNED ? ", the press's refusal " + refusal + ", the Stand's action "
							+ action : "") + (userStunned ? ", its user stunned too" : "") + " (1.16: " + expected + ")";
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
				AbilityInput.keyRelease(KEY, user);
				if (stand != null) {
					stand.removeEffect(ModStatusEffects.STUN);
				}
				if (power != null && type != null && power.isSummoned()) {
					type.forceUnsummon(user, power);
				}
				user.discard();
			}
		}
	}

	// a port of a 1.16 StandAction that its Stand performs: a 3-tick windup, or a 10-tick charge fired into its perform
	private static final class StandActionPort extends StandEntityAbility {
		StandActionPort(AbilityType<?> abilityType, AbilityId abilityId, boolean held) {
			super(abilityType, abilityId, StandActionPortAction::new);
			if (held) {
				setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, CHARGE);
			}
			else {
				setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP);
			}
			setDefaultPhaseLength(ActionPhase.PERFORM, 2);
			setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
		}
	}

	private static final class StandActionPortAction extends EntityActionInstance {
		boolean performed;

		StandActionPortAction(EntityActionType ability) {
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
