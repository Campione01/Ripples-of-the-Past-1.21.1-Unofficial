package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityrender.stand.StandHairSway;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StarPlatinumModel.manualAnimateHair swayed 43 hair parts every frame:
 * xRot 0.05 * sin(2pi (t + x) / 71), yRot 0.0125 * sin(2pi (t + y) / 31). The port dropped it.
 * StandEntityModel is a client class, so its bytecode is read as a resource instead of being loaded.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarPlatinumHairSwayGameTests {
	private StarPlatinumHairSwayGameTests() {}

	private static final String SP_GEO =
			"stand_skins/star_platinum/assets/jojo_ripples/geo/star_platinum.geo.json";
	private static final String SWAY = "rotp/core/client/entityrender/stand/StandHairSway";
	private static final float EPS = 1.5E-3F;

	@GameTest(template = "empty")
	public static void hairSwayFollows116Wave(GameTestHelper helper) {
		// x: 0.05 rad, 71-tick period, phase from pivot x
		near(helper, StandHairSway.xRot(0, 0), 0, "x sway at phase 0");
		near(helper, StandHairSway.xRot(17.75F, 0), 0.05F, "x sway peak at a quarter of 71 ticks");
		near(helper, StandHairSway.xRot(53.25F, 0), -0.05F, "x sway trough at three quarters of 71 ticks");
		near(helper, StandHairSway.xRot(0, 17.75F), 0.05F, "pivot x does not shift the x phase");
		near(helper, StandHairSway.xRot(10 + 71, 3), StandHairSway.xRot(10, 3), "x sway period is not 71 ticks");
		near(helper, StandHairSway.xRot(71000 + 17.75F, 0), 0.05F, "x sway loses precision on old entities");
		// y: 0.0125 rad, 31-tick period, phase from pivot y
		near(helper, StandHairSway.yRot(0, 0), 0, "y sway at phase 0");
		near(helper, StandHairSway.yRot(7.75F, 0), 0.0125F, "y sway peak at a quarter of 31 ticks");
		near(helper, StandHairSway.yRot(0, 7.75F), 0.0125F, "pivot y does not shift the y phase");
		near(helper, StandHairSway.yRot(7.75F + 31, 0), 0.0125F, "y sway period is not 31 ticks");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void starPlatinumSwaysThe116HairBones(GameTestHelper helper) {
		Set<String> expected = new HashSet<>(Set.of("hair2", "hair4", "hair6", "hair8"));
		for (int i = 10; i <= 48; i++) {
			expected.add("hair" + i);
		}
		helper.assertTrue(StandHairSway.STAR_PLATINUM_HAIR.size() == 43
				&& expected.equals(new HashSet<>(StandHairSway.STAR_PLATINUM_HAIR)),
				"Swayed hair bones differ from 1.16's 43 parts: " + StandHairSway.STAR_PLATINUM_HAIR);

		Set<String> geoBones = new HashSet<>();
		JsonObject geo = readJson(helper, SP_GEO);
		for (JsonElement geometry : geo.getAsJsonArray("minecraft:geometry")) {
			for (JsonElement bone : geometry.getAsJsonObject().getAsJsonArray("bones")) {
				geoBones.add(bone.getAsJsonObject().get("name").getAsString());
			}
		}
		for (String hair : StandHairSway.STAR_PLATINUM_HAIR) {
			helper.assertTrue(geoBones.contains(hair), "Star Platinum geo has no bone " + hair);
		}

		// the model resolves bones through collect: SP's geo yields all 43, a hairless model none
		List<String> found = StandHairSway.collect(name -> geoBones.contains(name) ? Optional.of(name) : Optional.empty());
		helper.assertTrue(found.equals(StandHairSway.STAR_PLATINUM_HAIR),
				"collect did not resolve every Star Platinum hair bone: " + found);
		helper.assertTrue(StandHairSway.collect(name -> Optional.empty()).isEmpty(),
				"collect invented hair bones for a model without them");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void standModelAddsHairSwayToPose(GameTestHelper helper) {
		String model = "rotp/core/client/entityrender/stand/StandEntityModel";
		Set<String> refs = memberRefs(helper, model);
		helper.assertTrue(refs.contains(SWAY + ".collect(Ljava/util/function/Function;)Ljava/util/List;"),
				"StandEntityModel does not collect the Star Platinum hair bones");
		helper.assertTrue(refs.contains(model + ".swayHair(F)V"),
				"StandEntityModel.setupAnim never applies the hair sway");
		helper.assertTrue(refs.contains("rotp/core/client/entityrender/stand/StandEntityRenderState.ageInTicks"),
				"The hair sway is not driven by the render state's age in ticks");
		helper.assertTrue(refs.contains(SWAY + ".xRot(FF)F"),
				"StandEntityModel does not add the hair xRot sway");
		helper.assertTrue(refs.contains(SWAY + ".yRot(FF)F"),
				"StandEntityModel does not add the hair yRot sway");
		helper.succeed();
	}

	private static void near(GameTestHelper helper, float got, float want, String message) {
		helper.assertTrue(Math.abs(got - want) < EPS, message + ": got " + got + ", want " + want);
	}

	private static JsonObject readJson(GameTestHelper helper, String assetPath) {
		try (InputStream in = StarPlatinumHairSwayGameTests.class.getResourceAsStream("/assets/jojo_ripples/" + assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			helper.fail("Cannot read " + assetPath + ": " + e);
			return new JsonObject();
		}
	}

	// owner.name (fields) and owner.name+descriptor (methods) of every member ref in the constant pool
	private static Set<String> memberRefs(GameTestHelper helper, String className) {
		String path = className + ".class";
		InputStream raw = StarPlatinumHairSwayGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = StarPlatinumHairSwayGameTests.class.getClassLoader().getResourceAsStream(path);
		}
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
			Set<String> refs = new HashSet<>();
			for (int i = 1; i < count; i++) {
				if (tag[i] == 9) {
					refs.add(utf[a[a[i]]] + "." + utf[a[b[i]]]);
				}
				else if (tag[i] == 10 || tag[i] == 11) {
					refs.add(utf[a[a[i]]] + "." + utf[a[b[i]]] + utf[b[b[i]]]);
				}
			}
			return refs;
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
			return Set.of();
		}
	}
}
