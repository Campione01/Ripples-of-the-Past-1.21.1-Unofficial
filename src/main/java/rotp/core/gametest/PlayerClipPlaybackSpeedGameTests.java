package rotp.core.gametest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityanim.RotpAnimDefinition.ClipClock;
import rotp.core.client.entityanim.action.AnimInstructionTimelines;
import rotp.core.client.entityanim.action.AnimObjTimeline;
import rotp.core.core.JojoMod;
import rotp.core.util.objects_java.OptionalFloat;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 played these player clips through a KosmX SpeedModifier (BladeBarrage 3.25, BladeSlash 1.5,
 * PillarManPunch 2, Evasion 2, UnnaturalAgility 1.75, VampireClawSwipe 1.75, HamonShock 2, HamonBeat 2.25).
 * The port keeps the 1.16 keyframe times, so each clip carries "anim_speed" in its timeline and
 * RotpAnimDefinition plays it through ClipClock. RotpAnimDefinition itself is client-only, so its
 * wiring is checked in the class file.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerClipPlaybackSpeedGameTests {
	private PlayerClipPlaybackSpeedGameTests() {}

	private static final float EPS = 1.0E-3F;

	// file, clip, 1.16 speed, action ticks (0 = held loop), clip must reach its end when the action stops
	private record Clip(String file, String name, float speed, int actionTicks, boolean fullSwing) {}

	private static final Clip[] CLIPS = {
			new Clip("vampire", "vampire_claws", 1.75F, 12, true),
			new Clip("pillar_man", "blade_slash", 1.5F, 20, true),
			new Clip("pillar_man", "pillar_man_punch", 2F, 8, false),
			new Clip("hamon", "hamon_shock", 2F, 16, false),
			// 1.16 punch at tick 5 lands at clip 0.56 s, just as the swing ends
			new Clip("hamon", "hamon_beat", 2.25F, 8, false),
			new Clip("pillar_man", "blade_barrage", 3.25F, 0, false),
			new Clip("pillar_man", "evasion", 2F, 0, false),
			new Clip("pillar_man", "unnatural_agility", 1.75F, 0, false),
	};

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void playerClipsKeep116PlaybackSpeeds(GameTestHelper helper) {
		for (Clip clip : CLIPS) {
			ClipClock clock = clockOf(helper, clip.file, clip.name);
			helper.assertTrue(Math.abs(clock.speed - clip.speed) < EPS,
					clip.name + " must play at the 1.16 speed " + clip.speed + ", got " + clock.speed);
			// phase keys make getAnimTime fit the clip to the action phases and skip anim_speed
			helper.assertTrue(!hasPhaseKeys(clipJson(helper, clip.file, clip.name)),
					clip.name + " must play from 0 through its clip clock like 1.16, not through phase timeline keys");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void speededClipsReach116PoseInActionTime(GameTestHelper helper) {
		for (Clip clip : CLIPS) {
			ClipClock clock = clockOf(helper, clip.file, clip.name);
			if (clip.actionTicks > 0) {
				float atEnd = clock.seconds(clip.actionTicks);
				float expected = clip.actionTicks * clip.speed / 20F;
				helper.assertTrue(Math.abs(atEnd - expected) < EPS,
						clip.name + " at action end (" + clip.actionTicks + " ticks) must be at " + expected + " s like 1.16, got " + atEnd);
				if (clip.fullSwing) {
					helper.assertTrue(atEnd >= clock.lengthInSeconds - EPS,
							clip.name + " must finish its " + clock.lengthInSeconds + " s swing before the action ends, reached " + atEnd);
				}
			}
			else {
				helper.assertTrue(clock.loopBackTo.isPresent(), clip.name + " lost its loop");
				float lb = clock.loopBackTo.getAsFloat();
				float len = clock.lengthInSeconds;
				// one pass takes len * 20 / speed ticks
				float passTicks = len * 20F / clip.speed;
				float nearEnd = clock.seconds(passTicks - 0.5F);
				helper.assertTrue(Math.abs(nearEnd - (len - 0.5F * clip.speed / 20F)) < EPS,
						clip.name + " must reach its end after " + passTicks + " ticks, got " + nearEnd + " s of " + len);
				// wraps in clip seconds after the end
				float ticks = passTicks + 7;
				float wrapped = clock.seconds(ticks);
				float expected = (ticks * clip.speed / 20F - lb) % (len - lb) + lb;
				helper.assertTrue(Math.abs(wrapped - expected) < EPS && wrapped >= lb - EPS && wrapped <= len + EPS,
						clip.name + " must loop back to " + lb + " s in clip time, expected " + expected + " got " + wrapped);
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void animTimeRunsThroughClipClock(GameTestHelper helper) {
		String def = "rotp/core/client/entityanim/RotpAnimDefinition";
		String clock = def + "$ClipClock";
		helper.assertTrue(invokes(helper, def, "getAnimTime", "(F)F", clock + ".seconds(F)F"),
				"RotpAnimDefinition.getAnimTime(float) must read the clip time from ClipClock.seconds");
		helper.assertTrue(invokes(helper, def, "<init>", null,
				clock + ".of(FLrotp/core/util/objects_java/OptionalFloat;Lrotp/core/client/entityanim/action/AnimInstructionTimelines;)"
				+ "L" + clock + ";"),
				"RotpAnimDefinition must build its ClipClock from the clip timelines");
		helper.succeed();
	}

	// same timeline handling as ParseGeckoAnims (client-only), limited to the keys used here
	private static ClipClock clockOf(GameTestHelper helper, String file, String name) {
		JsonObject clip = clipJson(helper, file, name);
		float length = clip.has("animation_length") ? clip.get("animation_length").getAsFloat() : 0;
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

	private static JsonObject clipJson(GameTestHelper helper, String file, String name) {
		JsonObject anims = readJson(helper, "animations/" + file + ".animation.json").getAsJsonObject("animations");
		JsonObject clip = anims.getAsJsonObject(name);
		helper.assertTrue(clip != null, file + ".animation.json lost " + name);
		return clip;
	}

	// true if any timeline instruction assigns "phase" (ParseGeckoAnims turns it into a phase keyframe)
	private static boolean hasPhaseKeys(JsonObject clip) {
		JsonObject timelineJson = clip.getAsJsonObject("timeline");
		if (timelineJson == null) return false;
		for (Map.Entry<String, JsonElement> entry : timelineJson.entrySet()) {
			JsonElement value = entry.getValue();
			Iterable<JsonElement> instructions = value.isJsonArray() ? value.getAsJsonArray() : Collections.singleton(value);
			for (JsonElement instruction : instructions) {
				for (String part : instruction.getAsString().split(";")) {
					if ("phase".equals(part.trim().split("[ ]*=[ ]*")[0])) return true;
				}
			}
		}
		return false;
	}

	private static JsonObject readJson(GameTestHelper helper, String assetPath) {
		try (InputStream in = PlayerClipPlaybackSpeedGameTests.class.getResourceAsStream("/assets/jojo_ripples/" + assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + assetPath, e);
		}
	}

	// true if the method's code has an invoke* of the member ref owner.name+desc (desc null = any overload)
	private static boolean invokes(GameTestHelper helper, String className, String name, String desc, String target) {
		String path = className + ".class";
		InputStream raw = PlayerClipPlaybackSpeedGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) raw = PlayerClipPlaybackSpeedGameTests.class.getClassLoader().getResourceAsStream(path);
		helper.assertTrue(raw != null, "Missing class file " + path);
		try (DataInputStream in = new DataInputStream(raw)) {
			helper.assertTrue(in.readInt() == 0xCAFEBABE, path + " is not a class file");
			in.readUnsignedShort();
			in.readUnsignedShort();
			int count = in.readUnsignedShort();
			String[] utf = new String[count];
			int[] a = new int[count];
			int[] b = new int[count];
			int[] tag = new int[count];
			for (int i = 1; i < count; i++) {
				tag[i] = in.readUnsignedByte();
				switch (tag[i]) {
					case 1 -> utf[i] = in.readUTF();
					case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
					case 3, 4 -> in.readInt();
					case 5, 6 -> { in.readLong(); i++; }
					case 9, 10, 11, 12, 17, 18 -> { a[i] = in.readUnsignedShort(); b[i] = in.readUnsignedShort(); }
					case 15 -> { in.readUnsignedByte(); a[i] = in.readUnsignedShort(); }
					default -> throw new IOException("Unknown constant tag " + tag[i]);
				}
			}
			String[] refs = new String[count];
			for (int i = 1; i < count; i++) {
				if (tag[i] == 10 || tag[i] == 11) {
					refs[i] = utf[a[a[i]]] + "." + utf[a[b[i]]] + utf[b[b[i]]];
				}
			}
			in.readUnsignedShort();
			in.readUnsignedShort();
			in.readUnsignedShort();
			in.skipBytes(2 * in.readUnsignedShort());
			int fields = in.readUnsignedShort();
			for (int f = 0; f < fields; f++) {
				in.skipBytes(6);
				int attrs = in.readUnsignedShort();
				for (int t = 0; t < attrs; t++) {
					in.readUnsignedShort();
					in.skipBytes(in.readInt());
				}
			}
			boolean found = false;
			int methods = in.readUnsignedShort();
			for (int m = 0; m < methods; m++) {
				in.readUnsignedShort();
				String mName = utf[in.readUnsignedShort()];
				String mDesc = utf[in.readUnsignedShort()];
				int attrs = in.readUnsignedShort();
				for (int t = 0; t < attrs; t++) {
					String attr = utf[in.readUnsignedShort()];
					byte[] body = new byte[in.readInt()];
					in.readFully(body);
					if (name.equals(mName) && (desc == null || desc.equals(mDesc)) && "Code".equals(attr)) {
						found = true;
						if (codeInvokes(body, refs, target)) return true;
					}
				}
			}
			helper.assertTrue(found, "Method " + className + "." + name + (desc != null ? desc : "") + " not found");
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
		}
		return false;
	}

	// byte scan of a small method body for invokevirtual/special/static/interface of target
	private static boolean codeInvokes(byte[] codeAttr, String[] refs, String target) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(codeAttr));
		in.skipBytes(4);
		byte[] code = new byte[in.readInt()];
		in.readFully(code);
		for (int pc = 0; pc + 2 < code.length; pc++) {
			int op = code[pc] & 0xFF;
			if (op >= 0xB6 && op <= 0xB9) {
				int idx = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
				if (idx > 0 && idx < refs.length && target.equals(refs[idx])) return true;
			}
		}
		return false;
	}
}
