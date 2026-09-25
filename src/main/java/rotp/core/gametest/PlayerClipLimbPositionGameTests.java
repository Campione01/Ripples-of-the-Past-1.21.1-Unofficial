package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 KosmX clips stored limb positions as absolute values whose rest value is the limb pivot
 * (rightArm -5/2/0, rightLeg -1.9/12/0.1 ...). The port adds a position channel to the part's initial pose
 * (AnimFramePose.ModelPartFrame.apply -> ModelPart.offsetPos), so converted clips must hold the offset
 * from that rest value; pivot-valued keys detached the arms and dropped the legs 12 px (wall climbing).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerClipLimbPositionGameTests {
	private PlayerClipLimbPositionGameTests() {}

	private static final float EPS = 1.0E-4F;
	private static final String[] PLAYER_CLIP_FILES = { "hamon", "pillar_man", "vampire" };
	private static final String[] LIMBS = { "right_arm", "left_arm", "right_leg", "left_leg" };
	// KosmX rest positions as the converter wrote them (y up)
	private static final float[][] KOSMX_REST = { { -5, -2, 0 }, { 5, -2, 0 }, { -1.9F, -12, 0.1F }, { 1.9F, -12, -0.1F } };
	private static final String[] WALL_CLIMB = { "wall_climb_up", "wall_climb_down", "wall_climb_left", "wall_climb_right" };

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void limbPositionKeysAreOffsetsFromTheRestPose(GameTestHelper helper) {
		for (String file : PLAYER_CLIP_FILES) {
			int keys = 0;
			for (Map.Entry<String, JsonElement> clip : readAnims(helper, file).entrySet()) {
				JsonObject bones = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
				if (bones == null) continue;
				for (int i = 0; i < LIMBS.length; i++) {
					JsonObject position = positionOf(bones, LIMBS[i]);
					if (position == null) continue;
					for (Map.Entry<String, JsonElement> key : position.entrySet()) {
						float[] v = vectorOf(key.getValue());
						if (v == null) continue; // Molang
						keys++;
						helper.assertTrue(dist(v, KOSMX_REST[i]) >= dist(v, new float[3]),
								file + " " + clip.getKey() + " " + LIMBS[i] + " position at " + key.getKey()
								+ " is an absolute KosmX value (" + v[0] + ", " + v[1] + ", " + v[2] + "), not an offset");
					}
				}
			}
			if (!"vampire".equals(file)) {
				helper.assertTrue(keys > 0, file + " has no limb position keys left to check");
			}
		}
		// the offsets only make sense while the frame adds them to the initial pose
		String frame = classBytes(helper, "rotp/core/client/entityanim/pose/AnimFramePose$ModelPartFrame");
		helper.assertTrue(frame.contains("offsetPos") && frame.contains("getInitialPose"),
				"AnimFramePose.ModelPartFrame must add the position channel to the part's initial pose");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void pillarManClipsKeepThe116LimbMotion(GameTestHelper helper) {
		JsonObject anims = readAnims(helper, "pillar_man");
		// 1.16 blade_slash tick 1: rightArm (-5.02289, 2.09657, -0.00278), leftLeg (1.94751, 12.00648, -0.10765)
		expectKey(helper, anims, "blade_slash", "right_arm", "0.05", -0.02289F, -0.09657F, -0.00278F);
		expectKey(helper, anims, "blade_slash", "left_leg", "0.05", 0.04751F, -0.00648F, -0.00765F);
		// the barrage arm swing moves the shoulder by up to ~4 px
		expectKey(helper, anims, "blade_barrage", "right_arm", "2.65", -0.72093F, 1.28262F, -2.79125F);
		// evasion kept the legs on their pivots in 1.16
		JsonObject legs = positionOf(clipBones(helper, anims, "evasion"), "right_leg");
		helper.assertTrue(legs != null && !legs.isEmpty(), "pillar_man evasion lost its right_leg position keys");
		for (Map.Entry<String, JsonElement> key : legs.entrySet()) {
			float[] v = vectorOf(key.getValue());
			helper.assertTrue(v != null && dist(v, new float[3]) < EPS,
					"pillar_man evasion right_leg must rest on its pivot at " + key.getKey());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void wallClimbKeepsLimbsOnTheirPivots(GameTestHelper helper) {
		JsonObject anims = readAnims(helper, "hamon");
		for (String clip : WALL_CLIMB) {
			JsonObject bones = clipBones(helper, anims, clip);
			for (String limb : LIMBS) {
				JsonObject bone = bones.getAsJsonObject(limb);
				helper.assertTrue(bone != null && bone.has("rotation"), clip + " lost the " + limb + " swing");
				JsonObject position = positionOf(bones, limb);
				if (position == null) continue;
				for (Map.Entry<String, JsonElement> key : position.entrySet()) {
					float[] v = vectorOf(key.getValue());
					helper.assertTrue(v != null && dist(v, new float[3]) < EPS,
							clip + " " + limb + " must stay on its pivot while climbing, moved at " + key.getKey());
				}
			}
		}
		helper.succeed();
	}

	private static void expectKey(GameTestHelper helper, JsonObject anims, String clip, String limb, String time,
			float x, float y, float z) {
		JsonObject position = positionOf(clipBones(helper, anims, clip), limb);
		helper.assertTrue(position != null && position.has(time), "pillar_man " + clip + " lost " + limb + " position at " + time);
		float[] v = vectorOf(position.get(time));
		helper.assertTrue(v != null && dist(v, new float[] { x, y, z }) < EPS,
				"pillar_man " + clip + " " + limb + " at " + time + " must be the 1.16 offset (" + x + ", " + y + ", " + z + ")");
	}

	private static JsonObject clipBones(GameTestHelper helper, JsonObject anims, String clip) {
		JsonObject anim = anims.getAsJsonObject(clip);
		helper.assertTrue(anim != null && anim.has("bones"), "missing clip " + clip);
		return anim.getAsJsonObject("bones");
	}

	private static JsonObject positionOf(JsonObject bones, String limb) {
		JsonObject bone = bones.getAsJsonObject(limb);
		if (bone == null || !bone.has("position")) return null;
		JsonElement position = bone.get("position");
		if (position.isJsonObject()) return position.getAsJsonObject();
		JsonObject single = new JsonObject(); // constant channel
		single.add("0", position);
		return single;
	}

	// null when a component is Molang
	private static float[] vectorOf(JsonElement key) {
		JsonElement e = key;
		if (e.isJsonObject()) {
			JsonObject o = e.getAsJsonObject();
			e = o.has("vector") ? o.get("vector") : o.has("post") ? o.get("post") : o.get("pre");
			if (e != null && e.isJsonObject()) e = e.getAsJsonObject().get("vector");
		}
		if (e == null) return null;
		if (e.isJsonPrimitive()) {
			Float f = number(e.getAsJsonPrimitive());
			return f == null ? null : new float[] { f, f, f };
		}
		if (!e.isJsonArray() || e.getAsJsonArray().size() != 3) return null;
		JsonArray a = e.getAsJsonArray();
		float[] v = new float[3];
		for (int i = 0; i < 3; i++) {
			if (!a.get(i).isJsonPrimitive()) return null;
			Float f = number(a.get(i).getAsJsonPrimitive());
			if (f == null) return null;
			v[i] = f;
		}
		return v;
	}

	private static Float number(JsonPrimitive p) {
		if (p.isNumber()) return p.getAsFloat();
		try {
			return Float.parseFloat(p.getAsString().trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static float dist(float[] a, float[] b) {
		float dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static JsonObject readAnims(GameTestHelper helper, String file) {
		String path = "/assets/jojo_ripples/animations/" + file + ".animation.json";
		try (InputStream in = PlayerClipLimbPositionGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "Missing asset " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("animations");
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	// constant pool strings are plain ASCII here, so a latin-1 view of the class file is enough
	private static String classBytes(GameTestHelper helper, String className) {
		String path = className + ".class";
		InputStream raw = PlayerClipLimbPositionGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) raw = PlayerClipLimbPositionGameTests.class.getClassLoader().getResourceAsStream(path);
		helper.assertTrue(raw != null, "Missing class file " + path);
		try (InputStream in = raw) {
			return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}
}
