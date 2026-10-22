package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The core clips converted from 1.16's Java-model poses (HumanoidStandModel and the Stands' own models, drawn by
 * LegacyStandAnimator) against those poses as the port samples the clips: each bone's keys eased by the key it moves
 * to, the look queries evaluated for a given look.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandClipPoses116GameTests {
	private StandClipPoses116GameTests() {}

	private static final String MR_ANIMS =
			"stand_skins/magicians_red/assets/jojo_ripples/animations/magicians_red.animation.json";
	private static final String SC_ANIMS =
			"stand_skins/silver_chariot/assets/jojo_ripples/animations/silver_chariot.animation.json";
	private static final String HG_ANIMS =
			"stand_skins/hierophant_green/assets/jojo_ripples/animations/hierophant_green.animation.json";

	// the port's bones from the model root to the head; head_rot under the head carries the look
	private static final String[] HEAD_CHAIN = { "stand_pos", "body_rot", "body", "torso", "torso_bend", "head", "head_rot" };
	// a look the head checks are also made at: pitch and yaw in degrees
	private static final float[] LOOK = { 20, -35 };

	// a 1.16 head at a clip time (0, PERFORM, RECOVERY, END, W<f> a fraction of the windup, R<f> of the recovery, or
	// seconds): its root-space angles, LOOK where it followed the look, HALF the given angles plus half the look (1.16 half
	// way from a posed head to the look), TURNED the look turned by the given angles (an official 1.21.1 clip's head),
	// IDLE where the clip hands over to the idle and the head is the idle's own, or TO_IDLE the given angles lerped angle
	// by angle to the idle's head by the recovery's completion (1.16's recovery transition with HEAD_ROTATION)
	private enum Head { POSED, LOOK, HALF, TURNED, IDLE, TO_IDLE }

	private record HeadPose(String asset, String clip, String at, Head kind, float[] head) {
		HeadPose(String asset, String clip, String at) {
			this(asset, clip, at, Head.LOOK, null);
		}
	}

	private static HeadPose posed(String asset, String clip, String at, float x, float y, float z) {
		return new HeadPose(asset, clip, at, Head.POSED, new float[] { x, y, z });
	}

	private static HeadPose idle(String asset, String clip) {
		return new HeadPose(asset, clip, "END", Head.IDLE, null);
	}

	private static final List<HeadPose> HEAD_POSES = List.of(
			// HumanoidStandModel :499-536, the jab: no head in its poses, the idle's look
			new HeadPose(MR_ANIMS, "punch", "0"), new HeadPose(MR_ANIMS, "punch", "0.1"),
			new HeadPose(MR_ANIMS, "punch", "PERFORM"), new HeadPose(MR_ANIMS, "punch", "RECOVERY"),
			new HeadPose(MR_ANIMS, "punch", "0.4667"), idle(MR_ANIMS, "punch"),
			// MagiciansRedModel :233-257, summon poses 1 and 2 (LegacyStandAnimator.poseSummon), then the idle's look
			posed(MR_ANIMS, "summon1", "0", 0, 7.5F, 0), idle(MR_ANIMS, "summon1"),
			posed(MR_ANIMS, "summon2", "0", -45, 0, 0), idle(MR_ANIMS, "summon2"),
			// MagiciansRedModel :287-322, the kick: kickPose1 -> kickPose2 over the windup, kickPose2 -> kickPose3 over the
			// perform, then to the idle copy's HEAD_ROTATION over the recovery
			posed(MR_ANIMS, "kick", "0", -15, 0, 0), posed(MR_ANIMS, "kick", "W0.5", -22.5F, -7.5F, 0),
			posed(MR_ANIMS, "kick", "PERFORM", -30, -15, 0), posed(MR_ANIMS, "kick", "0.55", -37.5F, -12.5F, 0),
			posed(MR_ANIMS, "kick", "RECOVERY", -45, -10, 0),
			new HeadPose(MR_ANIMS, "kick", "R0.25", Head.TO_IDLE, new float[] { -45, -10, 0 }),
			new HeadPose(MR_ANIMS, "kick", "R0.5", Head.TO_IDLE, new float[] { -45, -10, 0 }),
			new HeadPose(MR_ANIMS, "kick", "R0.75", Head.TO_IDLE, new float[] { -45, -10, 0 }), idle(MR_ANIMS, "kick"),
			// SilverChariotModel :245-372: the rapier stab, the barrage stab loop, the rapier heavy (dash), the jab without
			// the rapier, no head in any of them; RANGED_ATTACK (the rapier launch) was drawn as the idle
			new HeadPose(SC_ANIMS, "light_attack", "0"), new HeadPose(SC_ANIMS, "light_attack", "PERFORM"),
			new HeadPose(SC_ANIMS, "light_attack", "RECOVERY"), idle(SC_ANIMS, "light_attack"),
			// the barrage shows the idle in its windup and recovery (ArmsBarrageAnimation)
			new HeadPose(SC_ANIMS, "melee_barrage", "0", Head.IDLE, null), new HeadPose(SC_ANIMS, "melee_barrage", "PERFORM"),
			new HeadPose(SC_ANIMS, "melee_barrage", "1.0"), new HeadPose(SC_ANIMS, "melee_barrage", "RECOVERY", Head.IDLE, null),
			idle(SC_ANIMS, "melee_barrage"),
			new HeadPose(SC_ANIMS, "dash_attack", "0"), new HeadPose(SC_ANIMS, "dash_attack", "PERFORM"),
			new HeadPose(SC_ANIMS, "dash_attack", "RECOVERY"), idle(SC_ANIMS, "dash_attack"),
			new HeadPose(SC_ANIMS, "no_rapier_light_attack", "0"), new HeadPose(SC_ANIMS, "no_rapier_light_attack", "0.1"),
			new HeadPose(SC_ANIMS, "no_rapier_light_attack", "PERFORM"),
			new HeadPose(SC_ANIMS, "no_rapier_light_attack", "RECOVERY"),
			new HeadPose(SC_ANIMS, "no_rapier_light_attack", "0.4667"),
			new HeadPose(SC_ANIMS, "rapier_launch", "0"), new HeadPose(SC_ANIMS, "rapier_launch", "PERFORM"),
			new HeadPose(SC_ANIMS, "rangedAttack", "0"), new HeadPose(SC_ANIMS, "rangedAttack", "PERFORM"),
			// the idle is the official port's, its head 5 degrees off the look under body_rot -25 and torso_bend 20; the
			// other two idles (initBaseIdlePose: HEAD_ROTATION)
			new HeadPose(SC_ANIMS, "idle", "0", Head.TURNED, new float[] { 0, -5, 0 }),
			new HeadPose(SC_ANIMS, "no_rapier_idle", "0"), new HeadPose(SC_ANIMS, "no_armor_idle", "0"),
			// SilverChariotModel :211-241, summon poses 1-3 (pose 2 has no head: the reset head, the root's forward), their
			// head rolls zeroed by the idle's HEAD_ROTATION every frame (StandEntityModel :244, LegacyStandAnimator :119)
			posed(SC_ANIMS, "summon1", "0", -10, 0, 0), idle(SC_ANIMS, "summon1"),
			posed(SC_ANIMS, "summon2", "0", 0, 0, 0), idle(SC_ANIMS, "summon2"),
			posed(SC_ANIMS, "summon3", "0", 5, 2.5F, 0), idle(SC_ANIMS, "summon3"),
			// HierophantGreenModel :176-219, Emerald Splash: esPose1-2 without a head (the look), esPose3's head at .85 and
			// .9 of the windup, esPose4 with HEAD_ROTATION at the end of the windup, the idle in the recovery
			new HeadPose(HG_ANIMS, "emerald_splash", "0"), new HeadPose(HG_ANIMS, "emerald_splash", "W0.6"),
			posed(HG_ANIMS, "emerald_splash", "W0.85", 0, 22.5F, 0), posed(HG_ANIMS, "emerald_splash", "W0.9", 0, 22.5F, 0),
			new HeadPose(HG_ANIMS, "emerald_splash", "W0.95", Head.HALF, new float[] { 0, 22.5F / 4, 0 }),
			new HeadPose(HG_ANIMS, "emerald_splash", "PERFORM"), new HeadPose(HG_ANIMS, "emerald_splash", "RECOVERY"),
			idle(HG_ANIMS, "emerald_splash"),
			new HeadPose(HG_ANIMS, "rangedAttack", "0"), posed(HG_ANIMS, "rangedAttack", "W0.85", 0, 22.5F, 0),
			new HeadPose(HG_ANIMS, "rangedAttack", "PERFORM"),
			// the grapple pose has no head; summon pose 1's head, then the idle's look
			new HeadPose(HG_ANIMS, "grapple", "0"), new HeadPose(HG_ANIMS, "grapple", "PERFORM"),
			new HeadPose(HG_ANIMS, "grapple", "RECOVERY"), idle(HG_ANIMS, "grapple"),
			posed(HG_ANIMS, "summon1", "0", -30, 0, 0), idle(HG_ANIMS, "summon1"));

	/*
	 * 1.16's Java models hung the head from the model root beside the body: a pose's head angles were root-space angles,
	 * a pose without a head left it on the look the idle pose had just given it, and a summon pose without one left it on
	 * the reset pose, the root's forward, until the idle's look came back. The port's head hangs under body_rot, body,
	 * torso and torso_bend, with the look on head_rot: the chain, the head and head_rot together give 1.16's head within a
	 * degree, at a level look and at another one, and a clip hands over to its idle with the idle's own head.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theHeadsOf116JavaModelPosesStayInRootSpaceUnderThePortsBodyChain(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		for (HeadPose pose : HEAD_POSES) {
			JsonObject anims = files.computeIfAbsent(pose.asset(), asset -> readJson(helper, asset)).getAsJsonObject("animations");
			JsonObject clip = anims.getAsJsonObject(pose.clip());
			helper.assertTrue(clip != null && clip.has("bones"), pose.asset() + " lost " + pose.clip());
			float time = clipTime(clip, pose.at());
			for (float[] look : new float[][] { { 0, 0 }, LOOK }) {
				double[][] expected = switch (pose.kind()) {
					case POSED -> rotationMatrix(pose.head());
					case LOOK -> rotationMatrix(new float[] { look[0], look[1], 0 });
					case HALF -> rotationMatrix(new float[] { pose.head()[0] + look[0] / 2, pose.head()[1] + look[1] / 2, 0 });
					case TURNED -> rotationMatrix(new float[] { look[0] + pose.head()[0], look[1] + pose.head()[1], pose.head()[2] });
					case IDLE -> composedHead(anims.getAsJsonObject(idleOf(pose)), 0, look);
					case TO_IDLE -> {
						float[] idleHead = eulerOf(composedHead(anims.getAsJsonObject(idleOf(pose)), 0, look));
						float recovery = marker(clip, "phase=RECOVERY;");
						float f = (time - recovery) / (clip.get("animation_length").getAsFloat() - recovery);
						yield rotationMatrix(new float[] { pose.head()[0] + (idleHead[0] - pose.head()[0]) * f,
								pose.head()[1] + (idleHead[1] - pose.head()[1]) * f, pose.head()[2] + (idleHead[2] - pose.head()[2]) * f });
					}
				};
				double off = angleBetween(composedHead(clip, time, look), expected);
				if (off > 1) {
					wrong.add(pose.clip() + " of " + pose.asset() + " at " + pose.at() + " (" + time + ") looking "
							+ Arrays.toString(look) + ": head " + String.format("%.1f", off) + " degrees off "
							+ (pose.kind() == Head.TURNED ? "the look turned by " : "1.16's ")
							+ (pose.head() != null ? Arrays.toString(pose.head()) : pose.kind()));
				}
			}
		}
		// LegacyStandAnimator.poseSummon ran the idle's HEAD_ROTATION every frame of a summon, which set head.zRot = 0 at any
		// rotationAmount: no summon head rolls at any clip time
		for (String[] summon : SUMMON_CLIPS) {
			JsonObject clip = files.computeIfAbsent(summon[0], asset -> readJson(helper, asset)).getAsJsonObject("animations")
					.getAsJsonObject(summon[1]);
			float end = clip.get("animation_length").getAsFloat();
			for (float time = 0; time <= end + 0.00001F; time += 0.01F) {
				double[][] head = composedHead(clip, Math.min(time, end), new float[2]);
				double roll = Math.toDegrees(Math.atan2(head[1][0], head[0][0]));
				if (Math.abs(roll) > 0.5) {
					wrong.add(summon[1] + " of " + summon[0] + " at " + time + ": the head rolls " + String.format("%.1f", roll) + " degrees");
					break;
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "A 1.16 pose's head kept its root-space angles, the look where the pose had none: " + wrong);
		helper.succeed();
	}

	private static final String[][] SUMMON_CLIPS = { { MR_ANIMS, "summon1" }, { MR_ANIMS, "summon2" }, { SC_ANIMS, "summon1" },
			{ SC_ANIMS, "summon2" }, { SC_ANIMS, "summon3" }, { HG_ANIMS, "summon1" } };

	private static final String CD_ANIMS =
			"stand_skins/crazy_diamond/assets/jojo_ripples/animations/crazy_diamond.animation.json";

	// how much of the look pitch a pose put on both arms (StandEntityModel.setSecondXRot) at clip times
	private record ArmLook(String asset, String clip, String[] at, float[] share) {}

	private static final List<ArmLook> ARM_LOOKS = List.of(
			// HumanoidStandModel :565-584, heavyAttackAnim: armsRotationFull in every pose, the recovery's transition from
			// heavyImpact keeps it to the end of the action
			new ArmLook(MR_ANIMS, "heavy_punch", new String[] { "0", "W0.5", "PERFORM", "RECOVERY", "END" }, new float[] { 1, 1, 1, 1, 1 }),
			// HumanoidStandModel :482-536, the jab: xRot * t to jabArmTurn, the full look at jabImpact, xRot * (1 - t) to
			// jabArmTurnBack, none after (the clip's keys 0, 0.1, PERFORM, RECOVERY, 0.4667, the end)
			new ArmLook(MR_ANIMS, "punch", new String[] { "0", "0.05", "0.1", "PERFORM", "RECOVERY", "0.35", "0.4667", "END" },
					new float[] { 0, 0.5F, 1, 1, 1, 0.5F, 0, 0 }),
			new ArmLook(SC_ANIMS, "no_rapier_light_attack", new String[] { "0", "0.1", "PERFORM", "RECOVERY", "0.4667", "END" },
					new float[] { 0, 1, 1, 1, 0, 0 }),
			// SilverChariotModel :247-288, the rapier stab: armsRotation in both poses and the recovery's transition
			new ArmLook(SC_ANIMS, "light_attack", new String[] { "0", "PERFORM", "RECOVERY", "END" }, new float[] { 1, 1, 1, 1 }),
			// SilverChariotModel :361-365 and ArmsBarrageAnimation: no pose in the windup, the stab loop in the perform,
			// the idle in the recovery
			new ArmLook(SC_ANIMS, "melee_barrage", new String[] { "0", "0.2999", "PERFORM", "1.0", "2.2999", "RECOVERY", "END" },
					new float[] { 0, 0, 1, 1, 1, 0, 0 }));

	// a clip whose joints turn by half their forearm or lower leg, the limbs to check; one whose windup starts on the idle
	// starts on the idle's own joints and turns them by half the forearm from its second key
	private record JointClip(String asset, String clip, boolean fromIdle, String... limbs) {}

	private static final List<JointClip> JOINT_CLIPS = List.of(
			new JointClip(SC_ANIMS, "light_attack", false, "left_arm", "right_arm"),
			new JointClip(SC_ANIMS, "no_rapier_light_attack", false, "left_arm", "right_arm"),
			new JointClip(SC_ANIMS, "dash_attack", false, "left_arm", "right_arm"));

	/*
	 * 1.16's humanoid Java-model poses pitched both arms with the look (setSecondXRot, applied after the pose as a parent
	 * x rotation, the port's *_arm_xrot): heavyAttackAnim through the whole action, the jab from its arm turn to its turn
	 * back, Silver Chariot's rapier stab through the whole action and its barrage in the perform; the rapier heavy (the
	 * dash attack) tilted the rapier arm by the look (rightArm.zRot -= xRot) and lerped it out over the recovery. And the
	 * models turned each elbow and knee joint by half its forearm or lower leg (rotateJoint), here up to the clip's last
	 * key, which hands over to the idle's own joint.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theArmsFollowTheLookAndTheJointsBendHalfAsIn116sHumanoidModels(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		float[] level = { 0, 0 };
		for (ArmLook arms : ARM_LOOKS) {
			JsonObject clip = clipOf(helper, files, arms.asset(), arms.clip());
			for (int i = 0; i < arms.at().length; i++) {
				float time = clipTime(clip, arms.at()[i]);
				for (String bone : new String[] { "left_arm_xrot", "right_arm_xrot" }) {
					float[] pitched = rotationAt(clip, bone, time, LOOK);
					float[] flat = rotationAt(clip, bone, time, level);
					if (Math.abs(pitched[0] - arms.share()[i] * LOOK[0]) > 0.05F || Math.abs(flat[0]) > 0.05F
							|| Math.abs(pitched[1]) > 0.05F || Math.abs(pitched[2]) > 0.05F) {
						wrong.add(arms.clip() + " of " + arms.asset() + " " + bone + " at " + arms.at()[i] + " (" + time + ") looking "
								+ LOOK[0] + " down: " + Arrays.toString(pitched) + " where 1.16 pitched the arm by " + arms.share()[i] * LOOK[0]);
					}
				}
			}
		}
		JsonObject dash = clipOf(helper, files, SC_ANIMS, "dash_attack");
		String[] dashAt = { "0", "PERFORM", "RECOVERY", "0.375", "END" };
		float[] dashShare = { 1, 1, 1, 0.5F, 0 };
		for (int i = 0; i < dashAt.length; i++) {
			float time = clipTime(dash, dashAt[i]);
			float tilt = rotationAt(dash, "right_arm", time, LOOK)[2] - rotationAt(dash, "right_arm", time, level)[2];
			if (Math.abs(tilt + dashShare[i] * LOOK[0]) > 0.05F) {
				wrong.add("dash_attack right_arm at " + dashAt[i] + " (" + time + "): its z turns by " + tilt + " looking " + LOOK[0]
						+ " down, where 1.16 turned it by " + -dashShare[i] * LOOK[0]);
			}
		}
		for (JointClip joints : JOINT_CLIPS) {
			JsonObject clip = clipOf(helper, files, joints.asset(), joints.clip());
			for (String limb : joints.limbs()) {
				List<Map.Entry<Float, JsonElement>> bendKeys = keys(clip, limb + "_bend");
				helper.assertTrue(bendKeys.size() > 1, "setup: " + joints.clip() + " of " + joints.asset() + " keys no " + limb + "_bend");
				float until = bendKeys.get(bendKeys.size() - 2).getKey();
				float from = 0;
				if (joints.fromIdle()) {
					JsonObject idle = clipOf(helper, files, joints.asset(), "idle");
					float[] idleJoint = rotationAt(idle, limb + "_joint", 0, LOOK);
					float[] startJoint = rotationAt(clip, limb + "_joint", 0, LOOK);
					for (int axis = 0; axis < 3; axis++) {
						if (Math.abs(startJoint[axis] - idleJoint[axis]) > 0.05F) {
							wrong.add(joints.clip() + " of " + joints.asset() + " starts " + limb + "_joint on " + Arrays.toString(startJoint)
									+ " where the idle it starts from has " + Arrays.toString(idleJoint));
							break;
						}
					}
					from = bendKeys.get(1).getKey();
				}
				int off = 0;
				String first = null;
				for (float time = from; time <= until + 0.00001F; time += 0.01F) {
					float[] bend = rotationAt(clip, limb + "_bend", time, LOOK);
					float[] joint = rotationAt(clip, limb + "_joint", time, LOOK);
					for (int axis = 0; axis < 3; axis++) {
						if (Math.abs(joint[axis] - bend[axis] / 2) > 0.05F) {
							off++;
							if (first == null) {
								first = "at " + time + " " + Arrays.toString(joint) + " where the bend is " + Arrays.toString(bend);
							}
							break;
						}
					}
				}
				if (off > 0) {
					wrong.add(joints.clip() + " of " + joints.asset() + " " + limb + "_joint off half its bend at " + off + " samples, first "
							+ first);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's humanoid models pitched the arms with the look in these poses and turned the"
				+ " joints by half their forearm or lower leg: " + wrong);
		helper.succeed();
	}

	// bones no 1.16 pose of the clip named, so they stayed on the idle pose: on the clip's idle's t=0 value from one clip
	// time to another, or, for a summon (from null), on the reset pose to the clip's last quarter and lerped to the idle
	// by its end
	private record IdleBones(String asset, String clip, String idle, String from, String to, String... bones) {}

	private static IdleBones held(String asset, String clip, String... bones) {
		return new IdleBones(asset, clip, "idle", "0", "END", bones);
	}

	private static IdleBones summonReset(String asset, String clip, String... bones) {
		return new IdleBones(asset, clip, "idle", null, null, bones);
	}

	private static final String[] LEGS = { "left_leg", "left_leg_bend", "right_leg", "right_leg_bend" };

	private static String[] and(String[] bones, String... more) {
		String[] all = Arrays.copyOf(more, more.length + bones.length);
		System.arraycopy(bones, 0, all, more.length, bones.length);
		return all;
	}

	private static final List<IdleBones> IDLE_BONES = List.of(
			// HumanoidStandModel :449-607: the jab, heavyAttackAnim and the block name no leg
			held(MR_ANIMS, "punch", LEGS), held(MR_ANIMS, "heavy_punch", LEGS), held(MR_ANIMS, "block", LEGS),
			// MagiciansRedModel :287-322: no kick pose names upperPart or leftLowerLeg, kickPose1 no arm or forearm
			held(MR_ANIMS, "kick", "torso_bend", "left_leg_bend"),
			new IdleBones(MR_ANIMS, "kick", "idle", "0", "0", "left_arm", "left_arm_bend", "right_arm", "right_arm_bend"),
			// :269-285, FLAME_BURST_POSE (Flame Burst, Fireball, Crossfire Hurricane): no body, upperPart or rightLowerLeg
			held(MR_ANIMS, "flame_burst", "body_rot", "torso_bend", "right_leg_bend"),
			held(MR_ANIMS, "flameBurst", "body_rot", "torso_bend", "right_leg_bend"),
			held(MR_ANIMS, "fireball", "body_rot", "torso_bend", "right_leg_bend"),
			held(MR_ANIMS, "crossfire_hurricane", "body_rot", "torso_bend", "right_leg_bend"),
			// :324-331, RED_BIND_POSE: the arms and forearms only, the head on the idle's look
			held(MR_ANIMS, "red_bind", and(LEGS, "body_rot", "torso_bend", "head", "head_rot")),
			held(MR_ANIMS, "redBind", and(LEGS, "body_rot", "torso_bend", "head", "head_rot")),
			// :233-257, summon poses 1 and 2
			summonReset(MR_ANIMS, "summon1", "left_arm_bend", "right_arm_bend", "torso_bend", "left_leg_bend"),
			summonReset(MR_ANIMS, "summon2", "body_rot", "torso_bend", "left_leg_bend", "right_leg_bend"),
			// SilverChariotModel :354-361, the rapier block: arms, forearms and the rapier; the humanoid block without it
			held(SC_ANIMS, "block", and(LEGS, "body_rot", "torso_bend", "head", "head_rot")),
			new IdleBones(SC_ANIMS, "no_rapier_block", "no_rapier_idle", "0", "END", LEGS),
			// :245-352: the stab, the barrage stab loop, the dash, the sweep and the jab name no leg; RANGED_ATTACK (the
			// rapier launch) was drawn as the idle
			held(SC_ANIMS, "light_attack", LEGS), held(SC_ANIMS, "melee_barrage", LEGS), held(SC_ANIMS, "dash_attack", LEGS),
			held(SC_ANIMS, "sweeping_attack", LEGS), held(SC_ANIMS, "rapier_launch", LEGS), held(SC_ANIMS, "rangedAttack", LEGS),
			new IdleBones(SC_ANIMS, "no_rapier_light_attack", "no_rapier_idle", "0", "END", LEGS),
			// :211-241, summon poses 1-3
			summonReset(SC_ANIMS, "summon1", "body_rot", "left_arm_bend", "right_arm_bend", "left_leg_bend", "right_leg_bend"),
			summonReset(SC_ANIMS, "summon2", and(LEGS, "torso_bend", "right_arm_bend")),
			summonReset(SC_ANIMS, "summon3", "left_arm_bend", "left_leg_bend"),
			// HierophantGreenModel :161-228: the block, the grapple (rightArm, rightForeArm and body), the summon pose (no
			// body or rightForeArm)
			held(HG_ANIMS, "block", LEGS), held(HG_ANIMS, "grapple", and(LEGS, "left_arm", "left_arm_bend")),
			summonReset(HG_ANIMS, "summon1", "body_rot", "right_arm_bend"));

	// the converted clips that hand over to their idle when they end, and the idle's bones they may leave off it
	private record HandOver(String asset, String clip, String idle, String... except) {}

	private static final List<HandOver> HAND_OVERS = List.of(
			new HandOver(MR_ANIMS, "summon1", "idle"), new HandOver(MR_ANIMS, "summon2", "idle"),
			new HandOver(MR_ANIMS, "punch", "idle"), new HandOver(MR_ANIMS, "heavy_punch", "idle"),
			new HandOver(MR_ANIMS, "kick", "idle"), new HandOver(MR_ANIMS, "red_bind", "idle"),
			new HandOver(MR_ANIMS, "redBind", "idle"),
			// the flame poses set the head themselves (head.xRot = xRot - 22.5 degrees), the port's clips have none
			new HandOver(MR_ANIMS, "flame_burst", "idle", "head", "head_rot"),
			new HandOver(MR_ANIMS, "flameBurst", "idle", "head", "head_rot"),
			new HandOver(MR_ANIMS, "fireball", "idle", "head", "head_rot"),
			new HandOver(MR_ANIMS, "crossfire_hurricane", "idle", "head", "head_rot"),
			new HandOver(SC_ANIMS, "summon1", "idle"), new HandOver(SC_ANIMS, "summon2", "idle"),
			new HandOver(SC_ANIMS, "summon3", "idle"), new HandOver(SC_ANIMS, "light_attack", "idle"),
			new HandOver(SC_ANIMS, "melee_barrage", "idle"), new HandOver(SC_ANIMS, "dash_attack", "idle"),
			new HandOver(SC_ANIMS, "sweeping_attack", "idle"), new HandOver(SC_ANIMS, "rapier_launch", "idle"),
			new HandOver(SC_ANIMS, "rangedAttack", "idle"), new HandOver(SC_ANIMS, "no_rapier_light_attack", "no_rapier_idle"),
			new HandOver(HG_ANIMS, "summon1", "idle"), new HandOver(HG_ANIMS, "emerald_splash", "idle"),
			new HandOver(HG_ANIMS, "rangedAttack", "idle"), new HandOver(HG_ANIMS, "grapple", "idle"));

	/*
	 * LegacyStandAnimator.poseStand applied the idle pose at full weight before an action's poses, and those lerped only
	 * the parts they named, so a bone no active pose named stayed on the idle; poseSummon reset the model, applied the
	 * summon pose and lerped the idle in over ticks 15-20 of 20. The port draws one clip at a time on the geo's rest
	 * pose: a converted clip keys those bones with its idle's values (an official idle where the official port has one),
	 * a summon keeps them on the reset pose to its last quarter, and every clip ends on its idle bone by bone, so nothing
	 * snaps where the clip starts or hands over.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theBones116KeptOnTheIdleKeepTheIdlesValuesInTheConvertedClips(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		float[][] looks = { { 0, 0 }, LOOK };
		for (IdleBones row : IDLE_BONES) {
			JsonObject clip = clipOf(helper, files, row.asset(), row.clip());
			JsonObject idle = clipOf(helper, files, row.asset(), row.idle());
			float end = clip.get("animation_length").getAsFloat();
			List<float[]> samples = new ArrayList<>();
			if (row.from() != null) {
				float from = clipTime(clip, row.from()), to = clipTime(clip, row.to());
				for (float time = from; time < to; time += 0.01F) {
					samples.add(new float[] { time, 1 });
				}
				samples.add(new float[] { to, 1 });
			}
			else {
				for (float time = 0; time < end * 0.75F; time += 0.01F) {
					samples.add(new float[] { time, 0 });
				}
				samples.add(new float[] { end * 0.75F, 0 });
				samples.add(new float[] { end * 0.875F, 0.5F });
				samples.add(new float[] { end, 1 });
			}
			for (String bone : row.bones()) {
				helper.assertTrue(idle.getAsJsonObject("bones").has(bone), "setup: " + row.idle() + " of " + row.asset() + " has no " + bone);
				String first = null;
				for (float[] sample : samples) {
					for (float[] look : looks) {
						float[] idleAt0 = rotationAt(idle, bone, 0, look);
						float[] want = { idleAt0[0] * sample[1], idleAt0[1] * sample[1], idleAt0[2] * sample[1] };
						float[] got = rotationAt(clip, bone, sample[0], look);
						for (int axis = 0; axis < 3 && first == null; axis++) {
							if (Math.abs(got[axis] - want[axis]) > 0.05F) {
								first = "at " + sample[0] + " looking " + Arrays.toString(look) + " " + Arrays.toString(got) + " where 1.16 had "
										+ (sample[1] == 1 ? "the idle's " : sample[1] == 0 ? "the reset pose's " : "half the idle's ")
										+ Arrays.toString(want);
							}
						}
					}
				}
				if (first != null) {
					wrong.add(row.clip() + " of " + row.asset() + " " + bone + " " + first);
				}
			}
		}
		for (HandOver row : HAND_OVERS) {
			JsonObject clip = clipOf(helper, files, row.asset(), row.clip());
			JsonObject idle = clipOf(helper, files, row.asset(), row.idle());
			float end = clip.get("animation_length").getAsFloat();
			List<String> except = List.of(row.except());
			for (String bone : idle.getAsJsonObject("bones").keySet()) {
				if (bone.endsWith("_joint") || bone.endsWith("_xrot") || except.contains(bone)) {
					continue;
				}
				for (float[] look : looks) {
					float[] want = rotationAt(idle, bone, 0, look);
					float[] got = rotationAt(clip, bone, end, look);
					if (Math.abs(got[0] - want[0]) > 0.05F || Math.abs(got[1] - want[1]) > 0.05F || Math.abs(got[2] - want[2]) > 0.05F) {
						wrong.add(row.clip() + " of " + row.asset() + " ends " + bone + " on " + Arrays.toString(got) + " looking "
								+ Arrays.toString(look) + " where its " + row.idle() + " starts on " + Arrays.toString(want));
						break;
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 kept the bones a pose did not name on the idle pose, and the converted clips"
				+ " hand over to their idle: " + wrong);
		helper.succeed();
	}

	// SilverChariotModel :252-270, barrageRightStart and barrageRightImpact: the rapier arm and its forearm
	private static final String[] STAB_BONES = { "right_arm", "right_arm_bend" };
	private static final float[][] STAB_START = { { 89, 0, 90 }, { -135, 0, 0 } };
	private static final float[][] STAB_IMPACT = { { -90, 45, 0 }, { 0, 0, 0 } };
	// the bones the stab poses and the idle move, held over the recovery's first 75%
	private static final String[] STAB_HELD = { "right_arm", "right_arm_bend", "right_arm_joint", "rapier", "left_arm",
			"left_arm_bend", "left_arm_joint", "torso_bend", "body_rot", "head" };

	/*
	 * 1.16's rapier stab (SilverChariotModel :280-288) eased its windup from barrageStabStart to barrageStabImpact by
	 * sw < 0.75 ? 16 / 9 * sw * sw - 8 / 3 * sw + 1 : 16 * sw * sw - 24 * sw + 9, with Java's integer division (1 - sw)^2
	 * and then (4sw - 3)^2: the arm starts extended on the impact, draws back to the start pose by 0.75 of the windup and
	 * thrusts to the impact by its end; the recovery's transition to the idle, eased max(4(pr - 1) + 1, 0), held the
	 * thrust over 75% of the recovery and then returned linearly.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void silverChariotsRapierStabWindsUpAndHoldsItsThrustAs116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		JsonObject clip = clipOf(helper, new HashMap<>(), SC_ANIMS, "light_attack");
		float perform = marker(clip, "phase=PERFORM;"), recovery = marker(clip, "phase=RECOVERY;");
		float end = clip.get("animation_length").getAsFloat();
		float[] level = { 0, 0 };
		for (float sw : new float[] { 0, 0.1F, 0.25F, 0.4F, 0.5F, 0.6F, 0.7F, 0.74F, 0.76F, 0.8F, 0.9F, 0.95F, 1 }) {
			float w = sw < 0.75F ? (1 - sw) * (1 - sw) : (4 * sw - 3) * (4 * sw - 3);
			for (int b = 0; b < STAB_BONES.length; b++) {
				float[] got = rotationAt(clip, STAB_BONES[b], sw * perform, level);
				for (int axis = 0; axis < 3; axis++) {
					float want = STAB_START[b][axis] + (STAB_IMPACT[b][axis] - STAB_START[b][axis]) * w;
					if (Math.abs(got[axis] - want) > 2) {
						wrong.add(STAB_BONES[b] + " at " + sw + " of the windup " + Arrays.toString(got) + " where 1.16 had " + want + " on axis "
								+ axis + " (" + w + " of the impact)");
						break;
					}
				}
			}
		}
		for (String bone : STAB_HELD) {
			float[] thrust = rotationAt(clip, bone, recovery, level);
			float[] last = rotationAt(clip, bone, end, level);
			for (float pr : new float[] { 0.25F, 0.5F, 0.74F, 0.875F }) {
				float[] got = rotationAt(clip, bone, recovery + pr * (end - recovery), level);
				float f = Math.max(4 * (pr - 1) + 1, 0);
				for (int axis = 0; axis < 3; axis++) {
					float want = thrust[axis] + (last[axis] - thrust[axis]) * f;
					if (Math.abs(got[axis] - want) > 0.05F) {
						wrong.add(bone + " at " + pr + " of the recovery " + Arrays.toString(got) + " where 1.16 had " + f
								+ " of the way from the thrust " + Arrays.toString(thrust) + " to " + Arrays.toString(last));
						break;
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "Silver Chariot's rapier stab winds up and recovers on 1.16's curves: " + wrong);
		helper.succeed();
	}

	/*
	 * Crazy Diamond's heavy punch and finisher are the official 1.21.1 port's clips (OfficialClips1211GameTests); this
	 * checks how they meet the clips around them. The heavy punch keeps body_rot within half a turn of the idle's yaw and
	 * turns smoothly at every clip time, so an action ended early never leaves the body a turn away from the next clip,
	 * and the punch that may follow it does not start from its pose (StandEntityPunch.savePrevPoseForAnimTransition: only
	 * a punch after a punch does); no two keys of either clip lie under a millisecond apart unless they are adjacent
	 * floats (Float.parseFloat reads the port's key times), so no clip time draws a pose between them.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void crazyDiamondsHeavyPunchAndFinisherLeaveNoLongTurnAndNoInBetweenFrame(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		JsonObject anims = readJson(helper, CD_ANIMS).getAsJsonObject("animations");
		JsonObject heavy = anims.getAsJsonObject("heavy_punch");
		float[] level = { 0, 0 };
		float idleYaw = rotationAt(anims.getAsJsonObject("idle"), "body_rot", 0, level)[1];
		float end = heavy.get("animation_length").getAsFloat();
		List<Float> times = new ArrayList<>();
		for (float time = 0; time <= end; time += 0.0005F) {
			times.add(time);
		}
		keys(heavy, "body_rot").forEach(key -> times.add(key.getKey()));
		times.sort(null);
		float farthest = 0, farthestAt = 0, fastest = 0, fastestAt = 0;
		float[] prev = null;
		float prevAt = 0;
		for (float time : times) {
			float[] body = rotationAt(heavy, "body_rot", time, level);
			if (Math.abs(body[1] - idleYaw) > farthest) {
				farthest = Math.abs(body[1] - idleYaw);
				farthestAt = time;
			}
			if (prev != null && time > prevAt) {
				float turn = (float) angleBetween(rotationMatrix(prev), rotationMatrix(body));
				if (turn > fastest) {
					fastest = turn;
					fastestAt = time;
				}
			}
			prev = body;
			prevAt = time;
		}
		if (farthest > 180.01F) {
			wrong.add("heavy_punch's body_rot yaw is " + farthest + " degrees from the idle's " + idleYaw + " at " + farthestAt);
		}
		if (fastest > 2) {
			wrong.add("heavy_punch's body turns " + fastest + " degrees within half a millisecond at " + fastestAt);
		}
		for (String name : new String[] { "heavy_punch", "finisher" }) {
			JsonObject clip = anims.getAsJsonObject(name);
			for (String bone : clip.getAsJsonObject("bones").keySet()) {
				List<Map.Entry<Float, JsonElement>> keys = keys(clip, bone);
				for (int i = 1; i < keys.size(); i++) {
					float t0 = keys.get(i - 1).getKey(), t1 = keys.get(i).getKey();
					if (t1 - t0 < 0.001F && t1 != Math.nextUp(t0)) {
						wrong.add(name + " " + bone + " jumps between " + t0 + " and " + t1 + ", where a frame can fall");
					}
				}
			}
		}
		EntityActionInstance heavyAction = actionOf(helper, "heavy_punch");
		EntityActionInstance punchAction = actionOf(helper, "punch");
		if (punchAction.savePrevPoseForAnimTransition(heavyAction) || !punchAction.savePrevPoseForAnimTransition(punchAction)) {
			wrong.add("Crazy Diamond's punch starts from the heavy punch's pose (or not from a punch's)");
		}
		helper.assertTrue(wrong.isEmpty(), "Crazy Diamond's heavy punch and finisher meet the clips around them without a long"
				+ " turn or an in-between frame: " + wrong);
		helper.succeed();
	}

	private static final String SP_ANIMS =
			"stand_skins/star_platinum/assets/jojo_ripples/animations/star_platinum.animation.json";
	// 1.16 star_platinum.animation.json uppercut: body (a root child at the neck) and upperPart (under the body, at the
	// waist), time, angles, the easing into the key
	private static final float[] UPPERCUT_BODY_116 = { -4.73827F, -22.58344F, -0.01776F };
	private static final float[] UPPERCUT_UPPER_PART_TIMES = { 0, 0.4F, 0.45F, 0.8F, 1.1F };
	private static final float[] UPPERCUT_UPPER_PART_YAWS = { 0, 15, -30, -30, 0 };
	private static final boolean[] UPPERCUT_UPPER_PART_CUBIC = { false, false, true, false, false };

	/*
	 * 1.16's Star Platinum played star_platinum.animation.json's uppercut for its finisher: the body, pivoting at the neck,
	 * held its lean and twist while the upperPart, pivoting at the waist under it, wound up to 15 degrees and snapped to
	 * -30 (easeInCubic) on PERFORM; the head, a root child, kept the look. The port's uppercut clips carry the body on
	 * body_rot (the neck pivot) and the upperPart on torso (the waist pivot), the port-only body and torso_bend at 0, and
	 * the head on the look under that chain.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void starPlatinumsUppercutTurnsAt116sNeckAndWaist(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		JsonObject anims = readJson(helper, SP_ANIMS).getAsJsonObject("animations");
		float[] level = { 0, 0 };
		for (String name : new String[] { "finisher_uppercut", "uppercut" }) {
			JsonObject clip = anims.getAsJsonObject(name);
			helper.assertTrue(clip != null && clip.has("bones"), SP_ANIMS + " lost " + name);
			List<String> off = new ArrayList<>();
			for (int step = 0; step <= 110; step++) {
				float time = step / 100F;
				float upper = UPPERCUT_UPPER_PART_YAWS[0];
				for (int i = 1; i < UPPERCUT_UPPER_PART_TIMES.length; i++) {
					if (time <= UPPERCUT_UPPER_PART_TIMES[i] + 0.00001F) {
						float f = (time - UPPERCUT_UPPER_PART_TIMES[i - 1]) / (UPPERCUT_UPPER_PART_TIMES[i] - UPPERCUT_UPPER_PART_TIMES[i - 1]);
						f = UPPERCUT_UPPER_PART_CUBIC[i] ? f * f * f : f;
						upper = UPPERCUT_UPPER_PART_YAWS[i - 1] + (UPPERCUT_UPPER_PART_YAWS[i] - UPPERCUT_UPPER_PART_YAWS[i - 1]) * f;
						break;
					}
				}
				float[][] expected = { UPPERCUT_BODY_116, { 0, upper, 0 }, new float[3], new float[3] };
				String[] bones = { "body_rot", "torso", "body", "torso_bend" };
				for (int b = 0; b < bones.length; b++) {
					float[] at = rotationAt(clip, bones[b], time, level);
					for (int axis = 0; axis < 3; axis++) {
						if (Math.abs(at[axis] - expected[b][axis]) > 0.01F) {
							off.add(bones[b] + " at " + time + " " + Arrays.toString(at) + " where 1.16 had " + Arrays.toString(expected[b]));
							break;
						}
					}
				}
				for (float[] look : new float[][] { level, LOOK }) {
					double head = angleBetween(composedHead(clip, time, look), rotationMatrix(new float[] { look[0], look[1], 0 }));
					if (head > 1) {
						off.add("head at " + time + " looking " + Arrays.toString(look) + " " + String.format("%.1f", head) + " degrees off the look");
					}
				}
			}
			if (!off.isEmpty()) {
				wrong.add(name + " (" + off.size() + " samples): " + off.subList(0, Math.min(6, off.size())));
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's uppercut turned the body at the neck and the upperPart at the waist, the head on the look: "
				+ wrong);
		helper.succeed();
	}

	private static EntityActionInstance actionOf(GameTestHelper helper, String abilityName) {
		Ability ability = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond")).getBaseMoveset().getAbility(abilityName);
		helper.assertTrue(ability instanceof EntityActionAbility, "setup: Crazy Diamond has no " + abilityName);
		EntityActionInstance action = ((EntityActionAbility) ability).initActionOnAbilityUse(helper.getLevel(), null, null, null);
		helper.assertTrue(action != null, "setup: " + abilityName + " made no action");
		return action;
	}

	private static JsonObject clipOf(GameTestHelper helper, Map<String, JsonObject> files, String asset, String name) {
		JsonObject clip = files.computeIfAbsent(asset, a -> readJson(helper, a)).getAsJsonObject("animations").getAsJsonObject(name);
		helper.assertTrue(clip != null && clip.has("bones"), asset + " lost " + name);
		return clip;
	}

	private static String idleOf(HeadPose pose) {
		return pose.asset().equals(SC_ANIMS) && pose.clip().startsWith("no_rapier") ? "no_rapier_idle" : "idle";
	}

	// the head's rotation with its whole chain at a clip time, the look queries evaluated for the given look
	private static double[][] composedHead(JsonObject clip, float time, float[] look) {
		double[][] composed = rotationMatrix(new float[3]);
		for (String bone : HEAD_CHAIN) {
			composed = multiply(composed, rotationMatrix(rotationAt(clip, bone, time, look)));
		}
		return composed;
	}

	static float clipTime(JsonObject clip, String at) {
		float performAt = marker(clip, "phase=PERFORM;");
		return switch (at) {
			case "PERFORM" -> performAt;
			case "RECOVERY" -> marker(clip, "phase=RECOVERY;");
			case "END" -> clip.get("animation_length").getAsFloat();
			default -> at.startsWith("W") ? Float.parseFloat(at.substring(1)) * performAt
					: at.startsWith("R") ? marker(clip, "phase=RECOVERY;") + Float.parseFloat(at.substring(1))
							* (clip.get("animation_length").getAsFloat() - marker(clip, "phase=RECOVERY;"))
					: Float.parseFloat(at);
		};
	}

	// the angles of a ModelPart rotation Rz * Ry * Rx, in degrees
	static float[] eulerOf(double[][] m) {
		double y = Math.asin(Math.max(-1, Math.min(1, -m[2][0])));
		return new float[] { (float) Math.toDegrees(Math.atan2(m[2][1], m[2][2])), (float) Math.toDegrees(y),
				(float) Math.toDegrees(Math.atan2(m[1][0], m[0][0])) };
	}

	static float marker(JsonObject clip, String instruction) {
		if (!clip.has("timeline")) {
			return Float.NaN;
		}
		for (Map.Entry<String, JsonElement> marker : clip.getAsJsonObject("timeline").entrySet()) {
			JsonElement value = marker.getValue();
			List<JsonElement> lines = new ArrayList<>();
			if (value.isJsonArray()) {
				value.getAsJsonArray().forEach(lines::add);
			}
			else {
				lines.add(value);
			}
			for (JsonElement line : lines) {
				if (line.isJsonPrimitive() && instruction.equals(line.getAsString().replace(" ", ""))) {
					return Float.parseFloat(marker.getKey());
				}
			}
		}
		return Float.NaN;
	}

	// a bone's keys in time order: the time as Float.parseFloat reads it, and the key
	static List<Map.Entry<Float, JsonElement>> keys(JsonObject clip, String boneName) {
		JsonObject bone = clip.getAsJsonObject("bones").getAsJsonObject(boneName);
		List<Map.Entry<Float, JsonElement>> keys = new ArrayList<>();
		if (bone == null || !bone.has("rotation")) {
			return keys;
		}
		JsonElement rotation = bone.get("rotation");
		if (rotation.isJsonArray() || rotation.getAsJsonObject().has("vector")) {
			keys.add(Map.entry(0F, rotation));
			return keys;
		}
		for (Map.Entry<String, JsonElement> key : rotation.getAsJsonObject().entrySet()) {
			keys.add(Map.entry(Float.parseFloat(key.getKey()), key.getValue()));
		}
		keys.sort(Map.Entry.comparingByKey());
		return keys;
	}

	// a bone's rotation at a clip time as RotpAnimDefinition.lerpKeyframes samples it (each segment eased by the key it
	// moves to), the queries evaluated for the look (pitch, yaw); a bone the clip leaves out is 0
	static float[] rotationAt(JsonObject clip, String boneName, float time, float[] look) {
		List<Map.Entry<Float, JsonElement>> keys = keys(clip, boneName);
		if (keys.isEmpty()) {
			return new float[3];
		}
		if (time <= keys.get(0).getKey()) {
			return values(keys.get(0).getValue(), look);
		}
		for (int i = 1; i < keys.size(); i++) {
			float t1 = keys.get(i).getKey();
			if (time <= t1) {
				float t0 = keys.get(i - 1).getKey();
				float f = (time - t0) / (t1 - t0);
				JsonElement to = keys.get(i).getValue();
				String easing = to.isJsonObject() && to.getAsJsonObject().has("easing")
						? to.getAsJsonObject().get("easing").getAsString() : "linear";
				f = switch (easing) {
					case "linear" -> f;
					case "easeInQuad" -> f * f;
					case "easeInCubic" -> f * f * f;
					case "easeInSine" -> 1 - (float) Math.cos(f * Math.PI / 2);
					case "easeOutSine" -> (float) Math.sin(f * Math.PI / 2);
					case "easeInOutSine" -> (float) (-(Math.cos(Math.PI * f) - 1) / 2);
					default -> throw new IllegalArgumentException(boneName + ": easing " + easing + " not sampled");
				};
				float[] a = values(keys.get(i - 1).getValue(), look);
				float[] b = values(to, look);
				return new float[] { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f };
			}
		}
		return values(keys.get(keys.size() - 1).getValue(), look);
	}

	private static float[] values(JsonElement key, float[] look) {
		if (key.isJsonObject()) {
			key = key.getAsJsonObject().has("vector") ? key.getAsJsonObject().get("vector") : key.getAsJsonObject().get("post");
		}
		JsonArray vector = key.getAsJsonArray();
		float[] out = new float[3];
		for (int i = 0; i < 3; i++) {
			JsonElement value = vector.get(i);
			out[i] = value.getAsJsonPrimitive().isNumber() ? value.getAsFloat() : (float) new Expression(value.getAsString(), look).sum();
		}
		return out;
	}

	// the arithmetic of the clips' Molang values: numbers, + - * /, parentheses, math.min / max / abs and the look queries
	private static final class Expression {
		private final String text;
		private final float[] look;
		private int at;

		Expression(String text, float[] look) {
			this.text = text.replace(" ", "");
			this.look = look;
		}

		double sum() {
			double value = product();
			while (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
				value = text.charAt(at++) == '+' ? value + product() : value - product();
			}
			return value;
		}

		private double product() {
			double value = unary();
			while (at < text.length() && (text.charAt(at) == '*' || text.charAt(at) == '/')) {
				value = text.charAt(at++) == '*' ? value * unary() : value / unary();
			}
			return value;
		}

		private double unary() {
			if (text.charAt(at) == '-') {
				at++;
				return -unary();
			}
			if (text.charAt(at) == '(') {
				at++;
				double value = sum();
				at++;
				return value;
			}
			int start = at;
			while (at < text.length() && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '.' || text.charAt(at) == '_')) {
				at++;
			}
			String word = text.substring(start, at);
			switch (word) {
				case "query.head_x_rotation": return look[0];
				case "query.head_y_rotation": return look[1];
				case "math.min", "math.max", "math.abs": {
					at++;
					double a = sum();
					double b = 0;
					if (text.charAt(at) == ',') {
						at++;
						b = sum();
					}
					at++;
					return word.equals("math.min") ? Math.min(a, b) : word.equals("math.max") ? Math.max(a, b) : Math.abs(a);
				}
				default: return Double.parseDouble(word);
			}
		}
	}

	// a ModelPart's rotation, Rz * Ry * Rx, from degrees
	static double[][] rotationMatrix(float[] degrees) {
		double x = Math.toRadians(degrees[0]), y = Math.toRadians(degrees[1]), z = Math.toRadians(degrees[2]);
		double[][] rx = { { 1, 0, 0 }, { 0, Math.cos(x), -Math.sin(x) }, { 0, Math.sin(x), Math.cos(x) } };
		double[][] ry = { { Math.cos(y), 0, Math.sin(y) }, { 0, 1, 0 }, { -Math.sin(y), 0, Math.cos(y) } };
		double[][] rz = { { Math.cos(z), -Math.sin(z), 0 }, { Math.sin(z), Math.cos(z), 0 }, { 0, 0, 1 } };
		return multiply(rz, multiply(ry, rx));
	}

	static double[][] multiply(double[][] a, double[][] b) {
		double[][] out = new double[3][3];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				for (int k = 0; k < 3; k++) {
					out[i][j] += a[i][k] * b[k][j];
				}
			}
		}
		return out;
	}

	// the angle in degrees of the rotation taking a to b
	static double angleBetween(double[][] a, double[][] b) {
		double trace = 0;
		for (int i = 0; i < 3; i++) {
			for (int k = 0; k < 3; k++) {
				trace += a[k][i] * b[k][i];
			}
		}
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (trace - 1) / 2))));
	}

	static JsonObject readJson(GameTestHelper helper, String assetPath) {
		try (InputStream in = StandClipPoses116GameTests.class.getResourceAsStream("/assets/jojo_ripples/" + assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + assetPath, e);
		}
	}
}
