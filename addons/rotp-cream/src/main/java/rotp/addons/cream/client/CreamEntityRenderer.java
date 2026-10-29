package rotp.addons.cream.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.cream.entity.CreamEntity;
import rotp.addons.cream.entity.CreamEntity.VoidMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class CreamEntityRenderer
        extends StandEntityRenderer<
                CreamEntity,
                CreamRenderState,
                StandEntityModel<CreamEntity, CreamRenderState>> {
    private boolean secondaryVoidPass;

    public CreamEntityRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public CreamRenderState createRenderState() {
        return new CreamRenderState();
    }

    @Override
    public void extractRenderState(
            CreamEntity entity,
            CreamRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.voidMode = entity.getVoidMode();
    }

    @Override
    public void render(
            CreamEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light) {
        super.render(
                entity,
                entityYaw,
                partialTicks,
                poseStack,
                bufferSource,
                light);
        if (entity.getVoidMode() == VoidMode.NONE) {
            return;
        }

        StandVisualContext context =
                StandVisualContext.world(entity, 1.0F);
        float alpha = context.effectiveAlpha() * 0.35F;
        if (alpha <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        secondaryVoidPass = true;
        try {
            super.render(
                    entity,
                    entityYaw,
                    partialTicks,
                    poseStack,
                    AlphaMultiBufferSource.wrap(
                            bufferSource, alpha),
                    light);
        }
        finally {
            secondaryVoidPass = false;
            poseStack.popPose();
        }
    }

    @Override
    protected RenderType getRenderType(
            CreamEntity entity,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing) {
        return secondaryVoidPass
                ? ModRenderTypes.standTranslucentCull(
                        getTextureLocation(entity))
                : super.getRenderType(
                        entity,
                        bodyVisible,
                        translucent,
                        glowing);
    }

    @Override
    protected void scale(
            CreamEntity entity,
            PoseStack poseStack,
            float partialTick) {
        super.scale(entity, poseStack, partialTick);
        if (secondaryVoidPass) {
            poseStack.scale(1.08F, 1.08F, 1.08F);
        }
    }
}
