package rotp.addons.kingcrimson.client;

import java.util.Set;

import rotp.core.api.client.render.ClientSkyRenderers;
import rotp.core.api.client.render.LivingEntityMaterialTint;
import rotp.core.api.client.render.LivingEntityMaterialTintPolicies;
import rotp.core.api.client.render.ObserverWorldRenderPolicies;
import rotp.core.api.client.render.ObserverWorldRenderPolicyProvider.Pass;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.powersystem.standpower.StandUtil;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(
        modid = KingCrimsonAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class KingCrimsonClientEvents {
    private static final Set<Pass> TIME_ERASE_SUPPRESSED_PASSES =
            Set.of(
                    Pass.TERRAIN,
                    Pass.WEATHER,
                    Pass.PARTICLES,
                    Pass.BLOCK_ENTITIES);
    private static final KingCrimsonTimeEraseSkyRenderer
            TIME_ERASE_SKY_RENDERER =
                    new KingCrimsonTimeEraseSkyRenderer();
    private static boolean clientPoliciesRegistered;

    private KingCrimsonClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        registerClientPolicies();
        event.registerEntityRenderer(
                KingCrimsonAddon.KING_CRIMSON_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                KingCrimsonAddon.KC_AFTERIMAGE_ENTITY.get(),
                KingCrimsonAfterimageRenderer::new);
        event.registerEntityRenderer(
                KingCrimsonAddon.TIME_ERASE_DECOY_ENTITY.get(),
                KingCrimsonTimeEraseDecoyRenderer::new);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) {
                continue;
            }
            renderer.addLayer(
                    new KingCrimsonEpitaphLayer(renderer));
            renderer.addLayer(
                    KingCrimsonHalfBlindnessLayer.left(renderer));
            renderer.addLayer(
                    KingCrimsonHalfBlindnessLayer.right(renderer));
        }
    }

    @SubscribeEvent
    public static void registerGuiLayers(
            RegisterGuiLayersEvent event) {
        event.registerBelowAll(
                KingCrimsonAddon.id("time_erase_iris_sky"),
                new KingCrimsonTimeEraseIrisOverlay());
        // 1.16 drew the half blindness in RenderGameOverlayEvent.Pre(ALL): under the hotbar, hearts and chat
        event.registerBelow(
                VanillaGuiLayers.CAMERA_OVERLAYS,
                KingCrimsonAddon.id("half_blindness"),
                new KingCrimsonHalfBlindnessOverlay());
        event.registerAboveAll(
                KingCrimsonAddon.id("temporal"),
                new KingCrimsonTemporalOverlay());
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        KingCrimsonTimeEraseSkyRenderer.onRegisterShaders(event);
    }

    private static void registerClientPolicies() {
        if (clientPoliciesRegistered) {
            return;
        }
        clientPoliciesRegistered = true;
        ObserverWorldRenderPolicies.register(
                KingCrimsonAddon.id(
                        "time_erase_world_suppression"),
                context -> context.observer() != null
                        && KingCrimsonAddon
                                .temporalState(context.observer())
                                .isErasing(context.gameTime())
                                ? TIME_ERASE_SUPPRESSED_PASSES
                                : Set.of());
        ClientSkyRenderers.register(
                KingCrimsonAddon.id("time_erase_sky"),
                query -> KingCrimsonTemporalClientState.localTimeErase(
                                query.level())
                        ? TIME_ERASE_SKY_RENDERER
                        : null);
        LivingEntityMaterialTintPolicies.register(
                KingCrimsonAddon.id("time_erase_living_tint"),
                query -> timeEraseTint(query.entity()));
    }

    private static LivingEntityMaterialTint timeEraseTint(
            LivingEntity entity) {
        if (!KingCrimsonTemporalClientState.localTimeErase()
                || KingCrimsonAfterimageRenderer
                        .isRenderingAfterimage()) {
            return null;
        }

        LivingEntity standUser = StandUtil.getStandUser(entity);
        if (standUser instanceof KingCrimsonTimeEraseDecoyEntity) {
            return null;
        }
        if (standUser instanceof Player player
                && KingCrimsonAddon.temporalState(player).isErasing(
                        player.level().getGameTime())) {
            return null;
        }
        return originalArgb -> originalArgb & 0xFF000000
                | (originalArgb >> 17 & 0x7F) << 16;
    }
}
