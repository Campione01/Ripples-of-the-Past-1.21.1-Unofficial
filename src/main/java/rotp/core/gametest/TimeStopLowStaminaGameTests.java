package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopBehaviorPolicies;
import rotp.core.api.timestop.TimeStopBehaviorPolicy;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.api.timestop.TimeStopStartupCostContext;
import rotp.core.api.timestop.TimeStopStartupCostDecision;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 has no stamina gate on a time stop: StandAction.consumeStamina ignores a failed StandPower.consumeStamina
 * (which empties the bar), TimeStop.perform starts the instance, and the first TimeStopInstance.tick cannot pay its
 * ticking cost, so time resumes at once with the bar at 0.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopLowStaminaGameTests {
	private static final ChunkPos FAR_AWAY = new ChunkPos(50200, 50200);
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("time_stop_low_stamina_gametest"));

	private TimeStopLowStaminaGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60, batch = GameTestBatches.TIME_STOP)
	public static void lowStaminaTimeStopStartsEmptiesTheBarAndEndsOnItsFirstTick(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.setUp();
			scene.power.setStamina(1.0F);
			float fullCost = TimeStopLearning.getTimeStopStaminaCost(scene.power, TimeStopLearning.TIME_STOP,
					TimeStopLearning.getTimeStopTicks(scene.power));
			float minCost = TimeStopLearning.getTimeStopStaminaCost(scene.power, TimeStopLearning.TIME_STOP,
					TimeStopLearning.MIN_RELEASE_TIME_STOP_TICKS);
			helper.assertTrue(scene.power.getStamina() == 1.0F && minCost > 1.0F && fullCost > minCost,
					"LOW-STAMINA-TS premise: 1 stamina must be under the cheapest start: stamina="
							+ scene.power.getStamina() + " minCost=" + minCost + " fullCost=" + fullCost);
			boolean admitted = scene.timeStop.checkSpecificConditions(scene.power).isPositive();
			int holdTicks = scene.holdToFire();
			TimeStopState.Instance started = scene.instance();
			float staminaAfterStart = scene.power.getStamina();
			boolean effectAfterStart = scene.player.hasEffect(ModStatusEffects.TIME_STOP);
			helper.runAfterDelay(4, () -> {
				try {
					TimeStopState.Instance left = scene.instance();
					helper.assertTrue(admitted && started != null && staminaAfterStart == 0.0F && effectAfterStart
									&& scene.removed.size() == 1 && scene.removed.get(0).ticksPassed() <= 1
									&& scene.reasons.get(0) == TimeStopLifecycleEvent.RemovalReason.INTERRUPTED
									&& scene.staminaAtRemoval == 0.0F && left == null
									&& !scene.player.hasEffect(ModStatusEffects.TIME_STOP),
							"The World time stop held to fire with 1 stamina (1.16: it starts, the bar goes to 0 and the"
									+ " first tick ends it): admitted=" + admitted + " holdTicks=" + holdTicks
									+ " started=" + (started != null) + " staminaAfterStart=" + staminaAfterStart
									+ " effectAfterStart=" + effectAfterStart + " removed=" + scene.removed.size()
									+ " reasons=" + scene.reasons + " ticksPassedAtRemoval="
									+ (scene.removed.isEmpty() ? -1 : scene.removed.get(0).ticksPassed())
									+ " staminaAtRemoval=" + scene.staminaAtRemoval + " stillActive=" + (left != null)
									+ " effectLeft=" + scene.player.hasEffect(ModStatusEffects.TIME_STOP));
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

	@GameTest(template = "empty", timeoutTicks = 60, batch = GameTestBatches.TIME_STOP)
	public static void paidTimeStopKeepsItsOpeningAndItsStartCharge(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.setUp();
			float max = scene.power.getMaxStamina();
			scene.power.setStamina(max);
			float fullCost = TimeStopLearning.getTimeStopStaminaCost(scene.power, TimeStopLearning.TIME_STOP,
					TimeStopLearning.getTimeStopTicks(scene.power));
			helper.assertTrue(max > fullCost + 50.0F, "LOW-STAMINA-TS premise: the bar cannot pay a full start: max="
					+ max + " cost=" + fullCost);
			boolean admitted = scene.timeStop.checkSpecificConditions(scene.power).isPositive();
			int holdTicks = scene.holdToFire();
			TimeStopState.Instance started = scene.instance();
			float taken = max - scene.power.getStamina();
			helper.runAfterDelay(4, () -> {
				try {
					TimeStopState.Instance left = scene.instance();
					helper.assertTrue(admitted && started != null && started.isStartupSettling()
									&& started.ticksPassed() == -TimeStopAbility.TIME_STOP_OPENING_SETTLE_TICKS
									&& Math.abs(taken - fullCost) < 0.01F && scene.removed.isEmpty() && left != null
									&& left.isStartupSettling() && left.ticksLeft() == started.ticksLeft(),
							"The World time stop held to fire with a full bar: admitted=" + admitted + " holdTicks="
									+ holdTicks + " started=" + started + " taken=" + taken + " cost=" + fullCost
									+ " removed=" + scene.removed.size() + " left=" + left);
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

	/*
	 * A startup-cost policy (the add-on API) that denies the start is no stamina refusal: since R766 the bar gates no
	 * time stop, as in 1.16. The 1.16 TimeStop refused a start it would not make with ActionConditionResult.NEGATIVE,
	 * no message (checkSpecificConditions, an uncancellable Stand action), and the port's release already starts
	 * nothing in silence.
	 */
	@GameTest(template = "empty", timeoutTicks = 60, batch = GameTestBatches.TIME_STOP)
	public static void timeStopDeniedByItsStartupCostPolicyIsRefusedWithoutAStaminaMessage(GameTestHelper helper) {
		Runnable restorePolicy = TimeStopBehaviorPolicies.overrideForGameTest(JojoMod.resLoc("the_world"), DENYING_POLICY);
		Scene scene = new Scene(helper);
		try {
			scene.setUp();
			scene.player.addTag(DENIED_START_TAG);
			scene.power.setStamina(scene.power.getMaxStamina());
			helper.assertTrue(TimeStopBehaviorPolicies.resolveStartupCost(scene.power, scene.timeStop.getAbilityId(), null,
					1.0F).isDenied(), "LOW-STAMINA-TS premise: the test policy does not deny this user's start");
			ConditionCheck check = scene.timeStop.checkSpecificConditions(scene.power);
			helper.assertTrue(!check.isPositive() && check.getWarning() == null,
					"A time stop its startup-cost policy denies, with a full bar: positive=" + check.isPositive()
							+ " message=" + (check.getWarning() != null ? check.getWarning().getString() : null)
							+ "; a silent refusal expected, the bar does not gate a time stop");
			float stamina = scene.power.getStamina();
			int holdTicks = scene.holdToFire();
			helper.assertTrue(scene.instance() == null && scene.power.getStamina() == stamina,
					"A denied time stop held to fire started " + scene.instance() + " or took stamina " + stamina + " -> "
							+ scene.power.getStamina() + " after " + holdTicks + " ticks");
			restorePolicy.run();
			helper.assertTrue(!TimeStopBehaviorPolicies.resolveStartupCost(scene.power, scene.timeStop.getAbilityId(), null,
					1.0F).isDenied(), "The test's denying policy stayed bound to The World after the test");
		}
		finally {
			try {
				scene.close();
			}
			finally {
				restorePolicy.run();
			}
		}
		helper.succeed();
	}

	private static final String DENIED_START_TAG = "rotp_gametest_time_stop_start_denied";

	// in place of any add-on's The World policy for this test only; it denies only users with the tag
	private static final TimeStopBehaviorPolicy DENYING_POLICY = new TimeStopBehaviorPolicy() {
		@Override
		public TimeStopStartupCostDecision startupCost(TimeStopStartupCostContext context) {
			LivingEntity user = context.power().getUser();
			return user != null && user.getTags().contains(DENIED_START_TAG)
					? TimeStopStartupCostDecision.deny() : TimeStopStartupCostDecision.pass();
		}
	};

	private static final class Scene {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final List<TimeStopState.Instance> removed = new ArrayList<>();
		private final List<TimeStopLifecycleEvent.RemovalReason> reasons = new ArrayList<>();
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
			helper.assertTrue(level.addFreshEntity(player), "LOW-STAMINA-TS premise: could not add the user");
			StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(standType != null && StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "LOW-STAMINA-TS premise: could not grant The World");
			PowerSkillUnlocks.Result unlocked = TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
			helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED
					|| unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
					"LOW-STAMINA-TS premise: could not unlock the time stop: " + unlocked);
			// resolve level 3+ starts The World's time stop without the Stand body
			power.setResolveLevel(power.getMaxResolveLevel());
			int maxPoints = TimeStopLearning.getMaxTrainingPoints(power);
			power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, maxPoints, maxPoints, power);
			Ability ability = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
			helper.assertTrue(ability instanceof TimeStopAbility
							&& power.getResolveLevel() >= 3
							&& ResolveModeEffect.getResolveEffectLvl(player) < 0 && !power.isStaminaInfinite()
							&& TimeStopLearning.getTimeStopTicks(power) == TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS
							&& level.getEntity(player.getId()) == player,
					"LOW-STAMINA-TS premise: the user is not a plain survival The World user with a 100-tick stop: ticks="
							+ TimeStopLearning.getTimeStopTicks(power) + " infinite=" + power.isStaminaInfinite()
							+ " resolveEffect=" + ResolveModeEffect.getResolveEffectLvl(player));
			timeStop = (TimeStopAbility) ability;
			// the slot a press of the time stop takes (EntityActionAbility#performsInUsersStandPowerSlot)
			component = LivingComponentAction.getComponent(player, PowerClass.STAND);

			// keeps the stop away from every test cell
			Consumer<TimeStopLifecycleEvent.PreStart> isolate = event -> {
				if (!closed && event.getInstance().userId() == player.getId()) {
					event.setInstance(event.getInstance().withArea(FAR_AWAY, 1));
				}
			};
			Consumer<TimeStopLifecycleEvent.Removed> onRemoved = event -> {
				if (!closed && event.getInstance().userId() == player.getId()) {
					removed.add(event.getInstance());
					reasons.add(event.getReason());
					staminaAtRemoval = power.getStamina();
				}
			};
			listeners.add(isolate);
			NeoForge.EVENT_BUS.addListener(TimeStopLifecycleEvent.PreStart.class, isolate);
			listeners.add(onRemoved);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TimeStopLifecycleEvent.Removed.class, onRemoved);
		}

		/** The held charge as the action component runs it: BUTTON_CHARGE to its end, then PERFORM. */
		private int holdToFire() {
			EntityActionInstance action = timeStop.initActionOnAbilityUse(level, player, player, null);
			component.setAction(action, player, SyncType.NO_SYNC);
			helper.assertTrue(component.getAction() == action, "LOW-STAMINA-TS premise: the time stop action was rejected");
			int ticks = 0;
			while (ticks < 60 && component.getAction() == action && !action.isOver()) {
				component.tick();
				ticks++;
			}
			return ticks;
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
