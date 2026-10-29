package rotp.addons.frosttraveler.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public final class CreamProjectileRenderer<T extends Entity>
        extends EntityRenderer<T> {
    private static final ResourceLocation TEXTURE =
            FrostTravelerAddon.id(
                    "textures/particle/meat.png");

    public CreamProjectileRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            T entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                CreamVisuals.context(entity, partialTick);
        if (!ClientGlobals.canSeeStands
                || visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(TEXTURE));
        poseStack.pushPose();
        poseStack.mulPose(
                entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.18F, 0.18F, 0.18F);
        PoseStack.Pose pose = poseStack.last();
        vertex(pose, vertices, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F);
        vertex(pose, vertices, 0.5F, -0.5F, 0.0F, 1.0F, 1.0F);
        vertex(pose, vertices, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F);
        vertex(pose, vertices, -0.5F, 0.5F, 0.0F, 0.0F, 0.0F);
        poseStack.popPose();
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                alphaBuffer,
                packedLight);
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            float x,
            float y,
            float z,
            float u,
            float v) {
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(ClientUtil.MAX_LIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
