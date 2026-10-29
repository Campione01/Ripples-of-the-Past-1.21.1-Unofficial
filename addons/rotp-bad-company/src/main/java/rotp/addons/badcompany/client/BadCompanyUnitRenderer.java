package rotp.addons.badcompany.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.badcompany.entity.BadCompanyUnitEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

abstract class BadCompanyUnitRenderer<
        T extends BadCompanyUnitEntity,
        M extends EntityModel<T>> extends MobRenderer<T, M> {
    protected BadCompanyUnitRenderer(
            EntityRendererProvider.Context context,
            M model) {
        super(context, model, 0.0F);
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
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha()),
                packedLight);
    }

    @Override
    protected RenderType getRenderType(
            T entity,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing) {
        return ModRenderTypes.standTranslucentCull(
                getTextureLocation(entity));
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
