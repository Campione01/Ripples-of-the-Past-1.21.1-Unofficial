package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityanim.RotpAnimDefinition.ClipClock;
import rotp.core.client.entityanim.action.AnimInstructionTimelines;
import rotp.core.client.entityanim.action.AnimObjTimeline;
import rotp.core.client.entityanim.molang.AnimMolangQuery;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.config.MolangValue;
import rotp.core.core.JojoMod;
import rotp.core.util.objects_java.OptionalFloat;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 KosmXPlayerBarrageAnim played hamon/punch_barrage.json (returnTick 2, endTick 4, every track keyed at ticks 2
 * and 4 with EASEINOUTCUBIC) forwards with partial ticks and back in whole-tick steps: clip ticks 0 to 2 ease in from
 * the rest pose, then 2+pt, 3+pt, 4, 3, 2 repeat (a 5-tick cycle). An afterimage arm showed the clip tick
 * returnTick + (endTick - returnTick) * swing completion, the right arm from the end (getBarrageEffectLoopingTick).
 * The evaluated poses are checked by LegacyBodyClipSmokeTest with the client-only parser.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerBarrageLoopClockGameTests {
	private static final float EPS = 1.0E-5F;
	private static final int RETURN_TICK = 2;
	private static final int END_TICK = 4;
	// KosmXPlayerBarrageAnim by hand: one entry per client tick at partial tick 0
	private static final float[] CLIP_TICKS_116 = { 0, 1, 2, 3, 4, 3, 2, 2, 3, 4, 3, 2, 2, 3 };

	private PlayerBarrageLoopClockGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void punchBarrageClockPingPongsLike116(GameTestHelper helper) {
		ClipClock clock = clockOf(helper);
		// KosmXPlayerBarrageAnim.tick and getPlayerAnimatorLoopTick
		int currentTick = 0;
		for (int tick = 0; tick < CLIP_TICKS_116.length; tick++) {
			for (float partial : new float[] { 0F, 0.5F }) {
				boolean backwards = currentTick >= END_TICK;
				float clipTick = backwards ? END_TICK * 2 - currentTick : currentTick + partial;
				if (partial == 0) {
					helper.assertTrue(clipTick == CLIP_TICKS_116[tick], "Fixture: the 1.16 clock transcription is off at tick " + tick);
				}
				float got = clock.seconds(tick + partial) * 20;
				helper.assertTrue(Math.abs(got - clipTick) < 1.0E-3F,
						"1.16 shows clip tick " + clipTick + " of punch_barrage at action tick " + tick + " + " + partial
								+ ", the port shows clip tick " + got);
			}
			currentTick++;
			if (currentTick > RETURN_TICK + (END_TICK - RETURN_TICK) * 2) {
				currentTick = RETURN_TICK;
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void afterimageArmsShowTheClipTickOfTheirSwing(GameTestHelper helper) {
		ClipClock clock = clockOf(helper);
		// swing completion, 1.16 clip tick of a right arm, of a left arm
		float[][] swings = { { 0F, 4F, 2F }, { 0.25F, 3.5F, 2.5F }, { 0.5F, 3F, 3F }, { 0.74F, 2.52F, 3.48F }, { 1F, 2F, 4F } };
		for (float[] swing : swings) {
			float right = clock.afterimageSeconds(swing[0], true) * 20;
			float left = clock.afterimageSeconds(swing[0], false) * 20;
			helper.assertTrue(Math.abs(right - swing[1]) < 1.0E-3F && Math.abs(left - swing[2]) < 1.0E-3F,
					"1.16: an afterimage arm " + swing[0] + " through its swing shows clip tick " + swing[1]
							+ " (right arm) or " + swing[2] + " (left arm), got " + right + " and " + left);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void punchBarrageKeysEaseLike116(GameTestHelper helper) {
		JsonObject bones = clipJson(helper).getAsJsonObject("bones");
		helper.assertTrue(bones != null && bones.size() >= 10, "punch_barrage lost its bones");
		for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
			JsonObject rotation = bone.getValue().getAsJsonObject().getAsJsonObject("rotation");
			helper.assertTrue(rotation != null, "punch_barrage " + bone.getKey() + " has no rotation keys");
			JsonObject rest = null;
			for (Map.Entry<String, JsonElement> key : rotation.entrySet()) {
				float time = Float.parseFloat(key.getKey());
				String easing = key.getValue().getAsJsonObject().get("easing").getAsString();
				String at = "punch_barrage " + bone.getKey() + " key at " + key.getKey() + " s";
				if (time == 0) {
					rest = key.getValue().getAsJsonObject();
				}
				// a port key carries the easing of the move that ends on it
				else if (Math.abs(time - 0.1F) < EPS) {
					helper.assertTrue("easeInOutSine".equals(easing),
							at + ": 1.16 eases in from the rest pose with the library's INOUTSINE, got " + easing);
				}
				else if (Math.abs(time - 0.2F) < EPS) {
					helper.assertTrue("easeInOutCubic".equals(easing),
							at + ": 1.16 moves between its two poses with EASEINOUTCUBIC, got " + easing);
				}
				else {
					helper.fail(at + ": 1.16 keys ticks 2 and 4 only");
				}
			}
			helper.assertTrue(rest != null,
					"punch_barrage " + bone.getKey() + " has no rest key at 0 s: 1.16 eases in from the rest pose over 2 ticks");
			float[] restPose = vector(rest.getAsJsonArray("vector"));
			helper.assertTrue(Math.abs(restPose[0]) + Math.abs(restPose[1]) + Math.abs(restPose[2]) < EPS,
					"punch_barrage " + bone.getKey() + " must start from the rest pose, got "
							+ restPose[0] + ", " + restPose[1] + ", " + restPose[2]);
		}
		helper.succeed();
	}

	// the head keys add the look rotation; evaluated with no look
	private static float[] vector(JsonArray vector) {
		KeyframesMolangEngine.init();
		AnimMolangQuery.instance.reset(0);
		try {
			float[] result = new float[3];
			for (int i = 0; i < result.length; i++) {
				result[i] = MolangValue.fromJson(vector.get(i), KeyframesMolangEngine.get()).getAsFloat();
			}
			return result;
		}
		finally {
			AnimMolangQuery.instance.reset();
		}
	}

	// same timeline handling as ParseGeckoAnims (client-only), limited to the keys used here
	private static ClipClock clockOf(GameTestHelper helper) {
		JsonObject clip = clipJson(helper);
		float length = clip.get("animation_length").getAsFloat();
		OptionalFloat loopBack = clip.has("loop") && clip.get("loop").getAsJsonPrimitive().isBoolean()
				&& clip.get("loop").getAsBoolean() ? OptionalFloat.of(0) : OptionalFloat.empty();
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
						case "loopBack" -> loopBack = OptionalFloat.of(Float.parseFloat(val));
						case "phase", "mirror.default" -> {}
						default -> timelines.stringVals.computeIfAbsent(field, k -> new AnimObjTimeline<>()).add(time, val);
					}
				}
			}
		}
		timelines.onFinishedParsing();
		return ClipClock.of(length, loopBack, timelines);
	}

	private static JsonObject clipJson(GameTestHelper helper) {
		String path = "/assets/jojo_ripples/animations/hamon.animation.json";
		try (InputStream in = PlayerBarrageLoopClockGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "Missing asset " + path);
			JsonObject clip = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("animations").getAsJsonObject("punch_barrage");
			helper.assertTrue(clip != null, "hamon.animation.json lost punch_barrage");
			return clip;
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}
}
