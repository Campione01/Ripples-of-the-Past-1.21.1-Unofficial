package rotp.core.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
