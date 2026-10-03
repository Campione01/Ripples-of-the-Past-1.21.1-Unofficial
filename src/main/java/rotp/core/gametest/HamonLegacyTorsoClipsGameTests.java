package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Hamon clips key the legacy KosmX "torso" part, which the library renames to "body": a whole-body
 * transform applied before the model flip (offsets in blocks, y up; pitch, yaw, roll in radians).
 * The port "body" bone works in model space: position px = (-16x, 16y, 16z) (JSON y is flipped at load),
 * rotation deg = (-pitch, -yaw, roll). Expected values below are the 1.16 key values at the sampled tick.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonLegacyTorsoClipsGameTests {
	private static final float RIGHT_LEG_REST_Z = 0.1F;
	private HamonLegacyTorsoClipsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void overdrivesTurnTheWholeBodyLike116(GameTestHelper helper) {
		JsonObject anims = animations(helper);
		JsonObject scarlet = bones(helper, anims, "scarlet_overdrive");
		JsonObject syo = bones(helper, anims, "sunlight_yellow_overdrive");
		noUpperBodyTransform(helper, "scarlet_overdrive", scarlet);
		noUpperBodyTransform(helper, "sunlight_yellow_overdrive", syo);
		// scarlet_overdrive.json tick 80 (wind-up, turned left) and tick 86 (punch, turned right)
		rot(helper, "scarlet_overdrive", scarlet, 4.0F, -0.26795018F, 0.7630155F, 0F);
		rot(helper, "scarlet_overdrive", scarlet, 4.3F, -0.14839293F, -1.247923F, 0F);
		pos(helper, "scarlet_overdrive", scarlet, 4.3F, 0F, -0.01362976F, -0.44864637F);
		// sunlight_yellow_overdrive.json tick 80 (wind-up) and tick 88 (punch lunge)
		rot(helper, "sunlight_yellow_overdrive", syo, 4.0F, 0F, -1.2087338F, 0F);
		rot(helper, "sunlight_yellow_overdrive", syo, 4.4F, -0.42129365F, 0.6675723F, -0.27062008F);
		pos(helper, "sunlight_yellow_overdrive", syo, 4.4F, 0F, -0.2281622F, -0.6280594F);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonShockLeansAndLungesTheWholeBody(GameTestHelper helper) {
		JsonObject shock = bones(helper, animations(helper), "hamon_shock");
		// hamon_shock.json ticks 16 and 18: forward lean, 0.29 block lower, 0.62 block forward, no twist or roll
		rot(helper, "hamon_shock", shock, 0.8F, -0.35706747F, 0F, 0F);
		pos(helper, "hamon_shock", shock, 0.9F, 0F, -0.28849655F, -0.61901826F);
		JsonObject bend = shock.getAsJsonObject("torso_bend");
		helper.assertTrue(bend != null && bend.has("rotation"), "hamon_shock lost the 1.16 torso bend");
		float b = sample(bend.getAsJsonObject("rotation"), 0.9F)[0];
		float want = (float) Math.toDegrees(0.4215948F);
		helper.assertTrue(Math.abs(b - want) < 0.05F, "hamon_shock torso bend at 0.9 s must be " + want + " deg, got " + b);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonShockLimbRotationsUseDonorAngles(GameTestHelper helper) {
		JsonObject shock = bones(helper, animations(helper), "hamon_shock");
		// Donor ticks 1, 18 and 20: first key, perform-entry pose at 2x, last authored pose.
		float[] times = {0.05F, 0.9F, 1F};
		float[][] rightArm = {
				{0.014131877F, -0.004614539F, 0.054952923F},
				{-1.6549546F, 0.83808994F, 1.5523542F},
				{-1.6528165F, 0.7534446F, 1.553883F}};
		float[][] leftArm = {
				{0.014732392F, 0.005301654F, -0.055803373F},
				{-1.4697891F, -0.8390146F, -1.7069423F},
				{-1.4818623F, -0.77603805F, -1.698214F}};
		float[] rightLegPitch = {0.0039180415F, -0.18719684F, -0.0967175F};
		float[] leftLegPitch = {-0.05217933F, -1.6850756F, -1.6325617F};
		for (int i = 0; i < times.length; i++) {
			limbRotation(helper, shock, "right_arm", times[i], rightArm[i][0], rightArm[i][1], rightArm[i][2]);
			limbRotation(helper, shock, "left_arm", times[i], leftArm[i][0], leftArm[i][1], leftArm[i][2]);
			limbRotation(helper, shock, "right_leg", times[i], rightLegPitch[i], 0F, 0F);
			limbRotation(helper, shock, "left_leg", times[i], leftLegPitch[i], 0F, 0F);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonShockLimbPositionsAndBendsUseDonorFields(GameTestHelper helper) {
		JsonObject shock = bones(helper, animations(helper), "hamon_shock");
		float[] times = {0.05F, 0.9F, 1F};
		float[][] rightArm = {
				{-5.0323777F, 2.0016222F, 0.02936874F},
				{-5.859202F, 0.69651794F, -1.6128781F},
				{-5.859202F, 0.69651794F, -1.6128781F}};
		float[][] leftArm = {
				{5.0305576F, 2.0018673F, 0.029414179F},
				{6.5764923F, 0.61661434F, -1.3835106F},
				{6.5764923F, 0.61661434F, -1.3835106F}};
		float[] rightRest = {-5F, 2F, 0F};
		float[] leftRest = {5F, 2F, 0F};
		float[] rightLegRest = {-1.9F, 12F, RIGHT_LEG_REST_Z};
		float[] leftLegRest = {1.9F, 12F, -0.1F};
		float[] rightArmBend = {-0.030290876F, -0.6381061F, -0.6381061F};
		float[] leftArmBend = {-0.029863894F, -0.7521389F, -0.7521389F};
		float[] rightLegBend = {0.0032560327F, 1.4956671F, 1.3222065F};
		float[] leftLegBend = {0.0513011F, 1.3347887F, 1.2805724F};
		for (int i = 0; i < times.length; i++) {
			limbPosition(helper, "hamon_shock", shock, "right_arm", times[i], rightArm[i], rightRest);
			limbPosition(helper, "hamon_shock", shock, "left_arm", times[i], leftArm[i], leftRest);
			limbPosition(helper, "hamon_shock", shock, "right_leg", times[i], rightLegRest, rightLegRest);
			limbPosition(helper, "hamon_shock", shock, "left_leg", times[i], leftLegRest, leftLegRest);
			limbRotation(helper, shock, "right_arm_bend", times[i], rightArmBend[i], 0F, 0F);
			limbRotation(helper, shock, "left_arm_bend", times[i], leftArmBend[i], 0F, 0F);
			limbRotation(helper, shock, "right_leg_bend", times[i], rightLegBend[i], 0F, 0F);
			limbRotation(helper, shock, "left_leg_bend", times[i], leftLegBend[i], 0F, 0F);
		}
		// Tick 16: the donor absolute Y 0.69651794 must become JSON offset +1.30348206, not yaw.
		limbPosition(helper, "hamon_shock", shock, "right_arm", 0.8F, rightArm[1], rightRest);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonShockHeadAndPlaybackMetadataStayIntact(GameTestHelper helper) {
		JsonObject anims = animations(helper);
		JsonObject shock = bones(helper, anims, "hamon_shock");
		JsonObject clip = anims.getAsJsonObject("hamon_shock");
		helper.assertTrue(Math.abs(clip.get("animation_length").getAsFloat() - 5.05F) < 0.00001F,
				"Hamon Shock must retain its donor 101-tick clip length");
		helper.assertTrue(clip.getAsJsonObject("timeline").get("0").getAsString().replace(" ", "").equals("anim_speed=2"),
				"Hamon Shock must retain the donor handler's 2x playback");
		float[] times = {0.05F, 0.9F, 1F};
		float[] pitch = {0.032028824F, -0.81768656F, -0.73876494F};
		for (int i = 0; i < times.length; i++) {
			limbRotation(helper, shock, "head", times[i], pitch[i], 0F, 0F);
			// Every donor head position is neutral; omitting that zero channel is equivalent.
			JsonObject position = shock.getAsJsonObject("head").getAsJsonObject("position");
			if (position != null) {
				for (float value : sample(position, times[i])) {
					helper.assertTrue(Math.abs(value) < 0.00002F, "Hamon Shock head position must remain neutral");
				}
			}
		}
		helper.succeed();
	}

	private static void limbRotation(GameTestHelper helper, JsonObject bones, String bone,
			float time, float pitch, float yaw, float roll) {
		JsonObject part = bones.getAsJsonObject(bone);
		helper.assertTrue(part != null && part.has("rotation"), "hamon_shock lost " + bone + " rotation");
		float[] got = sample(part.getAsJsonObject("rotation"), time);
		float[] radians = {pitch, yaw, roll};
		for (int axis = 0; axis < 3; axis++) {
			float want = (float) Math.toDegrees(radians[axis]);
			helper.assertTrue(Math.abs(got[axis] - want) < 0.0001F,
					"hamon_shock " + bone + " rotation axis " + axis + " at " + time + " must be " + want + ", got " + got[axis]);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonBeatLungeIsInBlocks(GameTestHelper helper) {
		JsonObject beat = bones(helper, animations(helper), "hamon_beat");
		// hamon_beat.json tick 12: turned right 85 deg, 0.54 block forward
		rot(helper, "hamon_beat", beat, 0.6F, 0F, -1.4828748F, 0F);
		pos(helper, "hamon_beat", beat, 0.6F, -0.10222316F, -0.010222331F, -0.5429187F);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void limbOffsetsKeepTheDonorModelYAxis(GameTestHelper helper) {

		JsonObject anims = animations(helper);
		JsonObject beat = bones(helper, anims, "hamon_beat");
		// KosmX limb Y is an absolute model pivot; subtract rest and undo the JSON loader's Y flip.
		limbY(helper, "hamon_beat", beat, "left_arm", 0.05F, 2.0087347F, 2F);
		limbY(helper, "hamon_beat", beat, "left_arm", 0.5F, 2.635909F, 2F);
		limbY(helper, "hamon_beat", beat, "left_arm", 1F, 3.1248474F, 2F);
		limbY(helper, "hamon_beat", beat, "right_arm", 0.05F, 2.06074F, 2F);
		limbY(helper, "hamon_beat", beat, "right_arm", 0.5F, 2.12064F, 2F);
		limbY(helper, "hamon_beat", beat, "right_arm", 1F, 1.96405F, 2F);
		limbY(helper, "hamon_beat", beat, "left_leg", 0.05F, 11.96755F, 12F);
		limbY(helper, "hamon_beat", beat, "left_leg", 0.5F, 11.81932F, 12F);
		limbY(helper, "hamon_beat", beat, "left_leg", 1F, 11.81932F, 12F);
		limbY(helper, "hamon_beat", beat, "right_leg", 0.5F, 12.27592F, 12F);
		limbY(helper, "hamon_beat", beat, "right_leg", 1F, 12.41794F, 12F);
		JsonObject syo = bones(helper, anims, "sunlight_yellow_overdrive");
		limbY(helper, "sunlight_yellow_overdrive", syo, "left_leg", 4.15F, 12.071976F, 12F);
		for (float t : new float[] {4.25F, 4.4F, 4.55F}) {
			limbY(helper, "sunlight_yellow_overdrive", syo, "left_leg", t, 12.182871F, 12F);
		}
		helper.succeed();
	}

	private static void limbY(GameTestHelper helper, String clip, JsonObject bones,
			String bone, float t, float donorAbsoluteY, float restY) {
		JsonObject part = bones.getAsJsonObject(bone);
		helper.assertTrue(part != null && part.has("position"), clip + " lost " + bone + " position");
		float loadedY = -sample(part.getAsJsonObject("position"), t)[1];
		helper.assertTrue(Math.abs(loadedY - (donorAbsoluteY - restY)) < 0.00002F,
				clip + " " + bone + " Y at " + t + " s must retain the donor pivot offset");
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void rebuffLimbsKeepTheirOwnDonorPositions(GameTestHelper helper) {
		JsonObject rebuff = bones(helper, animations(helper), "rebuff_overdrive");
		limbPosition(helper, rebuff, "left_arm", 0.6F,
				new float[] {5F, 1.7281799F, -0.7097781F}, new float[] {5F, 2F, 0F});
		limbPosition(helper, rebuff, "right_arm", 0.9F,
				new float[] {-5F, 1.1654758F, -2.1791182F}, new float[] {-5F, 2F, 0F});
		limbPosition(helper, rebuff, "left_leg", 1.05F,
				new float[] {1.896645F, 10.781157F, 0.07470138F}, new float[] {1.9F, 12F, -0.1F});
		limbPosition(helper, rebuff, "right_leg", 1.15F,
				new float[] {-1.5306004F, 9.368938F, 0.5756785F}, new float[] {-1.9F, 12F, RIGHT_LEG_REST_Z});
		JsonObject positions = rebuff.getAsJsonObject("right_leg").getAsJsonObject("position");
		float[] times = {0.75F, 0.8F, 0.85F, 1F, 1.15F, 1.4F};
		float[] donorZ = {0.12618618F, 0.10862576F, 0.4129756F, 0.52831745F, 0.5756785F, 0.56721926F};
		for (int i = 0; i < times.length; i++) {
			helper.assertTrue(Math.abs(sample(positions, times[i])[2] - (donorZ[i] - RIGHT_LEG_REST_Z)) < 0.00002F,
					"Rebuff right-leg Z must subtract its +0.1 donor rest at " + times[i]);
		}
		for (float component : sample(positions, 0F)) {
			helper.assertTrue(component == 0F, "Rebuff's synthetic neutral position must stay zero");
		}
		helper.succeed();
	}

	private static void limbPosition(GameTestHelper helper, JsonObject bones, String bone,
			float time, float[] donorAbsolute, float[] rest) {
		limbPosition(helper, "rebuff_overdrive", bones, bone, time, donorAbsolute, rest);
	}

	private static void limbPosition(GameTestHelper helper, String clip, JsonObject bones, String bone,
			float time, float[] donorAbsolute, float[] rest) {
		JsonObject part = bones.getAsJsonObject(bone);
		helper.assertTrue(part != null && part.has("position"), clip + " lost " + bone + " position");
		float[] loaded = sample(part.getAsJsonObject("position"), time);
		loaded[1] = -loaded[1];
		for (int axis = 0; axis < 3; axis++) {
			helper.assertTrue(Math.abs(loaded[axis] - (donorAbsolute[axis] - rest[axis])) < 0.00002F,
					clip + " " + bone + " axis " + axis + " lost its own donor position at " + time);
		}
	}

	private static void noUpperBodyTransform(GameTestHelper helper, String clip, JsonObject bones) {
		JsonObject torso = bones.getAsJsonObject("torso");
		if (torso == null) return;
		for (String channel : new String[] {"position", "rotation"}) {
			JsonObject keys = torso.getAsJsonObject(channel);
			if (keys == null) continue;
			for (Map.Entry<String, JsonElement> key : keys.entrySet()) {
				float[] v = vector(key.getValue());
				helper.assertTrue(Math.abs(v[0]) + Math.abs(v[1]) + Math.abs(v[2]) < 1.0E-3F,
						clip + " keys the upper-body 'torso' " + channel + " at " + key.getKey() + " s; 1.16 moved the whole body");
			}
		}
	}

	private static void rot(GameTestHelper helper, String clip, JsonObject bones, float t, float pitch, float yaw, float roll) {
		float[] want = {(float) -Math.toDegrees(pitch), (float) -Math.toDegrees(yaw), (float) Math.toDegrees(roll)};
		float[] got = sample(channel(helper, clip, bones, "rotation"), t);
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(Math.abs(got[i] - want[i]) < 0.05F,
					clip + " body rotation[" + i + "] at " + t + " s must be " + want[i] + " deg, got " + got[i]);
		}
	}

	private static void pos(GameTestHelper helper, String clip, JsonObject bones, float t, float x, float y, float z) {
		float[] want = {-16 * x, 16 * y, 16 * z};
		float[] got = sample(channel(helper, clip, bones, "position"), t);
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(Math.abs(got[i] - want[i]) < 0.02F,
					clip + " body position[" + i + "] at " + t + " s must be " + want[i] + " px, got " + got[i]);
		}
	}

	private static JsonObject channel(GameTestHelper helper, String clip, JsonObject bones, String channel) {
		JsonObject body = bones.getAsJsonObject("body");
		helper.assertTrue(body != null && body.has(channel), clip + " lost its whole-body ('body' bone) " + channel);
		return body.getAsJsonObject(channel);
	}

	private static JsonObject animations(GameTestHelper helper) {
		try (InputStream in = HamonLegacyTorsoClipsGameTests.class.getResourceAsStream("/assets/jojo_ripples/animations/hamon.animation.json")) {
			helper.assertTrue(in != null, "Missing hamon.animation.json");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("animations");
		} catch (IOException e) {
			throw new IllegalStateException("Could not read hamon.animation.json", e);
		}
	}

	private static JsonObject bones(GameTestHelper helper, JsonObject anims, String clip) {
		JsonObject c = anims.getAsJsonObject(clip);
		helper.assertTrue(c != null && c.has("bones"), "hamon.animation.json lost " + clip);
		return c.getAsJsonObject("bones");
	}

	private static float[] vector(JsonElement key) {
		JsonArray arr = key.isJsonArray() ? key.getAsJsonArray() : key.getAsJsonObject().getAsJsonArray("vector");
		return new float[] {arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
	}

	// linear lerp between keys; the sampled times are key times
	private static float[] sample(JsonObject keys, float t) {
		List<float[]> frames = new ArrayList<>();
		for (Map.Entry<String, JsonElement> key : keys.entrySet()) {
			float[] v = vector(key.getValue());
			frames.add(new float[] {Float.parseFloat(key.getKey()), v[0], v[1], v[2]});
		}
		frames.sort((a, b) -> Float.compare(a[0], b[0]));
		float[] prev = frames.get(0);
		if (t <= prev[0]) return new float[] {prev[1], prev[2], prev[3]};
		for (float[] next : frames) {
			if (next[0] >= t) {
				float k = next[0] > prev[0] ? (t - prev[0]) / (next[0] - prev[0]) : 0;
				return new float[] {prev[1] + (next[1] - prev[1]) * k, prev[2] + (next[2] - prev[2]) * k, prev[3] + (next[3] - prev[3]) * k};
			}
			prev = next;
		}
		return new float[] {prev[1], prev[2], prev[3]};
	}
}
