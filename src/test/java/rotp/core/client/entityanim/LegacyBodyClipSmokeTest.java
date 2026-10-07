package rotp.core.client.entityanim;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.joml.Vector3f;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimFramePose.ModelPartFrame;

/**
 * Loads the converted 1.16 Hamon and Pillar Man player clips with the production parser and checks the
 * evaluated pose. 1.16 keyed the legacy KosmX "torso" part, which the library applies to the whole body
 * before the model flip (blocks, y up, radians). At runtime the port "body" bone is in model space:
 * position px = (-16x, -16y, 16z), rotation rad = (-pitch, -yaw, roll). Expected values are raw 1.16 keys.
 */
public final class LegacyBodyClipSmokeTest {
	private static final Path ANIMATIONS = Path.of("src/main/resources/assets/jojo_ripples/animations");

	private LegacyBodyClipSmokeTest() {}

	public static void main(String[] args) throws IOException {
		KeyframesMolangEngine.init();
		Map<String, RotpAnimDefinition> pillarMan = load("pillar_man.animation.json");
		Map<String, RotpAnimDefinition> hamon = load("hamon.animation.json");
		check(pillarMan.size() == 17, "pillar_man.animation.json keeps its 17 clips, parsed " + pillarMan.size());

		int poses = noUpperBodyTransform("pillar_man", pillarMan) + noUpperBodyTransform("hamon", hamon);

		// pillarman/evasion.json tick 85: the dodge carries the whole body 0.66 block sideways
		body(pillarMan, "evasion", 85, 0.6615185737609863F, -0.2223183661699295F, 0F,
				3.196132183074951F, 3.339398145675659F, -3.1467678546905518F);
		// pillarman/blade_dash.json tick 25, pillarman/pillar_man_possession.json tick 20
		body(pillarMan, "blade_dash", 25, 0F, -0.15447068214416504F, -0.8173444271087646F,
				-0.7521454095840454F, 1.0749298334121704F, -0.433560311794281F);
		body(pillarMan, "pillar_man_possession", 20, 0F, 0.11868451535701752F, -1.7331905364990234F,
				-1.9848747253417969F, 0F, 0F);
		// pillarman/stone_form_1.json keys the body at tick 15 only: tick 7.5 is halfway in from the rest pose
		body(pillarMan, "stone_form_1", 7.5F, 0F, 0F, 0.4024839401245117F / 2, 0F, 0.4612164795398712F / 2, 0F);
		body(pillarMan, "stone_form_1", 15, 0F, 0F, 0.4024839401245117F, 0F, 0.4612164795398712F, 0F);

		// hamon/wall_climb/wall_climb_right.json tick 5, hamon/punch_barrage.json ticks 2 and 4
		body(hamon, "wall_climb_right", 5, 0F, 0F, 0F, 0.028832439333200455F, -0.2834850251674652F, -0.09126254171133041F);
		body(hamon, "punch_barrage", 2, 0F, 0F, 0F, 0F, 0.5235987901687622F, 0F);
		body(hamon, "punch_barrage", 4, 0F, 0F, 0F, 0F, -0.5235987901687622F, 0F);
		// hamon/syo_barrage_start.json tick 2, hamon/syo_barrage_finisher.json ticks 6 and 20 (last key held)
		body(hamon, "syo_barrage_start", 2, 0F, 0F, 0F, -0.1745329201221466F, -0.0872664600610733F, -0.0872664600610733F);
		body(hamon, "syo_barrage_finisher", 6, 0F, 0F, 0F, 0F, 2.356194496154785F, 0F);
		body(hamon, "syo_barrage_finisher", 20, 0F, 0F, 0F, 0F, 2.572615385055542F, 0F);
		// limbs stay in model space as keyed: syo_barrage_start.json leftLeg and its bend at tick 2
		limb(hamon, "syo_barrage_start", 2, "left_leg", -0.2588479816913605F, -0.2855724096298218F, -0.25303441286087036F);
		limb(hamon, "syo_barrage_start", 2, "left_leg_bend", 0.5293871760368347F, 0F, 0F);
		limb(hamon, "punch_barrage", 4, "right_arm_bend", -2.356194496154785F, 0F, 0F);

		int barragePoses = punchBarragePlaysLike116(hamon);

		System.out.println("Legacy body clip smoke test passed: " + (pillarMan.size() + hamon.size())
				+ " clips parsed, " + poses + " poses without an upper-body transform, 14 donor anchors, "
				+ barragePoses + " punch_barrage poses on the 1.16 ping-pong clock");
	}

	/**
	 * 1.16 KosmXPlayerBarrageAnim on hamon/punch_barrage.json (returnTick 2, endTick 4, tracks keyed at ticks 2 and 4
	 * with EASEINOUTCUBIC): clip ticks 0 to 2 ease in from the rest pose (the library's INOUTSINE), then the clip
	 * runs 2+pt, 3+pt, 4, 3, 2 and repeats.
	 */
	private static int punchBarragePlaysLike116(Map<String, RotpAnimDefinition> hamon) {
		RotpAnimDefinition clip = hamon.get("punch_barrage");
		check(clip != null, "missing clip punch_barrage");
		int poses = 0;
		int currentTick = 0;
		for (int tick = 0; tick < 14; tick++) {
			for (float partial : new float[] { 0F, 0.25F, 0.5F, 0.75F }) {
				float clipTick = currentTick >= 4 ? 8 - currentTick : currentTick + partial;
				AnimFramePose pose = clip.calcAnimPose(null, null, clip.getAnimTime(tick + partial), 1,
						new AnimFramePose(), new Vector3f());
				String at = "punch_barrage at action tick " + (tick + partial) + " (1.16 clip tick " + clipTick + ") ";
				barrageTrack(pose, "body", 1, -key(clipTick, 0.5235987901687622F, -0.5235987901687622F), at + "body yaw");
				barrageTrack(pose, "right_arm", 0, key(clipTick, -1.5707963705062866F, 0.39269909262657166F), at + "right arm pitch");
				barrageTrack(pose, "right_arm", 2, key(clipTick, 1.5707963705062866F, 1.0471975803375244F), at + "right arm roll");
				barrageTrack(pose, "right_arm_bend", 0, key(clipTick, 0F, -2.356194496154785F), at + "right arm bend");
				barrageTrack(pose, "left_arm", 0, key(clipTick, 0.39269909262657166F, -1.5707963705062866F), at + "left arm pitch");
				barrageTrack(pose, "left_arm_bend", 0, key(clipTick, -2.35270357131958F, 0F), at + "left arm bend");
				barrageTrack(pose, "left_leg", 0, key(clipTick, -0.5235987901687622F, -0.2617993950843811F), at + "left leg pitch");
				poses++;
			}
			currentTick++;
			if (currentTick > 2 + (4 - 2) * 2) {
				currentTick = 2;
			}
		}
		return poses;
	}

	private static float key(float clipTick, float atTick2, float atTick4) {
		if (clipTick < 2) {
			return atTick2 * (float) ((1 - Math.cos(Math.PI * clipTick / 2)) / 2);
		}
		float f = (clipTick - 2) / 2;
		float eased = (float) (f < 0.5F ? 4 * f * f * f : 1 - Math.pow(-2 * f + 2, 3) / 2);
		return atTick2 + (atTick4 - atTick2) * eased;
	}

	private static void barrageTrack(AnimFramePose pose, String bone, int axis, float expected, String label) {
		ModelPartFrame frame = pose.getIfPresent(bone);
		check(frame != null, label + ": no " + bone + " pose");
		// the port's sine easing reads Mth's sine table
		near(frame.rotationOffset.get(axis), expected, 0.0005F, label + " (rad)");
	}

	private static Map<String, RotpAnimDefinition> load(String file) throws IOException {
		Map<String, RotpAnimDefinition> clips = new LinkedHashMap<>();
		try (Reader reader = Files.newBufferedReader(ANIMATIONS.resolve(file), StandardCharsets.UTF_8)) {
			JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
			for (Map.Entry<String, JsonElement> clip : animations.entrySet()) {
				clips.put(clip.getKey(), ParseGeckoAnims.parseAnim(clip.getValue().getAsJsonObject()));
			}
		}
		return clips;
	}

	// 1.16 never moved the upper body alone: the port "torso" bone stays untouched in every clip
	private static int noUpperBodyTransform(String file, Map<String, RotpAnimDefinition> clips) {
		int poses = 0;
		AnimFramePose scratch = new AnimFramePose();
		Vector3f target = new Vector3f();
		for (Map.Entry<String, RotpAnimDefinition> clip : clips.entrySet()) {
			RotpAnimDefinition animation = clip.getValue();
			for (float seconds = 0; seconds <= animation.lengthInSeconds + 0.001F; seconds += 0.05F) {
				ModelPartFrame torso = animation.calcAnimPose(null, null, seconds, 1, scratch, target).getIfPresent("torso");
				poses++;
				if (torso == null) continue;
				float moved = torso.positionOffset.length() + torso.rotationOffset.length();
				check(moved < 1.0E-5F, file + " " + clip.getKey() + " moves the upper-body 'torso' bone at " + seconds
						+ " s (position " + torso.positionOffset + ", rotation " + torso.rotationOffset
						+ "); 1.16 moved the whole body");
			}
		}
		return poses;
	}

	private static void body(Map<String, RotpAnimDefinition> clips, String clip, float tick,
			float x, float y, float z, float pitch, float yaw, float roll) {
		ModelPartFrame frame = pose(clips, clip, tick).getIfPresent("body");
		check(frame != null, clip + " has no whole-body ('body' bone) pose at tick " + tick);
		String at = clip + " body at tick " + tick;
		near(frame.positionOffset.x(), -16 * x, 0.001F, at + " position x (px)");
		near(frame.positionOffset.y(), -16 * y, 0.001F, at + " position y (px, model y points down)");
		near(frame.positionOffset.z(), 16 * z, 0.001F, at + " position z (px)");
		near(frame.rotationOffset.x(), -pitch, 0.00002F, at + " pitch (rad)");
		near(frame.rotationOffset.y(), -yaw, 0.00002F, at + " yaw (rad)");
		near(frame.rotationOffset.z(), roll, 0.00002F, at + " roll (rad)");
	}

	private static void limb(Map<String, RotpAnimDefinition> clips, String clip, float tick, String bone,
			float pitch, float yaw, float roll) {
		ModelPartFrame frame = pose(clips, clip, tick).getIfPresent(bone);
		check(frame != null, clip + " has no " + bone + " pose at tick " + tick);
		String at = clip + " " + bone + " at tick " + tick;
		near(frame.rotationOffset.x(), pitch, 0.00002F, at + " pitch (rad)");
		near(frame.rotationOffset.y(), yaw, 0.00002F, at + " yaw (rad)");
		near(frame.rotationOffset.z(), roll, 0.00002F, at + " roll (rad)");
	}

	private static AnimFramePose pose(Map<String, RotpAnimDefinition> clips, String clip, float tick) {
		RotpAnimDefinition animation = clips.get(clip);
		check(animation != null, "missing clip " + clip);
		return animation.calcAnimPose(null, null, tick / 20, 1, new AnimFramePose(), new Vector3f());
	}

	private static void near(float actual, float expected, float tolerance, String label) {
		if (!Float.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
