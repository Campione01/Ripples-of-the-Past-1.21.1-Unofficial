package com.github.standobyte.jojo.client.render.armor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class StoneMaskArmorRenderContractSmokeTest {
	private static final List<String> MASK_TEXTURES = List.of(
			"stone_mask.png",
			"stone_mask_activated.png",
			"aja_stone_mask.png",
			"aja_stone_mask_activated.png");

	private StoneMaskArmorRenderContractSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		Path root = Path.of(System.getProperty("user.dir"));
		Path main = root.resolve("src/main/java");

		String materials = read(main.resolve(
				"com/github/standobyte/jojo/init/"
						+ "ModArmorMaterials.java"));
		check(materials.contains(
				"DeferredHolder<ArmorMaterial, ArmorMaterial> STONE_MASK"),
				"stone masks must own a dedicated armor material");
		check(materials.contains(
				"new ArmorMaterial.Layer(JojoMod.resLoc(\"stone_mask\"))"),
				"stone-mask material must have exactly one core layer");

		String item = read(main.resolve(
				"com/github/standobyte/jojo/item/StoneMaskItem.java"));
		check(item.contains("super(ModArmorMaterials.STONE_MASK"),
				"stone masks must not use the leather armor material");
		check(!item.contains("ArmorMaterials.LEATHER"),
				"vanilla leather fallback must remain removed");
		check(item.contains("ArmorMaterial.Layer layer")
				&& item.contains("return getArmorTexture(stack);"),
				"armor pass must use the per-stack activated texture");
		check(item.contains("? \"_activated\""),
				"activated texture suffix must remain dynamic");

		String extensions = read(main.resolve(
				"com/github/standobyte/jojo/client/render/armor/"
						+ "StoneMaskArmorClientExtensions.java"));
		check(extensions.contains("getHumanoidArmorModel("),
				"stone-mask item extension must own the armor model");
		check(extensions.contains("STONE_MASK_ARMOR"),
				"stone-mask item extension must bake the canonical model");
		check(!extensions.contains("getArmorLayerTintColor"),
				"stone-mask rendering must not hide a second armor pass");

		String renderers = read(main.resolve(
				"com/github/standobyte/jojo/client/"
						+ "ModEntityTypeRenderers.java"));
		check(renderers.contains(
				"STONE_MASK_ARMOR, StoneMaskArmorModel::createBodyLayer"),
				"canonical stone-mask model layer must remain registered");
		check(!renderers.contains("new StoneMaskArmorLayer"),
				"core humanoid renderers must not install a second mask pass");

		String legacyLayer = read(main.resolve(
				"com/github/standobyte/jojo/client/render/armor/"
						+ "StoneMaskArmorLayer.java"));
		check(legacyLayer.contains("@Deprecated(forRemoval = false)"),
				"legacy mask layer must remain binary-compatible");
		check(legacyLayer.contains(".getArmorTexture(stack)"),
				"legacy layer must retain activated texture behavior");

		Path textures = root.resolve(
				"src/main/resources/assets/jojo_ripples/textures/armor");
		for (String texture : MASK_TEXTURES) {
			checkPng(textures.resolve(texture));
		}
	}

	private static void checkPng(Path path) {
		try {
			byte[] bytes = Files.readAllBytes(path);
			check(bytes.length >= 8
					&& bytes[0] == (byte) 0x89
					&& bytes[1] == 0x50
					&& bytes[2] == 0x4E
					&& bytes[3] == 0x47,
					"invalid stone-mask PNG: " + path);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
