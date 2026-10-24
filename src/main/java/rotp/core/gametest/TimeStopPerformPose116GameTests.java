package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TimeStopAbility;
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
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
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

	// the port's shorter stop from a charge let go early (D-core-91; 1.16 fired nothing) has no perform: its Stand takes
	// the full stop's perform pose all the same, and the clients, whose copy of the charge is cleared before the release
	// reaches them, take it from the Stand's event while that copy is still in its user's Stand-power slot
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void aTimeStopReleasedEarlyLeavesTheFullStopsPerformPoseOnItsStand(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String standId : List.of("star_platinum", "the_world")) {
			int poseTicks = POSE_TICKS_116.get(standId);
			try (Scene s = new Scene(helper, "EarlyPose" + standId, standId)) {
				EntityActionInstance stop = s.charge();
				for (int tick = 0; tick < 5; tick++) {
					s.tick();
				}
				int fullTicks = TimeStopLearning.getTimeStopTicks(s.power, TIME_STOP);
				helper.assertTrue(stop.getPhase() == ActionPhase.BUTTON_CHARGE && s.stand.getPerformPose() == null,
						"setup: " + standId + "'s time stop is not charging 5 ticks after its press, or its Stand is posed");
				AbilityInput.keyRelease(STOP_KEY, s.user);
				int started = s.stopTicks();
				helper.assertTrue(started > 0 && started < fullTicks && s.userSlot().getAction() == null,
						"setup: " + standId + "'s time stop let go 5 ticks into its charge did not start a shorter stop than its "
								+ fullTicks + " ticks (" + started + ") and clear its slot (" + s.userSlot().getAction() + ")");
				if (s.stand.getPerformPose() != stop || s.stand.getPerformPoseTicks(0) != 0) {
					wrong.add(standId + ": as its charge let go early stopped time the Stand took the pose "
							+ s.stand.getPerformPose() + " from " + s.stand.getPerformPoseTicks(0) + " ticks, where the full stop"
							+ " leaves its time stop's perform pose from that tick");
					continue;
				}
				for (int k = 1; k <= poseTicks; k++) {
					s.tick();
					if (s.stand.getPerformPose() != stop || s.stand.getPerformPoseTicks(0) != k) {
						wrong.add(standId + ": " + k + " ticks after its early release the Stand plays the pose "
								+ s.stand.getPerformPose() + " at " + s.stand.getPerformPoseTicks(0) + " ticks, the full stop's"
								+ " perform pose to the clip's end at tick " + poseTicks);
						break;
					}
				}
			}

			try (Scene s = new Scene(helper, "EarlyPoseEvent" + standId, standId)) {
				EntityActionInstance stop = s.charge();
				s.tick();
				s.stand.handleEntityEvent(StandEntity.PERFORM_POSE_EVENT);
				EntityActionInstance posed = s.stand.getPerformPose();
				float atEvent = s.stand.getPerformPoseTicks(0);
				s.tick();
				s.tick();
				s.stand.handleEntityEvent(StandEntity.PERFORM_POSE_EVENT);
				if (posed != stop || atEvent != 0 || s.stand.getPerformPose() != stop || s.stand.getPerformPoseTicks(0) != 2) {
					wrong.add(standId + ": the Stand's perform pose event, its charge in its user's Stand-power slot, gave the"
							+ " pose " + posed + " from " + atEvent + " ticks, and a second one 2 ticks later left "
							+ s.stand.getPerformPose() + " at " + s.stand.getPerformPoseTicks(0) + " ticks, where it gives the"
							+ " charge's pose from 0 ticks once and leaves a pose it has");
				}
			}
		}
		Set<String> release = StandClipFollowUps116GameTests.invokedMethods(helper,
				"rotp/core/impl/stands/theworld/TimeStopAbility$TimeStopAction.class");
		for (String call : new String[] { "rotp/core/powersystem/standpower/entity/StandEntity.setPerformPose",
				"net/minecraft/world/level/Level.broadcastEntityEvent" }) {
			if (!release.contains(call)) {
				wrong.add("TimeStopAction no longer calls " + call);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "The time stop let go early must leave the full stop's perform pose on its Stand: "
				+ String.join("; ", wrong));
		helper.succeed();
	}

	/*
	 * 1.16 posed the Stand only for a time stop that performed (TimeStop.perform). The user's own client lets go of its
	 * copy of the charge before the server decides the release (InputHandler.doReleaseInput), so it takes the pose from
	 * the server's PERFORM_POSE_EVENT, as the other clients do, with the copy it let go of. Let go early while an add-on
	 * cancels the start (TimeStopLifecycleEvent.PreStart), or with the Stand's body lost in that tick (the release's
	 * recheck), the server starts no stop: the Stand that client draws shows no pose, and an event after the wait (40
	 * ticks) does not take that copy either. Let go early otherwise, the shorter stop starts and that client's Stand takes
	 * the pose of its copy from 0 ticks as the event arrives, not before. (The core time stops ignore their Stand's stun,
	 * as 1.16's did, so a stun refuses nothing.)
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void theUsersOwnClientTakesAnEarlyReleasesPoseOnlyFromTheServer(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String standId : List.of("star_platinum", "the_world")) {
			for (String refusal : List.of("a cancelled start", "a lost body")) {
				try (Scene s = new Scene(helper, "RefusedPose" + standId, standId)) {
					EntityActionInstance stop = s.charge();
					for (int tick = 0; tick < 5; tick++) {
						s.tick();
					}
					Consumer<TimeStopLifecycleEvent.PreStart> deny = event -> {
						if (event.getInstance().userId() == s.user.getId()) {
							event.setCanceled(true);
						}
					};
					EntityActionInstance atRelease;
					if (refusal.equals("a lost body")) {
						s.power.getStandInstance().ifPresent(instance -> instance.removePart(StandPart.MAIN_BODY));
					}
					else {
						NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TimeStopLifecycleEvent.PreStart.class, deny);
					}
					try {
						UsersClientCharge copy = new UsersClientCharge(stop, s.stand, s.user);
						copy.onButtonStopHold();
						atRelease = s.stand.getPerformPose();
						AbilityInput.keyRelease(STOP_KEY, s.user);
					}
					finally {
						NeoForge.EVENT_BUS.unregister(deny);
					}
					helper.assertTrue(s.stopTicks() == 0 && s.userSlot().getAction() == null, "setup: " + standId + "'s release with "
							+ refusal + " started a stop of " + s.stopTicks() + " ticks or left " + s.userSlot().getAction());
					for (int tick = 0; tick < 3; tick++) {
						s.tick();
					}
					EntityActionInstance after = s.stand.getPerformPose();
					for (int tick = 0; tick < 38; tick++) {
						s.tick();
					}
					s.stand.handleEntityEvent(StandEntity.PERFORM_POSE_EVENT);
					if (atRelease != null || after != null || s.stand.getPerformPose() != null) {
						wrong.add(standId + ": its user's own client let go early, the server refusing the release (" + refusal
								+ "), drew the pose " + atRelease + " at the release, " + after + " 3 ticks later and "
								+ s.stand.getPerformPose() + " for an event 41 ticks later, where 1.16 posed only a time stop that"
								+ " performed");
					}
				}
			}

			try (Scene s = new Scene(helper, "ConfirmedPose" + standId, standId)) {
				EntityActionInstance stop = s.charge();
				for (int tick = 0; tick < 5; tick++) {
					s.tick();
				}
				UsersClientCharge copy = new UsersClientCharge(stop, s.stand, s.user);
				copy.onButtonStopHold();
				EntityActionInstance atRelease = s.stand.getPerformPose();
				AbilityInput.keyRelease(STOP_KEY, s.user);
				helper.assertTrue(s.stopTicks() > 0 && s.userSlot().getAction() == null, "setup: " + standId + "'s early release"
						+ " started no stop or left " + s.userSlot().getAction());
				// that client's Stand has none of the server's pose: it takes the one the server's event brings
				s.stand.endPerformPose();
				s.tick();
				s.stand.handleEntityEvent(StandEntity.PERFORM_POSE_EVENT);
				if (atRelease != null || s.stand.getPerformPose() != copy || s.stand.getPerformPoseTicks(0) != 0) {
					wrong.add(standId + ": its user's own client let go early, the server starting the shorter stop, drew the pose "
							+ atRelease + " at the release and " + s.stand.getPerformPose() + " from "
							+ s.stand.getPerformPoseTicks(0) + " ticks as the server's event came, where the other clients take the"
							+ " pose from that event");
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "The user's own client must take an early release's pose from the server: "
				+ String.join("; ", wrong));
		helper.succeed();
	}

	/*
	 * The other clients keep their copy of the charge in its slot until the server's PERFORM_POSE_EVENT or its clearing of
	 * the slot, so their Stand goes from the charge pose straight into the release pose (or back to its idle for a refused
	 * release). The user's own client lets go of its copy, and clears its slot, before the server answers: its Stand keeps
	 * the pose of the charge as it was let go of until the same answers, so no idle shows between them. Accepted: the event
	 * switches it to the perform pose. Refused (a cancelled start): the server's clearing of the user's Stand-power slot
	 * returns it to the idle, with no release pose. No answer: the wait of 40 ticks ends it. Let go of a tick before its
	 * copy's full charge, after the server's charge fired by its length (1.16's own client fired its own hold, and its
	 * Stand always went into the perform pose): the server's full-charge perform sends the event too, which a client
	 * whose copy performed has no use for and which turns the own client's kept charge into the perform pose.
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void theUsersOwnClientKeepsTheChargePoseUntilTheServerAnswersTheRelease(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String standId : List.of("star_platinum", "the_world")) {
			try (Scene s = new Scene(helper, "KeptChargeAccepted" + standId, standId)) {
				EntityActionInstance stop = s.charge();
				for (int tick = 0; tick < 5; tick++) {
					s.tick();
				}
				UsersClientCharge copy = new UsersClientCharge(stop, s.stand, s.user).chargedFor(5);
				copy.onButtonStopHold();
				StandEntity.ReleasedCharge atRelease = s.stand.getReleasedChargePose();
				AbilityInput.keyRelease(STOP_KEY, s.user);
				helper.assertTrue(s.stopTicks() > 0, "setup: " + standId + "'s early release started no stop");
				// that client's Stand has none of the server's pose
				s.stand.endPerformPose();
				s.tick();
				StandEntity.ReleasedCharge waiting = s.stand.getReleasedChargePose();
				s.stand.handleEntityEvent(StandEntity.PERFORM_POSE_EVENT);
				if (!heldCharge(atRelease, copy) || waiting != atRelease || s.stand.getPerformPose() != copy
						|| s.stand.getReleasedChargePose() != null) {
					wrong.add(standId + ", accepted: its user's own client let go of the charge (5 ticks) and drew "
							+ describe(atRelease) + " at the release, " + describe(waiting) + " a tick later, then the pose "
							+ s.stand.getPerformPose() + " and the kept charge " + describe(s.stand.getReleasedChargePose())
							+ " after the server's event, where the other clients went from the charge straight to the release pose");
				}
			}
			try (Scene s = new Scene(helper, "KeptChargeRefused" + standId, standId)) {
				EntityActionInstance stop = s.charge();
				for (int tick = 0; tick < 5; tick++) {
					s.tick();
				}
				Consumer<TimeStopLifecycleEvent.PreStart> deny = event -> {
					if (event.getInstance().userId() == s.user.getId()) {
						event.setCanceled(true);
					}
				};
				NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TimeStopLifecycleEvent.PreStart.class, deny);
				UsersClientCharge copy = new UsersClientCharge(stop, s.stand, s.user).chargedFor(5);
				try {
					copy.onButtonStopHold();
					AbilityInput.keyRelease(STOP_KEY, s.user);
				}
				finally {
					NeoForge.EVENT_BUS.unregister(deny);
				}
				helper.assertTrue(s.stopTicks() == 0, "setup: " + standId + "'s cancelled release started a stop");
				for (int tick = 0; tick < 3; tick++) {
					s.tick();
				}
				StandEntity.ReleasedCharge waiting = s.stand.getReleasedChargePose();
				// the server's clearing of the user's Stand-power slot, as that client applies it (setActionFromNetwork)
				StandEntity.onUsersStandPowerSlotCleared(s.user);
				StandEntity.ReleasedCharge answered = s.stand.getReleasedChargePose();
				s.stand.handleEntityEvent(StandEntity.PERFORM_POSE_EVENT);
				if (!heldCharge(waiting, copy) || answered != null || s.stand.getPerformPose() != null) {
					wrong.add(standId + ", refused: its user's own client drew " + describe(waiting) + " 3 ticks after the release,"
							+ " " + describe(answered) + " once the server cleared the slot and the pose " + s.stand.getPerformPose()
							+ " for a later event, where the other clients kept the charge until the server's answer, then the idle");
				}
			}
			try (Scene s = new Scene(helper, "KeptChargeFullCharge" + standId, standId)) {
				PoseEventWatcher watcher = new PoseEventWatcher(helper.getLevel(), s.stand);
				watcher.track(helper);
				try {
					EntityActionInstance stop = s.charge();
					int chargeTicks = (int) stop.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE);
					int performTick = s.tickUntilPerform(stop);
					helper.assertTrue(performTick > 0 && s.stand.getPerformPose() == stop, "setup: " + standId + "'s time stop"
							+ " held to its full charge did not perform (tick " + performTick + ") or left the pose "
							+ s.stand.getPerformPose());
					int sent = watcher.poseEvents;
					// a client whose copy performed with the server's: the event leaves the pose it has
					for (int event = 0; event < sent; event++) {
						s.stand.handleEntityEvent(StandEntity.PERFORM_POSE_EVENT);
					}
					boolean leftTheCopysPose = s.stand.getPerformPose() == stop && s.stand.getPerformPoseTicks(0) == 0;
					// the user's own client, a tick short of its copy's full charge, lets go of it; the release reaches the
					// server after its charge fired there and changes nothing
					UsersClientCharge copy = new UsersClientCharge(stop, s.stand, s.user).chargedFor(chargeTicks - 1);
					copy.onButtonStopHold();
					AbilityInput.keyRelease(STOP_KEY, s.user);
					// that client's Stand has none of the server's pose
					s.stand.endPerformPose();
					s.tick();
					StandEntity.ReleasedCharge waiting = s.stand.getReleasedChargePose();
					for (int event = 0; event < sent; event++) {
						s.stand.handleEntityEvent(StandEntity.PERFORM_POSE_EVENT);
					}
					if (sent != 1 || watcher.poseEvents != 1 || !leftTheCopysPose || !heldCharge(waiting, copy)
							|| s.stand.getPerformPose() != copy || s.stand.getPerformPoseTicks(0) != 0
							|| s.stand.getReleasedChargePose() != null) {
						wrong.add(standId + ", full charge: the server's charge fired by its length (tick " + performTick + ") sent "
								+ sent + " perform pose events (" + watcher.poseEvents + " by the end), a client whose copy performed"
								+ (leftTheCopysPose ? " kept" : " lost") + " its pose, and its user's own client, letting go of its"
								+ " copy a tick short of the full charge, drew " + describe(waiting) + " a tick later, then the pose "
								+ s.stand.getPerformPose() + " from " + s.stand.getPerformPoseTicks(0) + " ticks and "
								+ describe(s.stand.getReleasedChargePose()) + " after the events, where 1.16's own client went"
								+ " from the charge into the perform pose (one event, the perform pose from 0 ticks, no kept charge)");
					}
				}
				finally {
					watcher.untrack();
				}
			}
			try (Scene s = new Scene(helper, "KeptChargeUnanswered" + standId, standId)) {
				EntityActionInstance stop = s.charge();
				UsersClientCharge copy = new UsersClientCharge(stop, s.stand, s.user).chargedFor(2);
				// the server never answers: its charge is gone without a word
				s.userSlot().setAction(null, SyncType.NO_SYNC);
				copy.onButtonStopHold();
				for (int tick = 0; tick < 40; tick++) {
					s.tick();
				}
				StandEntity.ReleasedCharge lastWaited = s.stand.getReleasedChargePose();
				s.tick();
				if (!heldCharge(lastWaited, copy) || s.stand.getReleasedChargePose() != null) {
					wrong.add(standId + ", unanswered: its user's own client drew " + describe(lastWaited) + " 40 ticks after the"
							+ " release and " + describe(s.stand.getReleasedChargePose()) + " 41 ticks after, where the wait is 40 ticks");
				}
			}
		}
		Set<String> sync = StandClipFollowUps116GameTests.invokedMethods(helper,
				"rotp/core/powersystem/entityaction/LivingComponentAction.class");
		if (!sync.contains("rotp/core/powersystem/standpower/entity/StandEntity.onUsersStandPowerSlotCleared")) {
			wrong.add("LivingComponentAction (setActionFromNetwork) no longer tells the Stand that the server cleared its user's"
					+ " Stand-power slot");
		}
		Set<String> draw = StandClipFollowUps116GameTests.invokedMethods(helper,
				"rotp/core/client/entityanim/PreFrameEntityAnimCalc.class");
		if (!draw.contains("rotp/core/powersystem/standpower/entity/StandEntity.getReleasedChargePose")) {
			wrong.add("PreFrameEntityAnimCalc no longer draws the kept charge");
		}
		helper.assertTrue(wrong.isEmpty(), "The user's own client must keep the charge pose until the server answers an early"
				+ " release: " + String.join("; ", wrong));
		helper.succeed();
	}

	// the charge the copy was let go at: its phase and its 5 (or 2) ticks, the pose's frame
	private static boolean heldCharge(StandEntity.ReleasedCharge charge, UsersClientCharge copy) {
		return charge != null && charge.action() == copy && charge.phase() == ActionPhase.BUTTON_CHARGE
				&& charge.phaseTicks() == copy.chargedTicks && charge.phaseCompletion() > 0.0F
				&& charge.phaseCompletion() < 1.0F;
	}

	private static String describe(StandEntity.ReleasedCharge charge) {
		return charge == null ? "no kept charge (the idle)" : "the kept charge " + charge.phase() + " at " + charge.phaseTicks()
				+ " ticks (" + charge.phaseCompletion() + ")";
	}

	// the user's own client's copy of the charge (EntityActionInstance.takesTargetsFromServer), on the Stand it draws
	private static final class UsersClientCharge extends TimeStopAbility.TimeStopAction {
		UsersClientCharge(EntityActionInstance charge, StandEntity stand, LivingEntity user) {
			super(charge.ability);
			performer = stand;
			powerUser.setEntity(user);
			phase = ActionPhase.BUTTON_CHARGE;
			chargeLength = charge.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE);
		}

		private final float chargeLength;
		int chargedTicks;

		// held that long of the charge's length, as that client's copy ticked
		UsersClientCharge chargedFor(int ticks) {
			chargedTicks = ticks;
			curPhaseTick = ticks;
			curPhaseLength = chargeLength;
			return this;
		}

		@Override
		protected boolean takesTargetsFromServer() {
			return true;
		}
	}

	// a client tracking the Stand: the PERFORM_POSE_EVENTs the server sends it for that Stand
	private static final class PoseEventWatcher extends FakePlayer {
		private final StandEntity stand;
		int poseEvents;
		@Nullable private ChunkMap.TrackedEntity tracked;

		PoseEventWatcher(ServerLevel level, StandEntity stand) {
			super(level, new GameProfile(UUID.randomUUID(), "pose-event-watcher"));
			this.stand = stand;
			this.connection = new PoseEventConnection(level, this);
		}

		void track(GameTestHelper helper) {
			tracked = helper.getLevel().getChunkSource().chunkMap.entityMap.get(stand.getId());
			helper.assertTrue(tracked != null, "setup: " + stand + " is not tracked");
			tracked.seenBy.add(connection);
		}

		void untrack() {
			if (tracked != null) {
				tracked.seenBy.remove(connection);
				tracked = null;
			}
		}
	}

	private static final class PoseEventConnection extends ServerGamePacketListenerImpl {
		@Nullable private final PoseEventWatcher watcher;

		PoseEventConnection(ServerLevel level, PoseEventWatcher watcher) {
			super(level.getServer(), openConnection(), watcher,
					CommonListenerCookie.createInitial(watcher.getGameProfile(), false));
			this.watcher = watcher;
		}

		// as GameTestHelper.makeMockServerPlayerInLevel: a live channel, so channel lookups do not fail
		private static Connection openConnection() {
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			return connection;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
			if (watcher == null) return;
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(sub -> send(sub, null));
			}
			else if (packet instanceof ClientboundEntityEventPacket event && event.getEventId() == StandEntity.PERFORM_POSE_EVENT
					&& event.getEntity(watcher.stand.level()) == watcher.stand) {
				watcher.poseEvents++;
			}
		}
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

		// the length of the user's time stop, 0 for none
		int stopTicks() {
			return helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get()).getInstance(user.getId())
					.map(instance -> instance.totalTicks()).orElse(0);
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
