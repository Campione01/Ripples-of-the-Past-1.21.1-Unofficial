package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * task set as its Stand was summoned, with the lock at L, first ticked in the Stand's tick L + 1.
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
}
