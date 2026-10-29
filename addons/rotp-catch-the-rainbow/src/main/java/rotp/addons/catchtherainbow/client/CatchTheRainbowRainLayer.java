package rotp.addons.catchtherainbow.client;

import java.util.UUID;

import rotp.core.api.client.render.EntityPostRenderExtensions;
import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public final class CatchTheRainbowRainLayer
        extends RenderLayer<
                AbstractClientPlayer,
                PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE =
            CatchTheRainbowAddon.id(
                    "textures/entity/projectiles/"
                            + "catch_the_rainbow_rain_blade.png");

    private static long drawCount;
    private static long lastDrawFrame = Long.MIN_VALUE;
    private static UUID lastPlayerUuid;
    private static UUID lastControllerUuid;
    private static float lastLifecycleAlpha;
    private static boolean lastRainWalkingActive;
    private static boolean lastRainExposure;
    private static ResourceLocation lastTexture;

    private final CatchTheRainbowRainModel rainModel;

    public CatchTheRainbowRainLayer(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent,
            CatchTheRainbowRainModel rainModel) {
        super(parent);
        this.rainModel = rainModel;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        CatchTheRainbowEntity controller =
                CatchTheRainbowAddon.controller(player);
        if (!ClientGlobals.canSeeStands
                || controller == null
                || controller.clientStuff == null
                || !RainExposure.isExposedToRain(player)
                || player.onGround()
                        && player.getDeltaMovement()
                                .horizontalDistanceSqr()
                                < 1.0E-4D
                        && !controller.isBladeRainActive()
                        && !controller.isMergeActive()) {
            return;
        }
        float lifecycleAlpha =
                controller.clientStuff.getAlpha(
                        controller, partialTick);
        StandVisualContext visualContext =
                StandVisualContext.world(
                        controller, lifecycleAlpha);
        if (visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        rainModel.setupAnim(
                player,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch);
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(TEXTURE));
        rainModel.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        player, 0.0F),
                0xFFFFFFFF);
        synchronized (CatchTheRainbowRainLayer.class) {
            drawCount++;
            lastDrawFrame = EntityPostRenderExtensions.currentFrameId();
            lastPlayerUuid = player.getUUID();
            lastControllerUuid = controller.getUUID();
            lastLifecycleAlpha = visualContext.effectiveAlpha();
            lastRainWalkingActive = controller.isRainWalkingActive();
            lastRainExposure = RainExposure.isExposedToRain(player);
            lastTexture = TEXTURE;
        }
    }

    public static synchronized void resetDiagnostics() {
        drawCount = 0L;
        lastDrawFrame = Long.MIN_VALUE;
        lastPlayerUuid = null;
        lastControllerUuid = null;
        lastLifecycleAlpha = 0.0F;
        lastRainWalkingActive = false;
        lastRainExposure = false;
        lastTexture = null;
    }

    public static synchronized DiagnosticSnapshot diagnosticSnapshot() {
        return new DiagnosticSnapshot(
                drawCount,
                lastDrawFrame,
                lastPlayerUuid,
                lastControllerUuid,
                lastLifecycleAlpha,
                lastRainWalkingActive,
                lastRainExposure,
                lastTexture);
    }

    public record DiagnosticSnapshot(
            long drawCount,
            long lastDrawFrame,
            UUID lastPlayerUuid,
            UUID lastControllerUuid,
            float lifecycleAlpha,
            boolean rainWalkingActive,
            boolean rainExposure,
            ResourceLocation texture) {}
}
