package rotp.addons.thesun.client;

import rotp.core.api.client.render.ClientSkyPresentationProviders;

import rotp.addons.thesun.TheSunAddon;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(
        modid = TheSunAddon.MOD_ID,
        value = Dist.CLIENT)
public final class TheSunClientEvents {
    static {
        ClientSkyPresentationProviders.register(
                ResourceLocation.fromNamespaceAndPath(
                        TheSunAddon.MOD_ID, "day_sky"),
                TheSunDaylightController::presentation);
    }

    private TheSunClientEvents() {}

    @SubscribeEvent
    public static void onFogColor(
            ViewportEvent.ComputeFogColor event) {
        if (!TheSunDaylightController.isActive()) {
            return;
        }
        event.setRed(TheSunDaylightController.skyRed(
                event.getRed()));
        event.setGreen(TheSunDaylightController.skyGreen(
                event.getGreen()));
        event.setBlue(TheSunDaylightController.skyBlue(
                event.getBlue()));
    }

}
