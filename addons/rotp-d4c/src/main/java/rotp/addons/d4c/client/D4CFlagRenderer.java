package rotp.addons.d4c.client;

import rotp.core.client.util.functions.ClientUtil;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.entity.D4CMarkerEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class D4CFlagRenderer
        extends EntityRenderer<D4CMarkerEntity> {
    private static final ResourceLocation TEXTURE =
            D4CAddon.id("textures/entity/flag.png");
    private final D4CFlagModel model;

    public D4CFlagRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new D4CFlagModel(
                context.bakeLayer(D4CFlagModel.LAYER));
    }

    @Override
    public void render(
            D4CMarkerEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        poseStack.pushPose();
        model.setupAnim(
                entity,
                0.0F,
                0.0F,
                partialTick,
                0.0F,
                0.0F);
        poseStack.scale(
                model.renderScaleX(),
                model.renderScaleY(),
                model.renderScaleZ());
        D4CFlagModel.RenderPose modelPose = model.renderPose();
        float poseDeterminant = poseStack.last().pose().determinant();
        RenderType renderType = model.renderType(TEXTURE);
        D4CFlagRenderReceipts.CountingVertexConsumer vertices =
                D4CFlagRenderReceipts.counting(
                        buffer.getBuffer(renderType));
        model.renderToBuffer(
                poseStack,
                vertices,
                ClientUtil.MAX_LIGHT,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
        poseStack.popPose();
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                buffer,
                packedLight);
        D4CFlagRenderReceipts.record(
                this,
                model,
                entity,
                TEXTURE,
                renderType,
                modelPose,
                partialTick,
                poseDeterminant,
                vertices);
    }

    @Override
    public ResourceLocation getTextureLocation(
            D4CMarkerEntity entity) {
        return TEXTURE;
    }
}
