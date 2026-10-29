package rotp.addons.achtungbaby.client;

import rotp.addons.achtungbaby.AchtungBabyAddon;
import rotp.addons.achtungbaby.AchtungBabyService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(
        modid = AchtungBabyAddon.MOD_ID,
        value = Dist.CLIENT)
public final class AchtungBabyClientGameEvents {
    private AchtungBabyClientGameEvents() {}

    @SubscribeEvent
    public static void hidePerfectlyInvisiblePlayer(
            RenderPlayerEvent.Pre event) {
        if (AchtungBabyAddon.state(event.getEntity())
                .perfectInvisible()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void hidePerfectlyInvisibleHands(
            RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null
                && AchtungBabyAddon.state(player)
                        .perfectInvisible()) {
            event.setCanceled(true);
        }
    }

    /**
     * Ghost mode cannot open containers, its own inventory included.
     *
     * <p>1.16 did this by cancelling {@code ContainerScreen.init} and then setting the screen back to null.
     * {@code ScreenEvent.Opening} is the same thing without a mixin: cancelling it leaves the screen that was
     * already open - in the world, none - so the inventory key simply does nothing. The 1.16 mixin targeted the
     * container base class, not the player inventory alone, so every container screen is refused here too.
     */
    @SubscribeEvent
    public static void blockGhostContainerScreens(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof AbstractContainerScreen<?>
                && AchtungBabyService.isGhost(
                        Minecraft.getInstance().player)) {
            event.setCanceled(true);
        }
    }
}
