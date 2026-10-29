package rotp.addons.stickyfingers.client;

import org.joml.Matrix4f;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.state.ZipperInGround;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 1.16 ClientEvents in-ground view: forced first person, dense purple
 * fog, and the end sky instead of the in-wall overlay.
 */
@EventBusSubscriber(
        modid = StickyFingersAddon.MOD_ID,
        value = Dist.CLIENT)
public final class StickyFingersInGroundClient {
    private StickyFingersInGroundClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (ZipperInGround.forcesFirstPerson(
                        ZipperInGround.isInGround(player),
                        ZipperInGround.isOpenZipper(minecraft.level
                                .getBlockState(player.blockPosition())))
                && minecraft.options.getCameraType()
                        != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (fogged()) {
            int rgb = ZipperInGround.FOG_COLOR;
            event.setRed((rgb >> 16 & 0xFF) / 255.0F);
            event.setGreen((rgb >> 8 & 0xFF) / 255.0F);
            event.setBlue((rgb & 0xFF) / 255.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (fogged()) {
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(ZipperInGround.FOG_FAR);
            // Fog changes apply only when the event is cancelled.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockOverlay(RenderBlockScreenEffectEvent event) {
        if (ZipperInGround.hidesBlockOverlay(
                ZipperInGround.isInGround(event.getPlayer()),
                ZipperInGround.isOpenZipper(event.getBlockState()))) {
            event.setCanceled(true);
            renderEndSky(event.getPoseStack().last().pose());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(
            ClientPlayerNetworkEvent.LoggingOut event) {
        ZipperInGround.clearClientView();
    }

    // 1.16 used the block above the feet as the head block.
    private static boolean fogged() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return false;
        }
        BlockPos head = player.blockPosition().above();
        return ZipperInGround.fogsView(
                minecraft.level,
                head,
                ZipperInGround.isInGround(player));
    }

    // 1.16 renderEndSky: an end-portal quad across the view.
    private static void renderEndSky(Matrix4f pose) {
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer sky = buffers.getBuffer(RenderType.endPortal());
        sky.addVertex(pose, -1.0F, -1.0F, -0.5F);
        sky.addVertex(pose, 1.0F, -1.0F, -0.5F);
        sky.addVertex(pose, 1.0F, 1.0F, -0.5F);
        sky.addVertex(pose, -1.0F, 1.0F, -0.5F);
        buffers.endBatch(RenderType.endPortal());
    }
}
