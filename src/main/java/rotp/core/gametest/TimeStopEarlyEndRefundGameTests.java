package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.event.ModEventHooks;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner's rule for the stamina of a time stop that ends early (2026-10-14, calibration S04-04): the start pays the
 * full price scaled by the charged length; the shortest stop's price (a press released at once, the stop of pressing
 * time stop twice in quick succession) is never given back; an early end gives back the rest of the start's charge in
 * the share of its ticks left unused. The same holds when the stop ends because the last player left (no cooldown and
 * no training there, as 1.16's TimeStopHandler.reset).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopEarlyEndRefundGameTests {
	private static final ChunkPos FAR_AWAY = new ChunkPos(50400, 50400);
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("time_stop_early_end_refund_gametest"));

	private TimeStopEarlyEndRefundGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
	public static void instantTimeStopGivesNothingBack(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.setUp();
			EntityActionInstance charge = scene.startCharge();
			float before = scene.power.getStamina();
			// released at once: the shortest stop, 4 of 100 ticks with The World's 30-tick charge
			charge.onButtonStopHold();
			TimeStopState.Instance started = scene.instance();
			helper.assertTrue(started != null && started.totalTicks() == 4,
					"S04-04 premise: a release at once starts a 4-tick stop: " + started);
			float paid = before - scene.power.getStamina();
			float shortest = TimeStopLearning.getTimeStopStaminaCost(scene.power, TimeStopLearning.TIME_STOP, 4);
			helper.assertTrue(near(paid, shortest), "S04-04 premise: the instant stop paid " + paid + ", not " + shortest);
			float beforeEnd = scene.resumeAtOnce();
			helper.runAfterDelay(3, () -> {
				try {
					helper.assertTrue(scene.removed.size() == 1,
							"S04-04 premise: the instant stop did not end: " + scene.removed);
					helper.assertTrue(near(scene.staminaAtRemoval, beforeEnd),
							"the owner's rule: the instant stop's " + paid + " stamina is never given back, got "
									+ (scene.staminaAtRemoval - beforeEnd));
				}
				finally {
					scene.close();
				}
				helper.succeed();
			});
		}
		catch (RuntimeException | Error error) {
			scene.close();
			throw error;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 120, batch = GameTestBatches.TIME_STOP)
	public static void earlyResumeGivesBackTheUnusedShareAboveTheShortestStop(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.setUp();
			float before = scene.power.getStamina();
			scene.holdToFire();
			TimeStopState.Instance started = scene.instance();
			helper.assertTrue(started != null && started.totalTicks() == 100,
					"S04-04 premise: a full charge starts the trained 100-tick stop: " + started);
			float paid = before - scene.power.getStamina();
			float full = TimeStopLearning.getTimeStopStaminaCost(scene.power, TimeStopLearning.TIME_STOP, 100);
			float shortest = TimeStopLearning.getTimeStopStaminaCost(scene.power, TimeStopLearning.TIME_STOP, 4);
			helper.assertTrue(near(paid, full), "S04-04 premise: the full charge paid " + paid + ", not " + full);
			// past the 35-tick opening and some ticks of the stop itself
			helper.runAfterDelay(TimeStopAbility.TIME_STOP_OPENING_SETTLE_TICKS + 12, () -> {
				try {
					float beforeEnd = scene.resumeAtOnce();
					helper.runAfterDelay(3, () -> {
						try {
							helper.assertTrue(scene.removed.size() == 1,
									"S04-04 premise: the resumed stop did not end: " + scene.removed);
							TimeStopState.Instance ended = scene.removed.get(0);
							int unused = ended.totalTicks() - ended.ticksPassed();
							helper.assertTrue(ended.ticksPassed() > 0 && unused > 0,
									"S04-04 premise: the stop must end with ticks used and left: " + ended);
							float expected = (paid - shortest) * unused / ended.totalTicks();
							float refund = scene.staminaAtRemoval - beforeEnd;
							helper.assertTrue(near(refund, expected),
									"the owner's rule: (" + paid + " - " + shortest + ") x " + unused + " / "
											+ ended.totalTicks() + " = " + expected + " comes back, got " + refund);
						}
						finally {
							scene.close();
						}
						helper.succeed();
					});
				}
				catch (RuntimeException | Error error) {
					scene.close();
					throw error;
				}
			});
		}
		catch (RuntimeException | Error error) {
			scene.close();
			throw error;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
	public static void lastPlayerLeavingGetsTheEarlyEndRefund(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.setUp();
			helper.assertTrue(scene.power.getMaxStamina() >= 1000,
					"S04-04 premise: the bar holds the example's 1000: " + scene.power.getMaxStamina());
			// a state of its own: only the calls below tick it
			TimeStopState state = new TimeStopState(helper.getLevel());
			TimeStopState.Instance instance = new TimeStopState.Instance(1_000_007, 200, 200, FAR_AWAY, 1,
					scene.player.getId(), "time_stop_early_end_refund_gametest", 0.0F);
			// the owner's example: a full 200-tick stop that cost 1000, whose shortest stop costs 100
			helper.assertTrue(state.commitPreStart(ModEventHooks.onTimeStopPreStart(helper.getLevel(), instance),
					scene.timeStop, 1000.0F, 100.0F), "S04-04 premise: the stop was not committed");
			for (int tick = 0; tick < 10; tick++) {
				state.tickLifecycle();
			}
			helper.assertTrue(state.getInstance(1_000_007).filter(stop -> stop.ticksPassed() == 10).isPresent(),
					"S04-04 premise: the stop did not run 10 ticks");
			float points = scene.power.getCurTypeData().getAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP);
			scene.power.setStamina(0.0F);
			state.reset();
			helper.assertTrue(state.getInstance(1_000_007).isEmpty(), "S04-04 premise: the reset kept the stop");
			helper.assertTrue(near(scene.power.getStamina(), 855.0F),
					"the owner's rule reaches a stop the last player left: 900 x 190 / 200 = 855 comes back, got "
							+ scene.power.getStamina());
			helper.assertTrue(scene.power.getAbilityCooldown(TimeStopLearning.TIME_STOP) == 0
					&& near(scene.power.getCurTypeData().getAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP), points),
					"1.16 TimeStopHandler.reset settled no cooldown and no training");
			helper.assertTrue(records(state, "timeStopStarters").isEmpty() && records(state, "timeStopStartCharges").isEmpty(),
					"a reset must drop the starters and start charges it settled");
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	private static Map<?, ?> records(TimeStopState state, String field) {
		try {
			Field records = TimeStopState.class.getDeclaredField(field);
			records.setAccessible(true);
			return (Map<?, ?>) records.get(state);
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Could not read TimeStopState." + field, error);
		}
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(actual - expected) < 0.01F;
	}

	private static final class Scene {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final List<TimeStopState.Instance> removed = new ArrayList<>();
		private final List<Object> listeners = new ArrayList<>();
		private float staminaAtRemoval = Float.NaN;
		private Player player;
		private StandPower power;
		private TimeStopAbility timeStop;
		private LivingComponentAction component;
		private boolean closed;

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
		}

		private void setUp() {
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			player.setNoGravity(true);
			player.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(level.addFreshEntity(player), "S04-04 premise: could not add the user");
			StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(standType != null && StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "S04-04 premise: could not grant The World");
			PowerSkillUnlocks.Result unlocked = TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
			helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED
					|| unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "S04-04 premise: time stop not unlocked");
			// Resolve level 3 starts The World's time stop without the Stand body; 4 would shorten the charge to 20
			power.setResolveLevel(3);
			power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, 95,
					TimeStopLearning.getMaxTrainingPoints(power), power);
			power.setStamina(power.getMaxStamina());
			Ability ability = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
			helper.assertTrue(ability instanceof TimeStopAbility && !power.isStaminaInfinite()
					&& TimeStopLearning.getTimeStopTicks(power) == 100,
					"S04-04 premise: not a survival The World user with a 100-tick stop: "
							+ TimeStopLearning.getTimeStopTicks(power));
			timeStop = (TimeStopAbility) ability;
			component = LivingComponentAction.getComponent(player);

			// keeps the stop away from every test cell
			Consumer<TimeStopLifecycleEvent.PreStart> isolate = event -> {
				if (!closed && event.getInstance().userId() == player.getId()) {
					event.setInstance(event.getInstance().withArea(FAR_AWAY, 1));
				}
			};
			Consumer<TimeStopLifecycleEvent.Removed> onRemoved = event -> {
				if (!closed && event.getLevel() == level && event.getInstance().userId() == player.getId()) {
					removed.add(event.getInstance());
					staminaAtRemoval = power.getStamina();
				}
			};
			listeners.add(isolate);
			NeoForge.EVENT_BUS.addListener(TimeStopLifecycleEvent.PreStart.class, isolate);
			listeners.add(onRemoved);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TimeStopLifecycleEvent.Removed.class, onRemoved);
		}

		private EntityActionInstance startCharge() {
			EntityActionInstance action = timeStop.initActionOnAbilityUse(level, player, player, null);
			component.setAction(action, player, SyncType.NO_SYNC);
			helper.assertTrue(component.getAction() == action && action.getPhase() == ActionPhase.BUTTON_CHARGE
					&& action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE) == 30,
					"S04-04 premise: The World's 30-tick time stop charge did not start: " + action.getPhase());
			return action;
		}

		/** The held charge as the action component runs it: BUTTON_CHARGE to its end, then PERFORM. */
		private void holdToFire() {
			EntityActionInstance action = startCharge();
			int ticks = 0;
			while (ticks < 60 && component.getAction() == action && !action.isOver()) {
				component.tick();
				ticks++;
			}
		}

		/** 1.16 TimeResume: the first press leaves 11 ticks (none with 11 or fewer left), a second one none. */
		private float resumeAtOnce() {
			for (int press = 0; press < 2 && instance() != null && instance().ticksLeft() > 0; press++) {
				BufferingState resume = BufferingState.clickOnly();
				timeStop.onKeyPress(level, player, null, InputMethod.HOLD, 0.0F, resume);
				helper.assertTrue(resume.isActionSuccess, "S04-04 premise: the time stop key did not resume the stop");
			}
			TimeStopState.Instance stopping = instance();
			helper.assertTrue(stopping != null && stopping.ticksLeft() == 0,
					"S04-04 premise: two presses did not resume at once: " + stopping);
			// room under the bar's maximum for the whole refund
			power.setStamina(100.0F);
			return power.getStamina();
		}

		private TimeStopState.Instance instance() {
			return level.getData(ModDataAttachmentTypes.TIME_STOP.get()).getInstance(player.getId()).orElse(null);
		}

		private void close() {
			if (closed) return;
			try {
				if (player != null) {
					level.getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(player.getId());
				}
			}
			finally {
				closed = true;
				for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
				listeners.clear();
				if (component != null) component.setAction(null, SyncType.NO_SYNC);
				if (player != null) {
					player.removeEffect(ModStatusEffects.TIME_STOP);
					player.discard();
				}
			}
		}
	}
}
