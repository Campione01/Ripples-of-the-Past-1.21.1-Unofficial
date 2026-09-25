package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.core.JojoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 registered the Mona Lisa (32x48) and Hands (16x16) paintings, so a placed painting could roll them.
 * The port had no painting_variant data, textures, placeable tag entry or names for them.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PaintingVariantGameTests {
	private PaintingVariantGameTests() {}

	private static final ResourceKey<PaintingVariant> MONA_LISA = ResourceKey.create(Registries.PAINTING_VARIANT, JojoMod.resLoc("mona_lisa"));
	private static final ResourceKey<PaintingVariant> HANDS = ResourceKey.create(Registries.PAINTING_VARIANT, JojoMod.resLoc("hands"));

	@GameTest(template = "empty")
	public static void monaLisaAndHandsAreNamedPlaceableVariants(GameTestHelper helper) {
		Registry<PaintingVariant> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.PAINTING_VARIANT);
		checkVariant(helper, registry, MONA_LISA, 2, 3);
		checkVariant(helper, registry, HANDS, 1, 1);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void twoByThreeWallRollsMonaLisa(GameTestHelper helper) {
		// stone wall 2 wide (x 1..2) and 3 tall (y 2..4); no vanilla variant is 2x3
		for (int x = 0; x <= 3; x++) {
			for (int y = 1; y <= 5; y++) {
				boolean wall = x >= 1 && x <= 2 && y >= 2 && y <= 4;
				helper.setBlock(new BlockPos(x, y, 3), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
				helper.setBlock(new BlockPos(x, y, 2), Blocks.AIR.defaultBlockState());
			}
		}
		Optional<Painting> painting = Painting.create(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 2)), Direction.NORTH);
		helper.assertTrue(painting.isPresent(), "no painting fits the 2x3 wall");
		Holder<PaintingVariant> variant = painting.get().getVariant();
		helper.assertTrue(variant.is(MONA_LISA), "a 2x3 wall must roll " + MONA_LISA.location() + ", got " + variant.getRegisteredName());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void singleBlockWallCanRollHands(GameTestHelper helper) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 3), x == 2 && y == 3 ? Blocks.STONE.defaultBlockState() : air);
				helper.setBlock(new BlockPos(x, y, 2), air);
			}
		}
		BlockPos pos = helper.absolutePos(new BlockPos(2, 3, 2));
		int hands = 0;
		// about 1 in 9 among the 1x1 variants; 200 misses in a row is ~6e-11
		for (int i = 0; i < 200; i++) {
			Optional<Painting> painting = Painting.create(helper.getLevel(), pos, Direction.NORTH);
			helper.assertTrue(painting.isPresent(), "no painting fits the 1x1 wall");
			if (painting.get().getVariant().is(HANDS)) {
				hands++;
			}
		}
		helper.assertTrue(hands > 0, HANDS.location() + " never rolled on a 1x1 wall in 200 placements");
		helper.succeed();
	}

	private static void checkVariant(GameTestHelper helper, Registry<PaintingVariant> registry, ResourceKey<PaintingVariant> key, int width, int height) {
		Optional<Holder.Reference<PaintingVariant>> holder = registry.getHolder(key);
		helper.assertTrue(holder.isPresent(), "painting variant " + key.location() + " is not registered");
		PaintingVariant variant = holder.get().value();
		helper.assertTrue(variant.width() == width && variant.height() == height,
				key.location() + " must be " + width + "x" + height + ", is " + variant.width() + "x" + variant.height());
		helper.assertTrue(variant.assetId().equals(key.location()), key.location() + " points at asset " + variant.assetId());
		helper.assertTrue(holder.get().is(PaintingVariantTags.PLACEABLE), key.location() + " is not in #minecraft:placeable");
		// 16 px per block, as the 1.16 textures
		int[] size = pngSize(helper, variant.assetId());
		helper.assertTrue(size[0] == width * 16 && size[1] == height * 16,
				variant.assetId() + " texture is " + size[0] + "x" + size[1]);
		for (String lang : new String[] { "en_us", "zh_cn" }) {
			JsonObject json = readLang(helper, lang);
			for (String part : new String[] { "title", "author" }) {
				String langKey = key.location().toLanguageKey("painting", part);
				helper.assertTrue(json.has(langKey), lang + " has no " + langKey);
			}
		}
	}

	private static InputStream open(String path) {
		InputStream in = PaintingVariantGameTests.class.getResourceAsStream("/" + path);
		return in != null ? in : PaintingVariantGameTests.class.getClassLoader().getResourceAsStream(path);
	}

	private static int[] pngSize(GameTestHelper helper, ResourceLocation assetId) {
		String path = "assets/" + assetId.getNamespace() + "/textures/painting/" + assetId.getPath() + ".png";
		try (InputStream in = open(path)) {
			helper.assertTrue(in != null, "Missing texture " + path);
			byte[] head = in.readNBytes(24);
			helper.assertTrue(head.length == 24, "Short PNG " + path);
			return new int[] { readInt(head, 16), readInt(head, 20) };
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	private static int readInt(byte[] b, int at) {
		return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
	}

	private static JsonObject readLang(GameTestHelper helper, String lang) {
		String path = "assets/" + JojoMod.MOD_ID + "/lang/" + lang + ".json";
		try (InputStream in = open(path)) {
			helper.assertTrue(in != null, "Missing " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}
}
