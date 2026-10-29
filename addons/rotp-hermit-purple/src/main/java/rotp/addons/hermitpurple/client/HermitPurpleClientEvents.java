package rotp.addons.hermitpurple.client;

import rotp.core.client.entityrender.entities.NoopEntityRenderer;

import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.network.HermitTargetPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
        modid = HermitPurpleAddon.MOD_ID,
        value = Dist.CLIENT)
public final class HermitPurpleClientEvents {
    private HermitPurpleClientEvents() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(
                HermitPurplePlayerAnimations::register);
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        HermitTargetPayloads.installClientOpenHandler(
                payload -> Minecraft.getInstance().execute(
                        () -> Minecraft.getInstance().setScreen(
                                new HermitTargetSelectionScreen(
                                        payload))));
        event.registerEntityRenderer(
                HermitPurpleAddon.HERMIT_PURPLE_ENTITY.get(),
                NoopEntityRenderer::new);
        event.registerEntityRenderer(
                HermitPurpleAddon.HP_VINE_ENTITY_TYPE.get(),
                HermitVineRenderer::new);
        event.registerEntityRenderer(
                HermitPurpleAddon.HP_GRAB_VINE_ENTITY.get(),
                HermitVineRenderer::new);
        event.registerEntityRenderer(
                HermitPurpleAddon
                        .HP_GRAPPLING_VINE_ENTITY.get(),
                HermitVineRenderer::new);
        event.registerEntityRenderer(
                HermitPurpleAddon.HP_BARRIER_VINE_ENTITY.get(),
                HermitVineRenderer::new);
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            addPlayerLayer(
                    renderer, skin == PlayerSkin.Model.SLIM);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addPlayerLayer(
            PlayerRenderer renderer, boolean slim) {
        renderer.addLayer(
                new HermitPurpleUserLayer(renderer, slim));
    }
}
