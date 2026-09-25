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

import rotp.core.client.entityanim.molang.AnimMolangQuery;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.config.MolangValue;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 KosmXHeadRotationModifier added the vanilla look rotation to the head of the barrage, Sendo Wave Kick,
 * wall climb, Atmospheric Rift, Divine Sandstorm, Erratic Blaze King and Light Flash clips. The port keys
 * those head tracks with query.head_x_rotation / query.head_y_rotation, which are evaluated here.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerHeadLookClipGameTests {
	private PlayerHeadLookClipGameTests() {}

	private static final Map<String, String[]> LOOK_CLIPS = Map.of(
			"hamon", new String[] { "punch_barrage", "sendo_wave_kick", "sendo_wave_kick_l", "sendo_wave_kick_r",
					"sendo_wave_kick_lr", "wall_climb_up", "wall_climb_down", "wall_climb_left", "wall_climb_right" },
			"pillar_man", new String[] { "atmospheric_rift", "divine_sandstorm", "erratic_blaze_king", "light_flash" });
	private static final float PITCH = 23.5F;
	private static final float YAW = -41.25F;

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void headFollowingClipsAddTheLook(GameTestHelper helper) {
		KeyframesMolangEngine.init();
		try {
			for (Map.Entry<String, String[]> file : LOOK_CLIPS.entrySet()) {
				JsonObject anims = readAnims(helper, file.getKey());
				for (String clip : file.getValue()) {
					for (Map.Entry<String, JsonElement> kf : headRotation(helper, anims, clip).entrySet()) {
						JsonArray vec = kf.getValue().getAsJsonObject().getAsJsonArray("vector");
						float[] still = eval(vec, 0, 0);
						float[] looking = eval(vec, PITCH, YAW);
						helper.assertTrue(close(looking[0] - still[0], PITCH) && close(looking[1] - still[1], YAW)
								&& close(looking[2], still[2]),
								clip + " head at " + kf.getKey() + "s does not add the look rotation like 1.16");
						if ("light_flash".equals(clip)) {
							// the left-handed mirrored copy keeps query keyframes unmirrored: only safe with no yaw/roll offset
							helper.assertTrue(close(still[1], 0) && close(still[2], 0),
									"light_flash head gained a yaw/roll offset its left-handed mirror would not flip");
						}
					}
				}
			}
			// the clip offset stays on top of the look: 1.16 sendo_wave_kick head pitch 0.16713813 rad
			JsonArray kick = headRotation(helper, readAnims(helper, "hamon"), "sendo_wave_kick")
					.getAsJsonObject("0.05").getAsJsonArray("vector");
			helper.assertTrue(close(eval(kick, PITCH, YAW)[0], (float) Math.toDegrees(0.16713812947273254) + PITCH),
					"sendo_wave_kick lost its 1.16 head pitch offset");

			// 1.16 left the modifier commented out for Stone Form: its head keeps the authored pose
			for (Map.Entry<String, JsonElement> kf : headRotation(helper, readAnims(helper, "pillar_man"), "stone_form_1").entrySet()) {
				for (JsonElement e : kf.getValue().getAsJsonObject().getAsJsonArray("vector")) {
					helper.assertTrue(e.getAsJsonPrimitive().isNumber(),
							"stone_form_1 head follows the look, 1.16 did not attach the head modifier");
				}
			}
		}
		finally {
			AnimMolangQuery.instance.reset();
		}
		helper.succeed();
	}

	private static float[] eval(JsonArray vec, float pitch, float yaw) {
		AnimMolangQuery.instance.fillContext(AnimMolangVariables.set(pitch, yaw, 0));
		float[] out = new float[3];
		for (int i = 0; i < 3; i++) {
			out[i] = MolangValue.fromJson(vec.get(i), KeyframesMolangEngine.get()).getAsFloat();
		}
		return out;
	}

	private static boolean close(float a, float b) {
		return Math.abs(a - b) < 1.0E-3F;
	}

	private static JsonObject headRotation(GameTestHelper helper, JsonObject anims, String clip) {
		JsonObject anim = anims.getAsJsonObject(clip);
		helper.assertTrue(anim != null, "Missing player clip " + clip);
		JsonObject head = anim.getAsJsonObject("bones").getAsJsonObject("head");
		helper.assertTrue(head != null && head.has("rotation") && head.get("rotation").isJsonObject(),
				clip + " has no keyed head rotation");
		JsonObject rot = head.getAsJsonObject("rotation");
		helper.assertTrue(!rot.isEmpty(), clip + " has no head keyframes");
		return rot;
	}

	private static JsonObject readAnims(GameTestHelper helper, String file) {
		String path = "/assets/jojo_ripples/animations/" + file + ".animation.json";
		try (InputStream in = PlayerHeadLookClipGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "Missing asset " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("animations");
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}
}
