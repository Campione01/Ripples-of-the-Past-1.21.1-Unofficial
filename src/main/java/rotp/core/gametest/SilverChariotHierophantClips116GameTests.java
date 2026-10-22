package rotp.core.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Silver Chariot's and Hierophant Green's clips the official 1.21.1 port lacks (only their idles are official) against
 * 1.16's phase structure, poses and easing (SilverChariotModel, HierophantGreenModel and HumanoidStandModel drawn by
 * LegacyStandAnimator), handing over to the official idles.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilverChariotHierophantClips116GameTests {
	private SilverChariotHierophantClips116GameTests() {}

	private static final String SC_ANIMS =
			"stand_skins/silver_chariot/assets/jojo_ripples/animations/silver_chariot.animation.json";
	private static final String HG_ANIMS =
			"stand_skins/hierophant_green/assets/jojo_ripples/animations/hierophant_green.animation.json";
	private static final float[][] LOOKS = { { 0, 0 }, { 20, -35 } };

	/*
	 * 1.16 SilverChariotModel :361-365 put StandOneHandedBarrageAnimation(stabLoop, idlePose) on the rapier barrage, and
	 * ArmsBarrageAnimation.animate posed nothing in WINDUP (LegacyStandAnimator.poseStand had applied the idle pose) and
	 * the idle pose over that idle in RECOVERY: every bone on the idle up to PERFORM and from RECOVERY on, the stab loop in
	 * between, its joints at half the forearm (HumanoidStandModel.rotateJoint).
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void silverChariotsBarrageShowsTheIdleInItsWindupAndRecoveryAs116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject clip = clipOf(helper, files, SC_ANIMS, "melee_barrage");
		JsonObject idle = clipOf(helper, files, SC_ANIMS, "idle");
		float perform = StandClipPoses116GameTests.marker(clip, "phase=PERFORM;");
		float recovery = StandClipPoses116GameTests.marker(clip, "phase=RECOVERY;");
		float end = clip.get("animation_length").getAsFloat();
		List<Float> idleTimes = new ArrayList<>();
		for (float time = 0; time < perform; time += 0.01F) {
			idleTimes.add(time);
		}
		idleTimes.add(Math.nextDown(perform));
		for (float time = recovery; time < end; time += 0.01F) {
			idleTimes.add(time);
		}
		idleTimes.add(end);
		for (String bone : idle.getAsJsonObject("bones").keySet()) {
			String first = firstOffIdle(clip, idle, bone, idleTimes);
			if (first != null) {
				wrong.add(bone + " " + first);
			}
		}
		expect(wrong, clip, "right_arm", perform, new float[] { 89, 0, 90 }, "barrageRightStart at PERFORM");
		expect(wrong, clip, "right_arm_bend", perform, new float[] { -135, 0, 0 }, "barrageRightStart at PERFORM");
		expect(wrong, clip, "torso_bend", perform, new float[] { 0, -45, 0 }, "barrageRightStart at PERFORM");
		expect(wrong, clip, "right_arm", Math.nextDown(recovery), new float[] { -90, 45, 0 }, "barrageRightImpact before RECOVERY");
		for (float time = perform; time < recovery; time += 0.01F) {
			for (String limb : new String[] { "left_arm", "right_arm" }) {
				float[] bend = StandClipPoses116GameTests.rotationAt(clip, limb + "_bend", time, LOOKS[0]);
				float[] joint = StandClipPoses116GameTests.rotationAt(clip, limb + "_joint", time, LOOKS[0]);
				if (!near(joint, new float[] { bend[0] / 2, bend[1] / 2, bend[2] / 2 }, 0.05F)) {
					wrong.add(limb + "_joint at " + time + " " + Arrays.toString(joint) + " where its bend is " + Arrays.toString(bend));
					break;
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's Silver Chariot barrage showed the idle before PERFORM and from RECOVERY on: " + wrong);
		helper.succeed();
	}

	// HierophantGreenModel :176-219, esPose1-4 (esPose4 names rightForeArm twice: the map keeps the second)
	private static final String[] ES_BONES = { "left_arm", "left_arm_bend", "right_arm", "right_arm_bend", "body_rot", "torso_bend",
			"left_leg", "left_leg_bend", "right_leg", "right_leg_bend" };
	private static final float[][] ES_POSE_1 = { { -55, 0, 15 }, { 0, -90, 45 }, { -90, 0, 75 }, { -52.5F, 0, 0 } };
	private static final float[][] ES_POSE_2 = { { -90, 0, 0 }, { 0, 0, 42.5F }, { -90, 0, 0 }, { 0, 0, -42.5F } };
	private static final float[][] ES_POSE_3 = { { -90, 0, -56.25F }, { -39.38F, 0, 10.62F }, { -63.75F, 0, -11.25F },
			{ 0, 67.5F, -44.37F }, { 0, 60, 0 }, { 0, 30, 0 }, { 0, 0, -42.5F }, { 0, 0, 120 }, { 0, 0, 15 }, { 0, 0, 0 } };
	// esPose4: the arms, the right forearm, body and upperPart; the left forearm and the legs stay on esPose3's
	private static final float[][] ES_POSE_4 = { { -90, 0, -75 }, { -39.38F, 0, 10.62F }, { -55, 0, -15 }, { 0, 90, -45 }, { 0, 0, 0 },
			{ 0, 0, 0 }, { 0, 0, -42.5F }, { 0, 0, 120 }, { 0, 0, 15 }, { 0, 0, 0 } };

	/*
	 * 1.16's Emerald Splash (HierophantGreenModel :176-219): WINDUP ModelPoseTransitionMultiple(esPose1) .6 esPose2, .85
	 * esPose3, .9 esPose3, build(esPose4) over the idle pose LegacyStandAnimator applied first, so the body, upperPart, legs
	 * and head stay the idle's to .6 (esPose1-2 name only the arms), turn into esPose3 over .6-.85 and hold it to .9;
	 * PERFORM the rigid esPose4 (PosedActionAnimation's filler) with esPose3's legs; RECOVERY the idle pose, at once.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hierophantGreensEmeraldSplashTurnsAndRecoversAs116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject idle = clipOf(helper, files, HG_ANIMS, "idle");
		for (String name : new String[] { "emerald_splash", "rangedAttack" }) {
			JsonObject clip = clipOf(helper, files, HG_ANIMS, name);
			float perform = StandClipPoses116GameTests.marker(clip, "phase=PERFORM;");
			float recovery = StandClipPoses116GameTests.marker(clip, "phase=RECOVERY;");
			float end = clip.get("animation_length").getAsFloat();
			List<Float> early = new ArrayList<>();
			for (float f = 0; f < 0.6F; f += 0.05F) {
				early.add(f * perform);
			}
			early.add(0.6F * perform);
			for (String bone : new String[] { "body_rot", "torso_bend", "left_leg", "left_leg_bend", "right_leg", "right_leg_bend",
					"head", "head_rot" }) {
				String first = firstOffIdle(clip, idle, bone, early);
				if (first != null) {
					wrong.add(name + " " + bone + " before .6 of the windup " + first);
				}
			}
			List<Float> late = new ArrayList<>();
			for (float time = recovery; time < end; time += 0.01F) {
				late.add(time);
			}
			late.add(end);
			for (String bone : idle.getAsJsonObject("bones").keySet()) {
				String first = firstOffIdle(clip, idle, bone, late);
				if (first != null) {
					wrong.add(name + " " + bone + " in the recovery " + first);
				}
			}
			for (int b = 0; b < 4; b++) {
				expect(wrong, clip, ES_BONES[b], 0, ES_POSE_1[b], name + " esPose1");
				expect(wrong, clip, ES_BONES[b], 0.6F * perform, ES_POSE_2[b], name + " esPose2");
			}
			for (int b = 0; b < ES_BONES.length; b++) {
				for (float f : new float[] { 0.85F, 0.875F, 0.9F }) {
					expect(wrong, clip, ES_BONES[b], f * perform, ES_POSE_3[b], name + " esPose3 at " + f + " of the windup");
				}
				for (float time : new float[] { perform, (perform + recovery) / 2, Math.nextDown(recovery) }) {
					expect(wrong, clip, ES_BONES[b], time, ES_POSE_4[b], name + " esPose4 in the perform");
				}
			}
			// half way through the turn the body and upperPart are half way from the idle's to esPose3's
			float idleYaw = StandClipPoses116GameTests.rotationAt(idle, "body_rot", 0, LOOKS[0])[1];
			expect(wrong, clip, "body_rot", 0.725F * perform, new float[] { 0, (idleYaw + 60) / 2, 0 }, name + " half way to esPose3");
			expect(wrong, clip, "torso_bend", 0.725F * perform, new float[] { 0, 15, 0 }, name + " half way to esPose3");
		}
		helper.assertTrue(wrong.isEmpty(), "1.16's Emerald Splash kept the idle to .6 of the windup, held esPose3 from .85 to .9 and"
				+ " showed the idle from RECOVERY: " + wrong);
		helper.succeed();
	}

	// the first sample time at which a bone is off the idle's t=0 value, at either look, or null
	private static String firstOffIdle(JsonObject clip, JsonObject idle, String bone, List<Float> times) {
		for (float[] look : LOOKS) {
			float[] want = StandClipPoses116GameTests.rotationAt(idle, bone, 0, look);
			for (float time : times) {
				float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, time, look);
				if (!near(got, want, 0.05F)) {
					return "at " + time + " looking " + Arrays.toString(look) + " " + Arrays.toString(got) + " where the idle has "
							+ Arrays.toString(want);
				}
			}
		}
		return null;
	}

	private static void expect(List<String> wrong, JsonObject clip, String bone, float time, float[] want, String what) {
		float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, time, LOOKS[0]);
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
