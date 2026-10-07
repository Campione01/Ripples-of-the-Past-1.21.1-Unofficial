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
 * 1.16 Pillar Man clips key the legacy KosmX "torso" part, which the library renames to "body": a whole-body
 * transform applied before the model flip (offsets in blocks, y up; pitch, yaw, roll in radians).
 * The port "body" bone works in model space: position px = (-16x, 16y, 16z) (JSON y is flipped at load),
 * rotation deg = (-pitch, -yaw, roll). The rows below are raw key values of the 1.16 clip files.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarManLegacyTorsoClipsGameTests {
	private PillarManLegacyTorsoClipsGameTests() {}

	// clip, tick, torso x, y, z (blocks), pitch, yaw, roll (radians)
	private static final Object[][] DONOR_TORSO_KEYS = {
			{ "atmospheric_rift", 1, 0F, -5.490481271408498e-06F, 0.0030911541543900967F, 0.002974138595163822F, 0.012368839234113693F, 0F },
			{ "atmospheric_rift", 22, 0F, -0.13424482941627502F, 0.3815198540687561F, 0.22857366502285004F, 0.9700546264648438F, 0F },
			{ "atmospheric_rift", 45, 0F, -0.16628313064575195F, 0.16412508487701416F, -0.3899991810321808F, 0F, 0F },
			{ "blade_barrage", 1, 0F, -0.015592005103826523F, -0.03540661185979843F, -0.0314699225127697F, -0.05371762067079544F, 0F },
			{ "blade_barrage", 50, 0F, -0.09887585043907166F, -0.38128602504730225F, 0.2175953984260559F, 1.1788933277130127F, 0.4868796765804291F },
			{ "blade_barrage", 100, 0F, -0.1499231457710266F, -0.34044820070266724F, -0.3025955259799957F, -0.5165156126022339F, 0F },
			{ "blade_dash", 1, 0F, -0.00016896516899578273F, -0.0045788041315972805F, -0.00025978576741181314F, -0.016491975635290146F, 0.003202767577022314F },
			{ "blade_dash", 25, 0F, -0.15447068214416504F, -0.8173444271087646F, -0.7521454095840454F, 1.0749298334121704F, -0.433560311794281F },
			{ "blade_slash", 1, 0F, -0.009876975789666176F, -0.010845264419913292F, -0.013989991508424282F, -0.04362956061959267F, 0.008472928777337074F },
			{ "blade_slash", 15, 0F, -0.12607534229755402F, -0.5417832136154175F, -0.7521454095840454F, 1.0749298334121704F, -0.433560311794281F },
			{ "blade_slash", 30, 0F, 0F, 0F, 0F, 0F, 0F },
			{ "divine_sandstorm", 20, 0F, -0.25F, 0F, 0F, 0F, 0F },
			{ "divine_sandstorm", 40, 0F, -0.25F, 0F, 0F, 0F, 0F },
			{ "erratic_blaze_king", 5, 0F, -0.004088923335075378F, -0.17786839604377747F, -0.10329440236091614F, 0F, 0F },
			{ "erratic_blaze_king", 10, 0F, -0.15537932515144348F, -0.3107585906982422F, -0.20658883452415466F, 0F, 0F },
			{ "erratic_blaze_king", 25, 0F, -0.19626861810684204F, -0.3107585906982422F, -0.20658883452415466F, 0F, 0F },
			{ "erratic_blaze_king", 40, 0F, -0.15537932515144348F, -0.3107585906982422F, -0.20658883452415466F, 0F, 0F },
			{ "evasion", 1, -0.012550302781164646F, -0.003709644777700305F, 0F, -0.006496195215731859F, -0.00460693147033453F, 0.020619602873921394F },
			{ "evasion", 85, 0.6615185737609863F, -0.2223183661699295F, 0F, 3.196132183074951F, 3.339398145675659F, -3.1467678546905518F },
			{ "evasion", 100, 0F, 0F, 0F, 0F, 0F, 0F },
			{ "giant_cartwheel_prison", 7, 0.07360070943832397F, -0.07104840874671936F, 0F, 0.12995555996894836F, -0.22143587470054626F, 0.07522358000278473F },
			{ "giant_cartwheel_prison", 15, 0.03680035471916199F, -0.07837110757827759F, 0.0327114462852478F, 0.28874263167381287F, -0.49199873208999634F, 0.16713598370552063F },
			{ "light_flash", 5, 0F, -0.053395580500364304F, 0.16526228189468384F, -0.12296027690172195F, 0F, 0F },
			{ "light_flash", 10, 0F, -0.1528615951538086F, 0.30051735043525696F, -0.22359423339366913F, 0F, 0F },
			{ "light_flash_decoy", 15, -0.006814879365265369F, -0.057358574122190475F, 0F, 0.1472068876028061F, -0.7396851778030396F, 0F },
			{ "light_flash_decoy", 40, -0.02044464275240898F, -0.01306183636188507F, 0F, 0.04167824983596802F, 0.007776262238621712F, -0.0008236697176471353F },
			{ "pillar_man_possession", 1, 0F, -0.03947724401950836F, 0.022396421059966087F, -0.008258550427854061F, 0F, 0F },
			{ "pillar_man_possession", 20, 0F, 0.11868451535701752F, -1.7331905364990234F, -1.9848747253417969F, 0F, 0F },
			{ "pillar_man_punch", 1, 0F, 0F, -0.01949739083647728F, -0.00013526798284146935F, -0.038378193974494934F, 0.0023198591079562902F },
			{ "pillar_man_punch", 11, 0F, -0.1601496785879135F, -0.4571648836135864F, -0.2960326373577118F, 0.8916007876396179F, -0.04449230432510376F },
			{ "pillar_man_punch", 20, 0F, -0.06814877688884735F, -0.4537574350833893F, -0.2960326373577118F, 0.8916007876396179F, -0.04449230432510376F },
			{ "self_detonation", 1, 0F, -1.5274063969172857e-07F, 0.0008294165600091219F, 0F, 0F, 0F },
			{ "self_detonation", 41, 0F, -0.21313519775867462F, 0.24247916042804718F, -0.273335337638855F, 0F, 0F },
			{ "self_detonation", 60, 0F, -0.09584447741508484F, 0.21262434124946594F, 0.7178642749786377F, 0F, 0F },
			{ "stone_form_1", 15, 0F, 0F, 0.4024839401245117F, 0F, 0.4612164795398712F, 0F },
			{ "stone_form_2", 1, 0F, 0F, 0.005156620405614376F, 0F, -0.008939459919929504F, 0F },
			{ "stone_form_2", 15, 0F, 0F, 0.40473467111587524F, 0F, -0.7016435265541077F, 0F },
			{ "stone_form_3", 1, 0F, 0F, 0.005097844637930393F, 0F, -0.007146332412958145F, 0F },
			{ "stone_form_3", 15, 0F, 0F, 0.4001214802265167F, 0F, -0.5609039664268494F, 0F },
			{ "unnatural_agility", 1, -0.004585959017276764F, -0.0018100319430232048F, 0.020904241129755974F, 0.05205389857292175F, -0.10514450073242188F, -0.011188882403075695F },
			{ "unnatural_agility", 30, -0.5263814330101013F, 0.03877672553062439F, -0.025969311594963074F, -0.6168618202209473F, -0.9153875112533569F, 1.505463719367981F },
			{ "unnatural_agility", 55, 0F, 0F, 0F, 0F, 0F, 0F },
	};

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void pillarManClipsMoveTheWholeBodyLike116(GameTestHelper helper) {
		JsonObject anims = animations(helper);
		for (Object[] row : DONOR_TORSO_KEYS) {
			String clip = (String) row[0];
			float time = (Integer) row[1] / 20F;
			float x = (Float) row[2], y = (Float) row[3], z = (Float) row[4];
			float pitch = (Float) row[5], yaw = (Float) row[6], roll = (Float) row[7];
			JsonObject body = bones(helper, anims, clip).getAsJsonObject("body");
			helper.assertTrue(body != null, "pillar_man " + clip + " has no whole-body ('body' bone) keys; 1.16 moved the whole body");
			expect(helper, clip, body, "position", time, -16 * x, 16 * y, 16 * z, 0.002F);
			expect(helper, clip, body, "rotation", time,
					(float) -Math.toDegrees(pitch), (float) -Math.toDegrees(yaw), (float) Math.toDegrees(roll), 0.002F);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void pillarManClipsLeaveTheUpperBodyBoneAlone(GameTestHelper helper) {
		int clips = 0;
		for (Map.Entry<String, JsonElement> clip : animations(helper).entrySet()) {
			JsonObject bones = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
			if (bones == null) continue;
			clips++;
			JsonObject torso = bones.getAsJsonObject("torso");
			if (torso == null) continue;
			for (String channel : new String[] { "position", "rotation" }) {
				JsonObject keys = torso.getAsJsonObject(channel);
				if (keys == null) continue;
				for (Map.Entry<String, JsonElement> key : keys.entrySet()) {
					float[] v = vector(key.getValue());
					helper.assertTrue(Math.abs(v[0]) + Math.abs(v[1]) + Math.abs(v[2]) < 1.0E-3F,
							"pillar_man " + clip.getKey() + " keys the upper-body 'torso' " + channel + " at " + key.getKey()
							+ " s (" + v[0] + ", " + v[1] + ", " + v[2] + "); 1.16 moved the whole body");
				}
			}
		}
		helper.assertTrue(clips == 17, "pillar_man.animation.json must keep its 17 clips, found " + clips);
		helper.succeed();
	}

	/**
	 * Before its first key a 1.16 track eases from the rest pose (begin tick 0) with the library's default
	 * in-out sine; it does not wait in the rest pose and jump on the key.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void pillarManClipsEaseInFromTheRestPose(GameTestHelper helper) {
		JsonObject anims = animations(helper);
		// clip, bone, channel, axis, sampled tick, first key tick, key value in JSON units
		leadIn(helper, anims, "stone_form_1", "body", "rotation", 1, 7, 15, -deg(0.4612164795398712));
		leadIn(helper, anims, "stone_form_1", "body", "position", 2, 7, 15, 16 * 0.4024839401245117);
		leadIn(helper, anims, "stone_form_1", "right_arm", "rotation", 0, 7, 15, deg(-3.274296998977661));
		leadIn(helper, anims, "stone_form_1", "right_leg_bend", "rotation", 0, 7, 15, deg(1.29738187789917));
		leadIn(helper, anims, "light_flash", "body", "position", 2, 2, 5, 16 * 0.16526228189468384);
		leadIn(helper, anims, "light_flash", "right_arm", "rotation", 0, 4, 10, deg(-1.6646192073822021));
		leadIn(helper, anims, "light_flash", "left_arm_bend", "rotation", 0, 4, 10, deg(-2.1467549800872803));
		leadIn(helper, anims, "erratic_blaze_king", "body", "rotation", 0, 2, 5, -deg(-0.10329440236091614));
		leadIn(helper, anims, "erratic_blaze_king", "left_leg", "rotation", 0, 2, 5, deg(-1.4327199459075928));
		leadIn(helper, anims, "erratic_blaze_king", "right_leg_bend", "rotation", 0, 4, 10, deg(1.579846739768982));
		leadIn(helper, anims, "giant_cartwheel_prison", "body", "rotation", 1, 3, 7, -deg(-0.22143587470054626));
		leadIn(helper, anims, "giant_cartwheel_prison", "right_arm", "rotation", 2, 3, 7, deg(1.0943633317947388));
		leadIn(helper, anims, "giant_cartwheel_prison", "left_arm_bend", "rotation", 0, 6, 15, deg(-0.9463062882423401));
		leadIn(helper, anims, "light_flash_decoy", "body", "rotation", 1, 7, 15, -deg(-0.7396851778030396));
		leadIn(helper, anims, "light_flash_decoy", "right_arm", "rotation", 0, 7, 15, deg(-1.9607645273208618));
		leadIn(helper, anims, "light_flash_decoy", "right_leg_bend", "rotation", 0, 7, 15, deg(1.235724925994873));
		helper.succeed();
	}

	private static double deg(double radians) {
		return Math.toDegrees(radians);
	}

	private static void leadIn(GameTestHelper helper, JsonObject anims, String clip, String bone, String channel,
			int axis, int tick, int firstKeyTick, double keyValue) {
		JsonObject part = bones(helper, anims, clip).getAsJsonObject(bone);
		helper.assertTrue(part != null && part.has(channel), "pillar_man " + clip + " has no " + bone + " " + channel);
		float got = sample(part.getAsJsonObject(channel), tick / 20F)[axis];
		float want = (float) (keyValue * (1 - Math.cos(Math.PI * tick / firstKeyTick)) / 2);
		helper.assertTrue(Math.abs(got - want) < 0.002F,
				"pillar_man " + clip + " " + bone + " " + channel + "[" + axis + "] at tick " + tick
				+ " must ease in to " + want + " on the way to its first key at tick " + firstKeyTick + ", got " + got);
	}

	private static void expect(GameTestHelper helper, String clip, JsonObject body, String channel, float time,
			float x, float y, float z, float eps) {
		float[] want = { x, y, z };
		JsonObject keys = body.getAsJsonObject(channel);
		if (keys == null) {
			helper.assertTrue(Math.abs(x) + Math.abs(y) + Math.abs(z) < eps,
					"pillar_man " + clip + " lost its whole-body " + channel);
			return;
		}
		float[] got = sample(keys, time);
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(Math.abs(got[i] - want[i]) < eps,
					"pillar_man " + clip + " body " + channel + "[" + i + "] at " + time + " s must be " + want[i] + ", got " + got[i]);
		}
	}

	private static JsonObject animations(GameTestHelper helper) {
		String path = "/assets/jojo_ripples/animations/pillar_man.animation.json";
		try (InputStream in = PillarManLegacyTorsoClipsGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "Missing pillar_man.animation.json");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("animations");
		} catch (IOException e) {
			throw new IllegalStateException("Could not read pillar_man.animation.json", e);
		}
	}

	private static JsonObject bones(GameTestHelper helper, JsonObject anims, String clip) {
		JsonObject c = anims.getAsJsonObject(clip);
		helper.assertTrue(c != null && c.has("bones"), "pillar_man.animation.json lost " + clip);
		return c.getAsJsonObject("bones");
	}

	private static float[] vector(JsonElement key) {
		JsonArray arr = key.isJsonArray() ? key.getAsJsonArray() : key.getAsJsonObject().getAsJsonArray("vector");
		return new float[] { arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat() };
	}

	// linear lerp between keys; the sampled times are key times
	private static float[] sample(JsonObject keys, float t) {
		List<float[]> frames = new ArrayList<>();
		for (Map.Entry<String, JsonElement> key : keys.entrySet()) {
			float[] v = vector(key.getValue());
			frames.add(new float[] { Float.parseFloat(key.getKey()), v[0], v[1], v[2] });
		}
		frames.sort((a, b) -> Float.compare(a[0], b[0]));
		float[] prev = frames.get(0);
		if (t <= prev[0]) return new float[] { prev[1], prev[2], prev[3] };
		for (float[] next : frames) {
			if (next[0] >= t) {
				float k = next[0] > prev[0] ? (t - prev[0]) / (next[0] - prev[0]) : 0;
				return new float[] { prev[1] + (next[1] - prev[1]) * k, prev[2] + (next[2] - prev[2]) * k, prev[3] + (next[3] - prev[3]) * k };
			}
			prev = next;
		}
		return new float[] { prev[1], prev[2], prev[3] };
	}
}
