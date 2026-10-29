package rotp.addons.catchtherainbow.client;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(
        modid = CatchTheRainbowAddon.MOD_ID,
        value = Dist.CLIENT)
public final class CatchTheRainbowClientGameEvents {
    private CatchTheRainbowClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.isPaused()) {
            return;
        }
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(player);
        if (controller == null
                || !controller.isRainWalkingActive()
                || player.isShiftKeyDown()
                || player.isInWaterOrBubble()
                || player.isFallFlying()
                || player.getAbilities().flying) {
            return;
        }
        Vec3 movement = player.getDeltaMovement();
        if (movement.y <= 0.0D) {
            player.setDeltaMovement(
                    movement.x, 0.0D, movement.z);
            player.setOnGround(true);
            player.resetFallDistance();
        }
    }
}
