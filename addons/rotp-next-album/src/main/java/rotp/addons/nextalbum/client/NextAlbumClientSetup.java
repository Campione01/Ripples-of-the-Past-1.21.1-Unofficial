package rotp.addons.nextalbum.client;

import rotp.addons.nextalbum.NextAlbumAddon;
import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.client.render.AjaStoneCurioRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@EventBusSubscriber(
        modid = NextAlbumIds.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class NextAlbumClientSetup {
    private NextAlbumClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> CuriosRendererRegistry.register(
                NextAlbumAddon.AJA_STONE_NECKLACE.get(),
                AjaStoneCurioRenderer::new));
    }
}
