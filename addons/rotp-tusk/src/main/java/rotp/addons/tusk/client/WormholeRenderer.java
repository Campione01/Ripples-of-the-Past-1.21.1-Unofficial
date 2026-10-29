package rotp.addons.tusk.client;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.entity.WormholeEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class WormholeRenderer
        extends EntityRenderer<WormholeEntity> {
    private static final ResourceLocation TEXTURE =
            TuskAddon.id("textures/entity/tiny_wormhole.png");
    private final ModelPart portalPlane;

    public WormholeRenderer(EntityRendererProvider.Context context) {
        super(context);
        portalPlane = TuskWormholeRenderUtil.createTinyPortalPlane();
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            WormholeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            WormholeEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                TuskVisuals.forOwnerUuid(
                        entity.level(),
                        entity.getOwnerUuid(),
                        partialTick);
        if (visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource standBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        portalPlane.yRot = (entity.tickCount + partialTick)
                * TuskWormholeRenderUtil.ROTATION_RADIANS_PER_TICK;
        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        TuskWormholeRenderUtil.renderModelPart(
                portalPlane,
                poseStack,
                standBuffer,
                TEXTURE,
                packedLight,
                TuskClientRenderDiagnostics.VisualRoute
                        .DAMAGING_WORMHOLE,
                entity,
                entity.getOwnerUuid());
        poseStack.popPose();
        TuskClientRenderDiagnostics.recordEntityRender(
                TuskClientRenderDiagnostics.VisualRoute.DAMAGING_WORMHOLE,
                entity,
                entity.getOwnerUuid());
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                standBuffer,
                packedLight);
    }
}
