package rotp.addons.pearljam.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.pearljam.entity.PearlJamEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class PearlJamRenderer extends StandEntityRenderer<
        PearlJamEntity,
        StandEntityRenderState,
        StandEntityModel<PearlJamEntity, StandEntityRenderState>> {
    public PearlJamRenderer(
            EntityRendererProvider.Context context) {
        super(context, 0.0F);
    }

    @Override
    public void extractRenderState(
            PearlJamEntity entity,
            StandEntityRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        StandVisualContext visualContext =
                renderState.visualContext;
        if (visualContext != null
                && visualContext.classicObstruction()) {
            renderState.resetObstruction();
        }
    }

    @Override
    public void doRender(
            PearlJamEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light) {
        StandVisualContext visualContext =
                StandVisualContext.world(
                        entity,
                        entity.clientStuff.getAlpha(
                                entity, partialTicks));
        model.setAlpha(1.0F);
        super.doRender(
                entity,
                entityYaw,
                partialTicks,
                poseStack,
                AlphaMultiBufferSource.wrap(
                        bufferSource,
                        visualContext.effectiveAlpha()),
                light);
    }

    @Override
    protected RenderType getRenderType(
            PearlJamEntity entity,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing) {
        ResourceLocation texture = getTextureLocation(entity);
        if (bodyVisible || translucent) {
            return ModRenderTypes.standTranslucentCull(texture);
        }
        return glowing ? RenderType.outline(texture) : null;
    }
}
