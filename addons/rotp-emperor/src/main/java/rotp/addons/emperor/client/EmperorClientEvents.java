package rotp.addons.emperor.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.NoopEntityRenderer;

import rotp.addons.emperor.EmperorAddon;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = EmperorAddon.MOD_ID, value = Dist.CLIENT)
public final class EmperorClientEvents {
    private EmperorClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                EmperorAddon.EMPEROR_STAND_ENTITY.get(),
                NoopEntityRenderer::new);
        event.registerEntityRenderer(
                EmperorAddon.EMPEROR_BULLET_ENTITY.get(),
                EmperorBulletRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                EmperorAddon.EMPEROR_ITEM.get(),
                EmperorAddon.id("stand_invisible"),
                (stack, level, entity, seed) ->
                        ClientGlobals.canSeeStands ? 0.0F : 1.0F));
    }
}
