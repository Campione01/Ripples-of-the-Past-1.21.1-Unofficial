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

	// a 1.16 head at a clip time (0, PERFORM, RECOVERY, END, W<f> a fraction of the windup, or seconds): its root-space
	// angles, LOOK where it followed the look, HALF the given angles plus half the look (1.16 half way from a posed head
	// to the look), or IDLE where the clip hands over to the idle and the head is the idle's own
	private enum Head { POSED, LOOK, HALF, IDLE }

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
			// SilverChariotModel :245-372: the rapier stab, the barrage stab loop, the rapier heavy (dash), the jab without
			// the rapier, no head in any of them; RANGED_ATTACK (the rapier launch) was drawn as the idle
			new HeadPose(SC_ANIMS, "light_attack", "0"), new HeadPose(SC_ANIMS, "light_attack", "PERFORM"),
			new HeadPose(SC_ANIMS, "light_attack", "RECOVERY"), idle(SC_ANIMS, "light_attack"),
			new HeadPose(SC_ANIMS, "melee_barrage", "0"), new HeadPose(SC_ANIMS, "melee_barrage", "PERFORM"),
			new HeadPose(SC_ANIMS, "melee_barrage", "1.0"), new HeadPose(SC_ANIMS, "melee_barrage", "RECOVERY"),
			idle(SC_ANIMS, "melee_barrage"),
			new HeadPose(SC_ANIMS, "dash_attack", "0"), new HeadPose(SC_ANIMS, "dash_attack", "PERFORM"),
			new HeadPose(SC_ANIMS, "dash_attack", "RECOVERY"), idle(SC_ANIMS, "dash_attack"),
			new HeadPose(SC_ANIMS, "no_rapier_light_attack", "0"), new HeadPose(SC_ANIMS, "no_rapier_light_attack", "0.1"),
			new HeadPose(SC_ANIMS, "no_rapier_light_attack", "PERFORM"),
			new HeadPose(SC_ANIMS, "no_rapier_light_attack", "RECOVERY"),
			new HeadPose(SC_ANIMS, "no_rapier_light_attack", "0.4667"),
			new HeadPose(SC_ANIMS, "rapier_launch", "0"), new HeadPose(SC_ANIMS, "rapier_launch", "PERFORM"),
			new HeadPose(SC_ANIMS, "rangedAttack", "0"), new HeadPose(SC_ANIMS, "rangedAttack", "PERFORM"),
			// the three idles (initBaseIdlePose: HEAD_ROTATION)
			new HeadPose(SC_ANIMS, "idle", "0"), new HeadPose(SC_ANIMS, "no_rapier_idle", "0"),
			new HeadPose(SC_ANIMS, "no_armor_idle", "0"),
			// SilverChariotModel :100-136, summon poses 1-3 (pose 2 has no head: the reset head, the root's forward)
			posed(SC_ANIMS, "summon1", "0", -10, 0, -5), idle(SC_ANIMS, "summon1"),
			posed(SC_ANIMS, "summon2", "0", 0, 0, 0), idle(SC_ANIMS, "summon2"),
			posed(SC_ANIMS, "summon3", "0", 5, 2.5F, -10), idle(SC_ANIMS, "summon3"),
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
					case IDLE -> composedHead(anims.getAsJsonObject(idleOf(pose)), 0, look);
				};
				double off = angleBetween(composedHead(clip, time, look), expected);
				if (off > 1) {
					wrong.add(pose.clip() + " of " + pose.asset() + " at " + pose.at() + " (" + time + ") looking "
							+ Arrays.toString(look) + ": head " + String.format("%.1f", off) + " degrees off 1.16's "
							+ (pose.head() != null ? Arrays.toString(pose.head()) : pose.kind()));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "A 1.16 pose's head kept its root-space angles, the look where the pose had none: " + wrong);
		helper.succeed();
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
			default -> at.startsWith("W") ? Float.parseFloat(at.substring(1)) * performAt : Float.parseFloat(at);
		};
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
