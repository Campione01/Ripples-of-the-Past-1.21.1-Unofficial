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
	public static void hamonBeatLungeIsInBlocks(GameTestHelper helper) {
		JsonObject beat = bones(helper, animations(helper), "hamon_beat");
		// hamon_beat.json tick 12: turned right 85 deg, 0.54 block forward
		rot(helper, "hamon_beat", beat, 0.6F, 0F, -1.4828748F, 0F);
		pos(helper, "hamon_beat", beat, 0.6F, -0.10222316F, -0.010222331F, -0.5429187F);
		helper.succeed();
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
