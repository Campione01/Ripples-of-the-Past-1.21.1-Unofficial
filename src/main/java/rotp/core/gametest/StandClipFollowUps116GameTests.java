package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.client.entityanim.AnimationSet;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.crazydiamond.CrazyDHealAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Core clips the official 1.21.1 port lacks, against the parts of 1.16's poses, phases and easing the earlier clip
 * checks left out (StandClipPoses116GameTests, SilverChariotHierophantClips116GameTests, CoreClipPhases116GameTests).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandClipFollowUps116GameTests {
	private StandClipFollowUps116GameTests() {}

	private static final String SC_ANIMS =
			"stand_skins/silver_chariot/assets/jojo_ripples/animations/silver_chariot.animation.json";
	private static final float[][] LOOKS = { { 0, 0 }, { 20, -35 } };

	// SilverChariotModel :252-270, barrageRightStart and barrageRightImpact on the port's bones (body -> body_rot,
	// upperPart -> torso_bend); the elbow joints at half the forearms (HumanoidStandModel.rotateJoint)
	private static final String[] STAB_BONES = { "right_arm", "right_arm_bend", "right_arm_joint", "body_rot", "torso_bend",
			"left_arm", "left_arm_bend", "left_arm_joint", "rapier" };
	private static final float[][] STAB_START = { { 89, 0, 90 }, { -135, 0, 0 }, { -67.5F, 0, 0 }, { 0, 0, 0 }, { 0, -45, 0 },
			{ 45, 0, -60 }, { 0, 0, 0 }, { 0, 0, 0 }, { 90, 0, 0 } };
	private static final float[][] STAB_IMPACT = { { -90, 45, 0 }, { 0, 0, 0 }, { 0, 0, 0 }, { 0, 0, 0 }, { 0, -45, 0 },
			{ 45, 0, -60 }, { 0, 0, 0 }, { 0, 0, 0 }, { 90, 0, 0 } };

	// 1.16's stabLoop (SilverChariotModel :356-359): barrageStabStart -> barrageStabImpact by halfSwing^3
	private static float[] stab116(int bone, float sw) {
		float halfSwing = sw < 0.4F ? sw * 20 / 8 : sw > 0.6F ? (1 - sw) * 20 / 8 : 1F;
		float f = halfSwing * halfSwing * halfSwing;
		float[] a = STAB_START[bone], b = STAB_IMPACT[bone];
		return new float[] { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f };
	}

	/*
	 * 1.16's rapier barrage (SilverChariotModel :356-365, StandOneHandedBarrageAnimation with a 2-tick loop) posed the
	 * stab loop at frac(ticks / 2) through the whole PERFORM, and an afterimage arm at its own swing's completion (ticks /
	 * 2 from its random start until 0.75, ArmBarrageSwing): a stab every 2 ticks. The port loops the clip's PERFORM
	 * section (loopBack to RECOVERY) by the perform's ticks, and OneHandedBarrageLoopSwing shows an afterimage at the
	 * clip time of its swing's ticks plus the main clip time less its remainder of 2, on that loop: the section must be
	 * one 2-tick stab, every bone on 1.16's stab loop at each point of it.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void silverChariotsBarrageStabsEveryTwoTicksThroughItsPerformAs116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		JsonObject clip = clipOf(helper, new HashMap<>(), SC_ANIMS, "melee_barrage");
		float perform = StandClipPoses116GameTests.marker(clip, "phase=PERFORM;");
		float recovery = StandClipPoses116GameTests.marker(clip, "phase=RECOVERY;");
		float loopBack = loopBackOf(clip, perform);
		float loopLen = recovery - loopBack;
		if (loopBack != perform || Math.abs(loopLen * 20 - 2) > 0.001F) {
			wrong.add("the perform loops from " + loopBack + " to " + recovery + " (" + loopLen * 20 + " ticks), where 1.16 stabbed every 2 ticks");
		}
		for (int step = 0; step < 100; step++) {
			float sw = step / 100F;
			float time = loopBack + sw * loopLen;
			for (int b = 0; b < STAB_BONES.length; b++) {
				compare(wrong, clip, STAB_BONES[b], time, stab116(b, sw), "the stab loop at " + sw);
			}
			for (String arm : new String[] { "left_arm_xrot", "right_arm_xrot" }) {
				compareAt(wrong, clip, arm, time, LOOKS[1], new float[] { LOOKS[1][0], 0, 0 }, "the look on the arms at " + sw);
			}
		}
		// OneHandedBarrageLoopSwing.addMainHandSwings and TwoHandedBarrageLoopSwing.poseAndRender: phase time = the swing's
		// ticks + (main clip time - main clip time % 2), shown on the PERFORM's LOOP_BACK clock (RotpAnimDefinition.getAnimTime)
		for (float main : new float[] { perform, (perform + recovery) / 2, Math.nextDown(recovery) }) {
			float addTime = main - main % 2;
			for (int step = 0; step < 75; step++) {
				float sw = step / 100F;
				float phaseSecs = (sw * 2 + addTime) / 20F;
				float time = perform + phaseSecs % loopLen;
				for (int b = 0; b < 3; b++) {
					compare(wrong, clip, STAB_BONES[b], time, stab116(b, sw), "an afterimage arm " + sw + " through its swing"
							+ " (main clip time " + main + ")");
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's Silver Chariot stabbed every 2 ticks through its barrage, its afterimages on the"
				+ " same stab: " + wrong.subList(0, Math.min(12, wrong.size())) + (wrong.size() > 12 ? " and " + (wrong.size() - 12)
				+ " more" : ""));
		helper.succeed();
	}

	// SilverChariotModel :336-345, RANGED_ATTACK: body, upperPart, leftArm, rightArm and the rapier on the port's bones
	private static final Map<String, float[]> LAUNCH_POSE = Map.of("body_rot", new float[] { 0, -45, 0 },
			"torso_bend", new float[] { 0, -45, 0 }, "left_arm", new float[] { 15, 0, -7.5F }, "right_arm", new float[] { 0, 90, 90 },
			"rapier", new float[] { 90, 0, 0 });

	/*
	 * SilverChariotModel's RANGED_ATTACK (:336-345), the pose the port draws for the rapier launch (1.16's lookup never
	 * reached it): a PosedActionAnimation with a PERFORM pose only, so the WINDUP is idlePose (the rigid filler), the
	 * PERFORM the pose lerped in from the idle by the perform's completion with rightArm.zRot -= xRot in full, and the
	 * RECOVERY ModelPoseTransition(the pose, idlePose); the bones it does not name (the forearms, the joints, the legs, the
	 * head on the look) stay on the idle, the official idle's in the port.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void silverChariotsRapierLaunchTakesItsModelsRangedAttackPoseInThePerformAs116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject idle = clipOf(helper, files, SC_ANIMS, "idle");
		for (String name : new String[] { "rapier_launch", "rangedAttack" }) {
			JsonObject clip = clipOf(helper, files, SC_ANIMS, name);
			float perform = StandClipPoses116GameTests.marker(clip, "phase=PERFORM;");
			float recovery = StandClipPoses116GameTests.marker(clip, "phase=RECOVERY;");
			float end = clip.get("animation_length").getAsFloat();
			List<Float> times = new ArrayList<>();
			for (float time = 0; time < end; time += 0.005F) {
				times.add(time);
			}
			times.addAll(List.of(Math.nextDown(perform), perform, recovery, end));
			for (String bone : idle.getAsJsonObject("bones").keySet()) {
				if (bone.equals("head") || bone.equals("head_rot")) {
					continue;
				}
				for (float[] look : LOOKS) {
					String first = null;
					for (float time : times) {
						float[] idleValue = StandClipPoses116GameTests.rotationAt(idle, bone, 0, look);
						float[] pose = LAUNCH_POSE.getOrDefault(bone, idleValue);
						float tilt = bone.equals("right_arm") ? look[0] : 0;
						float[] want;
						if (time < perform) {
							want = idleValue;
						}
						else if (time <= recovery) {
							float f = (time - perform) / (recovery - perform);
							want = lerp(idleValue, pose, f);
							want[2] -= tilt;
						}
						else {
							float f = (time - recovery) / (end - recovery);
							want = lerp(new float[] { pose[0], pose[1], pose[2] - tilt }, idleValue, f);
						}
						float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, time, look);
						if (!near(got, want, 0.05F)) {
							first = "at " + time + " looking " + Arrays.toString(look) + " " + Arrays.toString(got) + " where 1.16 had "
									+ Arrays.toString(want);
							break;
						}
					}
					if (first != null) {
						wrong.add(name + " " + bone + " " + first);
						break;
					}
				}
			}
			for (float[] look : LOOKS) {
				double[][] lookHead = StandClipPoses116GameTests.rotationMatrix(new float[] { look[0], look[1], 0 });
				double off = StandClipPoses116GameTests.angleBetween(composedHead(clip, recovery, look), lookHead);
				if (off > 0.5) {
					wrong.add(name + " head at RECOVERY looking " + Arrays.toString(look) + " " + String.format("%.1f", off)
							+ " degrees off the look, where 1.16's pose left the head on it");
				}
				for (float time : new float[] { 0, Math.nextDown(perform), perform, end }) {
					off = StandClipPoses116GameTests.angleBetween(composedHead(clip, time, look), composedHead(idle, 0, look));
					if (off > 0.5) {
						wrong.add(name + " head at " + time + " looking " + Arrays.toString(look) + " " + String.format("%.1f", off)
								+ " degrees off the idle's");
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's RANGED_ATTACK pose of Silver Chariot came in over the perform from the idle,"
				+ " tilted the rapier arm by the look and went back to the idle over the recovery: " + wrong);
		helper.succeed();
	}

	private static final String MR_ANIMS =
			"stand_skins/magicians_red/assets/jojo_ripples/animations/magicians_red.animation.json";
	private static final String[] FLAME_CLIPS = { "flame_burst", "flameBurst", "fireball", "crossfire_hurricane" };

	/*
	 * 1.16's FLAME_BURST_POSE (MagiciansRedModel :269-285) moved both arms 2 pixels inward (leftArm.setPos(4, -10, 0),
	 * rightArm.setPos(-4, -10, 0)) in every phase and under the recovery's transition, setupAnim putting them back at
	 * +-6 once the idle was drawn again; and its first-person view (setupFirstPersonRotations) tilted by head.xRot - xRot:
	 * -22.5 degrees to RECOVERY and -22.5 (1 - t) over it, the head's x lerped to the look. The port's view reads head_rot's
	 * x (StandEntityRenderer.setupFirstPersonRotations), which the clips key with 1.16's head x.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void magiciansRedsFlamePosesMoveTheArmsInAndTiltTheFirstPersonViewAs116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject idle = clipOf(helper, files, MR_ANIMS, "idle");
		float[][] looks = { { 0, 0 }, { 20, -35 }, { -30, 40 } };
		for (String arm : new String[] { "left_arm", "right_arm" }) {
			if (!near(positionAt(idle, arm, 0), new float[3], 0.0001F)) {
				wrong.add("the idle moves " + arm + " by " + Arrays.toString(positionAt(idle, arm, 0)));
			}
		}
		for (String name : FLAME_CLIPS) {
			JsonObject clip = clipOf(helper, files, MR_ANIMS, name);
			float recovery = StandClipPoses116GameTests.marker(clip, "phase=RECOVERY;");
			float end = clip.get("animation_length").getAsFloat();
			String first = null;
			for (float time = 0; time <= end + 0.00001F && first == null; time += 0.005F) {
				float t = Math.min(time, end);
				float[] left = positionAt(clip, "left_arm", t), right = positionAt(clip, "right_arm", t);
				if (!near(left, new float[] { -2, 0, 0 }, 0.0001F) || !near(right, new float[] { 2, 0, 0 }, 0.0001F)) {
					first = "moves the arms by " + Arrays.toString(left) + " and " + Arrays.toString(right) + " at " + t
							+ ", where 1.16 had them 2 pixels in (left -2, right +2 on x) for the whole action";
				}
				for (float[] look : looks) {
					float f = t <= recovery ? 0 : (t - recovery) / (end - recovery);
					float tilt = StandClipPoses116GameTests.rotationAt(clip, "head_rot", t, look)[0] - look[0];
					if (first == null && Math.abs(tilt + 22.5F * (1 - f)) > 0.05F) {
						first = "tilts the first-person view by " + tilt + " at " + t + " looking " + Arrays.toString(look)
								+ ", where 1.16 tilted it by " + -22.5F * (1 - f);
					}
				}
			}
			if (first != null) {
				wrong.add(name + " " + first);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's flame pose moved the arms in and tilted the first-person view with its head: " + wrong);
		helper.succeed();
	}

	// a bone's position channel at a clip time: a constant vector or linear keys; none is 0
	private static float[] positionAt(JsonObject clip, String boneName, float time) {
		JsonObject bone = clip.getAsJsonObject("bones").getAsJsonObject(boneName);
		if (bone == null || !bone.has("position")) {
			return new float[3];
		}
		JsonElement channel = bone.get("position");
		if (channel.isJsonArray() || channel.getAsJsonObject().has("vector")) {
			return vector(channel);
		}
		List<Map.Entry<Float, JsonElement>> keys = new ArrayList<>();
		channel.getAsJsonObject().entrySet().forEach(key -> keys.add(Map.entry(Float.parseFloat(key.getKey()), key.getValue())));
		keys.sort(Map.Entry.comparingByKey());
		if (time <= keys.get(0).getKey()) {
			return vector(keys.get(0).getValue());
		}
		for (int i = 1; i < keys.size(); i++) {
			if (time <= keys.get(i).getKey()) {
				float f = (time - keys.get(i - 1).getKey()) / (keys.get(i).getKey() - keys.get(i - 1).getKey());
				return lerp(vector(keys.get(i - 1).getValue()), vector(keys.get(i).getValue()), f);
			}
		}
		return vector(keys.get(keys.size() - 1).getValue());
	}

	private static float[] vector(JsonElement key) {
		JsonElement values = key.isJsonObject() ? key.getAsJsonObject().get("vector") : key;
		return new float[] { values.getAsJsonArray().get(0).getAsFloat(), values.getAsJsonArray().get(1).getAsFloat(),
				values.getAsJsonArray().get(2).getAsFloat() };
	}

	/*
	 * 1.16's CrazyDiamondHeal had no standPose of its own (StandAction.Builder's IDLE): the Stand drew its idle while it
	 * healed, and the barrage's pose while the heal showed barrage visuals (StandEntityAction.getStandPose); the
	 * previous-state revert drew ITEM_FIX_POSE (the official repair_item) and the barrage's pose likewise.
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void crazyDiamondsHealShowsTheIdleWhileItHealsWithoutBarrageVisualsAs116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CDHealIdleClip"));
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
		StandPower power = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
			user.moveTo(pos.x, pos.y, pos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the Stand user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
					"setup: could not grant Crazy Diamond");
			power.setResolveLevel(power.getMaxResolveLevel());
			helper.assertTrue(type.summon(user, power), "setup: could not summon Crazy Diamond");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "setup: the summoned Stand is missing");
			// the ability, then the clip it asks for: not healing, healing without and with barrage visuals
			String[][] rows = { { "heal", "idle", "idle", "barrage" }, { "revert_state", "repair_item", "repair_item", "barrage" } };
			for (String[] row : rows) {
				Ability ability = power.getAbility(row[0]);
				helper.assertTrue(ability instanceof EntityActionAbility, "setup: Crazy Diamond has no " + row[0]);
				EntityActionInstance action = ((EntityActionAbility) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
				LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
				helper.assertTrue(action instanceof CrazyDHealAbility.HealingAction, "setup: " + row[0] + " is no healing action");
				String[] got = new String[3];
				got[0] = action.getEntityAnim().name();
				for (int visuals = 0; visuals < 2; visuals++) {
					action.synchedData.set(CrazyDHealAbility.HealingAction.HEAL_RESULT, new CrazyDHealAbility.HealingAction.HealResult.Synched(
							ActionTarget.EMPTY, true, visuals == 1, CrazyDHealAbility.HealingAction.HealResult.Synched.NO_DEATH_TIME_CHANGE));
					got[visuals + 1] = action.getEntityAnim().name();
				}
				if (!Arrays.equals(got, Arrays.copyOfRange(row, 1, 4))) {
					wrong.add(row[0] + " plays " + Arrays.toString(got) + " (not healing, healing, healing with barrage visuals) where 1.16"
							+ " drew " + Arrays.toString(Arrays.copyOfRange(row, 1, 4)));
				}
			}
		}
		finally {
			if (power != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's Crazy Diamond healed in its idle pose, the barrage's with barrage visuals: " + wrong);
		helper.succeed();
	}

	private static final String SP_ANIMS =
			"stand_skins/star_platinum/assets/jojo_ripples/animations/star_platinum.animation.json";
	// 1.16 star_platinum.animation.json at the end of each clip's first segment (uppercut 0.4 s, timeStop 0.25 s): its body
	// (pivoting at the neck: the port's body_rot), the port's waist body and torso_bend (0), the legs and fabrics
	private static final String[] SP_STANCE = { "body_rot", "body", "torso_bend", "left_leg", "right_leg", "left_leg_bend",
			"right_leg_bend", "front_fabric", "back_fabric" };
	private static final float[][] UPPERCUT_AT_04 = { { -4.73827F, -22.58344F, -0.01776F }, { 0, 0, 0 }, { 0, 0, 0 },
			{ 0.74992F, -16.88606F, -12.30217F }, { 10.84175F, 24.21385F, 17.86993F }, { 17, 0, 0 }, { 21.32615F, 0, 0 }, { -12, 0, 0 },
			{ 7.5F, 0, 0 } };
	private static final float[][] TIME_STOP_AT_025 = { { -9.85981F, -0.35098F, -4.06736F }, { 0, 0, 0 }, { 0, 0, 0 },
			{ 0.74992F, -16.88606F, -12.30217F }, { 10.84175F, 24.21385F, 17.86993F }, { 52.5F, 0, 0 }, { 95, 0, 0 },
			{ -44.91184F, -13.90719F, -10.10857F }, { 7.5F, 0, 0 } };
	// the clips, the end of their first segment and 1.16's stance there
	private static final Object[][] SP_CLIPS = { { "finisher_uppercut", 0.4F, UPPERCUT_AT_04 }, { "uppercut", 0.4F, UPPERCUT_AT_04 },
			{ "time_stop", 0.25F, TIME_STOP_AT_025 }, { "timeStop", 0.25F, TIME_STOP_AT_025 } };

	/*
	 * 1.16's GeckoStandAnimator drew Star Platinum's uppercut and time stop from star_platinum.animation.json, each
	 * starting on 1.16's idle stance where its idle clip had left the Stand. The port's idle is the official one, so by the
	 * coordinator's call the clips start on its t=0 values (the head on its own head and look) and lerp into 1.16's values
	 * over their first segment, keeping the 1.16 motion after it; the time stop's 1.16 body, a root bone at the neck, turns
	 * on body_rot (the neck pivot) and the port's waist body stays 0 until the hand-over, as the uppercut's.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void starPlatinumsClipsFrom116StartOnTheOfficialIdleAndLerpInto116sStance(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject idle = clipOf(helper, files, SP_ANIMS, "idle");
		float[][] looks = { { 0, 0 }, { 20, -35 }, { -30, 40 } };
		for (Object[] row : SP_CLIPS) {
			String name = (String) row[0];
			float first = (Float) row[1];
			float[][] stance = (float[][]) row[2];
			JsonObject clip = clipOf(helper, files, SP_ANIMS, name);
			for (String bone : idle.getAsJsonObject("bones").keySet()) {
				if (bone.equals("head") || bone.equals("head_rot") || bone.equals("right_shoulder")) {
					continue;
				}
				for (float[] look : looks) {
					float[] want = StandClipPoses116GameTests.rotationAt(idle, bone, 0, look);
					float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, 0, look);
					if (!near(got, want, 0.05F)) {
						wrong.add(name + " starts " + bone + " on " + Arrays.toString(got) + " where the official idle has " + Arrays.toString(want));
						break;
					}
				}
			}
			for (float[] look : looks) {
				double off = StandClipPoses116GameTests.angleBetween(composedHead(clip, 0, look), composedHead(idle, 0, look));
				if (off > 0.5) {
					wrong.add(name + " starts the head " + String.format("%.1f", off) + " degrees off the official idle's looking "
							+ Arrays.toString(look));
				}
			}
			for (int b = 0; b < SP_STANCE.length; b++) {
				float[] idleValue = StandClipPoses116GameTests.rotationAt(idle, SP_STANCE[b], 0, looks[0]);
				compare(wrong, clip, SP_STANCE[b], first, stance[b], name + "'s stance at the end of its first segment");
				compare(wrong, clip, SP_STANCE[b], first / 2, lerp(idleValue, stance[b], 0.5F), name + " half way from the official idle");
			}
		}
		// 1.16's time stop body on body_rot through the stop, the waist body still
		JsonObject stop = clipOf(helper, files, SP_ANIMS, (String) SP_CLIPS[2][0]);
		compare(wrong, stop, "body_rot", 1.0F, TIME_STOP_AT_025[0], "time_stop's body at the neck");
		compare(wrong, stop, "body_rot", 2.15F, new float[] { 7.5F, -30, 0 }, "time_stop's body at the neck");
		compare(wrong, stop, "body", 2.15F, new float[] { 0, 0, 0 }, "time_stop's waist");
		helper.assertTrue(wrong.isEmpty(), "Star Platinum's clips from 1.16 start on the official idle and lerp into 1.16's stance over"
				+ " their first segment: " + wrong.subList(0, Math.min(12, wrong.size())) + (wrong.size() > 12 ? " and "
				+ (wrong.size() - 12) + " more" : ""));
		helper.succeed();
	}

	// Silver Chariot's rapier moves (the ability names a press asks a clip by)
	private static final String[] SC_RAPIER_MOVES = { "light_attack", "melee_barrage", "dash_attack", "sweeping_attack",
			"rapier_launch", "guard" };
	// the parts 1.16's rapier poses name on the port's bones (SilverChariotModel :252-345; the joints follow their forearm):
	// the stab, the barrage loop and the heavy (dash) pose; RANGED_ATTACK; BLOCK; the sweep's poses 1-2 and pose 3 alone
	private static final List<String> STAB_PARTS = List.of("body_rot", "torso_bend", "left_arm", "left_arm_bend", "left_arm_joint",
			"right_arm", "right_arm_bend", "right_arm_joint", "rapier");
	private static final List<String> LAUNCH_PARTS = List.of("body_rot", "torso_bend", "left_arm", "right_arm", "rapier");
	private static final List<String> BLOCK_PARTS = List.of("left_arm", "left_arm_bend", "left_arm_joint", "right_arm",
			"right_arm_bend", "right_arm_joint", "rapier");
	private static final List<String> SWEEP_PARTS = List.of("body_rot", "torso_bend", "right_arm", "right_arm_bend",
			"right_arm_joint", "rapier");
	private static final List<String> SWEEP_LEFT_PARTS = List.of("left_arm", "left_arm_bend", "left_arm_joint");

	// 1.16's idle in a bone of a rapier move at a clip time: 1 where the move's poses leave the bone on the idle (applied
	// first by LegacyStandAnimator.poseStand, and the rigid filler of an idle phase), 0 where a pose holds it, the
	// transition's share between (perform p, recovery r, end e)
	private static float idleShare(String clip, String bone, float t, float p, float r, float e) {
		float recovery = (t - r) / (e - r);
		float sweepPose2 = p + 0.2F * (r - p);
		return switch (clip) {
			case "block" -> BLOCK_PARTS.contains(bone) ? 0 : 1;
			case "light_attack" -> !STAB_PARTS.contains(bone) ? 1 : t < r ? 0 : Math.max(4 * recovery - 3, 0);
			case "melee_barrage" -> !STAB_PARTS.contains(bone) || t < p || t >= r ? 1 : 0;
			case "dash_attack" -> !STAB_PARTS.contains(bone) ? 1 : t < r ? 0 : recovery;
			case "sweeping_attack" -> SWEEP_PARTS.contains(bone) ? (t < p ? 1 - t / p : t < r ? 0 : recovery)
					: SWEEP_LEFT_PARTS.contains(bone) ? (t < sweepPose2 ? 1 : t < r ? 1 - (t - sweepPose2) / (r - sweepPose2) : recovery) : 1;
			case "rapier_launch" -> !LAUNCH_PARTS.contains(bone) ? 1 : t < p ? 1 : t < r ? 1 - (t - p) / (r - p) : recovery;
			default -> throw new IllegalArgumentException("no 1.16 pose model for " + clip);
		};
	}

	// 1.16's root-space head of a rapier move: the look, but for the sweep's posed heads (sweepPose1-3 yaws -45, -36, 0)
	// lerped from and to the look angle by angle
	private static float[] head116(String clip, float t, float p, float r, float e, float[] look) {
		float share = 1, yaw = 0;
		if (clip.equals("sweeping_attack")) {
			float sweepPose2 = p + 0.2F * (r - p);
			share = t < p ? 1 - t / p : t < r ? 0 : (t - r) / (e - r);
			yaw = t < p ? -45 * t / p : t < sweepPose2 ? -45 + 9 * (t - p) / (sweepPose2 - p)
					: t < r ? -36 + 36 * (t - sweepPose2) / (r - sweepPose2) : 0;
		}
		return new float[] { look[0] * share, look[1] * share + yaw, 0 };
	}

	/*
	 * 1.16's SilverChariotModel drew every rapier move over its idle pose, a ConditionalModelPose: with the rapier in hand
	 * and the armour off, the armourless pose (the port's no_armor_idle). LegacyStandAnimator.poseStand applied it before
	 * the move's pose, so the parts a pose leaves out (the legs, the head on the look) stayed on it, a phase filled with the
	 * idle showed it, and every recovery went back to it. Over no_armor_idle each rapier move plays its no_armor_ clip
	 * (AnimationSet.armorlessClipKey, asked by PreFrameEntityAnimCalc): the move's clip with 1.16's idle share of every bone
	 * on no_armor_idle's values instead of the official idle's, the head on 1.16's root-space head, the end on no_armor_idle.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void silverChariotWithoutItsArmourDrawsItsRapierMovesOverTheArmourlessIdleAs116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject anims = files.computeIfAbsent(SC_ANIMS, a -> StandClipPoses116GameTests.readJson(helper, a)).getAsJsonObject("animations");
		JsonObject idle = clipOf(helper, files, SC_ANIMS, "idle");
		JsonObject armorless = clipOf(helper, files, SC_ANIMS, "no_armor_idle");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("silver_chariot"));
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		List<String> moves = new ArrayList<>();
		try {
			for (String move : SC_RAPIER_MOVES) {
				Ability ability = type.getBaseMoveset().getAbility(move);
				helper.assertTrue(ability instanceof EntityActionAbility, "setup: Silver Chariot has no " + move);
				EntityActionAbility actionAbility = (EntityActionAbility) ability;
				moves.add(actionAbility.getEntityAnim(actionAbility.initActionOnAbilityUse(helper.getLevel(), user, user, null)).name());
			}
		}
		finally {
			user.discard();
		}
		for (String name : moves) {
			String key = AnimationSet.armorlessClipKey("no_armor_idle", name, anims::has);
			if (!("no_armor_" + name).equals(key)) {
				wrong.add(name + " has no armourless clip over no_armor_idle (" + key + ")");
				continue;
			}
			for (String otherIdle : new String[] { "idle", "no_rapier_idle" }) {
				if (AnimationSet.armorlessClipKey(otherIdle, name, anims::has) != null) {
					wrong.add(name + " plays its armourless clip over " + otherIdle);
				}
			}
			JsonObject base = clipOf(helper, files, SC_ANIMS, name);
			JsonObject clip = clipOf(helper, files, SC_ANIMS, key);
			for (String field : new String[] { "timeline", "animation_length", "loop" }) {
				if (!String.valueOf(clip.get(field)).equals(String.valueOf(base.get(field)))) {
					wrong.add(key + "'s " + field + " " + clip.get(field) + " where " + name + " has " + base.get(field));
				}
			}
			float end = base.get("animation_length").getAsFloat();
			float perform = base.has("timeline") ? StandClipPoses116GameTests.marker(base, "phase=PERFORM;") : end;
			float recovery = base.has("timeline") ? StandClipPoses116GameTests.marker(base, "phase=RECOVERY;") : end;
			List<Float> times = new ArrayList<>();
			for (float time = 0; time < end; time += 0.005F) {
				times.add(time);
			}
			times.addAll(List.of(Math.nextDown(perform), perform, Math.nextDown(recovery), recovery, end));
			for (String bone : clip.getAsJsonObject("bones").keySet()) {
				StandClipPoses116GameTests.keys(clip, bone).forEach(entry -> times.add(entry.getKey()));
			}
			Set<String> bones = new LinkedHashSet<>(base.getAsJsonObject("bones").keySet());
			bones.addAll(clip.getAsJsonObject("bones").keySet());
			bones.addAll(armorless.getAsJsonObject("bones").keySet());
			bones.addAll(idle.getAsJsonObject("bones").keySet());
			bones.removeAll(List.of("head", "head_rot"));
			for (String bone : bones) {
				String first = null;
				for (float[] look : LOOKS) {
					float[] official = StandClipPoses116GameTests.rotationAt(idle, bone, 0, look);
					float[] own = StandClipPoses116GameTests.rotationAt(armorless, bone, 0, look);
					for (float time : times) {
						float share = idleShare(name, bone, time, perform, recovery, end);
						float[] want = StandClipPoses116GameTests.rotationAt(base, bone, time, look);
						for (int axis = 0; axis < 3; axis++) {
							want[axis] += share * (own[axis] - official[axis]);
						}
						float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, time, look);
						if (!near(got, want, 0.05F)) {
							first = "at " + time + " looking " + Arrays.toString(look) + " " + Arrays.toString(got) + " where 1.16 had "
									+ Arrays.toString(want) + " (" + share + " of the armourless idle)";
							break;
						}
					}
					if (first != null) {
						break;
					}
				}
				if (first != null) {
					wrong.add(key + " " + bone + " " + first);
				}
			}
			for (float[] look : LOOKS) {
				for (float time : times) {
					double[][] want = StandClipPoses116GameTests.rotationMatrix(head116(name, time, perform, recovery, end, look));
					double off = StandClipPoses116GameTests.angleBetween(composedHead(clip, time, look), want);
					if (off > 0.5) {
						wrong.add(key + " head at " + time + " looking " + Arrays.toString(look) + " " + String.format("%.2f", off)
								+ " degrees off 1.16's root-space head");
						break;
					}
				}
			}
			// every recovery went back to the armourless idle; the guard ends at once (no recovery), as 1.16's
			if (!name.equals("block")) {
				for (String bone : armorless.getAsJsonObject("bones").keySet()) {
					if (!bone.equals("head") && !bone.equals("head_rot")) {
						compare(wrong, clip, bone, end, StandClipPoses116GameTests.rotationAt(armorless, bone, 0, LOOKS[0]),
								key + " hands over to no_armor_idle at its end:");
					}
				}
			}
		}
		// 1.16's idle without the rapier ignored the armour, and so do the rapierless moves
		for (String name : new String[] { "no_rapier_light_attack", "no_rapier_block" }) {
			if (AnimationSet.armorlessClipKey("no_armor_idle", name, anims::has) != null) {
				wrong.add(name + " has an armourless clip");
			}
		}
		// the client-only clip lookup asks the rule (read from its class file)
		Set<String> invoked = invokedMethods(helper, "rotp/core/client/entityanim/PreFrameEntityAnimCalc.class");
		for (String call : new String[] { "rotp/core/client/entityanim/PreFrameEntityAnimCalc.armorlessVariant",
				"rotp/core/client/entityanim/AnimationSet.armorlessClipKey" }) {
			if (!invoked.contains(call)) {
				wrong.add("PreFrameEntityAnimCalc no longer calls " + call);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's Silver Chariot without its armour drew its rapier moves over the armourless idle: "
				+ wrong.subList(0, Math.min(12, wrong.size())) + (wrong.size() > 12 ? " and " + (wrong.size() - 12) + " more" : ""));
		helper.succeed();
	}

	// "owner.name" of every method a class file invokes (its Methodref constants)
	private static Set<String> invokedMethods(GameTestHelper helper, String classPath) {
		Set<String> out = new HashSet<>();
		try (InputStream raw = StandClipFollowUps116GameTests.class.getResourceAsStream("/" + classPath)) {
			helper.assertTrue(raw != null, "Missing class file " + classPath);
			DataInputStream in = new DataInputStream(raw);
			helper.assertTrue(in.readInt() == 0xCAFEBABE, classPath + " is not a class file");
			in.readUnsignedShort();
			in.readUnsignedShort();
			int count = in.readUnsignedShort();
			String[] utf = new String[count];
			int[] a = new int[count];
			int[] b = new int[count];
			int[] tag = new int[count];
			for (int i = 1; i < count; i++) {
				tag[i] = in.readUnsignedByte();
				switch (tag[i]) {
					case 1 -> utf[i] = in.readUTF();
					case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
					case 3, 4 -> in.readInt();
					case 5, 6 -> { in.readLong(); i++; }
					case 9, 10, 11, 12, 17, 18 -> { a[i] = in.readUnsignedShort(); b[i] = in.readUnsignedShort(); }
					case 15 -> { in.readUnsignedByte(); a[i] = in.readUnsignedShort(); }
					default -> throw new IOException("Unknown constant tag " + tag[i]);
				}
			}
			for (int i = 1; i < count; i++) {
				if (tag[i] == 10) {
					out.add(utf[a[a[i]]] + "." + utf[a[b[i]]]);
				}
			}
		}
		catch (IOException e) {
			helper.fail("Cannot read " + classPath + ": " + e);
		}
		return out;
	}

	private static float[] lerp(float[] a, float[] b, float f) {
		return new float[] { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f };
	}

	// the head's rotation with its chain (stand_pos, body_rot, body, torso, torso_bend, head, head_rot) at a clip time
	private static double[][] composedHead(JsonObject clip, float time, float[] look) {
		double[][] composed = StandClipPoses116GameTests.rotationMatrix(new float[3]);
		for (String bone : new String[] { "stand_pos", "body_rot", "body", "torso", "torso_bend", "head", "head_rot" }) {
			composed = StandClipPoses116GameTests.multiply(composed,
					StandClipPoses116GameTests.rotationMatrix(StandClipPoses116GameTests.rotationAt(clip, bone, time, look)));
		}
		return composed;
	}

	// the loopBack of the timeline entry at a clip time ("phase.loopBack = x;"), or NaN
	private static float loopBackOf(JsonObject clip, float at) {
		for (Map.Entry<String, JsonElement> marker : clip.getAsJsonObject("timeline").entrySet()) {
			if (Float.parseFloat(marker.getKey()) != at) {
				continue;
			}
			List<JsonElement> lines = new ArrayList<>();
			if (marker.getValue().isJsonArray()) {
				marker.getValue().getAsJsonArray().forEach(lines::add);
			}
			else {
				lines.add(marker.getValue());
			}
			for (JsonElement line : lines) {
				String text = line.getAsString().replace(" ", "");
				if (text.startsWith("phase.loopBack=")) {
					return Float.parseFloat(text.substring("phase.loopBack=".length(), text.length() - 1));
				}
			}
		}
		return Float.NaN;
	}

	private static void compare(List<String> wrong, JsonObject clip, String bone, float time, float[] want, String what) {
		compareAt(wrong, clip, bone, time, LOOKS[0], want, what);
	}

	private static void compareAt(List<String> wrong, JsonObject clip, String bone, float time, float[] look, float[] want,
			String what) {
		float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, time, look);
		if (!near(got, want, 0.05F)) {
			wrong.add(bone + " at " + time + " " + Arrays.toString(got) + " where 1.16 had " + what + " " + Arrays.toString(want));
		}
	}

	private static boolean near(float[] a, float[] b, float tolerance) {
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(a[axis] - b[axis]) > tolerance) {
				return false;
			}
		}
		return true;
	}

	private static JsonObject clipOf(GameTestHelper helper, Map<String, JsonObject> files, String asset, String name) {
		JsonObject clip = files.computeIfAbsent(asset, a -> StandClipPoses116GameTests.readJson(helper, a))
				.getAsJsonObject("animations").getAsJsonObject(name);
		helper.assertTrue(clip != null && clip.has("bones"), asset + " lost " + name);
		return clip;
	}
}
