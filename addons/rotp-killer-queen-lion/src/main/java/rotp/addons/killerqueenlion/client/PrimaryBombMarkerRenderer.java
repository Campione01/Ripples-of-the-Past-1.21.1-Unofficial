package rotp.addons.killerqueenlion.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.entity.PrimaryBombMarkerEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class PrimaryBombMarkerRenderer
        extends SimpleEntityRenderer<
                PrimaryBombMarkerEntity,
                PrimaryBombMarkerModel> {
    private static final ResourceLocation LIVING_TEXTURE =
            KillerQueenLionAddon.id(
                    "textures/ability/kq_entitybomb.png");
    private static final ResourceLocation BLOCK_TEXTURE =
            KillerQueenLionAddon.id(
                    "textures/ability/kq_block_bomb.png");

    public PrimaryBombMarkerRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        initTexture(LIVING_TEXTURE, true);
        initModel(new PrimaryBombMarkerModel(
                context.bakeLayer(
                        PrimaryBombMarkerModel.LAYER)));
        offsetModelByEntityHeight(false);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            PrimaryBombMarkerEntity entity) {
        ResourceLocation base = entity.markerKind()
                == PrimaryBombMarkerEntity.Kind.BLOCK
                        ? BLOCK_TEXTURE
                        : LIVING_TEXTURE;
        StandSkin skin = SimpleEntityRenderer.getStandSkin(entity);
        return skin != null ? skin.getTexture(base) : base;
    }

    @Override
    protected boolean shouldRender(
            PrimaryBombMarkerEntity entity) {
        StandVisualContext context =
                KillerQueenVisuals.context(
                        entity.level(),
                        entity.getOwnerUuid(),
                        entity.getStandUuid(),
                        1.0F);
        return ClientGlobals.canSeeStands
                && context != null
                && context.effectiveAlpha() > 0.0F
                && super.shouldRender(entity);
    }

    @Override
    protected void doRender(
            PrimaryBombMarkerEntity entity,
            PrimaryBombMarkerModel model,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext context =
                KillerQueenVisuals.context(
                        entity.level(),
                        entity.getOwnerUuid(),
                        entity.getStandUuid(),
                        partialTick);
        if (!ClientGlobals.canSeeStands
                || context == null
                || context.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer, context.effectiveAlpha());
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
