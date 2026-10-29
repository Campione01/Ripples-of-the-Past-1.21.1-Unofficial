package rotp.addons.ultimate_lifeform.client;

import java.util.OptionalInt;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.client.ModEntityTypeRenderers;
import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.client.render.armor.StoneMaskArmorClientExtensions;
import rotp.core.item.StoneMaskItem;
import rotp.core.impl.powers.pillarman.client.PillarmanBladesModel;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAbility;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
        modid = UltimateLifeformAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class UltimateLifeformClientEvents {
    private UltimateLifeformClientEvents() {}

    @SubscribeEvent
    public static void registerClientExtensions(
            RegisterClientExtensionsEvent event) {
        event.registerItem(
                new StoneMaskArmorClientExtensions(),
                UltimateLifeformAddon.STONE_MASK_UL_NOSTONE,
                UltimateLifeformAddon.STONE_MASK_UL_WITH_STONE);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        UltimateLifeformClientRenderReceipts.bootstrap();
        event.enqueueWork(() -> {
            requireStoneMaskArmorExtension(
                    "stone_mask_ul_nostone",
                    UltimateLifeformAddon
                            .STONE_MASK_UL_NOSTONE.get());
            requireStoneMaskArmorExtension(
                    "stone_mask_ul_with_stone",
                    UltimateLifeformAddon
                            .STONE_MASK_UL_WITH_STONE.get());
            UltimateLifeformPlayerAnimations.register();
            ItemProperties.register(
                    UltimateLifeformAddon
                            .STONE_MASK_UL_NOSTONE.get(),
                    UltimateLifeformAddon.id(
                            "stone_mask_ul_nostone_activated"),
                    (stack, level, entity, seed) ->
                            StoneMaskItem.getActivatedTicks(stack)
                                            > 0
                                    ? 1.0F
                                    : 0.0F);
            ItemProperties.register(
                    UltimateLifeformAddon
                            .STONE_MASK_UL_WITH_STONE.get(),
                    UltimateLifeformAddon.id(
                            "stone_mask_ul_with_stone_activated"),
                    (stack, level, entity, seed) ->
                            StoneMaskItem.getActivatedTicks(stack)
                                            > 0
                                    ? 1.0F
                                    : 0.0F);
        });
    }

    private static void requireStoneMaskArmorExtension(
            String itemId, Item item) {
        if (!(IClientItemExtensions.of(item)
                instanceof StoneMaskArmorClientExtensions)) {
            throw new IllegalStateException(
                    "Missing dedicated armor model for " + itemId);
        }
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                UltimateLifeformAddon.SCALE_PROJECTILE.get(),
                UltimateScaleProjectileRenderer::new);
        event.registerEntityRenderer(
                UltimateLifeformAddon.LASER_END.get(),
                UltimateProjectileRenderer::new);
        event.registerEntityRenderer(
                UltimateLifeformAddon.SQUID_TENTACLE.get(),
                UltimateProjectileRenderer::new);
        event.registerEntityRenderer(
                UltimateLifeformAddon.ULTIMATE_RABBIT.get(),
                UltimateRabbitRenderer::new);
        event.registerEntityRenderer(
                UltimateLifeformAddon.ULTIMATE_PHANTOM.get(),
                UltimatePhantomRenderer::new);
        event.registerEntityRenderer(
                UltimateLifeformAddon
                        .ULTIMATE_TURQUOISE_BLUE_OVERDRIVE.get(),
                UltimateAreaEffectRenderer::new);
        event.registerEntityRenderer(
                UltimateLifeformAddon
                        .ULTIMATE_SENDO_HAMON_OVERDRIVE.get(),
                UltimateAreaEffectRenderer::new);
        event.registerEntityRenderer(
                UltimateLifeformAddon.HAMON_BLOCK_CHARGE.get(),
                UltimateAreaEffectRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                UltimatePlayerVisualLayer.HEAD_FEATHERS,
                UltimatePlayerVisualLayer::createHeadFeathersLayer);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        EntityModelSet models = event.getEntityModels();
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) {
                continue;
            }
            addPlayerLayer(renderer, models);
            addBurnLayer(renderer);
        }
        // 1.16 ClientInit.addLayersToEntities: the burn on every living renderer
        for (EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof LivingEntityRenderer<?, ?> living) {
                addBurnLayer(living);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addBurnLayer(LivingEntityRenderer renderer) {
        renderer.addLayer(new UltimateHamonBurnLayer(renderer));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addPlayerLayer(
            PlayerRenderer renderer,
            EntityModelSet models) {
        renderer.addLayer(new UltimatePlayerVisualLayer(
                renderer,
                new PillarmanBladesModel<>(
                        models.bakeLayer(
                                ModEntityTypeRenderers
                                        .PILLARMAN_BLADES)),
                new ElytraModel<>(
                        models.bakeLayer(ModelLayers.ELYTRA)),
                models.bakeLayer(
                        UltimatePlayerVisualLayer.HEAD_FEATHERS)));
    }

    @SubscribeEvent
    public static void registerParticles(
            RegisterParticleProvidersEvent event) {
        // the first-person aura of a charged user picks its sprites from the
        // saved sets
        event.registerSpriteSet(
                UltimateLifeformAddon.HAMON_AURA_GREEN.get(),
                sprites -> {
                    CustomParticlesHelper.saveSpriteSet(
                            UltimateLifeformAddon.HAMON_AURA_GREEN.get(),
                            sprites);
                    return new UltimateParticle.Provider(sprites);
                });
        event.registerSpriteSet(
                UltimateLifeformAddon.HAMON_AURA_PURPLE.get(),
                UltimateParticle.Provider::new);
        event.registerSpriteSet(
                UltimateLifeformAddon.RAINBOW_SENDO.get(),
                UltimateParticle.Provider::new);
        event.registerSpriteSet(
                UltimateLifeformAddon.RAINBOW_SPARK.get(),
                sprites -> {
                    // The entity offset spark of a charged projectile
                    // picks its sprite from this saved set.
                    CustomParticlesHelper.saveSpriteSet(
                            UltimateLifeformAddon.RAINBOW_SPARK.get(),
                            sprites);
                    return new UltimateParticle.Provider(sprites);
                });
        event.registerSpriteSet(
                UltimateLifeformAddon.HAMON_AURA_RAINBOW.get(),
                sprites -> {
                    CustomParticlesHelper.saveSpriteSet(
                            UltimateLifeformAddon.HAMON_AURA_RAINBOW.get(),
                            sprites);
                    return new UltimateParticle.Provider(sprites);
                });
        event.registerSpriteSet(
                UltimateLifeformAddon
                        .HAMON_BREATH_AURA_RAINBOW.get(),
                UltimateParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerHud(
            RegisterGuiLayersEvent event) {
        AbilitySelectionVisualPolicies.register(
                UltimateLifeformAddon.id(
                        "ultimate_lifeform_mode_selection"),
                query -> {
                    if (!(query.ability()
                                    instanceof UltimateLifeformAbility
                                            ability)
                            || query.power() == null
                            || query.power().getPowerType()
                                    != UltimateLifeformAddon
                                            .ULTIMATE_LIFEFORM.get()
                            || !(query.power().getCurTypeData()
                                    instanceof UltimateLifeformData
                                            data)) {
                        return OptionalInt.empty();
                    }
                    boolean active = switch (ability.spec()) {
                        case STONE_FORM -> data.isStoneForm();
                        case NIGHT_VISION -> data.isNightVision();
                        case WALL_CLIMBING ->
                                data.isWallClimbing();
                        default -> false;
                    };
                    return active
                            ? OptionalInt.of(0xFF00FF00)
                            : OptionalInt.empty();
                });
        event.registerAboveAll(
                UltimateLifeformAddon.id(
                        "stone_form_blindness_iris"),
                new UltimateStoneFormBlindnessCompatLayer());
        // 1.16 had no own overlay: the energy bar sits in the core's ability
        // HUD (UltimateLifeformHud)
        UltimateLifeformHud.ensureAdded();
    }
}
