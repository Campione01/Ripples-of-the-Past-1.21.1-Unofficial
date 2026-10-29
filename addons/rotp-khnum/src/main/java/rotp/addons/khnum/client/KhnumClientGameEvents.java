package rotp.addons.khnum.client;

import rotp.core.api.client.render.ScopedPlayerModelVisibility;
import rotp.addons.khnum.KhnumAddon;
import rotp.addons.khnum.disguise.DisguiseMode;
import rotp.addons.khnum.entity.KhnumControllerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.util.TriState;

@EventBusSubscriber(
        modid = KhnumAddon.MOD_ID,
        value = Dist.CLIENT)
public final class KhnumClientGameEvents {
    private KhnumClientGameEvents() {}

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity()
                instanceof AbstractClientPlayer player)) {
            return;
        }
        KhnumControllerEntity controller =
                KhnumAddon.controller(player);
        if (controller == null) {
            return;
        }
        if (controller.getDisguiseMode()
                        == DisguiseMode.PLAYER
                && !controller.getDisguisePlayerName().isBlank()) {
            event.setContent(Component.literal(
                    controller.getDisguisePlayerName()));
        }
        else if (controller.getDisguiseMode()
                == DisguiseMode.ENTITY) {
            event.setCanRender(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void renderFirstPersonArm(
            RenderArmEvent event) {
        float partialTick = Minecraft.getInstance()
                .getTimer()
                .getGameTimeDeltaPartialTick(true);
        int light = Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getPackedLightCoords(
                        event.getPlayer(), partialTick);
        if (KhnumDisguiseLayer.renderFirstPersonReplacement(
                event.getPlayer(),
                event.getArm(),
                event.getPoseStack(),
                event.getMultiBufferSource(),
                light,
                partialTick)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hidePlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity()
                instanceof AbstractClientPlayer player)) {
            return;
        }
        if (!KhnumDisguiseLayer.canRender(
                player, event.getPartialTick())) {
            return;
        }
        ScopedPlayerModelVisibility.hideForCurrentRender(
                player, event.getRenderer().getModel());
    }

    @SubscribeEvent
    public static void onLogout(
            ClientPlayerNetworkEvent.LoggingOut event) {
        KhnumDisguiseLayer.clearCache();
    }
}
