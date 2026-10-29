package rotp.addons.badcompany.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.badcompany.entity.BadCompanyOwnedEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

abstract class BadCompanyProjectileRenderer<
        T extends Entity & BadCompanyOwnedEntity>
        extends EntityRenderer<T> {
    private final EntityModel<T> model;

    protected BadCompanyProjectileRenderer(
            EntityRendererProvider.Context context,
            EntityModel<T> model) {
        super(context);
        this.model = model;
    }

    @Override
    public void render(
            T entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                BadCompanyVisuals.context(entity, partialTick);
        if (!ClientGlobals.canSeeStands
                || visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(
                Mth.lerp(
                        partialTick,
                        entity.yRotO,
                        entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.lerp(
                        partialTick,
                        entity.xRotO,
                        entity.getXRot())));
        model.setupAnim(
                entity,
                0.0F,
                0.0F,
                entity.tickCount + partialTick,
                0.0F,
                0.0F);
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(
                        getTextureLocation(entity)));
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
        poseStack.popPose();
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                alphaBuffer,
                packedLight);
    }

    @Override
    public boolean shouldRender(
            T entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        StandVisualContext visualContext =
                BadCompanyVisuals.context(entity, 1.0F);
        return ClientGlobals.canSeeStands
                && visualContext != null
                && visualContext.effectiveAlpha() > 0.0F
                && super.shouldRender(
                        entity,
                        frustum,
                        cameraX,
                        cameraY,
                        cameraZ);
    }

    @Override
    public abstract ResourceLocation getTextureLocation(T entity);
}
