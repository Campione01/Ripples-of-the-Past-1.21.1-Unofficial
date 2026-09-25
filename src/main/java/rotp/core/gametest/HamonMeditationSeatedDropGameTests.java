package rotp.core.gametest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
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
 * 1.16 meditation.json keys the legacy KosmX "torso" part, which the library reads as the whole-body
 * transform ("body", offsets in blocks): y -0.597 from tick 29 sits the player on the ground, z -0.118
 * leans it forward, roll -0.58 tips it while sitting down. The port keeps that on its "body" bone
 * (it carries the legs too) in pixels; RotpAnimDefinition flips JSON y, so a negative y lowers the model.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonMeditationSeatedDropGameTests {
	private HamonMeditationSeatedDropGameTests() {}

	private static final float SEATED_FROM = 1.45F; // 1.16 tick 29
	private static final float DROP_PX = -0.5973925F * 16; // 1.16 torso y in blocks
	private static final float FORWARD_PX = -0.11809375F * 16;
	private static final String MODEL_PART_TR = "net/minecraft/client/model/geom/ModelPart.translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V";
	private static final String MAIN_BODY = "rotp/core/client/entityanim/playerbend/IPlayerBendModel.jojo_ripples$animMainBody()Lnet/minecraft/client/model/geom/ModelPart;";

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void meditationSeatsTheWholeBodyOnTheGround(GameTestHelper helper) {
		JsonObject bones = meditationBones(helper);
		JsonObject body = bones.getAsJsonObject("body");
		helper.assertTrue(body != null && body.has("position"), "meditation lost its whole-body ('body' bone) offset");
		JsonObject pos = body.getAsJsonObject("position");
		float[] start = sample(pos, 0);
		helper.assertTrue(Math.abs(start[1]) < 0.01F, "meditation must start standing, body y is " + start[1]);
		int seatedKeys = 0;
		for (Map.Entry<String, JsonElement> key : pos.entrySet()) {
			if (Float.parseFloat(key.getKey()) < SEATED_FROM - 1.0E-4F) continue;
			float[] v = vector(key.getValue());
			seatedKeys++;
			helper.assertTrue(Math.abs(v[1] - DROP_PX) < 0.15F,
					"seated meditation body y at " + key.getKey() + " s must be " + DROP_PX + " px (1.16 -0.597 block), got " + v[1]);
			helper.assertTrue(Math.abs(v[2] - FORWARD_PX) < 0.1F,
					"seated meditation body z at " + key.getKey() + " s must be " + FORWARD_PX + " px (1.16 -0.118 block), got " + v[2]);
		}
		helper.assertTrue(seatedKeys > 100, "meditation seated loop has only " + seatedKeys + " body keys");
		float[] mid = sample(pos, 5.0F);
		helper.assertTrue(mid[1] < -9.4F, "meditation body at 5 s must sit about 9.56 px lower, got " + mid[1]);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void meditationDropIsNotOnTheUpperBody(GameTestHelper helper) {
		JsonObject bones = meditationBones(helper);
		JsonObject torso = bones.getAsJsonObject("torso");
		if (torso != null) {
			for (String channel : new String[] {"position", "rotation"}) {
				JsonObject keys = torso.getAsJsonObject(channel);
				if (keys == null) continue;
				for (Map.Entry<String, JsonElement> key : keys.entrySet()) {
					float[] v = vector(key.getValue());
					helper.assertTrue(Math.abs(v[0]) + Math.abs(v[1]) + Math.abs(v[2]) < 1.0E-3F,
							"meditation keys the upper-body 'torso' " + channel + " at " + key.getKey() + " s; 1.16 moved the whole body");
				}
			}
		}
		// 1.16 roll -0.580 rad at tick 29 and -0.548 at tick 30 (whole body)
		JsonObject rot = bones.getAsJsonObject("body").getAsJsonObject("rotation");
		helper.assertTrue(rot != null, "meditation lost the whole-body lean");
		float r29 = sample(rot, 1.45F)[2];
		float r30 = sample(rot, 1.5F)[2];
		helper.assertTrue(Math.abs(r29 + 33.24F) < 0.3F, "meditation body roll at 1.45 s must be -33.24 deg, got " + r29);
		helper.assertTrue(Math.abs(r30 + 31.39F) < 0.3F, "meditation body roll at 1.5 s must be -31.39 deg, got " + r30);
		helper.assertTrue(Math.abs(sample(rot, 3.0F)[2]) < 0.01F, "meditation must sit upright in the loop");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void bodyBoneMovesTheLegs(GameTestHelper helper) {
		// the drop reaches the legs only if the renderers apply the main body before drawing them
		String model = "rotp/core/impl/powers/hamon/client/HamonMasterModel";
		String bends = "rotp/core/client/entityanim/playerbend/PlayerModelBends";
		helper.assertTrue(count(helper, model, "renderToBuffer", MAIN_BODY) >= 1,
				"HamonMasterModel.renderToBuffer no longer reads the main body bone");
		helper.assertTrue(count(helper, model, "renderToBuffer", MODEL_PART_TR) >= 2,
				"HamonMasterModel.renderToBuffer must apply both the main body and the torso bone");
		helper.assertTrue(count(helper, bends, "renderWithBends", MAIN_BODY) >= 1,
				"PlayerModelBends.renderWithBends no longer reads the main body bone");
		helper.assertTrue(count(helper, bends, "renderWithBends", MODEL_PART_TR) >= 2,
				"PlayerModelBends.renderWithBends must apply both the main body and the torso bone");
		helper.succeed();
	}

	private static JsonObject meditationBones(GameTestHelper helper) {
		try (InputStream in = HamonMeditationSeatedDropGameTests.class.getResourceAsStream("/assets/jojo_ripples/animations/hamon.animation.json")) {
			helper.assertTrue(in != null, "Missing hamon.animation.json");
			JsonObject clip = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("animations").getAsJsonObject("meditation");
			helper.assertTrue(clip != null, "hamon.animation.json lost meditation");
			return clip.getAsJsonObject("bones");
		} catch (IOException e) {
			throw new IllegalStateException("Could not read hamon.animation.json", e);
		}
	}

	private static float[] vector(JsonElement key) {
		JsonArray arr = key.isJsonArray() ? key.getAsJsonArray() : key.getAsJsonObject().getAsJsonArray("vector");
		return new float[] {arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
	}

	// linear lerp between keys, like the clip's "linear" easing
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

	// number of invoke* of target in the named method(s) of a class file read as a resource
	private static int count(GameTestHelper helper, String className, String method, String target) {
		String path = className + ".class";
		InputStream raw = HamonMeditationSeatedDropGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) raw = HamonMeditationSeatedDropGameTests.class.getClassLoader().getResourceAsStream(path);
		helper.assertTrue(raw != null, "Missing class file " + path);
		int hits = 0;
		boolean found = false;
		try (DataInputStream in = new DataInputStream(raw)) {
			helper.assertTrue(in.readInt() == 0xCAFEBABE, path + " is not a class file");
			in.readUnsignedShort();
			in.readUnsignedShort();
			int n = in.readUnsignedShort();
			String[] utf = new String[n];
			int[] a = new int[n];
			int[] b = new int[n];
			int[] tag = new int[n];
			for (int i = 1; i < n; i++) {
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
			String[] refs = new String[n];
			for (int i = 1; i < n; i++) {
				if (tag[i] == 10 || tag[i] == 11) refs[i] = utf[a[a[i]]] + "." + utf[a[b[i]]] + utf[b[b[i]]];
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
			int methods = in.readUnsignedShort();
			for (int m = 0; m < methods; m++) {
				in.readUnsignedShort();
				String mName = utf[in.readUnsignedShort()];
				in.readUnsignedShort();
				int attrs = in.readUnsignedShort();
				for (int t = 0; t < attrs; t++) {
					String attr = utf[in.readUnsignedShort()];
					byte[] body = new byte[in.readInt()];
					in.readFully(body);
					if (method.equals(mName) && "Code".equals(attr)) {
						found = true;
						hits += invokeCount(body, refs, target);
					}
				}
			}
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
		}
		helper.assertTrue(found, "Method " + className + "." + method + " not found");
		return hits;
	}

	// walks the bytecode so operand bytes are not read as opcodes
	private static int invokeCount(byte[] codeAttr, String[] refs, String target) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(codeAttr));
		in.skipBytes(4);
		byte[] code = new byte[in.readInt()];
		in.readFully(code);
		int hits = 0;
		int pc = 0;
		while (pc < code.length) {
			int op = code[pc] & 0xFF;
			if (op >= 0xB6 && op <= 0xB9) {
				int idx = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
				if (idx > 0 && idx < refs.length && target.equals(refs[idx])) hits++;
			}
			pc += insnLength(code, pc);
		}
		return hits;
	}

	private static int insnLength(byte[] code, int pc) {
		int op = code[pc] & 0xFF;
		switch (op) {
			case 0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3A, 0xA9, 0xBC: return 2;
			case 0x11, 0x13, 0x14, 0x84, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7, 0xA8,
					0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xBB, 0xBD, 0xC0, 0xC1, 0xC6, 0xC7: return 3;
			case 0xC5: return 4;
			case 0xB9, 0xBA, 0xC8, 0xC9: return 5;
			case 0xC4: return (code[pc + 1] & 0xFF) == 0x84 ? 6 : 4;
			case 0xAA: {
				int p = (pc + 4) & ~3;
				int lo = readInt(code, p + 4);
				int hi = readInt(code, p + 8);
				return p + 12 + 4 * (hi - lo + 1) - pc;
			}
			case 0xAB: {
				int p = (pc + 4) & ~3;
				return p + 8 + 8 * readInt(code, p + 4) - pc;
			}
			default: return 1;
		}
	}

	private static int readInt(byte[] code, int p) {
		return ((code[p] & 0xFF) << 24) | ((code[p + 1] & 0xFF) << 16) | ((code[p + 2] & 0xFF) << 8) | (code[p + 3] & 0xFF);
	}
}
