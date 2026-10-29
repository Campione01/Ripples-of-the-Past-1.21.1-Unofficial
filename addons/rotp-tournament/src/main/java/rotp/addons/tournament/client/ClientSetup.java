package rotp.addons.tournament.client;

import rotp.addons.tournament.AddonMain;
import rotp.addons.tournament.init.AddonBlocks;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
        modid = AddonMain.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(
                    AddonBlocks.SPIKES.get(),
                    RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                    AddonBlocks.FAKE_SPIKES.get(),
                    RenderType.cutout());
        });
    }
}
