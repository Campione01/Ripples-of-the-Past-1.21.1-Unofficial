package rotp.addons.greenday.client;

import rotp.core.api.client.render.ScopedPlayerModelVisibility;
import rotp.core.api.client.render.ScopedPlayerModelVisibility.Part;

import rotp.addons.greenday.GreenDayAddon;
import rotp.addons.greenday.GreenDayGameEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(
        modid = GreenDayAddon.MOD_ID,
        value = Dist.CLIENT)
public final class GreenDayLimbVisibilityRenderer {
    private GreenDayLimbVisibilityRenderer() {}

    @SubscribeEvent
    public static void hideMissingPlayerLimbs(
            RenderPlayerEvent.Pre event) {
        if (!(event.getEntity()
                instanceof AbstractClientPlayer player)) {
            return;
        }
        if (GreenDayGameEvents.armMissing(
                player, HumanoidArm.LEFT)) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player,
                    event.getRenderer().getModel(),
                    Part.LEFT_ARM,
                    Part.LEFT_SLEEVE);
        }
        if (GreenDayGameEvents.armMissing(
                player, HumanoidArm.RIGHT)) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player,
                    event.getRenderer().getModel(),
                    Part.RIGHT_ARM,
                    Part.RIGHT_SLEEVE);
        }
    }

    @SubscribeEvent
    public static void hideMissingFirstPersonHand(
            RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        if (GreenDayGameEvents.armMissing(player, arm)) {
            event.setCanceled(true);
        }
    }
}
