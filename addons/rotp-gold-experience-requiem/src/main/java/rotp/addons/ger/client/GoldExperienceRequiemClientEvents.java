package rotp.addons.ger.client;

import java.util.Set;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.api.client.render.ClientSkyRenderers;
import rotp.core.api.client.render.ObserverWorldRenderPolicies;
import rotp.core.api.client.render.ObserverWorldRenderPolicyProvider.Pass;
import rotp.core.init.ModEntityTypes;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.client.GerRtzVfxSettings.Mode;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(
        modid = GoldExperienceRequiemAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class GoldExperienceRequiemClientEvents {
    private static final GerRtzCosmicSkyRenderer RTZ_COSMIC_SKY =
            new GerRtzCosmicSkyRenderer();
    private static boolean policiesRegistered;

    private GoldExperienceRequiemClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        registerClientPolicies();
        event.registerEntityRenderer(
                GoldExperienceRequiemAddon.GER_STAND_ENTITY.get(),
                GerStandRenderer::new);
        event.registerEntityRenderer(
                GoldExperienceRequiemAddon.BEAM_ENTITY.get(),
                GerBeamRenderer::new);
        event.registerEntityRenderer(
                GoldExperienceRequiemAddon.FIELD_ENTITY.get(),
                GerFieldRenderer::new);
    }

    /** The 1.16 addon attached its beetle-arrow layer to the core Gold Experience renderer. */
    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        EntityRenderer<?> renderer = event.getRenderer(
                ModEntityTypes.GOLD_EXPERIENCE_STAND.get());
        if (renderer instanceof LivingEntityRenderer livingRenderer) {
            livingRenderer.addLayer(
                    new GerBeetleArrowLayer(livingRenderer));
        }
    }

    @SubscribeEvent
    public static void registerShaders(
            RegisterShadersEvent event) {
        GerRtzCosmicSkyRenderer.onRegisterShaders(event);
    }

    @SubscribeEvent
    public static void registerGuiLayers(
            RegisterGuiLayersEvent event) {
        event.registerBelowAll(
                GoldExperienceRequiemAddon.id(
                        "rtz_golden_fog_shader_pack"),
                new GerRtzGoldenFogShaderPackLayer());
    }

    private static void registerClientPolicies() {
        if (policiesRegistered) {
            return;
        }
        policiesRegistered = true;
        AbilitySelectionVisualPolicies.register(
                GoldExperienceRequiemAddon.id("life_shot_selection"),
                GoldExperienceRequiemAddon::lifeShotSelectionTint);
        ClientSkyRenderers.register(
                GoldExperienceRequiemAddon.id(
                        "rtz_cosmic_sky"),
                query -> GerRtzClientState.activeMode()
                                == Mode.TIME_ERASE
                        ? RTZ_COSMIC_SKY
                        : null);
        ObserverWorldRenderPolicies.register(
                GoldExperienceRequiemAddon.id(
                        "rtz_cosmic_sky_terrain"),
                context -> GerRtzClientState.activeMode()
                                == Mode.TIME_ERASE
                        ? Set.of(Pass.TERRAIN)
                        : Set.of());
    }
}
