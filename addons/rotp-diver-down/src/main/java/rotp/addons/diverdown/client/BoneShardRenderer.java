package rotp.addons.diverdown.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.BoneShardEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class BoneShardRenderer
        extends SimpleEntityRenderer<
                BoneShardEntity, BoneShardModel> {
    public BoneShardRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        initTexture(
                DiverDownAddon.id(
                        "textures/entity/bone_shard.png"),
                false);
        initModel(new BoneShardModel(
                context.bakeLayer(
                        BoneShardModel.LAYER_LOCATION)));
        offsetModelByEntityHeight(false);
        shadowRadius = 0.0F;
    }

    @Override
    protected boolean shouldRender(BoneShardEntity entity) {
        StandVisualContext visualContext =
                DiverDownAdditionalVisuals.context(
                        entity.level(),
                        entity.getControllerUuid(),
                        entity.getStandUuid(),
                        1.0F);
        return ClientGlobals.canSeeStands
                && visualContext != null
                && visualContext.effectiveAlpha() > 0.0F
                && super.shouldRender(entity);
    }

    @Override
    protected void doRender(
            BoneShardEntity entity,
            BoneShardModel model,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                DiverDownAdditionalVisuals.context(
                        entity.level(),
                        entity.getControllerUuid(),
                        entity.getStandUuid(),
                        partialTick);
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
                ModRenderTypes.standTranslucentCull(
                        getTextureLocation(entity)));
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
    }
}
