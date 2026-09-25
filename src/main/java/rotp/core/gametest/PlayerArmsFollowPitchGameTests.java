package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityanim.action.AnimInstructionTimelines;
import rotp.core.client.entityanim.action.AnimObjTimeline;
import rotp.core.client.entityanim.playerbend.ArmsFollowPitch;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 KosmXArmsRotationModifier turned both player arms by the look pitch in the Overdrive barrage
 * (KosmXBarrageAnimHandler), Blade Barrage and Divine Sandstorm; KosmXPlayerBarrageAnim did the same for
 * the afterimage arms. The SYO start/finisher handler (KosmXSYOBHandler) had no such modifier.
 * The port marks those clips with "arms_follow_pitch = 1"; the client wiring is checked in the class files.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerArmsFollowPitchGameTests {
	private PlayerArmsFollowPitchGameTests() {}

	private static final float EPS = 1.0E-4F;

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void barrageClipsTiltArmsLike116(GameTestHelper helper) {
		String[][] tilted = { { "hamon", "punch_barrage" }, { "pillar_man", "blade_barrage" }, { "pillar_man", "divine_sandstorm" } };
		for (String[] clip : tilted) {
			helper.assertTrue(ArmsFollowPitch.isSet(timelinesOf(helper, clip[0], clip[1])),
					clip[1] + " arms must follow the look pitch as in 1.16");
		}
		String[][] level = { { "hamon", "syo_barrage_start" }, { "hamon", "syo_barrage_finisher" },
				{ "hamon", "sendo_wave_kick" }, { "pillar_man", "blade_slash" }, { "pillar_man", "atmospheric_rift" } };
		for (String[] clip : level) {
			helper.assertFalse(ArmsFollowPitch.isSet(timelinesOf(helper, clip[0], clip[1])),
					clip[1] + " had no arms pitch modifier in 1.16");
		}
		helper.assertFalse(ArmsFollowPitch.isSet(null), "a clip without timelines must not tilt the arms");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void armTiltTurnsArmAboutParentXAxis(GameTestHelper helper) {
		// arm pointing forward, looking 30 degrees down -> arm 30 degrees lower
		Vector3f forward = ArmsFollowPitch.rotateAngles(new Vector3f(-90 * Mth.DEG_TO_RAD, 0, 0), 30 * Mth.DEG_TO_RAD);
		helper.assertTrue(near(forward.x, -60 * Mth.DEG_TO_RAD) && near(forward.y, 0) && near(forward.z, 0),
				"forward arm tilted by 30 degrees must reach xRot -60, got " + forward);
		// general pose: result equals Rx(pitch) * Rz * Ry * Rx (1.16 ClientUtil.rotateAngles);
		// the last case hits gimbal lock (yRot 90), handled like 1.16 Matrix4ZYX
		float[][] cases = { { -70, 25, 40, -35 }, { -110, -30, -15, 50 }, { 20, 60, 5, 80 }, { 0, 0, 90, -90 } };
		for (float[] c : cases) {
			float x = c[0] * Mth.DEG_TO_RAD, y = c[1] * Mth.DEG_TO_RAD, z = c[2] * Mth.DEG_TO_RAD, p = c[3] * Mth.DEG_TO_RAD;
			Matrix3f expected = new Matrix3f().rotationX(p).mul(zyx(x, y, z));
			Vector3f out = ArmsFollowPitch.rotateAngles(new Vector3f(x, y, z), p);
			Matrix3f actual = zyx(out.x, out.y, out.z);
			helper.assertTrue(actual.equals(expected, 1.0E-3F),
					"arm angles " + c[0] + "/" + c[1] + "/" + c[2] + " tilted by " + c[3] + " must turn about the parent X axis, got " + out);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void armTiltOnlyForMarkedClips(GameTestHelper helper) {
		AnimInstructionTimelines marked = timelinesOf(helper, "hamon", "punch_barrage");
		AnimInstructionTimelines plain = timelinesOf(helper, "hamon", "syo_barrage_start");
		Vector3f left = new Vector3f(-1.2F, 0.3F, 0.1F);
		Vector3f right = new Vector3f(-1.4F, -0.2F, -0.1F);
		ArmsFollowPitch.tiltArms(plain, 40, left, right);
		helper.assertTrue(left.equals(-1.2F, 0.3F, 0.1F) && right.equals(-1.4F, -0.2F, -0.1F),
				"an unmarked clip must leave the arms as keyed");
		ArmsFollowPitch.tiltArms(marked, 40, left, null);
		Vector3f expectedLeft = ArmsFollowPitch.rotateAngles(new Vector3f(-1.2F, 0.3F, 0.1F), 40 * Mth.DEG_TO_RAD);
		helper.assertTrue(left.equals(expectedLeft, EPS), "a marked clip must tilt the left arm, got " + left);
		ArmsFollowPitch.tiltArms(marked, 40, null, right);
		Vector3f expectedRight = ArmsFollowPitch.rotateAngles(new Vector3f(-1.4F, -0.2F, -0.1F), 40 * Mth.DEG_TO_RAD);
		helper.assertTrue(right.equals(expectedRight, EPS), "a marked clip must tilt the right arm, got " + right);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void playerPoseAndAfterimagesTiltArms(GameTestHelper helper) {
		String owner = "rotp/core/client/entityanim/playerbend/ArmsFollowPitch";
		for (String cls : new String[] {
				"rotp/core/client/entityanim/PreFrameEntityAnimCalc",
				"rotp/core/client/entityanim/barrage/TwoHandedBarrageLoopSwing" }) {
			String bytes = classBytes(helper, cls);
			helper.assertTrue(bytes.contains(owner) && bytes.contains("tiltArms"),
					cls + " must tilt the arms through ArmsFollowPitch.tiltArms");
		}
		helper.succeed();
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < EPS;
	}

	// ModelPart.translateAndRotate order
	private static Matrix3f zyx(float x, float y, float z) {
		return new Matrix3f().rotationZ(z).rotateY(y).rotateX(x);
	}

	// same timeline handling as ParseGeckoAnims (client-only)
	private static AnimInstructionTimelines timelinesOf(GameTestHelper helper, String file, String name) {
		JsonObject anims = readJson(helper, "animations/" + file + ".animation.json").getAsJsonObject("animations");
		JsonObject clip = anims.getAsJsonObject(name);
		helper.assertTrue(clip != null, file + ".animation.json lost " + name);
		AnimInstructionTimelines timelines = new AnimInstructionTimelines();
		JsonObject timelineJson = clip.getAsJsonObject("timeline");
		if (timelineJson != null) {
			for (Map.Entry<String, JsonElement> entry : timelineJson.entrySet()) {
				float time = Float.parseFloat(entry.getKey());
				JsonElement value = entry.getValue();
				Iterable<JsonElement> instructions = value.isJsonArray() ? value.getAsJsonArray() : Collections.singleton(value);
				for (JsonElement instruction : instructions) {
					String[] assignment = instruction.getAsString().split("[ ]*=[ ]*");
					if (assignment.length != 2) continue;
					String field = assignment[0];
					String val = assignment[1].replaceAll(";+$", "");
					switch (field) {
						case "loopBack", "phase", "mirror.default" -> {}
						default -> timelines.stringVals.computeIfAbsent(field, k -> new AnimObjTimeline<>()).add(time, val);
					}
				}
			}
		}
		timelines.onFinishedParsing();
		return timelines;
	}

	private static JsonObject readJson(GameTestHelper helper, String assetPath) {
		try (InputStream in = PlayerArmsFollowPitchGameTests.class.getResourceAsStream("/assets/jojo_ripples/" + assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + assetPath, e);
		}
	}

	// constant pool strings are plain ASCII here, so a latin-1 view of the class file is enough
	private static String classBytes(GameTestHelper helper, String className) {
		String path = className + ".class";
		InputStream raw = PlayerArmsFollowPitchGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) raw = PlayerArmsFollowPitchGameTests.class.getClassLoader().getResourceAsStream(path);
		helper.assertTrue(raw != null, "Missing class file " + path);
		try (InputStream in = raw) {
			return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}
}
