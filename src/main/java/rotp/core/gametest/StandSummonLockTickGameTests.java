package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
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
 * task set as its Stand was summoned, with the lock at L, first ticked in the Stand's tick L + 1; in a tick it did not
 * count the lock, it counted the gradual summon (gradualSummonWeaknessTicks) down only while the Stand was not stunned.
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
