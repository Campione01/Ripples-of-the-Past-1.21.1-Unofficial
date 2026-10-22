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
 * The core clips the official 1.21.1 port lacks, against the phase structure, poses and easing 1.16 drew them with, and
 * their hand-over to the Stand's idle. Clip times are read from the clips' phase markers, which the port plays phase by
 * phase (FIT_PHASE_LENGTH): a fraction of a 1.16 phase is the same fraction of the clip's phase.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreClipPhases116GameTests {
	private CoreClipPhases116GameTests() {}

	private static final String MR_ANIMS =
			"stand_skins/magicians_red/assets/jojo_ripples/animations/magicians_red.animation.json";
	private static final float[] LEVEL = { 0, 0 };
	private static final float[] LOOK = { 20, -35 };

	// a 1.16 pose a bone reaches at a clip time ("0", W/P/R<f> a fraction of the windup, perform or recovery, PERFORM,
	// RECOVERY, END), eased into by t to the given power (ModelPose's easing), or the idle clip's t=0 value (angles null)
	private record Key(String at, float[] angles, int power) {}

	private static Key key(String at, float[] angles, int power) {
		return new Key(at, angles, power);
	}

	private static float[] deg(float x, float y, float z) {
		return new float[] { x, y, z };
	}

	// HumanoidStandModel :449-480, jabRightAngles1-4 on the port's bones (body -> body_rot, upperPart -> torso_bend,
	// forearms -> *_arm_bend), in that order
	private static final String[] JAB_BONES = { "body_rot", "torso_bend", "left_arm", "left_arm_bend", "right_arm", "right_arm_bend" };
	private static final float[][][] JAB = {
			{ deg(0, 0, 0), deg(0, -15, 0), deg(-7.5F, 0, -15), deg(-100, 15, 7.5F), deg(22.5F, 0, 22.5F), deg(-105, 0, -15) },
			{ deg(0, -5, 0), deg(0, -20, 0), deg(30, 0, -15), deg(-107.5F, 15, 7.5F), deg(5.941F, 8.4211F, 69.059F), deg(-75, 0, 0) },
			{ deg(0, -12.5F, 0), deg(0, -17.5F, 0), deg(37.5F, 0, -15), deg(-115, 15, 7.5F), deg(-81.9244F, 11.0311F, 70.2661F), deg(0, 0, 0) },
			{ deg(0, -3.75F, 0), deg(0, -3.75F, 0), deg(5.63F, 0, -20.62F), deg(-103.75F, 3.75F, 13.13F), deg(5.941F, 8.4211F, 69.059F), deg(-75, 0, 0) } };

	// jabEnd of the right jab: mirrorAngles(jabRightAngles1), the other limb with y and z negated
	private static float[] jabEnd(int bone) {
		int from = switch (JAB_BONES[bone]) {
			case "left_arm" -> 4;
			case "left_arm_bend" -> 5;
			case "right_arm" -> 2;
			case "right_arm_bend" -> 3;
			default -> bone;
		};
		float[] a = JAB[0][from];
		return deg(a[0], -a[1], -a[2]);
	}

	/*
	 * 1.16's jab (HumanoidStandModel :499-536, the right jab; the port mirrors the clip for the other hand): over the
	 * windup jabStart lerped to jabArmTurn by .5, to jabImpact by .75 eased x^3 and held; over the perform the impact held
	 * to .25, turned back to jabArmTurnBack by .5 eased x^3 and lerped to jabEnd, the mirrored start, by the perform's end;
	 * the recovery held jabEnd to .75 and lerped to the idle. Every jab bone is sampled against that through the clip.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void magiciansRedsJabRunsThroughItsPosesOn116sPhaseFractions(GameTestHelper helper) {
		Map<String, JsonObject> files = new HashMap<>();
		JsonObject clip = clipOf(helper, files, MR_ANIMS, "punch");
		JsonObject idle = clipOf(helper, files, MR_ANIMS, "idle");
		List<String> wrong = new ArrayList<>();
		for (int b = 0; b < JAB_BONES.length; b++) {
			List<Key> track = List.of(key("0", JAB[0][b], 1), key("W0.5", JAB[1][b], 1), key("W0.75", JAB[2][b], 3),
					key("PERFORM", JAB[2][b], 1), key("P0.25", JAB[2][b], 1), key("P0.5", JAB[3][b], 3),
					key("RECOVERY", jabEnd(b), 1), key("R0.75", jabEnd(b), 1), key("END", null, 1));
			compare(clip, idle, JAB_BONES[b], track, 0.1F, wrong);
		}
		helper.assertTrue(wrong.isEmpty(), "Magician's Red's jab runs through 1.16's jab poses on its phase fractions: " + wrong);
		helper.succeed();
	}

	// the clip's bone against a 1.16 track sampled every 2 ms and at the track's times, at a level look
	private static void compare(JsonObject clip, JsonObject idle, String bone, List<Key> track, float tolerance, List<String> wrong) {
		float end = clip.get("animation_length").getAsFloat();
		List<Float> times = new ArrayList<>();
		for (float time = 0; time < end; time += 0.002F) {
			times.add(time);
		}
		track.forEach(k -> times.add(at(clip, k.at())));
		times.add(end);
		int off = 0;
		String first = null;
		for (float time : times) {
			float[] want = expected(clip, idle, bone, track, time);
			float[] got = StandClipPoses116GameTests.rotationAt(clip, bone, time, LEVEL);
			for (int axis = 0; axis < 3; axis++) {
				if (Math.abs(got[axis] - want[axis]) > tolerance) {
					off++;
					if (first == null) {
						first = "at " + time + " " + Arrays.toString(got) + " where 1.16 had " + Arrays.toString(want);
					}
					break;
				}
			}
		}
		if (off > 0) {
			wrong.add(bone + " off 1.16's track at " + off + " of " + times.size() + " samples, first " + first);
		}
	}

	private static float[] expected(JsonObject clip, JsonObject idle, String bone, List<Key> track, float time) {
		float[] prev = angles(idle, bone, track.get(0));
		float prevAt = at(clip, track.get(0).at());
		if (time <= prevAt) {
			return prev;
		}
		for (int i = 1; i < track.size(); i++) {
			float[] next = angles(idle, bone, track.get(i));
			float nextAt = at(clip, track.get(i).at());
			if (time <= nextAt) {
				float f = nextAt > prevAt ? (float) Math.pow((time - prevAt) / (nextAt - prevAt), track.get(i).power()) : 1;
				return deg(prev[0] + (next[0] - prev[0]) * f, prev[1] + (next[1] - prev[1]) * f, prev[2] + (next[2] - prev[2]) * f);
			}
			prev = next;
			prevAt = nextAt;
		}
		return prev;
	}

	private static float[] angles(JsonObject idle, String bone, Key key) {
		return key.angles() != null ? key.angles() : StandClipPoses116GameTests.rotationAt(idle, bone, 0, LEVEL);
	}

	// a clip time: "0", seconds, PERFORM, RECOVERY, END, or W/P/R<f> a fraction of the windup, the perform or the recovery
	static float at(JsonObject clip, String at) {
		float perform = StandClipPoses116GameTests.marker(clip, "phase=PERFORM;");
		float recovery = StandClipPoses116GameTests.marker(clip, "phase=RECOVERY;");
		float end = clip.get("animation_length").getAsFloat();
		return switch (at.charAt(0)) {
			case 'W' -> Float.parseFloat(at.substring(1)) * perform;
			case 'P' -> at.equals("PERFORM") ? perform : perform + Float.parseFloat(at.substring(1)) * (recovery - perform);
			case 'R' -> at.equals("RECOVERY") ? recovery : recovery + Float.parseFloat(at.substring(1)) * (end - recovery);
			case 'E' -> end;
			default -> Float.parseFloat(at);
		};
	}

	private static JsonObject clipOf(GameTestHelper helper, Map<String, JsonObject> files, String asset, String name) {
		JsonObject clip = files.computeIfAbsent(asset, a -> StandClipPoses116GameTests.readJson(helper, a))
				.getAsJsonObject("animations").getAsJsonObject(name);
		helper.assertTrue(clip != null && clip.has("bones"), asset + " lost " + name);
		return clip;
	}
}
