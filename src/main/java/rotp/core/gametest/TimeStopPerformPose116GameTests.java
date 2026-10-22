package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 TimeStop.perform set TimeStop.ANIM on its client Stand (setStandPose), and StandEntity.getCurPose drew it before
 * anything else: the time stop clip's PERFORM section (phase.constantLength, StandActionAnimation PRESERVE_PHASE_LENGTH)
 * at the Stand's own pace from that tick, until the clip ended (onSetPoseAnimEnded) or the Stand's task changed. The
 * port's time stop performs for one tick (RECOVERY 0), so its Stand keeps the pose (StandEntity#getPerformPose) after the
 * action is over, and the client draws it until the clip's end (PreFrameEntityAnimCalc).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopPerformPose116GameTests {
	private TimeStopPerformPose116GameTests() {}

	private static final short STOP_KEY = 141;
	private static final short PUNCH_KEY = 142;
	private static final String TIME_STOP = "time_stop";
	private static final String CLIP = "time_stop";
	// 1.16 star_platinum / the_world.animation.json timeStop: PERFORM at 2.0 of 2.5 s and at 1.0 of 2.2 s, constantLength
	private static final Map<String, Integer> POSE_TICKS_116 = Map.of("star_platinum", 10, "the_world", 24);

	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void theTimeStopLeavesItsPerformPoseOnItsStandUntilItsClipEndsAsIn116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		for (String standId : List.of("star_platinum", "the_world")) {
			JsonObject clip = files.computeIfAbsent(standId, id -> StandClipPoses116GameTests.readJson(helper,
					"stand_skins/" + id + "/assets/jojo_ripples/animations/" + id + ".animation.json"))
					.getAsJsonObject("animations").getAsJsonObject(CLIP);
			helper.assertTrue(clip != null, standId + " lost its time stop clip");
			float performAt = StandClipPoses116GameTests.marker(clip, "phase=PERFORM;");
			float length = clip.get("animation_length").getAsFloat();
			int poseTicks = Math.round((length - performAt) * 20);
			if (StandClipPoses116GameTests.marker(clip, "phase.constantLength=true;") != performAt
					|| poseTicks != POSE_TICKS_116.get(standId)) {
				wrong.add(standId + "'s clip plays its PERFORM section from " + performAt + " to " + length + " ("
						+ poseTicks + " ticks, constant length "
						+ (StandClipPoses116GameTests.marker(clip, "phase.constantLength=true;") == performAt) + "), 1.16's "
						+ POSE_TICKS_116.get(standId) + " ticks at its own pace");
			}

			try (Scene s = new Scene(helper, "PerformPose" + standId, standId)) {
				EntityActionInstance stop = s.charge();
				int performTick = s.tickUntilPerform(stop);
				if (performTick < 0 || s.stand.getPerformPose() != stop || s.stand.getPerformPoseTicks(0) != 0) {
					wrong.add(standId + ": as its time stop performed (tick " + performTick + ") the Stand took the pose "
							+ s.stand.getPerformPose() + " from " + s.stand.getPerformPoseTicks(0) + " ticks, 1.16 its time"
							+ " stop's perform pose from that tick");
					continue;
				}
				if (!stop.isOver() || s.userSlot().getAction() != null
						|| LivingComponentAction.getPoseComponent(s.stand) == s.userSlot()) {
					wrong.add(standId + ": the time stop is not over with its one-tick perform (phase " + stop.getPhase()
							+ ", the user's Stand-power action " + s.userSlot().getAction() + ")");
				}
				for (int k = 1; k <= poseTicks; k++) {
					s.tick();
					float clipTime = performAt + s.stand.getPerformPoseTicks(0) / 20;
					if (s.stand.getPerformPose() != stop || s.stand.getPerformPoseTicks(0) != k
							|| (clipTime < length - 1.0E-4F) != (k < poseTicks)) {
						wrong.add(standId + ": " + k + " ticks after its time stop performed the Stand plays the pose "
								+ s.stand.getPerformPose() + " at " + s.stand.getPerformPoseTicks(0) + " ticks (clip time "
								+ clipTime + " of " + length + "), 1.16 its perform pose to the clip's end at tick " + poseTicks);
						break;
					}
				}
			}

			try (Scene s = new Scene(helper, "PerformPosePunch" + standId, standId)) {
				EntityActionInstance stop = s.charge();
				s.tickUntilPerform(stop);
				s.tick();
				s.tick();
				EntityActionInstance before = s.stand.getPerformPose();
				BufferingState punch = s.press("punch", InputMethod.CLICK, PUNCH_KEY);
				EntityActionInstance task = s.standSlot().getAction();
				helper.assertTrue(punch.isActionSuccess && task != null && task.ability == s.power.getAbility("punch"),
						"setup: " + standId + "'s punch pressed after its time stop did not start: " + task);
				if (before != stop || s.stand.getPerformPose() != null) {
					wrong.add(standId + ": the Stand's punch started 2 ticks into the perform pose left it "
							+ s.stand.getPerformPose() + " (before the punch " + before + "), 1.16 replaced the pose with the"
							+ " new task's");
				}
			}
		}
		// the client draws the pose before the Stand's own action, at its ticks, and ends it at the clip's end
		Set<String> invoked = StandClipFollowUps116GameTests.invokedMethods(helper,
				"rotp/core/client/entityanim/PreFrameEntityAnimCalc.class");
		for (String call : new String[] { "rotp/core/powersystem/standpower/entity/StandEntity.getPerformPose",
				"rotp/core/powersystem/standpower/entity/StandEntity.getPerformPoseTicks",
				"rotp/core/powersystem/standpower/entity/StandEntity.endPerformPose" }) {
			if (!invoked.contains(call)) {
				wrong.add("PreFrameEntityAnimCalc no longer calls " + call);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 drew its time stop's perform pose on the Stand from the perform to the"
				+ " clip's end, unless a new task replaced it: " + String.join("; ", wrong));
		helper.succeed();
	}

	/** A player with Star Platinum or The World out at Resolve 4, its time stop unlocked and moved far from every cell. */
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final FakePlayer user;
		private final StandPower power;
		private final StandType type;
		private final StandEntity stand;
		private final Consumer<TimeStopLifecycleEvent.PreStart> isolate;

		private Scene(GameTestHelper helper, String name, String standId) {
			this.helper = helper;
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			user.getAbilities().instabuild = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user");
			ChunkPos farAway = new ChunkPos(50900 + user.getId() * 2, 50900);
			isolate = event -> {
				if (event.getInstance().userId() == user.getId()) {
					event.setInstance(event.getInstance().withArea(farAway, 1));
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TimeStopLifecycleEvent.PreStart.class, isolate);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(type != null, "Missing Stand type " + standId);
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
			power.setResolveLevel(4);
			power.getCurTypeData()._lockedAbilities.remove(TIME_STOP);
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(type.summon(user, power), "Could not summon " + standId);
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && power.getAbility(TIME_STOP).isAbilityUnlocked(power),
					"setup: " + standId + " is not out or its time stop is locked");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
		}

		LivingComponentAction standSlot() {
			return LivingComponentAction.getComponent(stand);
		}

		LivingComponentAction userSlot() {
			return LivingComponentAction.getComponent(user, PowerClass.STAND);
		}

		// one game tick: the Stand's own tick count, then the user's Stand-power slot and the Stand's slot
		void tick() {
			stand.tickCount++;
			userSlot().tick();
			standSlot().tick();
		}

		EntityActionInstance charge() {
			BufferingState pressed = press(TIME_STOP, InputMethod.HOLD, STOP_KEY);
			EntityActionInstance stop = userSlot().getAction();
			helper.assertTrue(pressed.isActionSuccess && stop != null && stop.getPhase() == ActionPhase.BUTTON_CHARGE
					&& stop.getPerformer() == stand, "setup: the time stop is not charging in its user's Stand-power slot,"
							+ " performed by its Stand: " + stop + (stop != null ? " in " + stop.getPhase() : ""));
			return stop;
		}

		// the tick the time stop performs in, stopping time (its one-tick perform and empty recovery end in that tick)
		int tickUntilPerform(EntityActionInstance stop) {
			for (int tick = 1; tick <= 60; tick++) {
				tick();
				if (helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get()).getInstance(user.getId()).isPresent()) {
					return tick;
				}
				if (stop.isOver()) {
					return -1;
				}
			}
			return -1;
		}

		// as ClAbilityInputPacket handles a press: the key's resolved ability, its condition check, then the press
		BufferingState press(String name, InputMethod inputMethod, short key) {
			power.setStamina(power.getMaxStamina());
			BufferingState state = BufferingState.clickCanBuffer();
			Ability base = power.getAbility(name);
			helper.assertTrue(base != null, "Missing ability " + name);
			AbilityConditionCheck container = resolve(power, base);
			if (container != null && AbilityInput.withConditionCheck(container, user, inputMethod)) {
				AbilityInput.keyPress(key, container.ability, user, null, inputMethod, 0, state, base.getAbilityId());
			}
			return state;
		}

		@Override
		public void close() {
			NeoForge.EVENT_BUS.unregister(isolate);
			AbilityInput.keyRelease(STOP_KEY, user);
			AbilityInput.keyRelease(PUNCH_KEY, user);
			userSlot().setAction(null, SyncType.NO_SYNC);
			standSlot().setAction(null, SyncType.NO_SYNC);
			helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(user.getId());
			user.removeAllEffects();
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
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
}
