package rotp.addons.cream.client;

import rotp.addons.cream.CreamAddon;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(
        modid = CreamAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class CreamHudVisibilityEvents {
    private static final CreamHudVisibilityLease HIDE_GUI_LEASE =
            new CreamHudVisibilityLease();

    private CreamHudVisibilityEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFramePre(
            RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean effectActive = minecraft.player != null
                && (minecraft.player.hasEffect(
                                CreamAddon.TRUE_BLINDNESS)
                        || minecraft.player.hasEffect(
                                CreamAddon.INSIDE_CREAM));
        minecraft.options.hideGui = HIDE_GUI_LEASE.beginFrame(
                effectActive, minecraft.options.hideGui);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFramePost(
            RenderFrameEvent.Post event) {
        release(Minecraft.getInstance());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLoggingOut(
            ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.hideGui = HIDE_GUI_LEASE.reset(
                minecraft.options.hideGui);
    }

    private static void release(Minecraft minecraft) {
        minecraft.options.hideGui = HIDE_GUI_LEASE.endFrame(
                minecraft.options.hideGui);
    }
}
