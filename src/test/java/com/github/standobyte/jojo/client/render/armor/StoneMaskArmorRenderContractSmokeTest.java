package com.github.standobyte.jojo.client.render.armor;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.standobyte.jojo.client.itemrender.CustomItemRenderers;
import com.github.standobyte.jojo.client.render.armor.model.StoneMaskArmorModel;
import com.github.standobyte.jojo.item.StoneMaskItem;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

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
		checkExecutableExtensionRegistration();

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

		String itemRenderers = read(main.resolve(
				"com/github/standobyte/jojo/client/itemrender/"
						+ "CustomItemRenderers.java"));
		check(itemRenderers.contains(
								"ModItems.STONE_MASK.get()")
						&& itemRenderers.contains(
								"ModItems.AJA_STONE_MASK.get()")
						&& itemRenderers.contains(
								"ownsArmorPass(item)")
						&& itemRenderers.contains(
								"Missing dedicated armor model"),
				"ordinary and Aja masks need a live extension check");

		String model = read(main.resolve(
				"com/github/standobyte/jojo/client/render/armor/model/"
						+ "StoneMaskArmorModel.java"));
		check(model.contains(
				".addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 6.0F")
						&& model.contains(
								"LayerDefinition.create(mesh, 32, 32)")
						&& model.contains(
								"protected Iterable<ModelPart> bodyParts()")
						&& model.contains(
								"return Collections.emptyList();"),
				"canonical stone-mask model must remain face-only");

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
		check(legacyLayer.contains("shouldRenderLegacyPass")
						&& legacyLayer.contains("ownsArmorPass"),
				"legacy layer must yield to the registered armor pass");

		String armorMixin = read(main.resolve(
				"com/github/standobyte/jojo/mixin/client/render/"
						+ "HumanoidArmorLayerAddonVisibilityMixin.java"));
		check(armorMixin.contains("@WrapOperation")
						&& armorMixin.contains(
								"instanceof StoneMaskItem")
						&& armorMixin.contains(
								"DataComponentType<?> componentType")
						&& armorMixin.contains(
								"skipStoneMaskArmorTrim")
						&& armorMixin.contains(
								"skipStoneMaskArmorGlint")
						&& armorMixin.contains(
								"ItemStack;hasFoil()Z"),
				"worn stone masks must suppress armor trim and glint");

		Path textures = root.resolve(
				"src/main/resources/assets/jojo_ripples/textures/armor");
		for (String texture : MASK_TEXTURES) {
			checkPng(textures.resolve(texture));
		}
		System.out.println(
				"Stone-mask armor render contract: PASS");
	}

	private static void checkExecutableExtensionRegistration() {
		try {
			Constructor<RegisterClientExtensionsEvent> constructor =
					RegisterClientExtensionsEvent.class
							.getDeclaredConstructor();
			constructor.setAccessible(true);
			RegisterClientExtensionsEvent event =
					constructor.newInstance();
			Method subscriber = CustomItemRenderers.class
					.getDeclaredMethod(
							"registerItemRenderers",
							RegisterClientExtensionsEvent.class);
			check(subscriber.isAnnotationPresent(
								SubscribeEvent.class)
							&& IModBusEvent.class.isAssignableFrom(
									RegisterClientExtensionsEvent.class),
					"client extension listener must auto-route to the mod bus");

			Item ordinaryMask =
					allocateItemIdentity(StoneMaskItem.class);
			Item ajaMask =
					allocateItemIdentity(StoneMaskItem.class);
			Item ultimateNoStoneMask =
					allocateItemIdentity(StoneMaskItem.class);
			Item ultimateWithStoneMask =
					allocateItemIdentity(StoneMaskItem.class);
			Item unregisteredHelmet =
					allocateItemIdentity(ArmorItem.class);
			Method register = CustomItemRenderers.class
					.getDeclaredMethod(
							"registerStoneMaskArmorExtensions",
							RegisterClientExtensionsEvent.class,
							Item.class,
							Item.class);
			register.setAccessible(true);
			register.invoke(
					null, event, ordinaryMask, ajaMask);
			StoneMaskArmorClientExtensions.register(
					event,
					ultimateNoStoneMask,
					ultimateWithStoneMask);

			check(event.isItemRegistered(ordinaryMask)
							&& event.isItemRegistered(ajaMask)
							&& event.isItemRegistered(
									ultimateNoStoneMask)
							&& event.isItemRegistered(
									ultimateWithStoneMask),
					"all four mask items must be registered");

			StoneMaskArmorModel<LivingEntity> maskModel =
					new StoneMaskArmorModel<>(
							StoneMaskArmorModel
									.createBodyLayer()
									.bakeRoot());
			Field model = StoneMaskArmorClientExtensions.class
					.getDeclaredField("model");
			model.setAccessible(true);
			HumanoidModel<LivingEntity> original =
					new HumanoidModel<>(
							LayerDefinition.create(
											HumanoidModel.createMesh(
													CubeDeformation.NONE,
													0.0F),
											64,
											64)
									.bakeRoot());
			Item[] masks = {
					ordinaryMask,
					ajaMask,
					ultimateNoStoneMask,
					ultimateWithStoneMask
			};
			String[] maskNames = {
					"ordinary mask",
					"Aja mask",
					"Ultimate mask without stone",
					"Ultimate mask with stone"
			};
			for (int index = 0; index < masks.length; index++) {
				IClientItemExtensions extension =
						IClientItemExtensions.of(masks[index]);
				check(extension
								instanceof StoneMaskArmorClientExtensions,
						maskNames[index]
								+ " must resolve the mask extension");
				model.set(extension, maskModel);
				checkSingleMaskSubmission(
						maskNames[index],
						masks[index],
						extension,
						original,
						maskModel);
			}

			IClientItemExtensions ordinaryExtension =
					IClientItemExtensions.of(ordinaryMask);
			check(ordinaryExtension.getHumanoidArmorModel(
							null,
							null,
							EquipmentSlot.CHEST,
							original) == original,
					"mask extension must not replace non-head armor");

			IClientItemExtensions fallback =
					IClientItemExtensions.of(unregisteredHelmet);
			Model normalHelmetModel =
					fallback.getGenericArmorModel(
							null,
							null,
							EquipmentSlot.HEAD,
							original);
			int normalDefaultSubmissions =
					normalHelmetModel == original ? 1 : 0;
			int normalCustomSubmissions =
					normalHelmetModel == maskModel ? 1 : 0;
			if (StoneMaskArmorLayer.shouldRenderLegacyPass(
					unregisteredHelmet)) {
				normalCustomSubmissions++;
			}
			check(fallback == IClientItemExtensions.DEFAULT
							&& normalDefaultSubmissions == 1
							&& normalCustomSubmissions == 0,
					"normal helmets must submit only default geometry");
		}
		catch (ReflectiveOperationException exception) {
			throw new AssertionError(
					"failed to execute extension registration",
					exception);
		}
	}

	private static void checkSingleMaskSubmission(
			String maskName,
			Item mask,
			IClientItemExtensions extension,
			HumanoidModel<LivingEntity> original,
			StoneMaskArmorModel<LivingEntity> maskModel) {
		Model selectedModel = extension.getGenericArmorModel(
				null,
				null,
				EquipmentSlot.HEAD,
				original);
		int defaultSubmissions =
				selectedModel == original ? 1 : 0;
		int customSubmissions =
				selectedModel == maskModel ? 1 : 0;
		if (StoneMaskArmorLayer.shouldRenderLegacyPass(mask)) {
			customSubmissions++;
		}
		check(defaultSubmissions == 0
						&& customSubmissions == 1,
				maskName
						+ " must submit one custom model and no default model");
	}

	private static <T extends Item> T allocateItemIdentity(
			Class<T> itemType)
			throws ReflectiveOperationException {
		Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
		Field singleton = unsafeClass
				.getDeclaredField("theUnsafe");
		singleton.setAccessible(true);
		Object unsafe = singleton.get(null);
		Method allocateInstance = unsafeClass
				.getMethod("allocateInstance", Class.class);
		return itemType.cast(allocateInstance.invoke(
				unsafe, itemType));
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
