package com.github.standobyte.jojo.client;

import java.util.Optional;

import com.github.standobyte.jojo.adventure.npc.client.CharacterMobRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.AfterimageRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.BladeHatEntityModel;
import com.github.standobyte.jojo.client.entityrender.entities.BladeHatRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.BlockShardRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.ClackersModel;
import com.github.standobyte.jojo.client.entityrender.entities.ClackersRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.KnifeRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.LightBeamRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.MRFlameRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.MannequinRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.MolotovRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.NoopEntityRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.PillarmanTempleEngravingRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.RoadRollerModel;
import com.github.standobyte.jojo.client.entityrender.entities.RoadRollerRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.SCFlameRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.SimpleEntityModel;
import com.github.standobyte.jojo.client.entityrender.entities.SimpleEntityRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.SpaceRipperStingyEyesRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.StandArrowRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.TommyGunBulletRenderer;
import com.github.standobyte.jojo.client.entityrender.entities.v1_21_2plus.MannequinModel_1_21_2plus;
import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderer;
import com.github.standobyte.jojo.client.layer.FrozenLayer;
import com.github.standobyte.jojo.client.layer.GlovesLayer;
import com.github.standobyte.jojo.client.layer.HamonBurnLayer;
import com.github.standobyte.jojo.client.render.armor.StoneMaskArmorLayer;
import com.github.standobyte.jojo.client.render.armor.model.BladeHatArmorModel;
import com.github.standobyte.jojo.client.render.armor.model.BreathControlMaskModel;
import com.github.standobyte.jojo.client.render.armor.model.SatiporojaScarfArmorModel;
import com.github.standobyte.jojo.client.render.armor.model.StoneMaskArmorModel;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.mechanics.clothes.client.layer.HumanoidClothesLayer;
import com.github.standobyte.jojo.mrpresident.client.CocoJumboTurtleModel;
import com.github.standobyte.jojo.mrpresident.client.CocoJumboTurtleRenderer;
import com.github.standobyte.jojo.subsystems.soul.client.SoulRenderer;
import com.github.standobyte.jojoimpl.stands.crazydiamond.client.AngeloRockRenderer;
import com.github.standobyte.jojoimpl.stands.crazydiamond.client.CrazyDBlockBulletRenderer;
import com.github.standobyte.jojoimpl.stands.hierophant.client.HGBarrierRenderer;
import com.github.standobyte.jojoimpl.stands.hierophant.client.HGGrappleRenderer;
import com.github.standobyte.jojoimpl.stands.hierophant.client.HGStringRenderer;
import com.github.standobyte.jojoimpl.stands.goldexperience.client.GETransformationRenderer;
import com.github.standobyte.jojoimpl.stands.magiciansred.client.MRCrossfireHurricaneRenderer;
import com.github.standobyte.jojoimpl.stands.magiciansred.client.MRDetectorRenderer;
import com.github.standobyte.jojoimpl.stands.magiciansred.client.MRFireballRenderer;
import com.github.standobyte.jojoimpl.stands.magiciansred.client.MRRedBindRenderer;
import com.github.standobyte.jojoimpl.stands.silverchariot.client.SCRapierRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.CrimsonBubbleRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonBubbleBarrierRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonBubbleCutterRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonBubbleRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonCutterRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.EnergyRippleLayer;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonMasterModel;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonMasterRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonProjectileShieldRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonProtectionLayer;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonSpriteRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonZoomPunchRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.LeavesGliderRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.SatiporojaScarfBindingRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.SatiporojaScarfRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.SendoHamonOverdriveRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.SnakeMufflerRenderer;
import com.github.standobyte.jojoimpl.powers.hamon.client.TornadoOverdriveEffectLayer;
import com.github.standobyte.jojoimpl.powers.hamon.client.TurquoiseBlueOverdriveRenderer;
import com.github.standobyte.jojoimpl.powers.pillarman.client.PillarmanBodyPartRenderer;
import com.github.standobyte.jojoimpl.powers.pillarman.client.PillarmanBladesLayer;
import com.github.standobyte.jojoimpl.powers.pillarman.client.PillarmanBladesModel;
import com.github.standobyte.jojoimpl.powers.pillarman.client.PillarmanDivineSandstormRenderer;
import com.github.standobyte.jojoimpl.powers.pillarman.client.PillarmanStoneFormLayer;
import com.github.standobyte.jojoimpl.powers.pillarman.client.WindCloakLayer;
import com.github.standobyte.jojoimpl.powers.vampirism.client.HungryZombieRenderer;
import com.github.standobyte.jojoimpl.powers.vampirism.client.SRSEEyesLayer;
import com.github.standobyte.jojoimpl.powers.vampirism.client.VampireEyesLayer;
import com.github.standobyte.jojoimpl.powers.zombie.client.ZombieLayer;
import com.github.standobyte.jojoimpl.npc.rps.client.RockPaperScissorsKidRenderer;
import com.github.standobyte.jojoimpl.stands.starplatinum.client.SPStarFingerRenderer;
import com.github.standobyte.v1_21_4_stuff.Reminder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.FireworkEntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class ModEntityTypeRenderers {
	
	// Entity renderers
	
	@SubscribeEvent
	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModEntityTypes.CHARACTER.get(), CharacterMobRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HUMANOID_STAND.get(), StandEntityRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.STAR_PLATINUM_STAND.get(), StandEntityRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.THE_WORLD_STAND.get(), StandEntityRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HIEROPHANT_GREEN_STAND.get(), StandEntityRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SILVER_CHARIOT_STAND.get(), StandEntityRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MAGICIANS_RED_STAND.get(), StandEntityRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.CRAZY_DIAMOND_STAND.get(), StandEntityRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.GOLD_EXPERIENCE_STAND.get(), StandEntityRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MANNEQUIN.get(), MannequinRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.BLOCK_SHARD.get(), BlockShardRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.OBJECT.get(), ctx -> new HamonSpriteRenderer<>(ctx,
				JojoMod.resLoc("textures/particle/tooth.png"), 0.1F));
		event.registerEntityRenderer(ModEntityTypes.NUGGET_BEARING.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.5f, false));
		event.registerEntityRenderer(ModEntityTypes.CLACKERS.get(), ClackersRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.ROAD_ROLLER.get(), RoadRollerRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.BLADE_HAT.get(), BladeHatRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.KNIFE.get(), KnifeRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MOLOTOV.get(), MolotovRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.TOMMY_GUN_BULLET.get(), TommyGunBulletRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.AFTERIMAGE.get(), AfterimageRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.AJA_STONE_BEAM.get(), LightBeamRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SPACE_RIPPER_STINGY_EYES.get(), SpaceRipperStingyEyesRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HUNGRY_ZOMBIE.get(), HungryZombieRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HAMON_MASTER.get(), HamonMasterRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HAMON_BUBBLE.get(), HamonBubbleRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HAMON_BUBBLE_CUTTER.get(), HamonBubbleCutterRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HAMON_CUTTER.get(), HamonCutterRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HAMON_ZOOM_PUNCH.get(), HamonZoomPunchRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SENDO_HAMON_OVERDRIVE.get(), SendoHamonOverdriveRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.TURQUOISE_BLUE_OVERDRIVE.get(), TurquoiseBlueOverdriveRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HAMON_BUBBLE_BARRIER.get(), HamonBubbleBarrierRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HAMON_PROJECTILE_SHIELD.get(), HamonProjectileShieldRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HAMON_BLOCK_CHARGE.get(), NoopEntityRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.LEAVES_GLIDER.get(), LeavesGliderRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.CRIMSON_BUBBLE.get(), CrimsonBubbleRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SATIPOROJA_SCARF.get(), SatiporojaScarfRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SATIPOROJA_SCARF_BINDING.get(), SatiporojaScarfBindingRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SNAKE_MUFFLER.get(), SnakeMufflerRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.CD_BLOOD_CUTTER.get(), ctx -> new SimpleEntityRenderer<>(ctx)
				.initTexture(JojoMod.resLoc("textures/entity/blood_cutter.png"), true)
				.initResourceModel(JojoMod.resLoc("blood_cutter"), SimpleEntityModel::new, true)
				.offsetModelByEntityHeight(false));
		event.registerEntityRenderer(ModEntityTypes.RPS_KID.get(), RockPaperScissorsKidRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.CD_BLOCK_BULLET.get(), ctx -> new CrazyDBlockBulletRenderer(ctx)
				.initResourceModel(JojoMod.resLoc("block_bullet"), SimpleEntityModel::new, true));
		event.registerEntityRenderer(ModEntityTypes.EYE_OF_ENDER_INSIDE.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.0F, false));
		event.registerEntityRenderer(ModEntityTypes.FIREWORK_INSIDE.get(), FireworkEntityRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.ANGELO_ROCK.get(), AngeloRockRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SP_STAR_FINGER.get(), SPStarFingerRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HG_EMERALD.get(), ctx -> new SimpleEntityRenderer<>(ctx)
				.initTexture(JojoMod.resLoc("textures/entity/projectiles/hg_emerald.png"), true)
				.initResourceModel(JojoMod.resLoc("hg_emerald"), SimpleEntityModel::new, true)
				.offsetModelByEntityHeight(false));
		event.registerEntityRenderer(ModEntityTypes.HG_STRING.get(), HGStringRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HG_GRAPPLE.get(), HGGrappleRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.HG_BARRIER.get(), HGBarrierRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MR_FLAME.get(), MRFlameRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.PILLAR_MAN_HORN.get(), ctx -> PillarmanBodyPartRenderer.horn(ctx));
		event.registerEntityRenderer(ModEntityTypes.PILLAR_MAN_RIBS.get(), ctx -> PillarmanBodyPartRenderer.rib(ctx));
		event.registerEntityRenderer(ModEntityTypes.PILLAR_MAN_VEINS.get(), ctx -> PillarmanBodyPartRenderer.vein(ctx));
		event.registerEntityRenderer(ModEntityTypes.PILLAR_MAN_DIVINE_SANDSTORM.get(), PillarmanDivineSandstormRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.PILLARMAN_TEMPLE_ENGRAVING.get(), PillarmanTempleEngravingRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SC_FLAME.get(), SCFlameRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MR_DETECTOR.get(), MRDetectorRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MR_FIREBALL.get(), MRFireballRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MR_CROSSFIRE_HURRICANE.get(), MRCrossfireHurricaneRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MR_CROSSFIRE_HURRICANE_SPECIAL.get(), MRCrossfireHurricaneRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MR_RED_BIND.get(), MRRedBindRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SC_RAPIER.get(), SCRapierRenderer::new);
		// стандо добавь пж initFromRenderer()
		event.registerEntityRenderer(ModEntityTypes.STAND_ARROW.get(), StandArrowRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SOUL.get(), SoulRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.GE_LIFEFORM_TRANSFORMATION.get(), GETransformationRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.COCO_JUMBO_TURTLE.get(), CocoJumboTurtleRenderer::new);
	}

	// Hardcoded models
	
	public static final ModelLayerLocation MANNEQUIN = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "mannequin"));
	public static final ModelLayerLocation MANNEQUIN_SLIM = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "mannequin_slim"));
	public static final ModelLayerLocation HAMON_PROTECTION = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "hamon_protection"));
	public static final ModelLayerLocation HAMON_MASTER = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "hamon_master"));
	public static final ModelLayerLocation HAMON_MASTER_EXTRA = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "hamon_master_extra"));
	public static final ModelLayerLocation WIND_CLOAK = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "wind_cloak"));
	public static final ModelLayerLocation PILLARMAN_BLADES = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "pillarman_blades"));
	public static final ModelLayerLocation CLACKERS = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "clackers"));
	public static final ModelLayerLocation ROAD_ROLLER = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "road_roller"));
	public static final ModelLayerLocation BLADE_HAT = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "blade_hat"));
	public static final ModelLayerLocation BLADE_HAT_ARMOR = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "blade_hat_armor"));
	public static final ModelLayerLocation GLOVES = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "gloves"));
	public static final ModelLayerLocation GLOVES_SLIM = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "gloves_slim"));
	public static final ModelLayerLocation COCO_JUMBO_TURTLE = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "coco_jumbo_turtle"));
	public static final ModelLayerLocation STONE_MASK_ARMOR = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "stone_mask_armor"));
	public static final ModelLayerLocation BREATH_CONTROL_MASK = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "breath_control_mask"));
	public static final ModelLayerLocation SATIPOROJA_SCARF_ARMOR = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "satiporoja_scarf_armor"));
//	public static final ModelLayerLocation MANNEQUIN_SMALL = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "mannequin_small"));
//	public static final ModelLayerLocation MANNEQUIN_SLIM_SMALL = mainLayer(ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "mannequin_slim_small"));
	
	@SubscribeEvent
	public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		LayerDefinition mannequin = MannequinModel_1_21_2plus.createMesh(CubeDeformation.NONE, false);
		LayerDefinition mannequinSlim = MannequinModel_1_21_2plus.createMesh(CubeDeformation.NONE, true);
		event.registerLayerDefinition(ModEntityTypeRenderers.MANNEQUIN, () -> mannequin);
		event.registerLayerDefinition(ModEntityTypeRenderers.MANNEQUIN_SLIM, () -> mannequinSlim);
		event.registerLayerDefinition(ModEntityTypeRenderers.HAMON_PROTECTION,
				() -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.20F), 0.0F), 64, 64));
		event.registerLayerDefinition(ModEntityTypeRenderers.HAMON_MASTER, HamonMasterModel::createBodyLayer);
		event.registerLayerDefinition(ModEntityTypeRenderers.HAMON_MASTER_EXTRA, HamonMasterModel::createExtraLayer);
		event.registerLayerDefinition(ModEntityTypeRenderers.WIND_CLOAK,
				() -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.50F), 0.0F), 64, 64));
		event.registerLayerDefinition(ModEntityTypeRenderers.PILLARMAN_BLADES,
				() -> PillarmanBladesModel.createBodyLayer(CubeDeformation.NONE));
		event.registerLayerDefinition(ModEntityTypeRenderers.CLACKERS, ClackersModel::createBodyLayer);
		event.registerLayerDefinition(ModEntityTypeRenderers.ROAD_ROLLER, RoadRollerModel::createBodyLayer);
		event.registerLayerDefinition(ModEntityTypeRenderers.BLADE_HAT, BladeHatEntityModel::createBodyLayer);
		event.registerLayerDefinition(ModEntityTypeRenderers.BLADE_HAT_ARMOR, BladeHatArmorModel::createBodyLayer);
		event.registerLayerDefinition(ModEntityTypeRenderers.GLOVES,
				() -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.3F), false), 64, 64));
		event.registerLayerDefinition(ModEntityTypeRenderers.GLOVES_SLIM,
				() -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.3F), true), 64, 64));
		event.registerLayerDefinition(ModEntityTypeRenderers.COCO_JUMBO_TURTLE, CocoJumboTurtleModel::createBodyLayer);
		event.registerLayerDefinition(ModEntityTypeRenderers.STONE_MASK_ARMOR, StoneMaskArmorModel::createBodyLayer);
		event.registerLayerDefinition(ModEntityTypeRenderers.BREATH_CONTROL_MASK, BreathControlMaskModel::createBodyLayer);
		event.registerLayerDefinition(ModEntityTypeRenderers.SATIPOROJA_SCARF_ARMOR, SatiporojaScarfArmorModel::createBodyLayer);
		Reminder.toRegisterBabyModels();
//		event.registerLayerDefinition(ModEntityRenderers.MANNEQUIN_SMALL, () -> mannequin.apply(HumanoidModel.BABY_TRANSFORMER));
//		event.registerLayerDefinition(ModEntityRenderers.MANNEQUIN_SLIM_SMALL, () -> mannequinSlim.apply(HumanoidModel.BABY_TRANSFORMER));
	}
	
	public static ModelLayerLocation mainLayer(ResourceLocation modelPath) {
		return new ModelLayerLocation(modelPath, "main");
	}
	
	// Entity render state extensions
	
//	public static final ContextKey<HumanoidClothesRSExtension> CLOTHES_CONTEXT = new ContextKey<>(JojoMod.resLoc("clothes"));
//
//	@SuppressWarnings("serial")
//	@SubscribeEvent
//	public static void registerRSModifiers(RegisterRenderStateModifiersEvent event) {
//		event.registerEntityModifier(
//				new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>(){},
//				(entity, state) -> {
//					if (HumanoidClothesRSExtension.reusedInstance.extract(entity)) {
//						state.setRenderData(CLOTHES_CONTEXT, HumanoidClothesRSExtension.reusedInstance);
//					}
//				});
//	}
	
	// Entity renderer layers
	
	@SubscribeEvent
	public static void addLayers(EntityRenderersEvent.AddLayers event) {
		var renderers = Minecraft.getInstance().getEntityRenderDispatcher();
		for (var renderer : renderers.renderers.values()) {
			castToLiving(renderer).ifPresent(livingRenderer -> {
				addLivingLayers(livingRenderer);
				castToHumanoid(renderer).ifPresentOrElse(
						ModEntityTypeRenderers::addHumanoidLayers,
						() -> addNonHumanoidLivingLayers(livingRenderer));
			});
		}
		for (var playerRendererEntry : renderers.getSkinMap().entrySet()) {
			var playerRenderer = playerRendererEntry.getValue();
			boolean slim = playerRendererEntry.getKey() == PlayerSkin.Model.SLIM;
			castToLiving(playerRenderer).ifPresent(livingRenderer -> {
				addLivingLayers(livingRenderer);
				castToHumanoid(playerRenderer).ifPresent(humanoidRenderer -> {
					addHumanoidLayers(humanoidRenderer);
					addPlayerLayers(humanoidRenderer);
					addGlovesLayer(humanoidRenderer, slim);
				});
			});
		}
	}

	private static <T extends LivingEntity, M extends EntityModel<T>> void addLivingLayers(LivingEntityRenderer<T, M> renderer) {
		renderer.addLayer(new HamonBurnLayer<>(renderer));
	}
	
	private static <T extends LivingEntity, M extends HumanoidModel<T>> void addHumanoidLayers(LivingEntityRenderer<T, M> renderer) {
		renderer.addLayer(new FrozenLayer<>(renderer, FrozenLayer.BIPED_PATH));
		renderer.addLayer(new StoneMaskArmorLayer<>(renderer));
		renderer.addLayer(new HumanoidClothesLayer<>(renderer));
		renderer.addLayer(new PillarmanStoneFormLayer<>(renderer));
		renderer.addLayer(new ZombieLayer<>(renderer));
		renderer.addLayer(new PillarmanBladesLayer<>(renderer,
				new PillarmanBladesModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(PILLARMAN_BLADES))));
	}

	private static <T extends LivingEntity, M extends HumanoidModel<T>> void addPlayerLayers(LivingEntityRenderer<T, M> renderer) {
		renderer.addLayer(new TornadoOverdriveEffectLayer<>(renderer));
		renderer.addLayer(new EnergyRippleLayer<>(renderer));
		renderer.addLayer(new WindCloakLayer<>(renderer,
				new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(WIND_CLOAK))));
		renderer.addLayer(new VampireEyesLayer<>(renderer));
		renderer.addLayer(new SRSEEyesLayer<>(renderer));
		renderer.addLayer(new HamonProtectionLayer<>(renderer,
				new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(HAMON_PROTECTION))));
	}

	private static <T extends LivingEntity, M extends HumanoidModel<T>> void addGlovesLayer(
			LivingEntityRenderer<T, M> renderer, boolean slim) {
		renderer.addLayer(new GlovesLayer<>(renderer, slim));
	}

	private static <T extends LivingEntity, M extends EntityModel<T>> void addNonHumanoidLivingLayers(LivingEntityRenderer<T, M> renderer) {
		renderer.addLayer(new FrozenLayer<>(renderer, FrozenLayer.NON_BIPED_PATH));
	}
	
	
	
	@SuppressWarnings("unchecked")
	public static <T extends LivingEntity, M extends EntityModel<T>> Optional<LivingEntityRenderer<T, M>> castToLiving(EntityRenderer<?> renderer) {
		if (renderer instanceof LivingEntityRenderer livingRenderer) {
			return Optional.of((LivingEntityRenderer<T, M>) livingRenderer);
		}
		return Optional.empty();
	}

	@SuppressWarnings("unchecked")
	public static <T extends LivingEntity, M extends HumanoidModel<T>> Optional<LivingEntityRenderer<T, M>> castToHumanoid(EntityRenderer<?> renderer) {
		if (renderer instanceof LivingEntityRenderer livingRenderer && livingRenderer.getModel() instanceof HumanoidModel /* && renderer.reusedState instanceof HumanoidRenderState*/) {
			var humanoid = (LivingEntityRenderer<T, M>) renderer;
			return Optional.of(humanoid);
		}
		return Optional.empty();
	}
}
