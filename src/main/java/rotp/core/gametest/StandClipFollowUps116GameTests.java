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
